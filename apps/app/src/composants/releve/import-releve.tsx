"use client";

import { Alerte, Bouton, Carte, classesChamp } from "@emine/ui";
import { Download, FileSpreadsheet, Upload } from "@emine/ui/icones";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useId, useRef, useState } from "react";

/** Relevé CSV de 2 Mo au plus (l'API refait tous les contrôles du contrat). */
export const TAILLE_MAX_RELEVE = 2 * 1024 * 1024;

/** Refus côté navigateur, avant tout envoi : clé de message, ou `null` si le fichier convient. */
export function controlerReleve(f: { name: string; size: number }): "format" | "taille" | null {
  if (f.size > TAILLE_MAX_RELEVE) return "taille";
  if (f.size === 0 || !/\.csv$/i.test(f.name)) return "format";
  return null;
}

type ErreurImport = { titre: string; details: string[] };

/**
 * Import d'un relevé bancaire (US-04.8) : fichier CSV obligatoire, banque et date facultatives.
 * L'API importe et rapproche en une fois ; on ouvre ensuite le relevé et ses résultats. Les lignes
 * refusées par le contrôle de format sont citées. En démonstration, un relevé d'exemple se
 * télécharge (une ligne conforme par virement déclaré).
 */
export function ImportReleve({ exempleDisponible }: { exempleDisponible: boolean }) {
  const t = useTranslations("releves.import");
  const id = useId();
  const router = useRouter();
  const champ = useRef<HTMLInputElement>(null);
  const [fichier, setFichier] = useState<File | null>(null);
  const [banque, setBanque] = useState("");
  const [date, setDate] = useState("");
  const [refus, setRefus] = useState<"obligatoire" | "format" | "taille" | null>(null);
  const [erreur, setErreur] = useState<ErreurImport | null>(null);
  const [envoi, setEnvoi] = useState(false);

  function choisir(f: File | undefined) {
    setErreur(null);
    if (!f) return;
    const probleme = controlerReleve(f);
    setRefus(probleme);
    setFichier(probleme ? null : f);
    if (probleme && champ.current) champ.current.value = "";
  }

  async function importer(e: React.FormEvent) {
    e.preventDefault();
    setErreur(null);
    if (!fichier) {
      setRefus("obligatoire");
      champ.current?.focus();
      return;
    }
    setEnvoi(true);
    try {
      const donnees = new FormData();
      donnees.set("fichier", fichier);
      if (banque.trim()) donnees.set("banque", banque.trim());
      if (date) donnees.set("dateReleve", date);
      const r = await fetch("/api/releves", { method: "POST", body: donnees });
      const corps = (await r.json().catch(() => ({}))) as {
        id?: number;
        message?: string;
        details?: { champs?: { message: string }[] };
      };
      if (!r.ok || corps.id == null) {
        setErreur({
          titre: corps.message ?? t("erreur"),
          details: (corps.details?.champs ?? []).map((c) => c.message),
        });
        return;
      }
      router.push(`/releves/${corps.id}?importe=1`);
    } catch {
      setErreur({ titre: t("erreur"), details: [] });
    } finally {
      setEnvoi(false);
    }
  }

  const aide = `${id}-aide`;
  const message = `${id}-refus`;
  return (
    <Carte titre={t("titre")}>
      <form onSubmit={importer} className="flex flex-col gap-4" noValidate>
        <div className="flex flex-col gap-2">
          <label htmlFor={id} className="font-semibold">
            {t("fichier")}
          </label>
          <p id={aide} className="text-sm text-texte-doux">
            {t("aideFichier")}
          </p>
          <input
            ref={champ}
            id={id}
            type="file"
            accept=".csv,text/csv"
            aria-describedby={refus ? `${aide} ${message}` : aide}
            aria-invalid={refus ? true : undefined}
            disabled={envoi}
            onChange={(e) => choisir(e.target.files?.[0])}
            className="block min-h-11 w-full max-w-md cursor-pointer rounded-lg border border-bordure bg-white p-2 text-sm file:mr-3 file:min-h-9 file:cursor-pointer file:rounded-md file:border-0 file:bg-ice file:px-3 file:font-semibold file:text-royal"
          />
          {fichier ? (
            <p className="flex items-center gap-2 text-sm">
              <FileSpreadsheet aria-hidden="true" className="size-4 text-succes" />
              {fichier.name}
            </p>
          ) : null}
          {refus ? (
            <p id={message} className="font-semibold text-danger">
              {t(refus)}
            </p>
          ) : null}
        </div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div className="flex flex-col gap-1">
            <label htmlFor={`${id}-banque`} className="font-semibold">
              {t("banque")}
            </label>
            <input
              id={`${id}-banque`}
              type="text"
              maxLength={120}
              value={banque}
              onChange={(e) => setBanque(e.target.value)}
              aria-describedby={`${id}-banque-aide`}
              className={classesChamp}
              disabled={envoi}
            />
            <p id={`${id}-banque-aide`} className="text-sm text-texte-doux">
              {t("aideBanque")}
            </p>
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor={`${id}-date`} className="font-semibold">
              {t("date")}
            </label>
            <input
              id={`${id}-date`}
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              aria-describedby={`${id}-date-aide`}
              className={classesChamp}
              disabled={envoi}
            />
            <p id={`${id}-date-aide`} className="text-sm text-texte-doux">
              {t("aideDate")}
            </p>
          </div>
        </div>

        {erreur ? (
          <Alerte nature="erreur" titre={t("erreur")}>
            <p>{erreur.titre}</p>
            {erreur.details.length > 0 ? (
              <ul className="mt-2 list-disc pl-5">
                {erreur.details.map((d) => (
                  <li key={d}>{d}</li>
                ))}
              </ul>
            ) : null}
          </Alerte>
        ) : null}

        <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center">
          <Bouton type="submit" taille="grande" disabled={envoi}>
            <Upload aria-hidden="true" className="size-5" />
            {envoi ? t("enCours") : t("bouton")}
          </Bouton>
          {exempleDisponible ? (
            <span className="flex flex-col">
              <a
                href="/api/releves/exemple"
                download="releve-exemple.csv"
                className="inline-flex min-h-11 items-center gap-2 font-semibold text-royal underline-offset-4 hover:underline"
              >
                <Download aria-hidden="true" className="size-4" />
                {t("exemple")}
              </a>
              <span className="text-sm text-texte-doux">{t("exempleAide")}</span>
            </span>
          ) : null}
        </div>
      </form>
    </Carte>
  );
}
