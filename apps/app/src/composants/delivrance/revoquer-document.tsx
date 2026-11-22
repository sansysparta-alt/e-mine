"use client";

import type { DocumentDelivreDto } from "@emine/api-client";
import { Alerte, Bouton, Dialogue } from "@emine/ui";
import { Ban } from "@emine/ui/icones";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useId, useState, useTransition } from "react";
import type { ResultatAction } from "@/serveur/erreurs-formulaire";
import { ChampMotif, erreurMotif } from "../instruction/champ-motif";

/** Profils autorisés à révoquer (indication d'interface ; l'API fait foi). */
export const PROFILS_REVOCATION = ["DIRECTEUR", "BRIGADE"];

export type RevoquerDocumentProps = {
  demandeId: number;
  numero: string;
  revoquer: (id: number, motif: string) => Promise<ResultatAction<DocumentDelivreDto>>;
};

/**
 * Révocation d'un document délivré (US-06.6) : motif obligatoire dans une boîte de dialogue, action
 * définitive annoncée ; la fiche se met à jour (« Non valide ») et la vérification publique affiche
 * « NON VALIDE » aussitôt.
 */
export function RevoquerDocument({ demandeId, numero, revoquer }: RevoquerDocumentProps) {
  const t = useTranslations("delivrance.revocation");
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
      const resultat = await revoquer(demandeId, motif);
      if (resultat.ok) {
        setOuvert(false);
        setMessage(t("succes", { numero }));
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
        variante="secondaire"
        className="border-danger text-danger hover:bg-danger-fond"
        onClick={() => {
          setErreurChamp(undefined);
          setOuvert(true);
        }}
      >
        <Ban aria-hidden="true" className="size-4" />
        {t("action")}
      </Bouton>
      {erreur ? (
        <div className="w-full">
          <Alerte nature="erreur" titre={t("echec")}>
            {erreur}
          </Alerte>
        </div>
      ) : null}
      <p role="status" className={message ? "w-full font-semibold text-succes" : "sr-only"}>
        {message}
      </p>
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
            <Bouton type="button" onClick={confirmer} disabled={enCours}>
              {enCours ? t("enCours") : t("confirmer")}
            </Bouton>
          </>
        }
      >
        <div className="flex flex-col gap-3">
          <p>{t("explication")}</p>
          <ChampMotif
            id={`${id}-motif`}
            libelle={t("motif")}
            aide={t("aide")}
            valeur={motif}
            surChangement={(v) => {
              setMotif(v);
              if (erreurChamp && !erreurMotif(v)) setErreurChamp(undefined);
            }}
            erreur={erreurChamp}
          />
        </div>
      </Dialogue>
    </>
  );
}
