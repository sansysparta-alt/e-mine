"use client";

import { Alerte, Bouton, Dialogue } from "@emine/ui";
import { CircleCheck, CircleX, Undo2 } from "@emine/ui/icones";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useId, useState, useTransition } from "react";
import type { ResultatAction } from "@/serveur/erreurs-formulaire";
import { ChampMotif, erreurMotif } from "../instruction/champ-motif";

export type DecisionVersionProps = {
  code: string;
  numero: number;
  /** DECIDER : le Ministre approuve ou refuse ; ATTENDRE : l'administrateur peut retirer. */
  action: "DECIDER" | "ATTENDRE";
  /** La proposition change le signataire (valeur juridique des documents). */
  changeSignataire: boolean;
  /** La proposition retire le document du catalogue. */
  desactive: boolean;
  decider: (
    code: string,
    numero: number,
    decision: "approuver" | "refuser" | "retirer",
    motif: string | null,
  ) => Promise<ResultatAction<null>>;
  /** Page ouverte après la décision (défaut : la fiche du catalogue), sans « ?resultat=… ». */
  adresseApres?: string;
  /** Texte de la confirmation de validation (défaut : publication de la fiche). */
  texteApprobation?: string;
  /**
   * Ce qui est décidé, nommé dans le titre de la confirmation (« la règle des délais ») : sans lui,
   * le titre dit seulement « la version N » (fiche du catalogue).
   */
  objet?: string;
  /** Effet d’un refus (défaut : « La fiche en vigueur ne change pas. »). */
  texteRefus?: string;
};

type Choix = "approuver" | "refuser" | "retirer";

/**
 * Décision sur une proposition (fiche du catalogue US-02.4, workflow et règle des délais US-02.8) :
 * le Ministre valide (confirmation, rappel des effets) ou refuse avec un motif ; l'administrateur
 * peut retirer sa proposition. Aucune décision n'est automatique ; l'API et la base contrôlent les
 * droits.
 */
export function DecisionVersion({
  code,
  numero,
  action,
  changeSignataire,
  desactive,
  decider,
  adresseApres,
  texteApprobation,
  objet,
  texteRefus,
}: DecisionVersionProps) {
  const t = useTranslations("catalogue.decision");
  const tMotif = useTranslations("instruction.motif");
  const id = useId();
  const router = useRouter();
  const [choix, setChoix] = useState<Choix | null>(null);
  const [motif, setMotif] = useState("");
  const [erreurChamp, setErreurChamp] = useState<string | undefined>();
  const [erreurApi, setErreurApi] = useState<string | null>(null);
  const [enCours, demarrer] = useTransition();

  function ouvrir(c: Choix) {
    setErreurApi(null);
    setErreurChamp(undefined);
    setChoix(c);
  }

  function envoyer() {
    if (!choix) return;
    if (choix === "refuser") {
      const probleme = erreurMotif(motif);
      if (probleme) {
        setErreurChamp(tMotif(`erreurs.${probleme}`));
        return;
      }
    }
    demarrer(async () => {
      const r = await decider(code, numero, choix, choix === "refuser" ? motif : null);
      if (r.ok) {
        const adresse = adresseApres ?? `/catalogue/${code}`;
        router.push(`${adresse}${adresse.includes("?") ? "&" : "?"}resultat=${choix}`);
        router.refresh();
        return;
      }
      const champ = r.erreur.champs.find((c) => c.champ === "motif");
      if (champ) {
        setErreurChamp(champ.message);
        return;
      }
      setChoix(null);
      setErreurApi(r.erreur.code === "INDISPONIBLE" ? t("indisponible") : r.erreur.message);
      router.refresh();
    });
  }

  return (
    <div className="flex flex-col gap-4">
      {erreurApi ? (
        <Alerte nature="erreur" titre={t("echec")}>
          {erreurApi}
        </Alerte>
      ) : null}
      <div className="flex flex-col gap-3 sm:flex-row">
        {action === "DECIDER" ? (
          <>
            <Bouton type="button" taille="grande" onClick={() => ouvrir("approuver")}>
              <CircleCheck aria-hidden="true" className="size-5" />
              {t("approuver")}
            </Bouton>
            <Bouton
              type="button"
              taille="grande"
              variante="secondaire"
              onClick={() => ouvrir("refuser")}
            >
              <CircleX aria-hidden="true" className="size-5" />
              {t("refuser")}
            </Bouton>
          </>
        ) : (
          <Bouton type="button" variante="secondaire" onClick={() => ouvrir("retirer")}>
            <Undo2 aria-hidden="true" className="size-5" />
            {t("retirer")}
          </Bouton>
        )}
      </div>

      <Dialogue
        ouvert={choix !== null}
        titre={
          choix
            ? objet
              ? t(`confirmation.${choix}.titreObjet`, { numero, objet })
              : t(`confirmation.${choix}.titre`, { numero })
            : ""
        }
        surFermeture={enCours ? undefined : () => setChoix(null)}
        actions={
          <>
            <Bouton
              type="button"
              variante="secondaire"
              disabled={enCours}
              onClick={() => setChoix(null)}
            >
              {t("annuler")}
            </Bouton>
            <Bouton type="button" disabled={enCours} onClick={envoyer}>
              {choix ? t(`confirmation.${choix}.bouton`) : ""}
            </Bouton>
          </>
        }
      >
        <div className="flex flex-col gap-4">
          <p>
            {choix === "approuver" && texteApprobation
              ? texteApprobation
              : choix === "refuser" && texteRefus
                ? texteRefus
                : choix
                  ? t(`confirmation.${choix}.texte`)
                  : null}
          </p>
          {choix === "approuver" && changeSignataire ? (
            <Alerte nature="avertissement" titre={t("signataire.titre")}>
              {t("signataire.texte")}
            </Alerte>
          ) : null}
          {choix === "approuver" && desactive ? (
            <Alerte nature="avertissement" titre={t("desactivation.titre")}>
              {t("desactivation.texte")}
            </Alerte>
          ) : null}
          {choix === "refuser" ? (
            <ChampMotif
              id={`${id}-motif`}
              libelle={t("motif")}
              aide={t("motifAide")}
              valeur={motif}
              surChangement={(v) => {
                setMotif(v);
                if (erreurChamp) setErreurChamp(undefined);
              }}
              erreur={erreurChamp}
            />
          ) : null}
        </div>
      </Dialogue>
    </div>
  );
}
