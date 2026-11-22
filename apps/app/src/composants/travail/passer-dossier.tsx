"use client";

import { Alerte, Bouton, Dialogue } from "@emine/ui";
import { ArrowRight } from "@emine/ui/icones";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { passerDossier } from "@/serveur/actions-demande";
import { ChampMotif } from "../instruction/champ-motif";
import { adresseFiche, type CleGroupe } from "./file-travail";

/** Bornes du motif de passage, identiques à celles de l'API. */
export const MOTIF_PASSAGE_MIN = 3;
export const MOTIF_PASSAGE_MAX = 300;

export type PasserDossierProps = {
  demandeId: number;
  groupe: CleGroupe;
  suivant: number;
  classes: string;
};

/**
 * « Passer ce dossier » (correctifs ergonomie) : demande un motif court, l'enregistre (visible des
 * autres agents ; « Bloqué » au 3e passage à la même étape), puis ouvre le dossier suivant.
 */
export function PasserDossier({ demandeId, groupe, suivant, classes }: PasserDossierProps) {
  const t = useTranslations("travail.serie.passerDialogue");
  const tSerie = useTranslations("travail.serie");
  const router = useRouter();
  const [ouvert, setOuvert] = useState(false);
  const [motif, setMotif] = useState("");
  const [erreurSaisie, setErreurSaisie] = useState<string | undefined>();
  const [erreur, setErreur] = useState<string | null>(null);
  const [enCours, demarrer] = useTransition();

  function confirmer() {
    const longueur = motif.trim().length;
    if (longueur < MOTIF_PASSAGE_MIN || longueur > MOTIF_PASSAGE_MAX) {
      setErreurSaisie(t(longueur < MOTIF_PASSAGE_MIN ? "motifCourt" : "motifLong"));
      return;
    }
    setErreurSaisie(undefined);
    setErreur(null);
    demarrer(async () => {
      const resultat = await passerDossier(demandeId, motif);
      if (resultat.ok) {
        router.push(adresseFiche(suivant, groupe));
        return;
      }
      setErreur(resultat.erreur.message || t("echec"));
    });
  }

  return (
    <>
      <button type="button" onClick={() => setOuvert(true)} className={classes}>
        {tSerie("passer")}
        <ArrowRight aria-hidden="true" className="size-4" />
      </button>
      <Dialogue
        ouvert={ouvert}
        titre={t("titre")}
        surFermeture={enCours ? undefined : () => setOuvert(false)}
        actions={
          <>
            <Bouton
              type="button"
              variante="secondaire"
              onClick={() => setOuvert(false)}
              disabled={enCours}
            >
              {t("annuler")}
            </Bouton>
            <Bouton type="button" onClick={confirmer} disabled={enCours}>
              {enCours ? t("enCours") : t("confirmer")}
            </Bouton>
          </>
        }
      >
        <div className="flex flex-col gap-3">
          <p>{t("texte")}</p>
          <ChampMotif
            id={`motif-passage-${demandeId}`}
            libelle={t("motif")}
            aide={t("aide")}
            valeur={motif}
            surChangement={setMotif}
            erreur={erreurSaisie}
            max={MOTIF_PASSAGE_MAX}
          />
          {erreur ? (
            <Alerte nature="erreur" titre={t("echec")}>
              {erreur}
            </Alerte>
          ) : null}
        </div>
      </Dialogue>
    </>
  );
}
