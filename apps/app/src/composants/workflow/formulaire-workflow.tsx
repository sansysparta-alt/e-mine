"use client";

import type { ModificationDto, VersionCreeeDto, VersionWorkflowDto } from "@emine/api-client";
import { Alerte, Bouton, Carte, classesChamp } from "@emine/ui";
import { ArrowDown, ArrowUp, Plus, Trash2 } from "@emine/ui/icones";
import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { Controller, useFieldArray, useForm } from "react-hook-form";
import { z } from "zod";
import type { ResultatAction } from "@/serveur/erreurs-formulaire";
import { aujourdhuiConakry, ChampDateEffet, dateEffetValide } from "../gouvernance/gouvernance";

/** Bornes communes avec l'API et la base (V20). */
export const ETAPES_MAX = 12;

export const schemaWorkflow = z.object({
  libelle: z.string().trim().min(3).max(150),
  reglePaiement: z.string().trim().min(3).max(300),
  etapes: z
    .array(
      z.object({
        acteur: z.string().trim().min(2).max(100),
        libelle: z.string().trim().min(2).max(150),
      }),
    )
    .min(1)
    .max(ETAPES_MAX),
  motif: z.string().trim().min(10).max(2000),
  dateEffet: z.string().refine((d) => dateEffetValide(d)),
});

type Valeurs = z.infer<typeof schemaWorkflow>;

export type FormulaireWorkflowProps = {
  code: string;
  enVigueur: VersionWorkflowDto;
  enregistrer: (
    code: string,
    modification: ModificationDto,
  ) => Promise<ResultatAction<VersionCreeeDto>>;
};

/**
 * Proposition de modification d'un workflow (US-02.5, US-02.8) : libellé, règle de paiement
 * affichée et étapes ordonnées (ajouter, retirer, monter, descendre), motif et date d'effet
 * obligatoires. Envoyée au Ministre, qui la valide ou la refuse ; les demandes en cours gardent la
 * version de leur dépôt.
 */
export function FormulaireWorkflow({ code, enVigueur, enregistrer }: FormulaireWorkflowProps) {
  const t = useTranslations("workflows.formulaire");
  const tGouvernance = useTranslations("gouvernance");
  const router = useRouter();
  const [erreurApi, setErreurApi] = useState<string | null>(null);
  const [enCours, demarrer] = useTransition();
  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<Valeurs>({
    resolver: zodResolver(schemaWorkflow),
    defaultValues: {
      libelle: enVigueur.libelle,
      reglePaiement: enVigueur.reglePaiement,
      etapes: enVigueur.etapes.map((e) => ({ acteur: e.acteur, libelle: e.libelle })),
      motif: "",
      dateEffet: aujourdhuiConakry(),
    },
  });
  const { fields, append, remove, move } = useFieldArray({ control, name: "etapes" });

  function envoyer(v: Valeurs) {
    setErreurApi(null);
    demarrer(async () => {
      const r = await enregistrer(code, v);
      if (r.ok) {
        router.push(`/workflows/${code}?proposee=${r.valeur.version}`);
        router.refresh();
        return;
      }
      setErreurApi(
        r.erreur.code === "DOC-010"
          ? t("aucunChangement")
          : r.erreur.code === "INDISPONIBLE"
            ? t("indisponible")
            : r.erreur.message,
      );
    });
  }

  const obligatoire = (
    <>
      {" "}
      <span aria-hidden="true" className="text-danger">
        *
      </span>
      <span className="sr-only">({t("obligatoire")})</span>
    </>
  );
  const message = (id: string, present: boolean, texte: string) =>
    present ? (
      <p id={id} className="text-sm font-semibold text-danger">
        {texte}
      </p>
    ) : null;

  return (
    <form onSubmit={handleSubmit(envoyer)} noValidate className="flex flex-col gap-6">
      <Alerte nature="info" titre={t("effet.titre")}>
        {t("effet.texte")}
      </Alerte>

      <Carte titre={t("sections.famille")}>
        <div className="flex flex-col gap-5">
          <div className="flex flex-col gap-1">
            <label htmlFor="libelle" className="font-semibold">
              {t("champs.libelle")}
              {obligatoire}
            </label>
            <input
              id="libelle"
              {...register("libelle")}
              aria-invalid={errors.libelle ? true : undefined}
              aria-describedby={errors.libelle ? "libelle-erreur" : undefined}
              className={classesChamp}
            />
            {message("libelle-erreur", !!errors.libelle, t("erreurs.libelle"))}
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="reglePaiement" className="font-semibold">
              {t("champs.reglePaiement")}
              {obligatoire}
            </label>
            <p id="reglePaiement-aide" className="text-sm text-texte-doux">
              {t("aide.reglePaiement")}
            </p>
            <input
              id="reglePaiement"
              {...register("reglePaiement")}
              aria-invalid={errors.reglePaiement ? true : undefined}
              aria-describedby={
                errors.reglePaiement
                  ? "reglePaiement-aide reglePaiement-erreur"
                  : "reglePaiement-aide"
              }
              className={classesChamp}
            />
            {message("reglePaiement-erreur", !!errors.reglePaiement, t("erreurs.reglePaiement"))}
          </div>
        </div>
      </Carte>

      <Carte titre={t("sections.etapes")}>
        <fieldset className="flex flex-col gap-4">
          <legend className="sr-only">{t("sections.etapes")}</legend>
          <ol className="flex flex-col gap-4">
            {fields.map((f, i) => {
              const err = errors.etapes?.[i];
              return (
                <li
                  key={f.id}
                  className="flex flex-col gap-3 rounded-lg border border-bordure p-4"
                  aria-label={t("etape", { n: i + 1 })}
                >
                  <p className="font-semibold text-navy">{t("etape", { n: i + 1 })}</p>
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                    <div className="flex flex-col gap-1">
                      <label htmlFor={`etapes.${i}.acteur`} className="font-semibold">
                        {t("champs.acteur")}
                        {obligatoire}
                      </label>
                      <input
                        id={`etapes.${i}.acteur`}
                        {...register(`etapes.${i}.acteur`)}
                        aria-invalid={err?.acteur ? true : undefined}
                        className={classesChamp}
                      />
                      {message(`etapes-${i}-acteur-erreur`, !!err?.acteur, t("erreurs.acteur"))}
                    </div>
                    <div className="flex flex-col gap-1">
                      <label htmlFor={`etapes.${i}.libelle`} className="font-semibold">
                        {t("champs.action")}
                        {obligatoire}
                      </label>
                      <input
                        id={`etapes.${i}.libelle`}
                        {...register(`etapes.${i}.libelle`)}
                        aria-invalid={err?.libelle ? true : undefined}
                        className={classesChamp}
                      />
                      {message(`etapes-${i}-libelle-erreur`, !!err?.libelle, t("erreurs.action"))}
                    </div>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Bouton
                      type="button"
                      variante="secondaire"
                      disabled={i === 0}
                      onClick={() => move(i, i - 1)}
                      aria-label={t("monter", { n: i + 1 })}
                    >
                      <ArrowUp aria-hidden="true" className="size-4" />
                    </Bouton>
                    <Bouton
                      type="button"
                      variante="secondaire"
                      disabled={i === fields.length - 1}
                      onClick={() => move(i, i + 1)}
                      aria-label={t("descendre", { n: i + 1 })}
                    >
                      <ArrowDown aria-hidden="true" className="size-4" />
                    </Bouton>
                    <Bouton
                      type="button"
                      variante="secondaire"
                      disabled={fields.length === 1}
                      onClick={() => remove(i)}
                      aria-label={t("retirer", { n: i + 1 })}
                    >
                      <Trash2 aria-hidden="true" className="size-4" />
                    </Bouton>
                  </div>
                </li>
              );
            })}
          </ol>
          {fields.length < ETAPES_MAX ? (
            <div>
              <Bouton
                type="button"
                variante="secondaire"
                onClick={() => append({ acteur: "", libelle: "" })}
              >
                <Plus aria-hidden="true" className="size-4" />
                {t("ajouter")}
              </Bouton>
            </div>
          ) : (
            <p className="text-sm text-texte-doux">{t("maximum", { n: ETAPES_MAX })}</p>
          )}
        </fieldset>
      </Carte>

      <Carte titre={t("sections.motif")}>
        <div className="flex flex-col gap-5">
          <div className="flex flex-col gap-1">
            <label htmlFor="motif" className="font-semibold">
              {t("champs.motif")}
              {obligatoire}
            </label>
            <p id="motif-aide" className="text-sm text-texte-doux">
              {t("aide.motif")}
            </p>
            <textarea
              id="motif"
              rows={3}
              {...register("motif")}
              aria-invalid={errors.motif ? true : undefined}
              aria-describedby={errors.motif ? "motif-aide motif-erreur" : "motif-aide"}
              className={classesChamp}
            />
            {message("motif-erreur", !!errors.motif, t("erreurs.motif"))}
          </div>
          <Controller
            control={control}
            name="dateEffet"
            render={({ field }) => (
              <ChampDateEffet
                id="dateEffet"
                valeur={field.value}
                surChangement={field.onChange}
                erreur={errors.dateEffet ? tGouvernance("champDate.erreur") : undefined}
              />
            )}
          />
        </div>
      </Carte>

      {erreurApi ? (
        <Alerte nature="erreur" titre={t("echec")}>
          {erreurApi}
        </Alerte>
      ) : null}

      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Bouton asChild variante="secondaire">
          <Link href={`/workflows/${code}`}>{t("annuler")}</Link>
        </Bouton>
        <Bouton type="submit" taille="grande" disabled={enCours}>
          {t("enregistrer")}
        </Bouton>
      </div>
    </form>
  );
}
