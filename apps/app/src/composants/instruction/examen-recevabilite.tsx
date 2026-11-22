"use client";

import type { ExamenRecevabiliteDto, ResultatRecevabiliteDto } from "@emine/api-client";
import { Alerte, Bouton, CaseACocher, Dialogue } from "@emine/ui";
import { CircleCheck, CircleX } from "@emine/ui/icones";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { type ReactNode, useId, useState, useTransition } from "react";
import type { ResultatAction } from "@/serveur/erreurs-formulaire";
import {
  adresseApresAction,
  type ContexteSerie,
  type ResultatAction as Resultat,
} from "../travail/file-travail";
import { ChampMotif, erreurMotif } from "./champ-motif";
import { DemandeComplement, type DemandeComplementProps } from "./complements";

export type ExamenRecevabiliteProps = {
  demandeId: number;
  numero: string;
  examen: ExamenRecevabiliteDto;
  /** Pièces obligatoires du catalogue, dans l'ordre du catalogue. */
  piecesRequises: string[];
  declarer: (
    id: number,
    recevable: boolean,
    motif: string | null,
  ) => Promise<ResultatAction<ResultatRecevabiliteDto>>;
  /** Traitement en série : après la déclaration, le dossier suivant de la file s'ouvre. */
  serie?: ContexteSerie | null;
  /** Demande de complément au demandeur (US-05.2). */
  demanderComplement?: DemandeComplementProps["demander"];
};

/**
 * Examen de recevabilité par l'agent du guichet (US-05.1) : liste de contrôle (frais, pièces
 * obligatoires), confirmation de la vérification des pièces, puis « Déclarer recevable » ; ou
 * « Déclarer non recevable » avec un motif obligatoire, dans une boîte de dialogue. Après la
 * déclaration, l'agent revient à la file du guichet avec un message de confirmation.
 */
export function ExamenRecevabilite({
  demandeId,
  numero,
  examen,
  piecesRequises,
  declarer,
  serie,
  demanderComplement,
}: ExamenRecevabiliteProps) {
  const t = useTranslations("instruction.recevabilite");
  const tMotif = useTranslations("instruction.motif");
  const id = useId();
  const router = useRouter();
  const [verifiee, setVerifiee] = useState(false);
  const [erreurCase, setErreurCase] = useState(false);
  const [dialogue, setDialogue] = useState(false);
  const [motif, setMotif] = useState("");
  const [erreurChampMotif, setErreurChampMotif] = useState<string | undefined>();
  const [erreurApi, setErreurApi] = useState<string | null>(null);
  const [enCours, demarrer] = useTransition();
  const manquantes = new Set(examen.piecesManquantes);

  function envoyer(recevable: boolean) {
    setErreurApi(null);
    demarrer(async () => {
      const resultat = await declarer(demandeId, recevable, recevable ? null : motif.trim());
      if (resultat.ok) {
        router.push(
          adresseApresAction(serie ?? null, "a-examiner", {
            numero,
            resultat: resultat.valeur.statut as Resultat,
          }),
        );
        return;
      }
      const champ = resultat.erreur.champs.find((c) => c.champ === "motif");
      if (champ && !recevable) {
        setErreurChampMotif(champ.message);
        return;
      }
      setDialogue(false);
      setErreurApi(
        resultat.erreur.code === "INDISPONIBLE" ? t("indisponible") : resultat.erreur.message,
      );
      router.refresh();
    });
  }

  function declarerRecevable() {
    if (!verifiee) {
      setErreurCase(true);
      return;
    }
    envoyer(true);
  }

  function confirmerNonRecevable() {
    const probleme = erreurMotif(motif);
    if (probleme) {
      setErreurChampMotif(tMotif(`erreurs.${probleme}`));
      return;
    }
    envoyer(false);
  }

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h3 className="text-lg">{t("controles")}</h3>
        <ul className="mt-2 flex flex-col gap-2">
          <Controle ok={examen.fraisPayes}>
            {examen.fraisPayes ? t("fraisPayes") : t("fraisNonPayes")}
          </Controle>
          {piecesRequises.map((p) => (
            <Controle key={p} ok={!manquantes.has(p)}>
              {manquantes.has(p)
                ? t("pieceManquante", { piece: p })
                : t("pieceFournie", { piece: p })}
            </Controle>
          ))}
        </ul>
      </div>

      {erreurApi ? (
        <Alerte nature="erreur" titre={t("echec")}>
          {erreurApi}
        </Alerte>
      ) : null}

      {examen.peutEtreRecevable ? (
        <CaseACocher
          id={`${id}-verifiee`}
          checked={verifiee}
          onChange={(e) => {
            setVerifiee(e.target.checked);
            if (e.target.checked) setErreurCase(false);
          }}
          erreur={erreurCase ? t("verificationRequise") : undefined}
        >
          {t("verification")}
        </CaseACocher>
      ) : (
        <Alerte nature="avertissement" titre={t("pasEncoreTitre")}>
          {examen.fraisPayes ? t("pasEncorePieces") : t("pasEncoreFrais")}
        </Alerte>
      )}

      <div className="flex flex-col gap-3">
        {examen.peutEtreRecevable ? (
          <Bouton type="button" taille="grande" onClick={declarerRecevable} disabled={enCours}>
            <CircleCheck aria-hidden="true" className="size-5" />
            {enCours && !dialogue ? t("enCours") : t("declarerRecevable")}
          </Bouton>
        ) : null}
        {demanderComplement ? (
          <DemandeComplement
            demandeId={demandeId}
            numero={numero}
            piecesProposees={piecesRequises}
            piecesCochees={examen.piecesManquantes}
            demander={demanderComplement}
            groupe="a-examiner"
            serie={serie}
            principal={!examen.peutEtreRecevable && examen.fraisPayes}
            desactive={enCours}
          />
        ) : null}
        <Bouton
          type="button"
          variante="secondaire"
          taille="grande"
          onClick={() => {
            setErreurChampMotif(undefined);
            setDialogue(true);
          }}
          disabled={enCours}
        >
          {t("declarerNonRecevable")}
        </Bouton>
      </div>

      <Dialogue
        ouvert={dialogue}
        titre={t("dialogue.titre", { numero })}
        surFermeture={enCours ? undefined : () => setDialogue(false)}
        actions={
          <>
            <Bouton
              type="button"
              variante="secondaire"
              onClick={() => setDialogue(false)}
              disabled={enCours}
            >
              {t("dialogue.annuler")}
            </Bouton>
            <Bouton type="button" onClick={confirmerNonRecevable} disabled={enCours}>
              {enCours ? t("enCours") : t("dialogue.confirmer")}
            </Bouton>
          </>
        }
      >
        <div className="flex flex-col gap-3">
          <p>{t("dialogue.explication")}</p>
          <ChampMotif
            id={`${id}-motif`}
            libelle={t("dialogue.motif")}
            aide={t("dialogue.aide")}
            valeur={motif}
            surChangement={(v) => {
              setMotif(v);
              if (erreurChampMotif && !erreurMotif(v)) setErreurChampMotif(undefined);
            }}
            erreur={erreurChampMotif}
          />
        </div>
      </Dialogue>
    </div>
  );
}

/** Ligne de la liste de contrôle : icône et texte (jamais la couleur seule). */
function Controle({ ok, children }: { ok: boolean; children: ReactNode }) {
  const Icone = ok ? CircleCheck : CircleX;
  return (
    <li className="flex items-start gap-2">
      <Icone
        aria-hidden="true"
        className={ok ? "mt-0.5 size-5 shrink-0 text-succes" : "mt-0.5 size-5 shrink-0 text-danger"}
      />
      <span>{children}</span>
    </li>
  );
}
