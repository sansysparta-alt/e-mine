"use client";

import type { SignalementDto } from "@emine/api-client";
import { Alerte, BadgeStatut, Bouton, Dialogue, formaterDateHeure, type TonBadge } from "@emine/ui";
import { Archive, Ban, Flag } from "@emine/ui/icones";
import { useRouter } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { type ReactNode, useId, useState, useTransition } from "react";
import type { ResultatAction } from "@/serveur/erreurs-formulaire";
import { ChampMotif, erreurMotif } from "../instruction/champ-motif";

type ActionMotifProps = {
  /** Libellé et icône du bouton qui ouvre la boîte de dialogue. */
  bouton: ReactNode;
  variante?: "primaire" | "secondaire";
  danger?: boolean;
  titre: string;
  explication: string;
  libelleMotif: string;
  aide: string;
  confirmer: string;
  enCours: string;
  echec: string;
  succes: string;
  executer: (motif: string) => Promise<ResultatAction<unknown>>;
  /** Titre de l'aperçu du motif (US-04.13). */
  apercu: string;
};

/**
 * Bouton qui ouvre une boîte de dialogue à motif obligatoire, puis exécute l'action (signalement,
 * décision du signataire). Les erreurs de champ restent dans la boîte ; les autres s'affichent sous
 * le bouton.
 */
function ActionMotif(p: ActionMotifProps) {
  const t = useTranslations("delivrance.signalements");
  const tMotif = useTranslations("instruction.motif");
  const id = useId();
  const router = useRouter();
  const [ouvert, setOuvert] = useState(false);
  const [motif, setMotif] = useState("");
  const [erreurChamp, setErreurChamp] = useState<string | undefined>();
  const [erreur, setErreur] = useState<string | null>(null);
  const [message, setMessage] = useState("");
  const [enCours, demarrer] = useTransition();

  function confirmer() {
    const probleme = erreurMotif(motif);
    if (probleme) {
      setErreurChamp(tMotif(`erreurs.${probleme}`));
      return;
    }
    setErreur(null);
    demarrer(async () => {
      const resultat = await p.executer(motif);
      if (resultat.ok) {
        setOuvert(false);
        setMotif("");
        setMessage(p.succes);
        router.refresh();
        return;
      }
      const champ = resultat.erreur.champs.find((c) => c.champ === "motif");
      if (champ) {
        setErreurChamp(champ.message);
        return;
      }
      setOuvert(false);
      setErreur(
        resultat.erreur.code === "INDISPONIBLE" ? t("indisponible") : resultat.erreur.message,
      );
    });
  }

  return (
    <>
      <Bouton
        type="button"
        variante={p.variante ?? "secondaire"}
        className={p.danger ? "border-danger text-danger hover:bg-danger-fond" : undefined}
        onClick={() => {
          setErreurChamp(undefined);
          setOuvert(true);
        }}
      >
        {p.bouton}
      </Bouton>
      {erreur ? (
        <div className="w-full">
          <Alerte nature="erreur" titre={p.echec}>
            {erreur}
          </Alerte>
        </div>
      ) : null}
      <p role="status" className={message ? "w-full font-semibold text-succes" : "sr-only"}>
        {message}
      </p>
      <Dialogue
        ouvert={ouvert}
        titre={p.titre}
        surFermeture={enCours ? undefined : () => setOuvert(false)}
        actions={
          <>
            <Bouton
              type="button"
              variante="secondaire"
              onClick={() => setOuvert(false)}
              disabled={enCours}
            >
              {t("fermer")}
            </Bouton>
            <Bouton type="button" onClick={confirmer} disabled={enCours}>
              {enCours ? p.enCours : p.confirmer}
            </Bouton>
          </>
        }
      >
        <div className="flex flex-col gap-3">
          <p>{p.explication}</p>
          <ChampMotif
            id={`${id}-motif`}
            libelle={p.libelleMotif}
            aide={p.aide}
            valeur={motif}
            surChangement={(v) => {
              setMotif(v);
              if (erreurChamp && !erreurMotif(v)) setErreurChamp(undefined);
            }}
            erreur={erreurChamp}
            apercu={p.apercu}
          />
        </div>
      </Dialogue>
    </>
  );
}

export type SignalerDocumentProps = {
  demandeId: number;
  numero: string;
  signaler: (id: number, motif: string) => Promise<ResultatAction<null>>;
};

/**
 * La Brigade anti-fraude signale le document à son signataire (US-06.8, décision V1-11) : le
 * document reste valide tant que le signataire n'a pas décidé.
 */
export function SignalerDocument({ demandeId, numero, signaler }: SignalerDocumentProps) {
  const t = useTranslations("delivrance.signalements.signaler");
  return (
    <ActionMotif
      bouton={
        <>
          <Flag aria-hidden="true" className="size-4" />
          {t("action")}
        </>
      }
      titre={t("titre", { numero })}
      explication={t("explication")}
      libelleMotif={t("motif")}
      aide={t("aide")}
      confirmer={t("confirmer")}
      enCours={t("enCours")}
      echec={t("echec")}
      succes={t("succes", { numero })}
      apercu={t("apercu")}
      executer={(motif) => signaler(demandeId, motif)}
    />
  );
}

/** Statut d'un signalement présenté au profil connecté (un seul badge, code couleur commun). */
export function presentationSignalement(s: Pick<SignalementDto, "statut" | "aTraiterParMoi">): {
  cle: "aDecider" | "attenteSignataire" | "revoque" | "classe";
  ton: TonBadge;
} {
  if (s.statut === "annule") return { cle: "revoque", ton: "danger" };
  if (s.statut === "classe") return { cle: "classe", ton: "succes" };
  return s.aTraiterParMoi
    ? { cle: "aDecider", ton: "avertissement" }
    : { cle: "attenteSignataire", ton: "neutre" };
}

export type ListeSignalementsProps = {
  signalements: SignalementDto[];
  decider: (
    id: number,
    decision: "ANNULER" | "CLASSER",
    motif: string,
  ) => Promise<ResultatAction<null>>;
};

/**
 * Signalements du document (profils internes) : motif, auteur, date, décision ; le signataire
 * révoque le document ou classe le signalement, avec un motif (US-06.8).
 */
export function ListeSignalements({ signalements, decider }: ListeSignalementsProps) {
  const t = useTranslations("delivrance.signalements");
  const langue = useLocale();
  if (signalements.length === 0) return null;
  return (
    <section
      id="signalements"
      aria-labelledby="signalements-titre"
      className="flex scroll-mt-24 flex-col gap-3"
    >
      <h3 id="signalements-titre" className="text-lg font-semibold">
        {t("titre", { nombre: signalements.length })}
      </h3>
      <ul className="flex flex-col gap-3">
        {signalements.map((s) => {
          const p = presentationSignalement(s);
          return (
            <li key={s.id} className="flex flex-col gap-2 rounded-md border border-bordure p-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="text-sm text-texte-doux">
                  {t("signalePar", {
                    auteur: s.auteur ?? t("brigade"),
                    date: formaterDateHeure(s.signaleLe, langue),
                  })}
                </p>
                <BadgeStatut ton={p.ton}>{t(`statuts.${p.cle}`)}</BadgeStatut>
              </div>
              <p className="whitespace-pre-line">{s.motif}</p>
              {s.statut !== "ouvert" && s.decideLe ? (
                <div className="border-l-2 border-bordure pl-3">
                  <p className="text-sm text-texte-doux">
                    {t(s.statut === "annule" ? "revoquePar" : "classePar", {
                      auteur: s.decidePar ?? "",
                      date: formaterDateHeure(s.decideLe, langue),
                    })}
                  </p>
                  {s.motifDecision ? (
                    <p className="whitespace-pre-line">{s.motifDecision}</p>
                  ) : null}
                </div>
              ) : null}
              {s.aTraiterParMoi ? (
                <div className="flex flex-wrap items-start gap-3">
                  <ActionMotif
                    danger
                    bouton={
                      <>
                        <Ban aria-hidden="true" className="size-4" />
                        {t("revoquer.action")}
                      </>
                    }
                    titre={t("revoquer.titre", { numero: s.numeroDocument })}
                    explication={t("revoquer.explication")}
                    libelleMotif={t("revoquer.motif")}
                    aide={t("revoquer.aide")}
                    confirmer={t("revoquer.confirmer")}
                    enCours={t("revoquer.enCours")}
                    echec={t("revoquer.echec")}
                    succes={t("revoquer.succes", { numero: s.numeroDocument })}
                    apercu={t("revoquer.apercu")}
                    executer={(motif) => decider(s.id, "ANNULER", motif)}
                  />
                  <ActionMotif
                    bouton={
                      <>
                        <Archive aria-hidden="true" className="size-4" />
                        {t("classer.action")}
                      </>
                    }
                    titre={t("classer.titre")}
                    explication={t("classer.explication", { numero: s.numeroDocument })}
                    libelleMotif={t("classer.motif")}
                    aide={t("classer.aide")}
                    confirmer={t("classer.confirmer")}
                    enCours={t("classer.enCours")}
                    echec={t("classer.echec")}
                    succes={t("classer.succes")}
                    apercu={t("classer.apercu")}
                    executer={(motif) => decider(s.id, "CLASSER", motif)}
                  />
                </div>
              ) : null}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
