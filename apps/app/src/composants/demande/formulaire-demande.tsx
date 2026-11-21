"use client";

import type { DemandeDto, PrefectureDto, SubstanceDto, TypeDocumentDto } from "@emine/api-client";
import { Alerte, Bouton, Carte, classesChamp, formaterGnf } from "@emine/ui";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import { type ReactNode, useCallback, useEffect, useRef, useState, useTransition } from "react";
import { useForm, useWatch } from "react-hook-form";
import type { ResultatAction } from "@/serveur/erreurs-formulaire";
import {
  CHAMPS_PAR_ETAPE,
  depuisDemande,
  type Etape,
  ETAPES,
  schemaBrouillon,
  type ValeursBrouillon,
} from "./schema";

/** Délai de l'enregistrement automatique du brouillon (US-03.1). */
export const DELAI_ENREGISTREMENT_MS = 30_000;

export type Demandeur = { prenom: string; nom: string; email: string; organisation: string | null };

export type FormulaireDemandeProps = {
  demande: DemandeDto | null;
  demandeur: Demandeur;
  documents: TypeDocumentDto[];
  prefectures: PrefectureDto[];
  substances: SubstanceDto[];
  typeInitial?: string;
  enregistrer: (id: number | null, v: ValeursBrouillon) => Promise<ResultatAction<DemandeDto>>;
};

type Etat =
  | { nature: "repos" }
  | { nature: "enregistrement" }
  | { nature: "enregistre"; a: Date }
  | { nature: "erreur"; code: string; message: string };

/**
 * Formulaire guidé de demande (US-03.1) : Demandeur, Document, Pièces, Récapitulatif. Le brouillon
 * est enregistré à chaque changement de section et automatiquement toutes les 30 s s'il a changé.
 */
export function FormulaireDemande(props: FormulaireDemandeProps) {
  const { demandeur, documents, prefectures, substances, enregistrer } = props;
  const t = useTranslations("demande");
  const [demande, setDemande] = useState<DemandeDto | null>(props.demande);
  const [etape, setEtape] = useState<Etape>(props.demande ? "document" : "demandeur");
  const [etat, setEtat] = useState<Etat>({ nature: "repos" });
  const [enCours, demarrer] = useTransition();
  const titreEtape = useRef<HTMLHeadingElement>(null);

  const formulaire = useForm<ValeursBrouillon>({
    resolver: zodResolver(schemaBrouillon),
    defaultValues: depuisDemande(props.demande, props.typeInitial ?? ""),
    mode: "onTouched",
  });
  const { control, register, formState, getValues, trigger, reset, setError } = formulaire;
  const codeDocument = useWatch({ control, name: "typeDocumentCode" });
  const document = documents.find((d) => d.code === codeDocument);

  const sauvegarder = useCallback(async (): Promise<boolean> => {
    const valeurs = getValues();
    setEtat({ nature: "enregistrement" });
    const resultat = await enregistrer(demande?.id ?? null, valeurs);
    if (!resultat.ok) {
      for (const c of resultat.erreur.champs) {
        if (c.champ in valeurs) {
          setError(c.champ as keyof ValeursBrouillon, { message: c.message });
        }
      }
      setEtat({ nature: "erreur", code: resultat.erreur.code, message: resultat.erreur.message });
      return false;
    }
    setDemande(resultat.valeur);
    reset(valeurs, { keepValues: true });
    setEtat({ nature: "enregistre", a: new Date() });
    if (demande === null) {
      window.history.replaceState(null, "", `/demandes/${resultat.valeur.id}/modifier`);
    }
    return true;
  }, [demande, enregistrer, getValues, reset, setError]);

  // L'état « modifié » est lu pendant le rendu (abonnement de React Hook Form), puis gardé dans une
  // référence pour la minuterie d'enregistrement automatique.
  const modifie = formState.isDirty;
  const etatMinuterie = useRef({ modifie, enCours });
  useEffect(() => {
    etatMinuterie.current = { modifie, enCours };
  }, [modifie, enCours]);

  // Enregistrement automatique toutes les 30 s, seulement si le brouillon existe et a changé.
  useEffect(() => {
    if (!demande) return;
    const minuterie = setInterval(() => {
      const { modifie: aEnregistrer, enCours: occupe } = etatMinuterie.current;
      if (aEnregistrer && !occupe) {
        void trigger(CHAMPS_PAR_ETAPE.document).then((valide) => {
          if (valide) void sauvegarder();
        });
      }
    }, DELAI_ENREGISTREMENT_MS);
    return () => clearInterval(minuterie);
  }, [demande, sauvegarder, trigger]);

  // Au changement de section (pas au premier affichage), le focus va sur son titre.
  const premierAffichage = useRef(true);
  useEffect(() => {
    if (premierAffichage.current) {
      premierAffichage.current = false;
      return;
    }
    titreEtape.current?.focus();
  }, [etape]);

  function allerA(cible: Etape) {
    demarrer(async () => {
      const avance = ETAPES.indexOf(cible) > ETAPES.indexOf(etape);
      if (etape === "document" && (demande === null ? avance : modifie)) {
        if (!(await trigger(CHAMPS_PAR_ETAPE.document))) return;
        if (!(await sauvegarder())) return;
      }
      setEtape(cible);
    });
  }

  const index = ETAPES.indexOf(etape);
  const precedente = ETAPES[index - 1];
  const suivante = ETAPES[index + 1];
  const accessible = (e: Etape) => e === "demandeur" || e === "document" || demande !== null;

  return (
    <form
      noValidate
      onSubmit={(e) => {
        e.preventDefault();
        if (suivante) allerA(suivante);
      }}
      className="flex flex-col gap-6"
    >
      <nav aria-label={t("etapes.titre")}>
        <ol className="flex flex-wrap gap-2">
          {ETAPES.map((e, i) => (
            <li key={e}>
              <button
                type="button"
                onClick={() => allerA(e)}
                disabled={!accessible(e) || enCours}
                aria-current={e === etape ? "step" : undefined}
                className={`rounded-full border-2 px-4 py-2 font-semibold ${
                  e === etape
                    ? "border-royal bg-royal text-white"
                    : "border-bordure bg-white text-navy"
                } disabled:opacity-60`}
              >
                {i + 1}. {t(`etapes.${e}`)}
              </button>
            </li>
          ))}
        </ol>
      </nav>

      <EtatEnregistrement etat={etat} />

      <Carte>
        <h2 ref={titreEtape} tabIndex={-1} className="mb-4 text-2xl outline-none">
          {t(`etapes.${etape}`)}
        </h2>

        {etape === "demandeur" ? (
          <dl className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-[auto_1fr]">
            <dt className="font-semibold">{t("demandeur.nom")}</dt>
            <dd>
              {demandeur.prenom} {demandeur.nom}
            </dd>
            <dt className="font-semibold">{t("demandeur.email")}</dt>
            <dd>{demandeur.email}</dd>
            <dt className="font-semibold">{t("demandeur.organisation")}</dt>
            <dd>{demandeur.organisation ?? t("demandeur.aucuneOrganisation")}</dd>
          </dl>
        ) : null}

        {etape === "document" ? (
          <div className="flex flex-col gap-5">
            <Champ
              id="typeDocumentCode"
              libelle={t("document.type")}
              erreur={formState.errors.typeDocumentCode?.message}
            >
              <select
                id="typeDocumentCode"
                {...register("typeDocumentCode")}
                className={classesChamp}
                aria-invalid={formState.errors.typeDocumentCode ? true : undefined}
                aria-describedby={
                  formState.errors.typeDocumentCode ? "typeDocumentCode-erreur" : undefined
                }
              >
                <option value="">{t("document.choisir")}</option>
                {documents.map((d) => (
                  <option key={d.code} value={d.code}>
                    {d.libelleFr} ({d.code})
                  </option>
                ))}
              </select>
            </Champ>
            {document ? <ResumeDocument document={document} /> : null}
            <Champ
              id="prefectureId"
              libelle={t("document.prefecture")}
              facultatif={t("facultatif")}
            >
              <select id="prefectureId" {...register("prefectureId")} className={classesChamp}>
                <option value="">{t("document.nonPrecise")}</option>
                {prefectures.map((p) => (
                  <option key={p.id} value={String(p.id)}>
                    {p.nom} ({p.region})
                  </option>
                ))}
              </select>
            </Champ>
            <Champ
              id="substanceCode"
              libelle={t("document.substance")}
              facultatif={t("facultatif")}
            >
              <select id="substanceCode" {...register("substanceCode")} className={classesChamp}>
                <option value="">{t("document.nonPrecise")}</option>
                {substances.map((s) => (
                  <option key={s.code} value={s.code}>
                    {s.libelleFr}
                  </option>
                ))}
              </select>
            </Champ>
            <Champ
              id="superficieHa"
              libelle={t("document.superficie")}
              facultatif={t("facultatif")}
              aide={t("document.superficieAide")}
              erreur={formState.errors.superficieHa?.message}
            >
              <input
                id="superficieHa"
                inputMode="decimal"
                autoComplete="off"
                {...register("superficieHa")}
                className={`${classesChamp} max-w-xs`}
                aria-invalid={formState.errors.superficieHa ? true : undefined}
                aria-describedby={`superficieHa-aide${formState.errors.superficieHa ? " superficieHa-erreur" : ""}`}
              />
            </Champ>
          </div>
        ) : null}

        {etape === "pieces" && demande && document ? (
          <ListePiecesRequises pieces={document.piecesRequises} />
        ) : null}

        {etape === "recapitulatif" && demande && document ? (
          <div className="flex flex-col gap-5">
            <dl className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-[auto_1fr]">
              <dt className="font-semibold">{t("demandeur.nom")}</dt>
              <dd>
                {demandeur.prenom} {demandeur.nom}
                {demandeur.organisation ? ` · ${demandeur.organisation}` : ""}
              </dd>
              <dt className="font-semibold">{t("document.type")}</dt>
              <dd>
                {document.libelleFr} ({document.code})
              </dd>
              <dt className="font-semibold">{t("document.prefecture")}</dt>
              <dd>{demande.prefectureNom ?? t("document.nonPrecise")}</dd>
              <dt className="font-semibold">{t("document.substance")}</dt>
              <dd>{demande.substanceLibelle ?? t("document.nonPrecise")}</dd>
              <dt className="font-semibold">{t("document.superficie")}</dt>
              <dd>
                {demande.superficieHa == null
                  ? t("document.nonPrecise")
                  : t("document.hectares", { valeur: demande.superficieHa })}
              </dd>
              <dt className="font-semibold">{t("document.aPayer")}</dt>
              <dd>
                {document.tarifs.aPayerAuDepotGnf == null
                  ? t("document.gratuit")
                  : formaterGnf(document.tarifs.aPayerAuDepotGnf)}
              </dd>
            </dl>
          </div>
        ) : null}
      </Carte>

      <div className="flex flex-wrap justify-between gap-3">
        {precedente ? (
          <Bouton
            type="button"
            variante="secondaire"
            onClick={() => allerA(precedente)}
            disabled={enCours}
          >
            {t("precedent")}
          </Bouton>
        ) : (
          <span />
        )}
        <div className="flex flex-wrap gap-3">
          {etape !== "demandeur" ? (
            <Bouton
              type="button"
              variante="discret"
              disabled={enCours}
              onClick={() =>
                demarrer(async () => {
                  if (await trigger(CHAMPS_PAR_ETAPE.document)) await sauvegarder();
                })
              }
            >
              {t("enregistrer")}
            </Bouton>
          ) : null}
          {suivante ? (
            <Bouton type="submit" disabled={enCours}>
              {t("continuer")}
            </Bouton>
          ) : null}
        </div>
      </div>
    </form>
  );
}

function EtatEnregistrement({ etat }: { etat: Etat }) {
  const t = useTranslations("demande");
  if (etat.nature === "erreur") {
    const cle = ["DEM-003", "DEM-005", "VAL-001", "PERM-001", "DEM-002"].includes(etat.code)
      ? etat.code
      : "autre";
    return (
      <Alerte nature="erreur" titre={t(`enregistrement.erreurs.${cle}`)}>
        {etat.message || t("enregistrement.reessayer")}
      </Alerte>
    );
  }
  return (
    <p role="status" className="min-h-6 text-texte-doux">
      {etat.nature === "enregistrement" ? t("enregistrement.enCours") : null}
      {etat.nature === "enregistre"
        ? t("enregistrement.fait", {
            heure: etat.a.toLocaleTimeString("fr-FR", {
              hour: "2-digit",
              minute: "2-digit",
              timeZone: "Africa/Conakry",
            }),
          })
        : null}
    </p>
  );
}

function Champ(props: {
  id: string;
  libelle: string;
  facultatif?: string;
  aide?: string;
  erreur?: string;
  children: ReactNode;
}) {
  const t = useTranslations("demande.erreurs");
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={props.id} className="font-semibold">
        {props.libelle}
        {props.facultatif ? (
          <span className="font-normal text-texte-doux"> ({props.facultatif})</span>
        ) : null}
      </label>
      {props.aide ? (
        <p id={`${props.id}-aide`} className="text-sm text-texte-doux">
          {props.aide}
        </p>
      ) : null}
      {props.children}
      {props.erreur ? (
        <p id={`${props.id}-erreur`} className="font-semibold text-danger">
          {t.has(props.erreur) ? t(props.erreur) : props.erreur}
        </p>
      ) : null}
    </div>
  );
}

function ResumeDocument({ document }: { document: TypeDocumentDto }) {
  const t = useTranslations("demande.document");
  return (
    <div className="rounded-lg bg-ice p-4 text-navy">
      <p>
        {t("delai", { jours: document.delaiJours })} ·{" "}
        {document.tarifs.aPayerAuDepotGnf == null
          ? t("gratuit")
          : t("frais", { montant: formaterGnf(document.tarifs.aPayerAuDepotGnf) })}
      </p>
      <p className="mt-2 font-semibold">{t("piecesRequises")}</p>
      <ul className="list-disc pl-6">
        {document.piecesRequises.map((p) => (
          <li key={p}>{p}</li>
        ))}
      </ul>
    </div>
  );
}

function ListePiecesRequises({ pieces }: { pieces: string[] }) {
  const t = useTranslations("demande.document");
  return (
    <div>
      <p className="font-semibold">{t("piecesRequises")}</p>
      <ul className="list-disc pl-6">
        {pieces.map((p) => (
          <li key={p}>{p}</li>
        ))}
      </ul>
    </div>
  );
}
