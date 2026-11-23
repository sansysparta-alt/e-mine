"use client";

import type {
  PropositionCreeeDto,
  PropositionDto,
  TarifAVenirDto,
  VersionDto,
} from "@emine/api-client";
import { Alerte, Bouton, CaseACocher, Carte, classesChamp } from "@emine/ui";
import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import type { ResultatAction } from "@/serveur/erreurs-formulaire";

/** Bornes communes avec l'API (FicheDocumentController, V18). */
export const BORNES_FICHE = {
  libelleMin: 3,
  libelleMax: 150,
  delaiMax: 365,
  piecesMax: 20,
  pieceMax: 200,
  motifMin: 10,
  motifMax: 2000,
  montantMax: 1_000_000_000_000,
} as const;

/** Pièces saisies une par ligne, sans ligne vide ni doublon (comme l'API). */
export function lignesPieces(texte: string): string[] {
  const vues = new Set<string>();
  const pieces: string[] = [];
  for (const brute of texte.split(/\r?\n/)) {
    const p = brute.trim().replace(/\s+/g, " ");
    if (!p || vues.has(p.toLowerCase())) continue;
    vues.add(p.toLowerCase());
    pieces.push(p);
  }
  return pieces;
}

/** Montant du barème en GNF entiers (vide si le document n'a pas cet objet de paiement). */
const montant = z
  .string()
  .trim()
  .refine(
    (v) =>
      v === "" || (/^\d{1,13}$/.test(v) && Number(v) > 0 && Number(v) <= BORNES_FICHE.montantMax),
  );

/** Objets du barème (US-02.3), dans l'ordre d'affichage. */
export const OBJETS_BAREME = [
  { champ: "fraisDossierGnf", objet: "FRAIS_DOSSIER" },
  { champ: "droitsGnf", objet: "DROITS" },
  { champ: "attestationGnf", objet: "ATTESTATION" },
] as const;

/**
 * Barème de référence du formulaire : le dernier montant approuvé de chaque objet, y compris un
 * montant à venir (une nouvelle proposition ne doit pas l'annuler par mégarde).
 */
export function baremeApprouve(
  enVigueur: VersionDto,
  aVenir: TarifAVenirDto[],
): Record<(typeof OBJETS_BAREME)[number]["champ"], number | null> {
  const dernier = (objet: string) =>
    aVenir
      .filter((x) => x.objet === objet)
      .sort((a, b) => b.dateEffet.localeCompare(a.dateEffet))[0]?.montantGnf;
  return {
    fraisDossierGnf: dernier("FRAIS_DOSSIER") ?? enVigueur.fraisDossierGnf ?? null,
    droitsGnf: dernier("DROITS") ?? enVigueur.droitsGnf ?? null,
    attestationGnf: dernier("ATTESTATION") ?? enVigueur.attestationGnf ?? null,
  };
}

export const schemaFiche = z.object({
  libelleFr: z.string().trim().min(BORNES_FICHE.libelleMin).max(BORNES_FICHE.libelleMax),
  libelleEn: z
    .string()
    .trim()
    .max(BORNES_FICHE.libelleMax)
    .refine((v) => v === "" || v.length >= BORNES_FICHE.libelleMin),
  pieces: z.string().refine((v) => {
    const p = lignesPieces(v);
    return (
      p.length >= 1 &&
      p.length <= BORNES_FICHE.piecesMax &&
      p.every((x) => !x.includes(",") && x.length >= 2 && x.length <= BORNES_FICHE.pieceMax)
    );
  }),
  delaiJours: z.coerce.number().int().min(1).max(BORNES_FICHE.delaiMax),
  instruitPar: z.string().trim().min(2).max(BORNES_FICHE.libelleMax),
  signataire: z.string().min(1),
  avis: z.array(z.string()),
  actif: z.boolean(),
  motif: z.string().trim().min(BORNES_FICHE.motifMin).max(BORNES_FICHE.motifMax),
  fraisDossierGnf: montant,
  droitsGnf: montant,
  attestationGnf: montant,
  tarifsDateEffet: z.string(),
});

type Saisie = z.input<typeof schemaFiche>;
type Valeurs = z.output<typeof schemaFiche>;

export type FormulaireFicheProps = {
  code: string;
  enVigueur: VersionDto;
  /** Tarifs approuvés pas encore en vigueur (US-02.3). */
  tarifsAVenir?: TarifAVenirDto[];
  /** Date du jour à Conakry (AAAA-MM-JJ), borne de la date d'effet. */
  aujourdhui: string;
  signataires: string[];
  profilsAvis: { code: string; libelle: string }[];
  proposer: (
    code: string,
    proposition: PropositionDto,
  ) => Promise<ResultatAction<PropositionCreeeDto>>;
};

/**
 * Proposition d'une nouvelle version de la fiche (US-02.2) : pré-remplie avec la fiche en vigueur ;
 * rien ne s'applique avant l'approbation du Ministre. Le motif est obligatoire (motif de la
 * désactivation si le document est retiré du catalogue).
 */
export function FormulaireFiche({
  code,
  enVigueur,
  tarifsAVenir = [],
  aujourdhui,
  signataires,
  profilsAvis,
  proposer,
}: FormulaireFicheProps) {
  const t = useTranslations("catalogue.formulaire");
  const tChamps = useTranslations("catalogue.fiche.champs");
  const router = useRouter();
  const [erreurApi, setErreurApi] = useState<string | null>(null);
  const [enCours, demarrer] = useTransition();
  const bareme = baremeApprouve(enVigueur, tarifsAVenir);
  const {
    register,
    handleSubmit,
    control,
    setError,
    formState: { errors },
  } = useForm<Saisie, unknown, Valeurs>({
    resolver: zodResolver(schemaFiche),
    defaultValues: {
      libelleFr: enVigueur.libelleFr,
      libelleEn: enVigueur.libelleEn ?? "",
      pieces: enVigueur.pieces.join("\n"),
      delaiJours: enVigueur.delaiJours,
      instruitPar: enVigueur.instruitPar,
      signataire: enVigueur.signataire,
      avis: enVigueur.avis.map((a) => a.code),
      actif: enVigueur.actif,
      fraisDossierGnf: texteMontant(bareme.fraisDossierGnf),
      droitsGnf: texteMontant(bareme.droitsGnf),
      attestationGnf: texteMontant(bareme.attestationGnf),
      tarifsDateEffet: "",
      motif: "",
    },
  });
  const actif = useWatch({ control, name: "actif" });
  const saisieBareme = useWatch({
    control,
    name: ["fraisDossierGnf", "droitsGnf", "attestationGnf"],
  });
  const baremeChange = OBJETS_BAREME.some(
    (o, i) => texteMontant(bareme[o.champ]) !== (saisieBareme[i] ?? "").trim(),
  );
  const signataire = useWatch({ control, name: "signataire" });

  function envoyer(v: Valeurs) {
    if (baremeChange && (!v.tarifsDateEffet || v.tarifsDateEffet < aujourdhui)) {
      setError("tarifsDateEffet", { type: "validation" });
      return;
    }
    setErreurApi(null);
    demarrer(async () => {
      const r = await proposer(code, {
        libelleFr: v.libelleFr,
        libelleEn: v.libelleEn || null,
        pieces: lignesPieces(v.pieces),
        delaiJours: v.delaiJours,
        instruitPar: v.instruitPar,
        signataire: v.signataire,
        avis: profilsAvis.map((p) => p.code).filter((c) => v.avis.includes(c)),
        actif: v.actif,
        fraisDossierGnf: v.fraisDossierGnf ? Number(v.fraisDossierGnf) : null,
        droitsGnf: v.droitsGnf ? Number(v.droitsGnf) : null,
        attestationGnf: v.attestationGnf ? Number(v.attestationGnf) : null,
        tarifsDateEffet: baremeChange ? v.tarifsDateEffet : null,
        motif: v.motif,
      } as PropositionDto);
      if (r.ok) {
        router.push(`/catalogue/${code}?proposee=${r.valeur.numero}`);
        router.refresh();
        return;
      }
      let surChamp = false;
      for (const c of r.erreur.champs) {
        if (c.champ in schemaFiche.shape) {
          setError(c.champ as keyof Saisie, { type: "api", message: c.message });
          surChamp = true;
        }
      }
      if (!surChamp) {
        setErreurApi(r.erreur.code === "INDISPONIBLE" ? t("indisponible") : r.erreur.message);
      }
    });
  }

  const erreur = (champ: keyof Saisie) =>
    errors[champ] ? (
      <p id={`${champ}-erreur`} className="text-sm font-semibold text-danger">
        {errors[champ]?.type === "api" ? errors[champ]?.message : t(`erreurs.${champ}`)}
      </p>
    ) : null;
  const decrit = (champ: keyof Saisie, aide = false) =>
    [aide ? `${champ}-aide` : null, errors[champ] ? `${champ}-erreur` : null]
      .filter(Boolean)
      .join(" ") || undefined;
  const libelle = (champ: string, obligatoire = true) => (
    <label htmlFor={champ} className="font-semibold">
      {tChamps(champ)}
      {obligatoire ? (
        <>
          {" "}
          <span aria-hidden="true" className="text-danger">
            *
          </span>
          <span className="sr-only">({t("obligatoire")})</span>
        </>
      ) : (
        <span className="font-normal text-texte-doux"> ({t("facultatif")})</span>
      )}
    </label>
  );

  return (
    <form onSubmit={handleSubmit(envoyer)} noValidate className="flex flex-col gap-6">
      <Alerte nature="info" titre={t("circuit.titre")}>
        {t("circuit.texte")}
      </Alerte>

      <Carte titre={t("sections.document")}>
        <div className="flex flex-col gap-5">
          <div className="flex flex-col gap-1">
            {libelle("libelleFr")}
            <input
              id="libelleFr"
              {...register("libelleFr")}
              aria-invalid={errors.libelleFr ? true : undefined}
              aria-describedby={decrit("libelleFr")}
              className={classesChamp}
            />
            {erreur("libelleFr")}
          </div>
          <div className="flex flex-col gap-1">
            {libelle("libelleEn", false)}
            <input
              id="libelleEn"
              lang="en"
              {...register("libelleEn")}
              aria-invalid={errors.libelleEn ? true : undefined}
              aria-describedby={decrit("libelleEn")}
              className={classesChamp}
            />
            {erreur("libelleEn")}
          </div>
          <div className="flex flex-col gap-1">
            {libelle("pieces")}
            <p id="pieces-aide" className="text-sm text-texte-doux">
              {t("aide.pieces")}
            </p>
            <textarea
              id="pieces"
              rows={5}
              {...register("pieces")}
              aria-invalid={errors.pieces ? true : undefined}
              aria-describedby={decrit("pieces", true)}
              className={classesChamp}
            />
            {erreur("pieces")}
          </div>
          <div className="flex flex-col gap-1">
            {libelle("delaiJours")}
            <p id="delaiJours-aide" className="text-sm text-texte-doux">
              {t("aide.delai")}
            </p>
            <input
              id="delaiJours"
              type="number"
              inputMode="numeric"
              min={1}
              max={BORNES_FICHE.delaiMax}
              {...register("delaiJours")}
              aria-invalid={errors.delaiJours ? true : undefined}
              aria-describedby={decrit("delaiJours", true)}
              className={`${classesChamp} sm:max-w-40`}
            />
            {erreur("delaiJours")}
          </div>
        </div>
      </Carte>

      <Carte titre={t("sections.circuit")}>
        <div className="flex flex-col gap-5">
          <div className="flex flex-col gap-1">
            {libelle("instruitPar")}
            <input
              id="instruitPar"
              {...register("instruitPar")}
              aria-invalid={errors.instruitPar ? true : undefined}
              aria-describedby={decrit("instruitPar")}
              className={classesChamp}
            />
            {erreur("instruitPar")}
          </div>
          <div className="flex flex-col gap-1">
            {libelle("signataire")}
            <p id="signataire-aide" className="text-sm text-texte-doux">
              {t("aide.signataire")}
            </p>
            <select
              id="signataire"
              {...register("signataire")}
              aria-describedby={decrit("signataire", true)}
              className={`${classesChamp} sm:max-w-sm`}
            >
              {signataires.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
            {erreur("signataire")}
            {signataire !== enVigueur.signataire ? (
              <Alerte nature="avertissement" titre={t("signataireChange.titre")}>
                {t("signataireChange.texte")}
              </Alerte>
            ) : null}
          </div>
          <fieldset className="flex flex-col gap-1" aria-describedby="avis-aide">
            <legend className="font-semibold">{tChamps("avis")}</legend>
            <p id="avis-aide" className="text-sm text-texte-doux">
              {t("aide.avis")}
            </p>
            {profilsAvis.map((p) => (
              <CaseACocher key={p.code} value={p.code} {...register("avis")}>
                {p.libelle}
              </CaseACocher>
            ))}
          </fieldset>
        </div>
      </Carte>

      <Carte titre={t("sections.tarifs")}>
        <div className="flex flex-col gap-5">
          <p className="text-sm text-texte-doux">{t("aide.tarifs")}</p>
          {OBJETS_BAREME.filter((o) => bareme[o.champ] != null).map((o) => (
            <div key={o.champ} className="flex flex-col gap-1">
              {libelle(o.champ)}
              <div className="flex items-center gap-2">
                <input
                  id={o.champ}
                  inputMode="numeric"
                  autoComplete="off"
                  {...register(o.champ)}
                  aria-invalid={errors[o.champ] ? true : undefined}
                  aria-describedby={decrit(o.champ)}
                  className={`${classesChamp} sm:max-w-56`}
                />
                <span aria-hidden="true" className="text-texte-doux">
                  GNF
                </span>
              </div>
              {erreur(o.champ)}
            </div>
          ))}
          {baremeChange ? (
            <div className="flex flex-col gap-1">
              {libelle("tarifsDateEffet")}
              <p id="tarifsDateEffet-aide" className="text-sm text-texte-doux">
                {t("aide.tarifsDateEffet")}
              </p>
              <input
                id="tarifsDateEffet"
                type="date"
                min={aujourdhui}
                {...register("tarifsDateEffet")}
                aria-invalid={errors.tarifsDateEffet ? true : undefined}
                aria-describedby={decrit("tarifsDateEffet", true)}
                className={`${classesChamp} sm:max-w-56`}
              />
              {erreur("tarifsDateEffet")}
            </div>
          ) : null}
        </div>
      </Carte>

      <Carte titre={t("sections.publication")}>
        <div className="flex flex-col gap-5">
          <CaseACocher {...register("actif")}>{t("actif")}</CaseACocher>
          {!actif ? (
            <Alerte nature="avertissement" titre={t("desactivation.titre")}>
              {t("desactivation.texte")}
            </Alerte>
          ) : null}
          <div className="flex flex-col gap-1">
            {libelle("motif")}
            <p id="motif-aide" className="text-sm text-texte-doux">
              {actif ? t("aide.motif") : t("aide.motifDesactivation")}
            </p>
            <textarea
              id="motif"
              rows={4}
              {...register("motif")}
              aria-invalid={errors.motif ? true : undefined}
              aria-describedby={decrit("motif", true)}
              className={classesChamp}
            />
            {erreur("motif")}
          </div>
        </div>
      </Carte>

      {erreurApi ? (
        <Alerte nature="erreur" titre={t("echec")}>
          {erreurApi}
        </Alerte>
      ) : null}

      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Bouton asChild variante="secondaire">
          <Link href={`/catalogue/${code}`}>{t("annuler")}</Link>
        </Bouton>
        <Bouton type="submit" taille="grande" disabled={enCours}>
          {t("envoyer")}
        </Bouton>
      </div>
    </form>
  );
}

/** Montant affiché dans un champ : chiffres seuls, vide s'il n'existe pas. */
function texteMontant(m: number | null | undefined): string {
  return m == null ? "" : String(m);
}
