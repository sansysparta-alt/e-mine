"use client";

import { Alerte } from "@emine/ui";
import { FileSearch, Loader2 } from "@emine/ui/icones";
import { useTranslations } from "next-intl";
import { useId, useState } from "react";
import { empreinteSha256 } from "./verification";

type Resultat = "identique" | "different" | "illisible";

/**
 * Comparaison d'un fichier avec le document délivré (US-06.5) : l'empreinte SHA-256 est calculée
 * sur l'appareil de la personne (le fichier n'est pas envoyé) puis comparée à celle du registre.
 * Un seul octet modifié suffit à la changer.
 */
export function VerifierFichier({ empreinteAttendue }: { empreinteAttendue: string }) {
  const t = useTranslations("verification.fichier");
  const id = useId();
  const [resultat, setResultat] = useState<Resultat | null>(null);
  const [nom, setNom] = useState("");
  const [enCours, setEnCours] = useState(false);

  async function comparer(fichier: File | undefined) {
    if (!fichier) return;
    setEnCours(true);
    setResultat(null);
    setNom(fichier.name);
    try {
      const empreinte = await empreinteSha256(await fichier.arrayBuffer());
      setResultat(empreinte === empreinteAttendue.toLowerCase() ? "identique" : "different");
    } catch {
      setResultat("illisible");
    } finally {
      setEnCours(false);
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <p className="text-texte-doux">{t("explication")}</p>
      <label
        htmlFor={`${id}-fichier`}
        className="inline-flex min-h-11 cursor-pointer items-center gap-2 self-start rounded-lg border-2 border-royal bg-white px-4 font-semibold text-royal focus-within:outline-3 focus-within:outline-offset-2 focus-within:outline-azure hover:bg-ice"
      >
        {enCours ? (
          <Loader2 aria-hidden="true" className="size-5 motion-safe:animate-spin" />
        ) : (
          <FileSearch aria-hidden="true" className="size-5" />
        )}
        <span aria-hidden="true">{t("choisir")}</span>
        <input
          id={`${id}-fichier`}
          type="file"
          accept="application/pdf,.pdf"
          aria-label={t("champ")}
          className="sr-only"
          onChange={(e) => void comparer(e.target.files?.[0])}
        />
      </label>
      <div aria-live="polite">
        {resultat === "identique" ? (
          <Alerte nature="succes" titre={t("identique.titre")}>
            {t("identique.texte", { nom })}
          </Alerte>
        ) : null}
        {resultat === "different" ? (
          <Alerte nature="erreur" titre={t("different.titre")}>
            {t("different.texte", { nom })}
          </Alerte>
        ) : null}
        {resultat === "illisible" ? (
          <Alerte nature="erreur" titre={t("illisible.titre")}>
            {t("illisible.texte")}
          </Alerte>
        ) : null}
      </div>
    </div>
  );
}
