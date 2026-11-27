"use client";

import type { LigneReleveDto, ReleveDto } from "@emine/api-client";
import {
  BadgeStatut,
  classesAuDessusDuLien,
  cn,
  type ComposantLien,
  formaterDate,
  formaterGnf,
  TableauDonnees,
  type TonBadge,
} from "@emine/ui";
import Link from "next/link";
import { useTranslations } from "next-intl";

const LienNext: ComposantLien = ({ href, children, ...props }) => (
  <Link href={href} {...props}>
    {children}
  </Link>
);

/**
 * Badge d'une ligne pour le Responsable fiscal : vert rapprochée (ou validée, associée), orange à
 * vérifier (sa décision est attendue), gris traitée sans encaissement (rejetée, classée).
 */
export function tonLigneReleve(statut: string, decision?: string | null): TonBadge {
  if (decision) return decision === "valide" || decision === "associe" ? "succes" : "neutre";
  if (statut === "rapprochee") return "succes";
  if (statut === "ecart" || statut === "inconnue") return "avertissement";
  return "neutre";
}

/** Relevés importés (US-04.8) : la ligne entière ouvre le relevé et ses résultats. */
export function TableauReleves({ releves }: { releves: ReleveDto[] }) {
  const t = useTranslations("releves");
  return (
    <TableauDonnees
      legende={t("titre")}
      lignes={releves}
      cleLigne={(r) => r.id}
      Lien={LienNext}
      lienLigne={(r) => `/releves/${r.id}`}
      colonnes={[
        {
          cle: "date",
          entete: t("colonnes.date"),
          rendu: (r) => (
            <span className="flex flex-col">
              <span className="font-semibold text-navy">{formaterDate(r.dateReleve)}</span>
              <span className="text-sm text-texte-doux">{r.banque}</span>
            </span>
          ),
        },
        {
          cle: "fichier",
          entete: t("colonnes.fichier"),
          rendu: (r) => <span className="break-all">{r.nomFichier}</span>,
        },
        {
          cle: "lignes",
          entete: t("colonnes.lignes"),
          alignement: "droite",
          rendu: (r) => <span className="whitespace-nowrap tabular-nums">{r.lignes}</span>,
        },
        {
          cle: "rapprochees",
          entete: t("colonnes.rapprochees"),
          alignement: "droite",
          rendu: (r) => <span className="whitespace-nowrap tabular-nums">{r.rapprochees}</span>,
        },
        {
          cle: "aVerifier",
          entete: t("colonnes.aVerifier"),
          alignement: "droite",
          rendu: (r) => {
            const n = r.ecarts + r.inconnues;
            return n > 0 ? (
              <BadgeStatut ton="avertissement">{n}</BadgeStatut>
            ) : (
              <span className="whitespace-nowrap tabular-nums">0</span>
            );
          },
        },
        {
          cle: "importe",
          entete: t("colonnes.importe"),
          rendu: (r) =>
            r.importePar
              ? t("importePar", { date: formaterDate(r.importeLe), nom: r.importePar })
              : t("importeLe", { date: formaterDate(r.importeLe) }),
        },
      ]}
    />
  );
}

/**
 * Lignes d'un relevé (US-04.8), dans l'ordre du fichier : un seul badge (rapprochée ou à vérifier)
 * et le résultat en clair ; le paiement rapproché ouvre la demande concernée.
 */
export function TableauLignesReleve({ lignes }: { lignes: LigneReleveDto[] }) {
  const t = useTranslations("releves.ligne");
  const tTitre = useTranslations("releves.detail");
  return (
    <TableauDonnees
      legende={tTitre("lignes")}
      lignes={lignes}
      cleLigne={(l) => l.id}
      Lien={LienNext}
      colonnes={[
        {
          cle: "numero",
          entete: t("numero"),
          rendu: (l) => (
            <span className="whitespace-nowrap text-texte-doux tabular-nums">{l.numero}</span>
          ),
        },
        {
          cle: "date",
          entete: t("date"),
          rendu: (l) => formaterDate(l.dateOperation),
        },
        {
          cle: "libelle",
          entete: t("libelle"),
          rendu: (l) => <span className="break-words">{l.libelle}</span>,
        },
        {
          cle: "montant",
          entete: t("montant"),
          alignement: "droite",
          rendu: (l) => (
            <span className="flex flex-col items-end">
              <span className="font-semibold whitespace-nowrap tabular-nums">
                {formaterGnf(l.montantGnf)}
              </span>
              <span className="text-sm text-texte-doux">
                {l.sens === "D" ? t("debit") : t("credit")}
              </span>
            </span>
          ),
        },
        {
          cle: "resultat",
          entete: t("resultat"),
          rendu: (l) => (
            <span className="flex flex-col items-start gap-1">
              <BadgeStatut ton={tonLigneReleve(l.statut, l.decision)}>
                {l.decision ? t(`decisions.${l.decision}`) : t(`statuts.${l.statut}`)}
              </BadgeStatut>
              {l.nature ? (
                <span className="text-sm text-texte-doux">
                  {t(`natures.${l.nature}`, {
                    ecart: formaterGnf(Math.abs(l.ecartGnf ?? 0)),
                  })}
                </span>
              ) : null}
              {l.decision && l.motif ? (
                <span className="text-sm text-texte-doux">{t("motif", { motif: l.motif })}</span>
              ) : null}
            </span>
          ),
        },
        {
          cle: "paiement",
          entete: t("paiement"),
          interactive: true,
          rendu: (l) =>
            l.referencePaiement ? (
              <span className="flex flex-col">
                <span className="whitespace-nowrap tabular-nums">{l.referencePaiement}</span>
                {l.demandeId && l.numeroDemande ? (
                  <Link
                    href={`/demandes/${l.demandeId}`}
                    className={cn(
                      classesAuDessusDuLien,
                      "inline-flex min-h-11 items-center text-sm font-semibold whitespace-nowrap text-royal tabular-nums underline-offset-4 hover:underline",
                    )}
                  >
                    {l.numeroDemande}
                  </Link>
                ) : null}
              </span>
            ) : (
              <span className="text-texte-doux">—</span>
            ),
        },
      ]}
    />
  );
}
