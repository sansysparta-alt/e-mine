"use client";

import { Alerte, Bouton, Carte } from "@emine/ui";
import { FileUp, Send } from "@emine/ui/icones";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useId, useRef, useState } from "react";

/** Justificatif : PDF, JPG ou PNG de 5 Mo au plus (l'API refait le contrôle par la signature). */
export const TAILLE_MAX_JUSTIFICATIF = 5 * 1024 * 1024;
const TYPES = ["application/pdf", "image/jpeg", "image/png"];
const EXTENSIONS = /\.(pdf|jpe?g|png)$/i;

/** Refus côté navigateur, avant tout envoi : clé de message, ou `null` si le fichier convient. */
export function controlerJustificatif(f: {
  name: string;
  size: number;
  type: string;
}): "format" | "taille" | null {
  if (f.size > TAILLE_MAX_JUSTIFICATIF) return "taille";
  if (f.size === 0 || !(TYPES.includes(f.type) || EXTENSIONS.test(f.name))) return "format";
  return null;
}

/**
 * « J'ai effectué le virement » (US-04.7) : le demandeur choisit son justificatif (obligatoire),
 * puis déclare le virement en un geste. L'envoi passe par le relais BFF ; la page se recharge sur
 * l'état « en cours de vérification ». Le justificatif ne vaut pas paiement : rappelé sous le
 * bouton.
 */
export function DeclarationVirement({ reference }: { reference: string }) {
  const t = useTranslations("detailPaiement.declaration");
  const id = useId();
  const router = useRouter();
  const champ = useRef<HTMLInputElement>(null);
  const [fichier, setFichier] = useState<File | null>(null);
  const [refus, setRefus] = useState<"obligatoire" | "format" | "taille" | null>(null);
  const [erreurApi, setErreurApi] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  function choisir(f: File | undefined) {
    setErreurApi(null);
    if (!f) return;
    const probleme = controlerJustificatif(f);
    setRefus(probleme);
    setFichier(probleme ? null : f);
    if (probleme && champ.current) champ.current.value = "";
  }

  async function declarer() {
    setErreurApi(null);
    if (!fichier) {
      setRefus("obligatoire");
      champ.current?.focus();
      return;
    }
    setEnvoi(true);
    try {
      const donnees = new FormData();
      donnees.set("fichier", fichier);
      const r = await fetch(`/api/paiements/${encodeURIComponent(reference)}/virement`, {
        method: "POST",
        body: donnees,
      });
      if (!r.ok) {
        const corps = (await r.json().catch(() => ({}))) as { message?: string };
        setErreurApi(corps.message ?? t("erreur"));
        return;
      }
      router.refresh();
    } catch {
      setErreurApi(t("erreur"));
    } finally {
      setEnvoi(false);
    }
  }

  const aide = `${id}-aide`;
  const erreur = `${id}-erreur`;
  return (
    <Carte titre={t("titre")}>
      <div className="flex flex-col gap-4">
        <div className="flex flex-col gap-2">
          <label htmlFor={id} className="font-semibold">
            {t("champ")}
          </label>
          <p id={aide} className="text-sm text-texte-doux">
            {t("aide")}
          </p>
          <input
            ref={champ}
            id={id}
            type="file"
            accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
            aria-describedby={refus ? `${aide} ${erreur}` : aide}
            aria-invalid={refus ? true : undefined}
            disabled={envoi}
            onChange={(e) => choisir(e.target.files?.[0])}
            className="block min-h-11 w-full max-w-md cursor-pointer rounded-lg border border-bordure bg-white p-2 text-sm file:mr-3 file:min-h-9 file:cursor-pointer file:rounded-md file:border-0 file:bg-ice file:px-3 file:font-semibold file:text-royal"
          />
          {fichier ? (
            <p className="flex items-center gap-2 text-sm">
              <FileUp aria-hidden="true" className="size-4 text-succes" />
              {t("choisi", { nom: fichier.name })}
            </p>
          ) : null}
          {refus ? (
            <p id={erreur} className="font-semibold text-danger">
              {t(refus)}
            </p>
          ) : null}
        </div>

        {erreurApi ? (
          <Alerte nature="erreur" titre={t("erreur")}>
            {erreurApi}
          </Alerte>
        ) : null}

        <div className="flex flex-col gap-2">
          <Bouton
            type="button"
            taille="grande"
            onClick={declarer}
            disabled={envoi}
            className="self-start"
          >
            <Send aria-hidden="true" className="size-5" />
            {envoi ? t("enCours") : t("bouton")}
          </Bouton>
          <p className="text-sm text-texte-doux">{t("rappel")}</p>
        </div>
      </div>
    </Carte>
  );
}
