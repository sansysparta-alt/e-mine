import type { EntreeJournalDto } from "@emine/api-client";
import {
  classesAuDessusDuLien,
  type ComposantLien,
  formaterDateHeure,
  TableauDonnees,
} from "@emine/ui";
import { Cpu } from "@emine/ui/icones";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { libelleProfil } from "../referentiels";
import { estTechnique, formaterValeur, libelleChamp, trierChangements } from "./journal";

const LienNext: ComposantLien = ({ href, children, ...props }) => (
  <Link href={href} {...props}>
    {children}
  </Link>
);

/** Nombre de champs modifiés résumés dans la liste (le détail est dans la fiche). */
const RESUME_MAX = 2;

/**
 * Journal d'audit (US-11.7) : date, auteur (ou système), action, objet et résumé des champs
 * modifiés ; la ligne entière ouvre la fiche de l'entrée, le lien vers la demande reste
 * utilisable au-dessus.
 */
export function TableauJournal({
  entrees,
  legende,
}: {
  entrees: EntreeJournalDto[];
  legende: string;
}) {
  const t = useTranslations("audit");
  const tReferentiels = useTranslations("referentiels");
  const tStatut = useTranslations("statuts");
  const tStatutPaiement = useTranslations("statutsPaiement");
  const tValeurs = useTranslations("audit.valeurs");
  const traductions = {
    statuts: tStatut,
    statutsPaiement: tStatutPaiement,
    oui: t("oui"),
    non: t("non"),
    valeurs: tValeurs,
    jours: (n: number) => tValeurs("jours", { n }),
  };
  const champ = (c: string) => libelleChamp(c, t);

  /** Un changement : « Champ : avant → après », ou « Champ : valeur (renseigné) » s'il était vide. */
  const ligneChangement = (e: EntreeJournalDto, c: EntreeJournalDto["changements"][number]) => (
    <li key={c.champ}>
      <span className="font-semibold">{champ(c.champ)}</span>
      {" : "}
      {c.avant == null && e.operation === "U" ? (
        <>
          {formaterValeur(e.table, c.champ, c.apres, traductions)}{" "}
          <span className="text-sm text-texte-doux">({t("renseigne")})</span>
        </>
      ) : (
        <>
          {formaterValeur(e.table, c.champ, c.avant, traductions)}
          <span aria-hidden="true"> → </span>
          <span className="sr-only"> {t("devient")} </span>
          {formaterValeur(e.table, c.champ, c.apres, traductions)}
        </>
      )}
    </li>
  );

  return (
    <TableauDonnees
      legende={legende}
      lignes={entrees}
      cleLigne={(e) => e.id}
      Lien={LienNext}
      lienLigne={(e) => `/journal-audit/${e.id}`}
      colonnes={[
        {
          cle: "date",
          entete: t("colonnes.date"),
          rendu: (e) => <span className="whitespace-nowrap">{formaterDateHeure(e.date)}</span>,
        },
        {
          cle: "auteur",
          entete: t("colonnes.auteur"),
          rendu: (e) =>
            e.auteur ? (
              <span className="flex flex-col">
                <span>{e.auteur}</span>
                {e.auteurProfil ? (
                  <span className="text-sm text-texte-doux">
                    {libelleProfil(e.auteurProfilCode, e.auteurProfil, tReferentiels)}
                  </span>
                ) : null}
              </span>
            ) : (
              <span className="flex flex-col text-texte-doux">
                <span className="inline-flex items-center gap-1">
                  <Cpu aria-hidden="true" className="size-4" />
                  {t("systeme")}
                </span>
                <span className="text-sm">{t("systemeAide")}</span>
              </span>
            ),
        },
        {
          cle: "action",
          entete: t("colonnes.action"),
          rendu: (e) => (
            <span className="flex flex-col">
              <span className="font-semibold">{t(`operations.${e.operation}`)}</span>
              <span className="text-sm text-texte-doux">
                {t.has(`tables.${e.table}`) ? t(`tables.${e.table}`) : e.table}
              </span>
            </span>
          ),
        },
        {
          cle: "objet",
          entete: t("colonnes.objet"),
          interactive: true,
          rendu: (e) => {
            const libelle = e.objetLibelle ?? (e.objetId ? `#${e.objetId}` : "—");
            return e.demandeId != null ? (
              <Link
                href={`/demandes/${e.demandeId}`}
                className={`${classesAuDessusDuLien} inline-flex min-h-11 items-center text-royal underline underline-offset-4`}
                title={t("ouvrirDemande")}
              >
                {libelle}
              </Link>
            ) : (
              <span className="break-all">{libelle}</span>
            );
          },
        },
        {
          cle: "changements",
          entete: t("colonnes.changements"),
          interactive: true,
          rendu: (e) => {
            if (e.changements.length === 0) {
              return <span className="text-texte-doux">{t("voirDetail")}</span>;
            }
            // L'essentiel d'abord ; le reste (dont les identifiants techniques) est replié.
            const tries = trierChangements(e.changements);
            const essentiels = tries.filter((c) => !estTechnique(c.champ)).slice(0, RESUME_MAX);
            const affiches = essentiels.length ? essentiels : tries.slice(0, 1);
            const reste = tries.filter((c) => !affiches.includes(c));
            return (
              <div className="flex flex-col gap-1">
                <ul className="flex flex-col gap-1">
                  {affiches.map((c) => ligneChangement(e, c))}
                </ul>
                {reste.length > 0 ? (
                  <details className={classesAuDessusDuLien}>
                    <summary className="inline-flex min-h-11 cursor-pointer items-center text-sm text-royal">
                      {t("autres", { nombre: reste.length })}
                    </summary>
                    <ul className="flex flex-col gap-1 pb-2">
                      {reste.map((c) => ligneChangement(e, c))}
                    </ul>
                  </details>
                ) : null}
              </div>
            );
          },
        },
      ]}
    />
  );
}
