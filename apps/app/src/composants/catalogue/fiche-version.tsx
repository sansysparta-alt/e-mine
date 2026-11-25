import type { ChangementFicheDto, VersionDto } from "@emine/api-client";
import type { TarifAVenirDto } from "@emine/api-client";
import { formaterDate, formaterGnf } from "@emine/ui";

/** Champs du barème (US-02.3), montants en GNF entiers. */
const CHAMPS_MONTANT = ["fraisDossierGnf", "droitsGnf", "attestationGnf"];
import { useTranslations } from "next-intl";
import { libelleProfil, libelleSignataire } from "../referentiels";
import { Fragment } from "react";

/** Valeur d'un champ en texte, traduite pour l'activation et le délai. */
function useValeur() {
  const t = useTranslations("catalogue.fiche");
  return (champ: string, valeur: string | null | undefined) => {
    if (valeur == null || valeur === "") return t("vide");
    if (champ === "actif") return valeur === "true" ? t("actif.oui") : t("actif.non");
    if (champ === "delaiJours") return t("jours", { n: Number(valeur) });
    if (CHAMPS_MONTANT.includes(champ)) return formaterGnf(Number(valeur));
    return valeur;
  };
}

/**
 * Changements d'une version par rapport à la fiche en vigueur : champ, valeur actuelle, valeur
 * proposée (ancienne et nouvelle valeur, cahier des charges diapo 32).
 */
export function TableauChangements({
  changements,
  legende,
}: {
  changements: ChangementFicheDto[];
  legende: string;
}) {
  const t = useTranslations("catalogue.fiche");
  const valeur = useValeur();
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-left">
        <caption className="sr-only">{legende}</caption>
        <thead>
          <tr className="border-b border-bordure text-sm text-texte-doux">
            <th scope="col" className="py-2 pr-4 font-semibold">
              {t("changements.champ")}
            </th>
            <th scope="col" className="py-2 pr-4 font-semibold">
              {t("changements.avant")}
            </th>
            <th scope="col" className="py-2 font-semibold">
              {t("changements.apres")}
            </th>
          </tr>
        </thead>
        <tbody>
          {changements.map((c) => (
            <tr key={c.champ} className="border-b border-bordure align-top last:border-0">
              <th scope="row" className="py-3 pr-4 font-semibold text-navy">
                {t(`champs.${c.champ}`)}
              </th>
              <td className="py-3 pr-4 text-texte-doux">
                <span className="sr-only">{t("changements.avant")} : </span>
                {valeur(c.champ, c.avant)}
              </td>
              <td className="py-3 font-semibold">
                <span className="sr-only">{t("changements.apres")} : </span>
                {valeur(c.champ, c.apres)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/**
 * Contenu d'une fiche, en trois groupes (document, instruction et signature, tarifs) : chaque
 * intitulé porte une aide courte pour qui ne connaît pas le domaine ; les champs vides sont omis.
 */
export function ContenuFiche({ version }: { version: VersionDto }) {
  const t = useTranslations("catalogue.fiche");
  const tReferentiels = useTranslations("referentiels");
  const ligne = (cle: string, contenu: React.ReactNode, aide?: string) => (
    <div className="flex flex-col gap-1 border-b border-bordure py-3 last:border-0 sm:flex-row sm:gap-6">
      <dt className="sm:w-60 sm:shrink-0">
        <span className="font-semibold text-navy">{t(`champs.${cle}`)}</span>
        {aide ? <span className="block text-sm text-texte-doux">{aide}</span> : null}
      </dt>
      <dd className="min-w-0">{contenu}</dd>
    </div>
  );
  const montants = CHAMPS_MONTANT.flatMap((m) => {
    const montant = version[m as "fraisDossierGnf" | "droitsGnf" | "attestationGnf"];
    return montant == null ? [] : [{ m, montant }];
  });
  const groupe = (titre: string, contenu: React.ReactNode) => (
    <section className="flex flex-col gap-1">
      <h3 className="text-sm font-semibold tracking-wide text-texte-doux uppercase">{titre}</h3>
      <dl>{contenu}</dl>
    </section>
  );
  return (
    <div className="flex flex-col gap-6">
      {groupe(
        t("sections.document"),
        <>
          {ligne("libelleFr", version.libelleFr)}
          {version.libelleEn
            ? ligne("libelleEn", <span lang="en">{version.libelleEn}</span>)
            : null}
          {ligne("delaiJours", t("jours", { n: version.delaiJours }), t("aide.delaiJours"))}
          {ligne(
            "pieces",
            <ul className="list-disc pl-5">
              {version.pieces.map((p) => (
                <li key={p}>{p}</li>
              ))}
            </ul>,
            t("aide.pieces"),
          )}
          {version.piecesEn?.length
            ? ligne(
                "piecesEn",
                <ul lang="en" className="list-disc pl-5">
                  {version.piecesEn.map((p) => (
                    <li key={p}>{p}</li>
                  ))}
                </ul>,
              )
            : null}
          {ligne("actif", version.actif ? t("actif.oui") : t("actif.non"))}
        </>,
      )}
      {groupe(
        t("sections.circuit"),
        <>
          {ligne("instruitPar", version.instruitPar, t("aide.instruitPar"))}
          {version.instruitParEn
            ? ligne("instruitParEn", <span lang="en">{version.instruitParEn}</span>)
            : null}
          {ligne(
            "signataire",
            libelleSignataire(version.signataire, tReferentiels),
            t("aide.signataire"),
          )}
          {ligne(
            "avis",
            version.avis.length
              ? version.avis.map((a) => libelleProfil(a.code, a.libelle, tReferentiels)).join(" · ")
              : t("aucunAvis"),
            t("aide.avis"),
          )}
        </>,
      )}
      {montants.length
        ? groupe(
            t("sections.tarifs"),
            <>
              {montants.map(({ m, montant }) => (
                <Fragment key={m}>{ligne(m, formaterGnf(montant))}</Fragment>
              ))}
            </>,
          )
        : null}
    </div>
  );
}

/** Tarifs approuvés dont la date d'effet n'est pas encore arrivée (US-02.3). */
export function TarifsAVenir({ tarifs }: { tarifs: TarifAVenirDto[] }) {
  const t = useTranslations("catalogue.fiche");
  if (!tarifs.length) return null;
  return (
    <ul className="flex flex-col gap-1">
      {tarifs.map((x) => (
        <li key={`${x.objet}-${x.dateEffet}`}>
          {t("tarifsAVenir.ligne", {
            objet: t(`objets.${x.objet}`),
            montant: formaterGnf(x.montantGnf),
            date: formaterDate(x.dateEffet),
          })}
        </li>
      ))}
    </ul>
  );
}

/** « Approuvée le 23/11/2026 par … » ou « Version initiale du catalogue ». */
export function LigneDecision({ version }: { version: VersionDto }) {
  const t = useTranslations("catalogue.fiche");
  if (!version.proposeePar || !version.decideeLe) return <>{t("versionInitiale")}</>;
  return (
    <>
      {t(`decision.${version.statut}`, {
        date: formaterDate(version.decideeLe),
        nom: version.decideePar ?? "",
      })}
    </>
  );
}
