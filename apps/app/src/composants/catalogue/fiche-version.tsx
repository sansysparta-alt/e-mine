import type { ChangementFicheDto, VersionDto } from "@emine/api-client";
import { formaterDate } from "@emine/ui";
import { useTranslations } from "next-intl";

/** Valeur d'un champ en texte, traduite pour l'activation et le délai. */
function useValeur() {
  const t = useTranslations("catalogue.fiche");
  return (champ: string, valeur: string | null | undefined) => {
    if (valeur == null || valeur === "") return t("vide");
    if (champ === "actif") return valeur === "true" ? t("actif.oui") : t("actif.non");
    if (champ === "delaiJours") return t("jours", { n: Number(valeur) });
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

/** Contenu d'une fiche (version en vigueur) : liste de définitions lisible au lecteur d'écran. */
export function ContenuFiche({ version }: { version: VersionDto }) {
  const t = useTranslations("catalogue.fiche");
  const ligne = (libelle: string, contenu: React.ReactNode) => (
    <div className="flex flex-col gap-1 border-b border-bordure py-3 last:border-0 sm:flex-row sm:gap-6">
      <dt className="font-semibold text-navy sm:w-56 sm:shrink-0">{libelle}</dt>
      <dd className="min-w-0">{contenu}</dd>
    </div>
  );
  return (
    <dl>
      {ligne(t("champs.libelleFr"), version.libelleFr)}
      {ligne(t("champs.libelleEn"), version.libelleEn ?? t("vide"))}
      {ligne(t("champs.delaiJours"), t("jours", { n: version.delaiJours }))}
      {ligne(
        t("champs.pieces"),
        <ul className="list-disc pl-5">
          {version.pieces.map((p) => (
            <li key={p}>{p}</li>
          ))}
        </ul>,
      )}
      {ligne(t("champs.instruitPar"), version.instruitPar)}
      {ligne(t("champs.signataire"), version.signataire)}
      {ligne(
        t("champs.avis"),
        version.avis.length ? version.avis.map((a) => a.libelle).join(" · ") : t("aucunAvis"),
      )}
      {ligne(t("champs.actif"), version.actif ? t("actif.oui") : t("actif.non"))}
    </dl>
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
