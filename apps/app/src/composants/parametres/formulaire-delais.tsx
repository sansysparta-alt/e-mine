"use client";

import type {
  ParametresDelaiDto,
  PropositionRegleDelaiDto,
  VersionProposeeDto,
} from "@emine/api-client";
import { Alerte, Bouton, CaseACocher, cn, classesChamp } from "@emine/ui";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import type { ResultatAction } from "@/serveur/erreurs-formulaire";
import { erreurMotif } from "../instruction/champ-motif";
import { aujourdhuiConakry, ChampDateEffet, dateEffetValide } from "../gouvernance/gouvernance";
import { ChangementsRegleDelais } from "./resume-regle-delais";

export type FormulaireDelaisProps = {
  /** Point de départ : la dernière règle validée (programmée, sinon en vigueur). */
  initial: ParametresDelaiDto;
  proposer: (p: PropositionRegleDelaiDto) => Promise<ResultatAction<VersionProposeeDto>>;
};

/**
 * Proposition d'une nouvelle règle des délais (US-02.6, US-02.8) : départ du délai, pauses, seuil
 * « proche du dépassement », motif et date d'effet. Envoyée au Ministre, qui la valide ou la
 * refuse ; les demandes en cours gardent la règle de leur dépôt.
 */
export function FormulaireDelais({ initial, proposer }: FormulaireDelaisProps) {
  const t = useTranslations("parametres.delais");
  const tGouvernance = useTranslations("gouvernance");
  const router = useRouter();
  const [v, setV] = useState(initial);
  const [motif, setMotif] = useState("");
  const [dateEffet, setDateEffet] = useState(aujourdhuiConakry());
  const [soumis, setSoumis] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);
  const [enCours, demarrer] = useTransition();
  const pctValide =
    Number.isInteger(v.seuilProchePourcent) &&
    v.seuilProchePourcent >= 1 &&
    v.seuilProchePourcent <= 100;
  const minValide =
    Number.isInteger(v.seuilProcheMinJours) &&
    v.seuilProcheMinJours >= 0 &&
    v.seuilProcheMinJours <= 30;
  const problemeMotif = erreurMotif(motif);
  const dateValide = dateEffetValide(dateEffet);

  function envoyer(e: React.FormEvent) {
    e.preventDefault();
    setSoumis(true);
    setErreur(null);
    if (!pctValide || !minValide || problemeMotif || !dateValide) return;
    demarrer(async () => {
      const r = await proposer({ ...v, motif: motif.trim(), dateEffet });
      if (r.ok) {
        router.push(`/parametres?proposee=${r.valeur.version}&regle=delais`);
        router.refresh();
      } else {
        setErreur(
          r.erreur.code === "DOC-010"
            ? t("aucunChangement")
            : r.erreur.code === "INDISPONIBLE"
              ? t("indisponible")
              : r.erreur.message,
        );
      }
    });
  }

  const changer = (p: Partial<ParametresDelaiDto>) => setV((x) => ({ ...x, ...p }));

  return (
    <form onSubmit={envoyer} noValidate className="flex flex-col gap-5">
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-1 font-semibold">{t("depart.titre")}</legend>
        {(["paiement_frais", "depot"] as const).map((d) => (
          <label
            key={d}
            className={cn(
              "flex min-h-11 items-start gap-3 rounded-lg border-2 bg-white p-3",
              v.depart === d ? "border-royal bg-ice" : "border-bordure",
              "cursor-pointer hover:border-royal",
            )}
          >
            <input
              type="radio"
              name="depart"
              value={d}
              checked={v.depart === d}
              onChange={() => changer({ depart: d })}
              className="mt-1 size-5 shrink-0 accent-royal"
            />
            <span className="flex flex-col">
              <span className="font-semibold">{t(`depart.${d}`)}</span>
              <span className="text-sm text-texte-doux">{t(`depart.${d}Aide`)}</span>
            </span>
          </label>
        ))}
      </fieldset>

      <fieldset className="flex flex-col gap-1">
        <legend className="mb-1 font-semibold">{t("pauses.titre")}</legend>
        <CaseACocher
          checked={v.pauseComplement}
          onChange={(e) => changer({ pauseComplement: e.target.checked })}
        >
          {t("pauses.complement")}
        </CaseACocher>
        <CaseACocher
          checked={v.pauseDroits}
          onChange={(e) => changer({ pauseDroits: e.target.checked })}
        >
          {t("pauses.droits")}
        </CaseACocher>
      </fieldset>

      <fieldset className="flex flex-col gap-3">
        <legend className="mb-1 font-semibold">{t("seuil.titre")}</legend>
        <p id="seuil-aide" className="text-sm text-texte-doux">
          {t("seuil.aide", { pct: v.seuilProchePourcent, min: v.seuilProcheMinJours })}
        </p>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div className="flex flex-col gap-1">
            <label htmlFor="seuil-pct" className="font-semibold">
              {t("seuil.pourcent")}
            </label>
            <div className="flex items-center gap-2">
              <input
                id="seuil-pct"
                type="number"
                inputMode="numeric"
                min={1}
                max={100}
                value={Number.isNaN(v.seuilProchePourcent) ? "" : v.seuilProchePourcent}
                onChange={(e) => changer({ seuilProchePourcent: e.target.valueAsNumber })}
                aria-invalid={pctValide ? undefined : true}
                aria-describedby={pctValide ? "seuil-aide" : "seuil-aide seuil-pct-erreur"}
                className={`${classesChamp} w-28`}
              />
              <span className="text-texte-doux">{t("seuil.pourcentUnite")}</span>
            </div>
            {pctValide ? null : (
              <p id="seuil-pct-erreur" className="text-sm font-semibold text-danger">
                {t("seuil.erreurPourcent")}
              </p>
            )}
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="seuil-min" className="font-semibold">
              {t("seuil.minimum")}
            </label>
            <div className="flex items-center gap-2">
              <input
                id="seuil-min"
                type="number"
                inputMode="numeric"
                min={0}
                max={30}
                value={Number.isNaN(v.seuilProcheMinJours) ? "" : v.seuilProcheMinJours}
                onChange={(e) => changer({ seuilProcheMinJours: e.target.valueAsNumber })}
                aria-invalid={minValide ? undefined : true}
                aria-describedby={minValide ? "seuil-aide" : "seuil-aide seuil-min-erreur"}
                className={`${classesChamp} w-28`}
              />
              <span className="text-texte-doux">{t("seuil.joursUnite")}</span>
            </div>
            {minValide ? null : (
              <p id="seuil-min-erreur" className="text-sm font-semibold text-danger">
                {t("seuil.erreurMinimum")}
              </p>
            )}
          </div>
        </div>
      </fieldset>

      <ChangementsRegleDelais
        formulaire
        avant={initial}
        apres={{
          ...v,
          // Saisie en cours non valide : la comparaison garde la valeur validée.
          seuilProchePourcent: pctValide ? v.seuilProchePourcent : initial.seuilProchePourcent,
          seuilProcheMinJours: minValide ? v.seuilProcheMinJours : initial.seuilProcheMinJours,
        }}
      />

      <div className="flex flex-col gap-1">
        <label htmlFor="motif-delais" className="font-semibold">
          {t("motif")}
        </label>
        <p id="motif-delais-aide" className="text-sm text-texte-doux">
          {t("motifAide")}
        </p>
        <textarea
          id="motif-delais"
          rows={3}
          value={motif}
          onChange={(e) => setMotif(e.target.value)}
          aria-invalid={soumis && problemeMotif ? true : undefined}
          aria-describedby={
            soumis && problemeMotif ? "motif-delais-aide motif-delais-erreur" : "motif-delais-aide"
          }
          className={classesChamp}
        />
        {soumis && problemeMotif ? (
          <p id="motif-delais-erreur" className="text-sm font-semibold text-danger">
            {t(`erreursMotif.${problemeMotif}`)}
          </p>
        ) : null}
      </div>

      <ChampDateEffet
        id="date-effet-delais"
        valeur={dateEffet}
        surChangement={setDateEffet}
        erreur={soumis && !dateValide ? tGouvernance("champDate.erreur") : undefined}
      />

      {erreur ? (
        <Alerte nature="erreur" titre={t("echec")}>
          {erreur}
        </Alerte>
      ) : null}
      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Bouton asChild variante="secondaire">
          <Link href="/parametres">{t("annuler")}</Link>
        </Bouton>
        <Bouton type="submit" taille="grande" disabled={enCours}>
          {t("soumettre")}
        </Bouton>
      </div>
    </form>
  );
}
