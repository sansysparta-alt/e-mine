"use client";

import type { ComplementDto, ResultatComplementDto } from "@emine/api-client";
import { Alerte, Bouton, CaseACocher, classesChamp, Dialogue, formaterDate } from "@emine/ui";
import { MessageSquareText, Send, TriangleAlert } from "@emine/ui/icones";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useId, useState, useTransition } from "react";
import type { ResultatAction } from "@/serveur/erreurs-formulaire";
import { SectionPieces } from "../demande/section-pieces";
import { adresseApresAction, type CleGroupe, type ContexteSerie } from "../travail/file-travail";
import { ChampMotif, erreurMotif } from "./champ-motif";

type Action<T> = Promise<ResultatAction<T>>;

/* -------------------------------------------------------------------------------------------- */
/* Agent : demander un complément                                                               */
/* -------------------------------------------------------------------------------------------- */

export type DemandeComplementProps = {
  demandeId: number;
  numero: string;
  /** Pièces du catalogue proposées à cocher. */
  piecesProposees: string[];
  /** Pièces cochées d'emblée (pièces obligatoires sans fichier). */
  piecesCochees?: string[];
  demander: (id: number, motif: string, pieces: string[]) => Action<ComplementDto>;
  /** Groupe de la file où revenir après l'envoi, hors traitement en série. */
  groupe: CleGroupe;
  serie?: ContexteSerie | null;
  /** Bouton principal (pièce manquante : c'est l'action attendue) ou secondaire. */
  principal?: boolean;
  desactive?: boolean;
};

/**
 * Demande de complément par l'agent qui instruit (US-05.2) : pièces attendues (cochées parmi celles
 * du catalogue, ou une autre pièce), motif communiqué au demandeur. Après l'envoi, l'agent passe au
 * dossier suivant de la file ou y revient, avec la confirmation.
 */
export function DemandeComplement({
  demandeId,
  numero,
  piecesProposees,
  piecesCochees = [],
  demander,
  groupe,
  serie,
  principal = false,
  desactive = false,
}: DemandeComplementProps) {
  const t = useTranslations("instruction.complement.demande");
  const tMotif = useTranslations("instruction.motif");
  const id = useId();
  const router = useRouter();
  const [ouvert, setOuvert] = useState(false);
  const [pieces, setPieces] = useState<string[]>(piecesCochees);
  const [autre, setAutre] = useState("");
  const [motif, setMotif] = useState("");
  const [erreurChampMotif, setErreurChampMotif] = useState<string | undefined>();
  const [erreurApi, setErreurApi] = useState<string | null>(null);
  const [enCours, demarrer] = useTransition();

  function basculer(piece: string, coche: boolean) {
    setPieces((avant) => (coche ? [...avant, piece] : avant.filter((p) => p !== piece)));
  }

  function envoyer() {
    const probleme = erreurMotif(motif);
    if (probleme) {
      setErreurChampMotif(tMotif(`erreurs.${probleme}`));
      return;
    }
    const attendues = autre.trim() ? [...pieces, autre.trim()] : pieces;
    setErreurApi(null);
    demarrer(async () => {
      const resultat = await demander(demandeId, motif.trim(), attendues);
      if (resultat.ok) {
        router.push(
          adresseApresAction(serie ?? null, groupe, { numero, resultat: "complement_requis" }),
        );
        return;
      }
      const champ = resultat.erreur.champs.find((c) => c.champ === "motif");
      if (champ) {
        setErreurChampMotif(champ.message);
        return;
      }
      setErreurApi(resultat.erreur.message);
    });
  }

  return (
    <>
      <Bouton
        type="button"
        variante={principal ? "primaire" : "secondaire"}
        taille="grande"
        onClick={() => {
          setErreurChampMotif(undefined);
          setErreurApi(null);
          setOuvert(true);
        }}
        disabled={desactive || enCours}
      >
        <MessageSquareText aria-hidden="true" className="size-5" />
        {t("bouton")}
      </Bouton>
      <Dialogue
        ouvert={ouvert}
        titre={t("titre", { numero })}
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
            <Bouton type="button" onClick={envoyer} disabled={enCours}>
              {enCours ? t("enCours") : t("confirmer")}
            </Bouton>
          </>
        }
      >
        <div className="flex flex-col gap-4">
          <p>{t("explication")}</p>
          {erreurApi ? (
            <Alerte nature="erreur" titre={t("echec")}>
              {erreurApi}
            </Alerte>
          ) : null}
          <fieldset className="flex flex-col gap-2">
            <legend className="font-semibold">{t("pieces")}</legend>
            <p className="text-sm text-texte-doux">{t("piecesAide")}</p>
            {piecesProposees.map((p, i) => (
              <CaseACocher
                key={p}
                id={`${id}-piece-${i}`}
                checked={pieces.includes(p)}
                onChange={(e) => basculer(p, e.target.checked)}
              >
                {p}
              </CaseACocher>
            ))}
            <label htmlFor={`${id}-autre`} className="mt-1 text-sm font-semibold">
              {t("autre")}
            </label>
            <input
              id={`${id}-autre`}
              className={classesChamp}
              value={autre}
              maxLength={200}
              onChange={(e) => setAutre(e.target.value)}
            />
          </fieldset>
          <ChampMotif
            id={`${id}-motif`}
            libelle={t("motif")}
            aide={t("motifAide")}
            valeur={motif}
            surChangement={(v) => {
              setMotif(v);
              if (erreurChampMotif && !erreurMotif(v)) setErreurChampMotif(undefined);
            }}
            erreur={erreurChampMotif}
          />
        </div>
      </Dialogue>
    </>
  );
}

/* -------------------------------------------------------------------------------------------- */
/* Rappel d'un complément (agent et demandeur)                                                  */
/* -------------------------------------------------------------------------------------------- */

/**
 * Ce qui est demandé : date, auteur (nom pour un agent, service pour le demandeur), motif, pièces
 * attendues, date limite et relance.
 */
export function ResumeComplement({
  complement,
  interne,
}: {
  complement: ComplementDto;
  interne: boolean;
}) {
  const t = useTranslations("instruction.complement");
  const auteur = interne
    ? `${complement.auteur} (${complement.auteurProfil})`
    : complement.auteurProfil;
  return (
    <dl className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-[auto_1fr]">
      <dt className="font-semibold">{t("demandeLe")}</dt>
      <dd>{t("demandeLeValeur", { date: formaterDate(complement.demandeLe), auteur })}</dd>
      <dt className="font-semibold">{t("motif")}</dt>
      <dd className="whitespace-pre-line">{complement.motif}</dd>
      {complement.piecesDemandees.length > 0 ? (
        <>
          <dt className="font-semibold">{t("piecesDemandees")}</dt>
          <dd>
            <ul className="list-disc pl-5">
              {complement.piecesDemandees.map((p) => (
                <li key={p}>{p}</li>
              ))}
            </ul>
          </dd>
        </>
      ) : null}
      {complement.ouvert ? (
        <>
          <dt className="font-semibold">{t("dateLimite")}</dt>
          <dd>
            {formaterDate(complement.dateLimite)}
            {complement.relanceLe ? (
              <span className="text-texte-doux">
                {" · "}
                {t("relanceLe", { date: formaterDate(complement.relanceLe) })}
              </span>
            ) : null}
          </dd>
        </>
      ) : null}
    </dl>
  );
}

/* -------------------------------------------------------------------------------------------- */
/* Demandeur : répondre                                                                         */
/* -------------------------------------------------------------------------------------------- */

export type ReponseComplementProps = {
  demandeId: number;
  complement: ComplementDto;
  repondre: (id: number, message: string | null) => Action<ResultatComplementDto>;
};

/**
 * Réponse du demandeur (US-05.2) : il joint chaque pièce demandée (seules les pièces ajoutées
 * depuis la demande de complément comptent), écrit un message (obligatoire si aucune pièce n'est
 * demandée), puis envoie. Le dossier retourne à l'agent.
 */
export function ReponseComplement({ demandeId, complement, repondre }: ReponseComplementProps) {
  const t = useTranslations("instruction.complement.reponse");
  const id = useId();
  const router = useRouter();
  const [jointes, setJointes] = useState<string[]>([]);
  const [message, setMessage] = useState("");
  const [erreurMessage, setErreurMessage] = useState<string | undefined>();
  const [erreur, setErreur] = useState<string | null>(null);
  const [enCours, demarrer] = useTransition();
  const demandees = complement.piecesDemandees;
  const messageObligatoire = demandees.length === 0;
  const manquantes = demandees.filter((p) => !jointes.includes(p));

  function envoyer() {
    setErreur(null);
    if (manquantes.length > 0) {
      setErreur(t("piecesManquantes", { pieces: manquantes.join(", ") }));
      return;
    }
    const probleme = erreurMotif(message);
    if ((messageObligatoire && probleme) || probleme === "long") {
      setErreurMessage(t(`erreurs.${probleme}`));
      return;
    }
    demarrer(async () => {
      const resultat = await repondre(demandeId, message);
      if (resultat.ok) {
        router.push(`/demandes/${demandeId}?complement=envoye`);
        return;
      }
      setErreur(resultat.erreur.message);
      router.refresh();
    });
  }

  return (
    <div className="flex flex-col gap-4">
      {demandees.length > 0 ? (
        <SectionPieces
          demandeId={demandeId}
          piecesRequises={demandees}
          depuis={complement.demandeLe}
          surChangement={(liste) => setJointes(liste.map((p) => p.typePiece))}
        />
      ) : null}
      <ChampMotif
        id={`${id}-message`}
        libelle={t("message")}
        aide={messageObligatoire ? t("messageAideObligatoire") : t("messageAide")}
        valeur={message}
        obligatoire={messageObligatoire}
        surChangement={(v) => {
          setMessage(v);
          if (erreurMessage && !erreurMotif(v)) setErreurMessage(undefined);
        }}
        erreur={erreurMessage}
      />
      {erreur ? (
        <Alerte nature="erreur" titre={t("echec")}>
          {erreur}
        </Alerte>
      ) : null}
      <div>
        <Bouton type="button" taille="grande" onClick={envoyer} disabled={enCours}>
          <Send aria-hidden="true" className="size-5" />
          {enCours ? t("enCours") : t("envoyer")}
        </Bouton>
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------------------------- */
/* Agent : délai de réponse dépassé                                                             */
/* -------------------------------------------------------------------------------------------- */

export type ComplementSansReponseProps = {
  demandeId: number;
  numero: string;
  dateLimite: string;
  relancer: (id: number) => Action<ComplementDto>;
  clore: (id: number, motif: string) => Action<ResultatComplementDto>;
  serie?: ContexteSerie | null;
};

/**
 * Complément resté sans réponse dans le délai (US-05.2) : rien n'est automatique ; l'agent relance
 * le demandeur (nouveau délai de 5 jours) ou clôt le dossier avec un motif (demande rejetée).
 */
export function ComplementSansReponse({
  demandeId,
  numero,
  dateLimite,
  relancer,
  clore,
  serie,
}: ComplementSansReponseProps) {
  const t = useTranslations("instruction.complement.sansReponse");
  const tMotif = useTranslations("instruction.motif");
  const id = useId();
  const router = useRouter();
  const [dialogue, setDialogue] = useState(false);
  const [motif, setMotif] = useState("");
  const [erreurChampMotif, setErreurChampMotif] = useState<string | undefined>();
  const [erreurApi, setErreurApi] = useState<string | null>(null);
  const [enCours, demarrer] = useTransition();

  function apres(resultat: "relance" | "rejetee") {
    router.push(adresseApresAction(serie ?? null, "sans-reponse", { numero, resultat }));
  }

  function lancerRelance() {
    setErreurApi(null);
    demarrer(async () => {
      const r = await relancer(demandeId);
      if (r.ok) return apres("relance");
      setErreurApi(r.erreur.message);
      router.refresh();
    });
  }

  function confirmerCloture() {
    const probleme = erreurMotif(motif);
    if (probleme) {
      setErreurChampMotif(tMotif(`erreurs.${probleme}`));
      return;
    }
    demarrer(async () => {
      const r = await clore(demandeId, motif.trim());
      if (r.ok) return apres("rejetee");
      const champ = r.erreur.champs.find((c) => c.champ === "motif");
      if (champ) {
        setErreurChampMotif(champ.message);
        return;
      }
      setDialogue(false);
      setErreurApi(r.erreur.message);
      router.refresh();
    });
  }

  return (
    <div className="flex flex-col gap-4">
      <Alerte nature="avertissement" titre={t("titre")}>
        {t("explication", { date: formaterDate(dateLimite) })}
      </Alerte>
      {erreurApi ? (
        <Alerte nature="erreur" titre={t("echec")}>
          {erreurApi}
        </Alerte>
      ) : null}
      <div className="flex flex-col gap-3">
        <Bouton type="button" taille="grande" onClick={lancerRelance} disabled={enCours}>
          <Send aria-hidden="true" className="size-5" />
          {enCours && !dialogue ? t("enCours") : t("relancer")}
        </Bouton>
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
          <TriangleAlert aria-hidden="true" className="size-5" />
          {t("clore")}
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
            <Bouton type="button" onClick={confirmerCloture} disabled={enCours}>
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

/* -------------------------------------------------------------------------------------------- */
/* Historique                                                                                   */
/* -------------------------------------------------------------------------------------------- */

/** Compléments traités (réponse ou clôture), du plus récent au plus ancien : traçabilité. */
export function HistoriqueComplements({
  complements,
  interne,
}: {
  complements: ComplementDto[];
  interne: boolean;
}) {
  const t = useTranslations("instruction.complement.historique");
  return (
    <ol className="flex flex-col divide-y divide-bordure">
      {complements.map((c) => (
        <li key={c.id} className="flex flex-col gap-2 py-3 first:pt-0 last:pb-0">
          <ResumeComplement complement={c} interne={interne} />
          <p className="font-semibold">
            {c.reponduLe
              ? t("repondu", { date: formaterDate(c.reponduLe) })
              : c.closLe
                ? t("clos", { date: formaterDate(c.closLe) })
                : t("enAttente")}
          </p>
          {c.reponse ? <p className="whitespace-pre-line text-texte-doux">{c.reponse}</p> : null}
        </li>
      ))}
    </ol>
  );
}
