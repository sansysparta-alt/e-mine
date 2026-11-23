"use client";

import type { ParametresDelaiDto } from "@emine/api-client";
import { Alerte, Bouton, CaseACocher, cn, classesChamp } from "@emine/ui";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import type { ResultatAction } from "@/serveur/erreurs-formulaire";

export type FormulaireDelaisProps = {
  initial: ParametresDelaiDto;
  /** Administrateur : modifie ; Ministre : consulte (champs désactivés, aucun bouton). */
  modifiable: boolean;
  enregistrer: (p: ParametresDelaiDto) => Promise<ResultatAction<ParametresDelaiDto>>;
};

/**
 * Règle des délais (US-02.6) : départ du délai, pauses, seuil « proche du dépassement ». Une
 * modification s'applique aussitôt à tous les calculs (fiches, listes, tableaux de bord).
 */
export function FormulaireDelais({ initial, modifiable, enregistrer }: FormulaireDelaisProps) {
  const t = useTranslations("parametres.delais");
  const router = useRouter();
  const [v, setV] = useState(initial);
  const [erreur, setErreur] = useState<string | null>(null);
  const [succes, setSucces] = useState(false);
  const [enCours, demarrer] = useTransition();
  const pctValide =
    Number.isInteger(v.seuilProchePourcent) &&
    v.seuilProchePourcent >= 1 &&
    v.seuilProchePourcent <= 100;
  const minValide =
    Number.isInteger(v.seuilProcheMinJours) &&
    v.seuilProcheMinJours >= 0 &&
    v.seuilProcheMinJours <= 30;

  function envoyer(e: React.FormEvent) {
    e.preventDefault();
    setSucces(false);
    setErreur(null);
    if (!pctValide || !minValide) return;
    demarrer(async () => {
      const r = await enregistrer(v);
      if (r.ok) {
        setV(r.valeur);
        setSucces(true);
        router.refresh();
      } else {
        setErreur(r.erreur.code === "INDISPONIBLE" ? t("indisponible") : r.erreur.message);
      }
    });
  }

  const changer = (p: Partial<ParametresDelaiDto>) => {
    setSucces(false);
    setV((x) => ({ ...x, ...p }));
  };

  return (
    <form onSubmit={envoyer} noValidate className="flex flex-col gap-5">
      <fieldset className="flex flex-col gap-2" disabled={!modifiable}>
        <legend className="mb-1 font-semibold">{t("depart.titre")}</legend>
        {(["paiement_frais", "depot"] as const).map((d) => (
          <label
            key={d}
            className={cn(
              "flex min-h-11 items-start gap-3 rounded-lg border-2 bg-white p-3",
              v.depart === d ? "border-royal bg-ice" : "border-bordure",
              modifiable && "cursor-pointer hover:border-royal",
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

      <fieldset className="flex flex-col gap-1" disabled={!modifiable}>
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

      <fieldset className="flex flex-col gap-3" disabled={!modifiable}>
        <legend className="mb-1 font-semibold">{t("seuil.titre")}</legend>
        <p id="seuil-aide" className="text-sm text-texte-doux">
          {t("seuil.aide", { pct: v.seuilProchePourcent, min: v.seuilProcheMinJours })}
        </p>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div className="flex flex-col gap-1">
            <label htmlFor="seuil-pct" className="font-semibold">
              {t("seuil.pourcent")}
            </label>
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
              className={`${classesChamp} sm:max-w-40`}
            />
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
              className={`${classesChamp} sm:max-w-40`}
            />
            {minValide ? null : (
              <p id="seuil-min-erreur" className="text-sm font-semibold text-danger">
                {t("seuil.erreurMinimum")}
              </p>
            )}
          </div>
        </div>
      </fieldset>

      {succes ? (
        <Alerte nature="succes" titre={t("enregistre.titre")}>
          {t("enregistre.texte")}
        </Alerte>
      ) : null}
      {erreur ? (
        <Alerte nature="erreur" titre={t("echec")}>
          {erreur}
        </Alerte>
      ) : null}
      {modifiable ? (
        <div>
          <Bouton type="submit" disabled={enCours}>
            {t("enregistrer")}
          </Bouton>
        </div>
      ) : null}
    </form>
  );
}
