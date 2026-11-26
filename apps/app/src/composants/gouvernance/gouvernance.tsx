import { BadgeStatut, Carte, classesChamp, formaterDate, type TonBadge } from "@emine/ui";
import { useTranslations } from "next-intl";
import type { ReactNode } from "react";

/**
 * Gouvernance des paramètres (lot 4, US-02.8) : une version est proposée par l'administrateur,
 * validée par le Ministre, puis appliquée à sa date d'effet. Présentation commune aux workflows et
 * à la règle des délais (même circuit que les fiches du catalogue).
 */

/** État d'une version, calculé par l'API. */
export type EtatVersion =
  "a_valider" | "en_vigueur" | "programmee" | "remplacee" | "refusee" | "retiree";

/**
 * Badge d'une version : le libellé dit à chacun ce qui le concerne. Proposition : orange « À
 * valider » pour le Ministre (action attendue de lui), gris « En attente du Ministre » pour
 * l'administrateur ; en vigueur : vert ; programmée : bleu ; refusée : rouge ; sinon gris.
 */
export function presentationEtat(etat: string, decideur: boolean): { ton: TonBadge; cle: string } {
  switch (etat) {
    case "a_valider":
      return decideur
        ? { ton: "avertissement", cle: "aValider" }
        : { ton: "neutre", cle: "enAttenteMinistre" };
    case "en_vigueur":
      return { ton: "succes", cle: "enVigueur" };
    case "programmee":
      return { ton: "info", cle: "programmee" };
    case "refusee":
      return { ton: "danger", cle: "refusee" };
    case "retiree":
      return { ton: "neutre", cle: "retiree" };
    default:
      return { ton: "neutre", cle: "remplacee" };
  }
}

/** Version telle que l'API la donne (workflow ou règle des délais). */
export type VersionGouvernee = {
  version: number;
  etat: string;
  motif: string;
  dateEffet: string;
  proposeePar?: string | null;
  proposeeLe: string;
  decideePar?: string | null;
  decideeLe?: string | null;
  motifRefus?: string | null;
};

/** Badge d'état d'une version. */
export function BadgeVersion({ etat, decideur }: { etat: string; decideur: boolean }) {
  const t = useTranslations("gouvernance.etats");
  const p = presentationEtat(etat, decideur);
  return <BadgeStatut ton={p.ton}>{t(p.cle)}</BadgeStatut>;
}

/** « Proposée le … par … · Validée le … par … » : qui a fait quoi, et quand. */
export function TraceVersion({ v }: { v: VersionGouvernee }) {
  const t = useTranslations("gouvernance");
  const morceaux: string[] = [];
  if (v.proposeePar) {
    morceaux.push(t("proposee", { date: formaterDate(v.proposeeLe), nom: v.proposeePar }));
  }
  if (v.decideeLe && v.etat !== "a_valider") {
    const cle = v.etat === "refusee" ? "refusee" : v.etat === "retiree" ? "retiree" : "validee";
    morceaux.push(
      v.decideePar
        ? t(`decision.${cle}`, { date: formaterDate(v.decideeLe), nom: v.decideePar })
        : v.proposeePar
          ? t(`decision.${cle}SansNom`, { date: formaterDate(v.decideeLe) })
          : t("decision.initiale"),
    );
  }
  return <>{morceaux.join(" · ")}</>;
}

/**
 * Proposition en attente : qui l'a faite, quand elle s'appliquerait, pourquoi ; son contenu
 * (`children`) ; puis la décision (`decision`) attendue du Ministre ou le retrait possible.
 */
export function CarteProposition({
  v,
  decideur,
  children,
  decision,
}: {
  v: VersionGouvernee;
  decideur: boolean;
  children: ReactNode;
  decision?: ReactNode;
}) {
  const t = useTranslations("gouvernance");
  return (
    <Carte titre={t("proposition.titre", { n: v.version })}>
      <div className="flex flex-col gap-4">
        <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center sm:gap-4">
          <BadgeVersion etat={v.etat} decideur={decideur} />
          <span>
            <TraceVersion v={v} />
          </span>
        </div>
        <p className="text-sm text-texte-doux">
          {decideur ? t("proposition.aideMinistre") : t("proposition.aideAdmin")}
        </p>
        <p className="font-semibold">{t("dateEffet", { date: formaterDate(v.dateEffet) })}</p>
        <div>
          <h3 className="font-semibold text-navy">{t("proposition.motif")}</h3>
          <p className="whitespace-pre-line">{v.motif}</p>
        </div>
        {children}
        {decision}
      </div>
    </Carte>
  );
}

/** Historique : une ligne par version (la plus récente d'abord), contenu repliable. */
export function HistoriqueVersions<V extends VersionGouvernee>({
  versions,
  decideur,
  contenu,
  titre,
}: {
  versions: V[];
  decideur: boolean;
  contenu: (v: V) => ReactNode;
  /** Titre de la carte, quand plusieurs règles se partagent la page ; « Historique des versions » par défaut. */
  titre?: string;
}) {
  const t = useTranslations("gouvernance");
  return (
    <Carte titre={titre ?? t("historique")}>
      <ol className="flex flex-col">
        {versions.map((v) => (
          <li
            key={v.version}
            className="flex flex-col gap-2 border-b border-bordure py-4 first:pt-0 last:border-0 last:pb-0"
          >
            <div className="flex flex-wrap items-center gap-3">
              <span className="font-semibold text-navy">{t("version", { n: v.version })}</span>
              <BadgeVersion etat={v.etat} decideur={decideur} />
            </div>
            <p className="text-sm text-texte-doux">
              <TraceVersion v={v} />
              {v.etat === "en_vigueur" ||
              v.etat === "programmee" ||
              v.etat === "remplacee" ||
              v.etat === "a_valider" ? (
                <>
                  {v.proposeePar || v.decideeLe ? " · " : null}
                  {t("depuis", { date: formaterDate(v.dateEffet) })}
                </>
              ) : null}
            </p>
            <p className="whitespace-pre-line">{v.motif}</p>
            {v.motifRefus ? (
              <p>
                <span className="font-semibold">{t("motifRefus")} : </span>
                {v.motifRefus}
              </p>
            ) : null}
            <details>
              <summary className="inline-flex min-h-11 cursor-pointer items-center text-royal">
                {t("voirContenu", { n: v.version })}
              </summary>
              <div className="pt-2">{contenu(v)}</div>
            </details>
          </li>
        ))}
      </ol>
    </Carte>
  );
}

/** Date du jour à l'heure de Conakry (UTC+0), au format AAAA-MM-JJ d'un champ date. */
export function aujourdhuiConakry(maintenant = new Date()): string {
  return maintenant.toISOString().slice(0, 10);
}

/** La date d'effet est-elle acceptable (aujourd'hui ou plus tard) ? */
export function dateEffetValide(date: string, aujourdhui = aujourdhuiConakry()): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(date) && date >= aujourdhui;
}

/** Champ « Date d'effet » d'une proposition, avec son aide et son erreur. */
export function ChampDateEffet({
  id,
  valeur,
  surChangement,
  erreur,
}: {
  id: string;
  valeur: string;
  surChangement: (v: string) => void;
  erreur?: string;
}) {
  const t = useTranslations("gouvernance.champDate");
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="font-semibold text-navy">
        {t("libelle")}
      </label>
      <p id={`${id}-aide`} className="text-sm text-texte-doux">
        {t("aide")}
      </p>
      <input
        id={id}
        type="date"
        min={aujourdhuiConakry()}
        value={valeur}
        onChange={(e) => surChangement(e.target.value)}
        aria-describedby={`${id}-aide${erreur ? ` ${id}-erreur` : ""}`}
        aria-invalid={erreur ? true : undefined}
        className={`${classesChamp} max-w-60`}
      />
      {erreur ? (
        <p id={`${id}-erreur`} className="text-sm font-semibold text-danger">
          {erreur}
        </p>
      ) : null}
    </div>
  );
}
