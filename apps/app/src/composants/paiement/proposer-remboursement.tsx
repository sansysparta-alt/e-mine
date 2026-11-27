"use client";

import type { PaiementDto } from "@emine/api-client";
import {
  Alerte,
  Bouton,
  classesAuDessusDuLien,
  classesChamp,
  cn,
  Dialogue,
  formaterGnf,
} from "@emine/ui";
import { Undo2 } from "@emine/ui/icones";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useId, useState, useTransition } from "react";
import type { ResultatAction } from "@/serveur/erreurs-formulaire";
import { ChampMotif, erreurMotif } from "../instruction/champ-motif";
import { plafondRemboursement } from "./statut";

export type ProposerRemboursement = (
  reference: string,
  montantGnf: number,
  explication: string,
) => Promise<ResultatAction<{ numero: string }>>;

/**
 * « Proposer un remboursement » (R7) : le Responsable fiscal propose de rendre tout ou partie d'un
 * paiement payé, avec un motif dont il voit l'aperçu ; la proposition suit le circuit commun
 * (validation du Directeur, puis ordre du Trésor).
 */
export function BoutonProposerRemboursement({
  paiement,
  proposer,
}: {
  paiement: PaiementDto;
  proposer: ProposerRemboursement;
}) {
  const t = useTranslations("proposerRemboursement");
  const tMotif = useTranslations("instruction.motif");
  const id = useId();
  const router = useRouter();
  const plafond = plafondRemboursement(paiement);
  const [ouvert, setOuvert] = useState(false);
  const [montant, setMontant] = useState(String(plafond));
  const [motif, setMotif] = useState("");
  const [erreurs, setErreurs] = useState<{ montant?: string; motif?: string; api?: string }>({});
  const [message, setMessage] = useState("");
  const [enCours, demarrer] = useTransition();

  function confirmer() {
    const valeur = Number.parseInt(montant.replace(/\D/g, ""), 10);
    const montantInvalide = !Number.isFinite(valeur) || valeur < 1 || valeur > plafond;
    const probleme = erreurMotif(motif);
    if (montantInvalide || probleme) {
      setErreurs({
        montant: montantInvalide
          ? t("montantInvalide", { plafond: formaterGnf(plafond) })
          : undefined,
        motif: probleme ? tMotif(`erreurs.${probleme}`) : undefined,
      });
      return;
    }
    demarrer(async () => {
      const r = await proposer(paiement.reference, valeur, motif.trim());
      if (!r.ok) {
        const champMontant = r.erreur.champs.find((c) => c.champ === "montantGnf");
        const champMotif = r.erreur.champs.find((c) => c.champ === "explication");
        setErreurs({
          montant: champMontant?.message,
          motif: champMotif?.message,
          api: champMontant || champMotif ? undefined : r.erreur.message,
        });
        return;
      }
      setOuvert(false);
      setMessage(t("succes", { numero: r.valeur.numero }));
      router.refresh();
    });
  }

  if (plafond <= 0) return null;
  return (
    <div className={cn(classesAuDessusDuLien, "flex flex-col items-start gap-1")}>
      <Bouton
        type="button"
        variante="secondaire"
        taille="ligne"
        onClick={() => {
          setMontant(String(plafond));
          setMotif("");
          setErreurs({});
          setOuvert(true);
        }}
      >
        <Undo2 aria-hidden="true" className="size-4" />
        {t("bouton")}
        <span className="sr-only"> · {paiement.reference}</span>
      </Bouton>
      <p role="status" className={message ? "text-sm font-semibold text-succes" : "sr-only"}>
        {message}
      </p>
      <Dialogue
        ouvert={ouvert}
        titre={t("titre", { reference: paiement.reference })}
        surFermeture={enCours ? undefined : () => setOuvert(false)}
        actions={
          <>
            <Bouton
              type="button"
              variante="secondaire"
              disabled={enCours}
              onClick={() => setOuvert(false)}
            >
              {t("annuler")}
            </Bouton>
            <Bouton type="button" disabled={enCours} onClick={confirmer}>
              {enCours ? t("enCours") : t("confirmer")}
            </Bouton>
          </>
        }
      >
        <div className="flex flex-col gap-4">
          <p>{t("effet")}</p>
          <div className="flex flex-col gap-1">
            <label htmlFor={`${id}-montant`} className="font-semibold">
              {t("montant")}
            </label>
            <p id={`${id}-montant-aide`} className="text-sm text-texte-doux">
              {t("montantAide", { plafond: formaterGnf(plafond) })}
            </p>
            <input
              id={`${id}-montant`}
              type="text"
              inputMode="numeric"
              autoComplete="off"
              value={montant}
              onChange={(e) => setMontant(e.target.value)}
              aria-invalid={erreurs.montant ? true : undefined}
              aria-describedby={
                erreurs.montant ? `${id}-montant-aide ${id}-montant-erreur` : `${id}-montant-aide`
              }
              className={cn(classesChamp, "max-w-xs tabular-nums")}
            />
            {erreurs.montant ? (
              <p id={`${id}-montant-erreur`} className="font-semibold text-danger">
                {erreurs.montant}
              </p>
            ) : null}
          </div>
          <ChampMotif
            id={`${id}-motif`}
            libelle={t("motif")}
            aide={t("motifAide")}
            valeur={motif}
            surChangement={setMotif}
            erreur={erreurs.motif}
            apercu={t("apercu")}
          />
          {erreurs.api ? (
            <Alerte nature="erreur" titre={t("erreur")}>
              {erreurs.api}
            </Alerte>
          ) : null}
        </div>
      </Dialogue>
    </div>
  );
}
