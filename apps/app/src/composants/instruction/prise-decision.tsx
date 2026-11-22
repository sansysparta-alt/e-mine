"use client";

import type { ResultatDecisionDto } from "@emine/api-client";
import { Alerte, Bouton, cn, Dialogue, formaterGnf } from "@emine/ui";
import { CircleCheck, CircleX } from "@emine/ui/icones";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useId, useState, useTransition } from "react";
import type { ResultatAction } from "@/serveur/erreurs-formulaire";
import {
  adresseApresAction,
  type ContexteSerie,
  type ResultatAction as Resultat,
} from "../travail/file-travail";
import { ChampMotif, erreurMotif, MOTIF_MAX } from "./champ-motif";

export type PriseDecisionProps = {
  demandeId: number;
  numero: string;
  /** Libellé du signataire du catalogue (D4), rappelé à la personne qui décide. */
  signataire: string;
  /** Droits à payer après une décision favorable, null si le document n'en a pas. */
  droitsGnf: number | null;
  decider: (
    id: number,
    favorable: boolean,
    motif: string | null,
  ) => Promise<ResultatAction<ResultatDecisionDto>>;
  /** Traitement en série : après la décision, le dossier suivant de la file s'ouvre. */
  serie?: ContexteSerie | null;
};

type Sens = "favorable" | "rejet";

/**
 * Décision du signataire (US-05.4) : choix du sens (deux grandes options, conséquence affichée),
 * motif obligatoire pour un rejet ou observations facultatives, puis confirmation dans une boîte de
 * dialogue (décision définitive). Après la décision, retour à la file des décisions.
 */
export function PriseDecision({
  demandeId,
  numero,
  signataire,
  droitsGnf,
  decider,
  serie,
}: PriseDecisionProps) {
  const t = useTranslations("instruction.decision");
  const tMotif = useTranslations("instruction.motif");
  const id = useId();
  const router = useRouter();
  const [sens, setSens] = useState<Sens | null>(null);
  const [erreurSens, setErreurSens] = useState(false);
  const [motif, setMotif] = useState("");
  const [erreurChamp, setErreurChamp] = useState<string | undefined>();
  const [confirmation, setConfirmation] = useState(false);
  const [erreurApi, setErreurApi] = useState<string | null>(null);
  const [enCours, demarrer] = useTransition();

  function verifier(): boolean {
    if (!sens) {
      setErreurSens(true);
      return false;
    }
    const probleme = erreurMotif(motif);
    if (sens === "rejet" && probleme) {
      setErreurChamp(tMotif(`erreurs.${probleme}`));
      return false;
    }
    if (sens === "favorable" && motif.trim().length > MOTIF_MAX) {
      setErreurChamp(tMotif("erreurs.long"));
      return false;
    }
    setErreurChamp(undefined);
    return true;
  }

  function envoyer() {
    if (!sens) return;
    setErreurApi(null);
    demarrer(async () => {
      const resultat = await decider(demandeId, sens === "favorable", motif.trim() || null);
      if (resultat.ok) {
        router.push(
          adresseApresAction(serie ?? null, "a-decider", {
            numero,
            resultat: resultat.valeur.statut as Resultat,
          }),
        );
        return;
      }
      setConfirmation(false);
      const champ = resultat.erreur.champs.find((c) => c.champ === "motif");
      if (champ) {
        setErreurChamp(champ.message);
        return;
      }
      setErreurApi(
        resultat.erreur.code === "INDISPONIBLE" ? t("indisponible") : resultat.erreur.message,
      );
      router.refresh();
    });
  }

  const consequence =
    sens === "favorable"
      ? droitsGnf != null
        ? t("consequence.droits", { montant: formaterGnf(droitsGnf) })
        : t("consequence.sansDroits")
      : sens === "rejet"
        ? t("consequence.rejet")
        : null;

  return (
    <div className="flex flex-col gap-4">
      <p className="text-texte-doux">{t("signataire", { signataire })}</p>
      <fieldset
        className="flex flex-col gap-3"
        aria-describedby={erreurSens ? `${id}-erreur-sens` : undefined}
      >
        <legend className="mb-2 font-semibold">{t("sens")}</legend>
        <div className="grid grid-cols-1 gap-3">
          {(["favorable", "rejet"] as const).map((s) => {
            const Icone = s === "favorable" ? CircleCheck : CircleX;
            const choisi = sens === s;
            return (
              <label
                key={s}
                className={cn(
                  "flex min-h-11 cursor-pointer items-start gap-3 rounded-lg border-2 bg-white p-4",
                  choisi ? "border-royal bg-ice" : "border-bordure hover:border-royal",
                )}
              >
                <input
                  type="radio"
                  name={`${id}-sens`}
                  value={s}
                  checked={choisi}
                  onChange={() => {
                    setSens(s);
                    setErreurSens(false);
                    setErreurChamp(undefined);
                  }}
                  className="mt-1 size-5 shrink-0 accent-royal"
                />
                <span className="flex flex-col gap-1">
                  <span className="flex items-center gap-2 font-semibold">
                    <Icone
                      aria-hidden="true"
                      className={cn("size-5", s === "favorable" ? "text-succes" : "text-danger")}
                    />
                    {t(`options.${s}`)}
                  </span>
                  <span className="text-sm text-texte-doux">{t(`options.${s}Aide`)}</span>
                </span>
              </label>
            );
          })}
        </div>
        {erreurSens ? (
          <p id={`${id}-erreur-sens`} className="font-semibold text-danger">
            {t("sensRequis")}
          </p>
        ) : null}
      </fieldset>

      {consequence ? (
        <Alerte nature={sens === "rejet" ? "avertissement" : "info"} titre={t("consequence.titre")}>
          {consequence}
        </Alerte>
      ) : null}

      {sens ? (
        <ChampMotif
          id={`${id}-motif`}
          libelle={sens === "rejet" ? t("motifRejet") : t("observations")}
          aide={sens === "rejet" ? t("motifRejetAide") : t("observationsAide")}
          obligatoire={sens === "rejet"}
          valeur={motif}
          surChangement={(v) => {
            setMotif(v);
            if (erreurChamp) setErreurChamp(undefined);
          }}
          erreur={erreurChamp}
        />
      ) : null}

      {erreurApi ? (
        <Alerte nature="erreur" titre={t("echec")}>
          {erreurApi}
        </Alerte>
      ) : null}

      <div>
        <Bouton
          type="button"
          taille="grande"
          disabled={enCours}
          onClick={() => {
            if (verifier()) setConfirmation(true);
          }}
        >
          {t("enregistrer")}
        </Bouton>
      </div>

      <Dialogue
        ouvert={confirmation}
        titre={
          sens === "rejet"
            ? t("confirmation.titreRejet", { numero })
            : t("confirmation.titreFavorable", { numero })
        }
        surFermeture={enCours ? undefined : () => setConfirmation(false)}
        actions={
          <>
            <Bouton
              type="button"
              variante="secondaire"
              onClick={() => setConfirmation(false)}
              disabled={enCours}
            >
              {t("confirmation.annuler")}
            </Bouton>
            <Bouton type="button" onClick={envoyer} disabled={enCours}>
              {enCours ? t("enCours") : t("confirmation.confirmer")}
            </Bouton>
          </>
        }
      >
        <div className="flex flex-col gap-2">
          {consequence ? <p>{consequence}</p> : null}
          {motif.trim() ? (
            <blockquote className="border-l-2 border-bordure pl-3 whitespace-pre-line text-texte-doux">
              {motif.trim()}
            </blockquote>
          ) : null}
          <p className="font-semibold">{t("confirmation.definitive")}</p>
        </div>
      </Dialogue>
    </div>
  );
}
