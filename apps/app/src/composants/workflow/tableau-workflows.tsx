import type { WorkflowDto } from "@emine/api-client";
import { type ComposantLien, formaterDate, TableauDonnees } from "@emine/ui";
import { ChevronRight } from "@emine/ui/icones";
import Link from "next/link";
import { useLocale, useTranslations } from "next-intl";
import { BadgeVersion } from "../gouvernance/gouvernance";
import { libelleDocument, libelleWorkflow } from "../referentiels";

const LienNext: ComposantLien = ({ href, children, ...props }) => (
  <Link href={href} {...props}>
    {children}
  </Link>
);

/** Fourchette de délais : « 2 à 3 jours », ou « 15 jours » si elle est fixe. */
export function useFourchette() {
  const t = useTranslations("workflows");
  return (w: { delaiMinJours: number; delaiMaxJours: number }) =>
    w.delaiMinJours === w.delaiMaxJours
      ? t("jours", { n: w.delaiMinJours })
      : t("fourchette", { min: w.delaiMinJours, max: w.delaiMaxJours });
}

/**
 * Circuit en une ligne : étapes numérotées, séparées par une flèche, qui passent à la ligne sur
 * petit écran. Lu comme une liste ordonnée par les lecteurs d'écran.
 */
export function CircuitCompact({ etapes }: { etapes: WorkflowDto["enVigueur"]["etapes"] }) {
  return (
    <ol className="flex flex-wrap items-center gap-x-1 gap-y-1.5">
      {etapes.map((e, i) => (
        <li key={`${i}-${e.acteur}`} className="flex items-center gap-1">
          {i > 0 ? (
            <ChevronRight aria-hidden="true" className="size-4 shrink-0 text-texte-doux" />
          ) : null}
          <span className="inline-flex items-center gap-1.5 rounded-full bg-ice py-0.5 pr-2.5 pl-1 text-sm text-navy">
            <span
              aria-hidden="true"
              className="flex size-5 items-center justify-center rounded-full bg-white text-xs font-semibold"
            >
              {i + 1}
            </span>
            {e.acteur}
          </span>
        </li>
      ))}
    </ol>
  );
}

/**
 * Documents d'une famille, lisibles partout (mobile compris, rien au seul survol) : code et nom,
 * « désactivé » en clair pour un document qui ne se demande plus.
 */
export function DocumentsFamille({
  documents,
  className,
}: {
  documents: WorkflowDto["documents"];
  className?: string;
}) {
  const t = useTranslations("workflows");
  const langue = useLocale();
  return (
    <ul className={className ?? "flex flex-col gap-0.5 text-sm"}>
      {documents.map((d) => (
        <li key={d.code} className={d.actif ? undefined : "text-texte-doux"}>
          <span className="font-mono font-semibold">{d.code}</span>
          {" · "}
          {libelleDocument(d, langue)}
          {d.actif ? null : <> ({t("desactive")})</>}
        </li>
      ))}
    </ul>
  );
}

/**
 * Les workflows (US-02.5) : famille et ses documents (code et nom), circuit compact, délais,
 * version ; la ligne entière ouvre le workflow.
 */
export function TableauWorkflows({
  lignes,
  legende,
  decideur = false,
}: {
  lignes: WorkflowDto[];
  legende: string;
  /** Le profil connecté valide les propositions (Ministre) : « À valider » en orange. */
  decideur?: boolean;
}) {
  const t = useTranslations("workflows");
  const tReferentiels = useTranslations("referentiels");
  const fourchette = useFourchette();
  return (
    <TableauDonnees
      legende={legende}
      lignes={lignes}
      cleLigne={(w) => w.code}
      Lien={LienNext}
      lienLigne={(w) => `/workflows/${w.code}`}
      colonnes={[
        {
          cle: "famille",
          entete: t("colonnes.famille"),
          rendu: (w) => (
            <span className="flex flex-col gap-0.5">
              <span className="font-semibold text-navy">
                {libelleWorkflow(w.code, w.enVigueur.libelle, tReferentiels)}
              </span>
              <DocumentsFamille documents={w.documents} />
            </span>
          ),
        },
        {
          cle: "etapes",
          entete: t("colonnes.etapes"),
          rendu: (w) => (
            <span className="flex flex-col gap-1">
              <span className="sr-only">{t("etapesNombre", { n: w.enVigueur.etapes.length })}</span>
              <CircuitCompact etapes={w.enVigueur.etapes} />
            </span>
          ),
        },
        {
          cle: "delai",
          entete: t("colonnes.delai"),
          rendu: (w) => (
            <span className="flex flex-col whitespace-nowrap">
              <span>{fourchette(w)}</span>
              {w.delaiMinJours !== w.delaiMaxJours ? (
                <span className="text-sm text-texte-doux">{t("selonDocument")}</span>
              ) : null}
            </span>
          ),
        },
        {
          cle: "version",
          entete: t("colonnes.version"),
          rendu: (w) => (
            <span className="flex flex-col whitespace-nowrap">
              <span>{t("version", { n: w.enVigueur.version })}</span>
              <span className="text-sm text-texte-doux">
                {t("modifieeLe", { date: formaterDate(w.enVigueur.dateEffet) })}
              </span>
              {/* Un seul badge : la proposition en attente ; sinon la version programmée. */}
              {w.aValider ? (
                <span className="mt-1">
                  <BadgeVersion etat="a_valider" decideur={decideur} />
                </span>
              ) : w.programmee ? (
                <span className="text-sm text-royal">
                  {t("programmeeLe", {
                    n: w.programmee.version,
                    date: formaterDate(w.programmee.dateEffet),
                  })}
                </span>
              ) : null}
            </span>
          ),
        },
      ]}
    />
  );
}
