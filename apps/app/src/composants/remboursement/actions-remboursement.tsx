"use client";

import type { RemboursementDto } from "@emine/api-client";
import {
  Alerte,
  Bouton,
  classesAuDessusDuLien,
  classesChamp,
  cn,
  Dialogue,
  formaterGnf,
} from "@emine/ui";
import { Ban, CircleCheck, ReceiptText } from "@emine/ui/icones";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useId, useState, useTransition } from "react";
import type { DecisionRemboursement } from "@/serveur/actions-remboursements";
import type { ResultatAction } from "@/serveur/erreurs-formulaire";
import { ChampMotif, erreurMotif } from "../instruction/champ-motif";

export type DeciderRemboursement = (
  id: number,
  decision: DecisionRemboursement,
  texte: string,
) => Promise<ResultatAction<null>>;

/**
 * Actions du profil connecté (US-04.18) : le Directeur valide ou refuse une proposition, le
 * Responsable fiscal enregistre l'ordre du Trésor d'un remboursement validé. Seulement quand l'API
 * dit que c'est à lui de le faire (`aTraiterParMoi`, séparation des tâches comprise).
 */
export function actionsRemboursement(
  r: Pick<RemboursementDto, "statut" | "aTraiterParMoi">,
): DecisionRemboursement[] {
  if (!r.aTraiterParMoi) return [];
  if (r.statut === "demande") return ["valider", "refuser"];
  if (r.statut === "valide") return ["executer"];
  return [];
}

const ICONES = { valider: CircleCheck, refuser: Ban, executer: ReceiptText } as const;

/**
 * Actions sur un remboursement (US-04.10, US-04.18), confirmées dans une boîte de dialogue : le
 * Directeur valide, ou refuse avec un motif ; le Responsable fiscal enregistre la référence de
 * l'ordre de remboursement émis par le Trésor (l'argent est rendu hors plateforme, le demandeur
 * est informé).
 */
export function ActionsRemboursement({
  remboursement,
  decider,
}: {
  remboursement: RemboursementDto;
  decider: DeciderRemboursement;
}) {
  const t = useTranslations("remboursements.actions");
  const tMotif = useTranslations("instruction.motif");
  const id = useId();
  const router = useRouter();
  const [choix, setChoix] = useState<DecisionRemboursement | null>(null);
  const [texte, setTexte] = useState("");
  const [erreur, setErreur] = useState<{ champ?: string; api?: string }>({});
  const [enCours, demarrer] = useTransition();
  const actions = actionsRemboursement(remboursement);

  function envoyer() {
    if (!choix) return;
    if (choix === "refuser") {
      const probleme = erreurMotif(texte);
      if (probleme) {
        setErreur({ champ: tMotif(`erreurs.${probleme}`) });
        return;
      }
    }
    if (choix === "executer" && texte.trim().length < 3) {
      setErreur({ champ: t("ordreObligatoire") });
      return;
    }
    demarrer(async () => {
      const r = await decider(remboursement.id, choix, texte);
      if (!r.ok) {
        const champ = r.erreur.champs[0]?.message;
        setErreur(champ ? { champ } : { api: r.erreur.message });
        return;
      }
      setChoix(null);
      router.refresh();
    });
  }

  if (actions.length === 0) return null;
  return (
    <div className={cn(classesAuDessusDuLien, "flex flex-wrap gap-2")}>
      {actions.map((a) => {
        const Icone = ICONES[a];
        return (
          <Bouton
            key={a}
            type="button"
            variante={a === "refuser" ? "secondaire" : "primaire"}
            taille="ligne"
            onClick={() => {
              setChoix(a);
              setTexte("");
              setErreur({});
            }}
          >
            <Icone aria-hidden="true" className="size-4" />
            {t(`${a}.bouton`)}
            <span className="sr-only"> · {remboursement.numero}</span>
          </Bouton>
        );
      })}
      <Dialogue
        ouvert={choix !== null}
        titre={choix ? t(`${choix}.titre`, { numero: remboursement.numero }) : ""}
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
              {enCours ? t("enCours") : choix ? t(`${choix}.confirmer`) : ""}
            </Bouton>
          </>
        }
      >
        {choix ? (
          <div className="flex flex-col gap-4">
            <p>
              {t(`${choix}.effet`, {
                montant: formaterGnf(remboursement.montantGnf),
                beneficiaire: remboursement.beneficiaire ?? "",
              })}
            </p>
            {choix === "refuser" ? (
              <ChampMotif
                id={`${id}-motif`}
                libelle={t("motif")}
                aide={t("motifAide")}
                valeur={texte}
                surChangement={setTexte}
                erreur={erreur.champ}
              />
            ) : null}
            {choix === "executer" ? (
              <div className="flex flex-col gap-1">
                <label htmlFor={`${id}-ordre`} className="font-semibold">
                  {t("ordre")}
                </label>
                <p id={`${id}-ordre-aide`} className="text-sm text-texte-doux">
                  {t("ordreAide")}
                </p>
                <input
                  id={`${id}-ordre`}
                  type="text"
                  maxLength={60}
                  autoComplete="off"
                  value={texte}
                  onChange={(e) => setTexte(e.target.value)}
                  aria-invalid={erreur.champ ? true : undefined}
                  aria-describedby={
                    erreur.champ ? `${id}-ordre-aide ${id}-ordre-erreur` : `${id}-ordre-aide`
                  }
                  className={cn(classesChamp, "max-w-sm")}
                />
                {erreur.champ ? (
                  <p id={`${id}-ordre-erreur`} className="font-semibold text-danger">
                    {erreur.champ}
                  </p>
                ) : null}
              </div>
            ) : null}
            {erreur.api ? (
              <Alerte nature="erreur" titre={t("erreur")}>
                {erreur.api}
              </Alerte>
            ) : null}
          </div>
        ) : null}
      </Dialogue>
    </div>
  );
}
