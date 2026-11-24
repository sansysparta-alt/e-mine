"use client";

import { cn } from "@emine/ui";
import { useRouter } from "next/navigation";
import { useTransition } from "react";
import { LANGUES } from "@/i18n/langues";
import { changerLangue } from "@/serveur/actions-langue";

const NOMS: Record<(typeof LANGUES)[number], string> = { fr: "Français", en: "English" };

/** Choix de la langue de l'interface (page « Mon profil ») : appliqué aussitôt. */
export function ChoixLangue({ langue, legende }: { langue: string; legende: string }) {
  const router = useRouter();
  const [enCours, demarrer] = useTransition();
  return (
    <fieldset disabled={enCours} className="flex flex-col gap-2">
      <legend className="mb-1 text-texte-doux">{legende}</legend>
      <div className="flex flex-col gap-2 sm:flex-row">
        {LANGUES.map((l) => (
          <label
            key={l}
            lang={l}
            className={cn(
              "flex min-h-11 cursor-pointer items-center gap-3 rounded-lg border-2 bg-white px-4",
              langue === l ? "border-royal bg-ice" : "border-bordure hover:border-royal",
            )}
          >
            <input
              type="radio"
              name="langue"
              value={l}
              checked={langue === l}
              onChange={() =>
                demarrer(async () => {
                  await changerLangue(l);
                  router.refresh();
                })
              }
              className="size-5 accent-royal"
            />
            {NOMS[l]}
          </label>
        ))}
      </div>
    </fieldset>
  );
}
