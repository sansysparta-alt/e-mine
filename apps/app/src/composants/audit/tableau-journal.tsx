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
import { formaterValeur } from "./journal";

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
  const tStatut = useTranslations("statuts");
  const tStatutPaiement = useTranslations("statutsPaiement");
  const traductions = {
    statuts: tStatut,
    statutsPaiement: tStatutPaiement,
    oui: t("oui"),
    non: t("non"),
  };
  const champ = (c: string) => (t.has(`champs.${c}`) ? t(`champs.${c}`) : c);

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
                  <span className="text-sm text-texte-doux">{e.auteurProfil}</span>
                ) : null}
              </span>
            ) : (
              <span className="inline-flex items-center gap-1 text-texte-doux">
                <Cpu aria-hidden="true" className="size-4" />
                {t("systeme")}
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
          rendu: (e) => {
            if (e.changements.length === 0) {
              return <span className="text-texte-doux">{t("voirDetail")}</span>;
            }
            const affiches = e.changements.slice(0, RESUME_MAX);
            const reste = e.changements.length - affiches.length;
            return (
              <ul className="flex flex-col gap-1">
                {affiches.map((c) => (
                  <li key={c.champ}>
                    <span className="font-semibold">{champ(c.champ)}</span>
                    {" : "}
                    {formaterValeur(e.table, c.champ, c.avant, traductions)}
                    <span aria-hidden="true"> → </span>
                    <span className="sr-only"> {t("devient")} </span>
                    {formaterValeur(e.table, c.champ, c.apres, traductions)}
                  </li>
                ))}
                {reste > 0 ? (
                  <li className="text-sm text-texte-doux">{t("autres", { nombre: reste })}</li>
                ) : null}
              </ul>
            );
          },
        },
      ]}
    />
  );
}
