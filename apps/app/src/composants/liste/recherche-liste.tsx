"use client";

import { ChampRecherche, type ChampRechercheProps } from "@emine/ui";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useTransition } from "react";

/**
 * Recherche d'une liste de l'application : résultats mis à jour pendant la saisie (court délai),
 * en remplaçant le paramètre `q` de l'adresse ; retour en page 1, filtres et tri conservés.
 */
export function RechercheListe(props: Omit<ChampRechercheProps, "surRecherche" | "enCours">) {
  const router = useRouter();
  const chemin = usePathname();
  const parametres = useSearchParams();
  const [enCours, demarrer] = useTransition();

  function rechercher(texte: string) {
    const q = new URLSearchParams(parametres.toString());
    if ((q.get("q") ?? "") === texte) return;
    if (texte) q.set("q", texte.slice(0, 100));
    else q.delete("q");
    q.delete("page");
    const chaine = q.toString();
    demarrer(() => router.replace(chaine ? `${chemin}?${chaine}` : chemin, { scroll: false }));
  }

  return <ChampRecherche {...props} surRecherche={rechercher} enCours={enCours} />;
}
