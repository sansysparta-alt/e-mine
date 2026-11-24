"use client";

import { classesChamp, cn } from "@emine/ui";
import { X } from "@emine/ui/icones";
import { usePathname, useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useId, useTransition } from "react";

/**
 * Filtre de période d'une liste (socle) : la période s'applique **dès qu'une date est choisie**
 * (adresse mise à jour, retour en page 1), une croix la vide ; une période inversée est signalée
 * sans rien appliquer. Les autres paramètres de l'adresse sont conservés.
 */
export function FiltrePeriode({
  du,
  au,
  conserver = {},
}: {
  du?: string;
  au?: string;
  /** Autres paramètres de la liste à garder (recherche, filtres…). */
  conserver?: Record<string, string | undefined>;
}) {
  const t = useTranslations("periode");
  const router = useRouter();
  const chemin = usePathname();
  const [enCours, demarrer] = useTransition();
  const id = useId();
  const inversee = Boolean(du && au && au < du);

  function aller(suivant: { du?: string; au?: string }) {
    const params = new URLSearchParams();
    for (const [cle, valeur] of Object.entries({ ...conserver, ...suivant })) {
      if (valeur) params.set(cle, valeur);
    }
    const chaine = params.toString();
    demarrer(() => router.replace(chaine ? `${chemin}?${chaine}` : chemin, { scroll: false }));
  }

  return (
    <fieldset
      className="flex flex-col gap-2"
      aria-busy={enCours || undefined}
      aria-describedby={inversee ? `${id}-erreur` : undefined}
    >
      <legend className="sr-only">{t("libelle")}</legend>
      <div className="flex flex-wrap items-end gap-3">
        <div className="flex flex-col gap-1">
          <label htmlFor={`${id}-du`} className="text-sm font-semibold">
            {t("du")}
          </label>
          <input
            id={`${id}-du`}
            type="date"
            value={du ?? ""}
            max={au}
            onChange={(e) => aller({ du: e.target.value || undefined, au })}
            aria-invalid={inversee || undefined}
            className={cn(classesChamp, "w-44")}
          />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor={`${id}-au`} className="text-sm font-semibold">
            {t("au")}
          </label>
          <input
            id={`${id}-au`}
            type="date"
            value={au ?? ""}
            min={du}
            onChange={(e) => aller({ du, au: e.target.value || undefined })}
            aria-invalid={inversee || undefined}
            className={cn(classesChamp, "w-44")}
          />
        </div>
        {du || au ? (
          <button
            type="button"
            onClick={() => aller({ du: undefined, au: undefined })}
            className="inline-flex min-h-11 items-center gap-1 rounded-lg px-2 font-semibold text-royal hover:bg-ice"
          >
            <X aria-hidden="true" className="size-4" />
            {t("vider")}
          </button>
        ) : null}
      </div>
      {inversee ? (
        <p id={`${id}-erreur`} role="alert" className="text-sm font-semibold text-danger">
          {t("inverseeTitre")} : {t("inversee")}
        </p>
      ) : null}
    </fieldset>
  );
}
