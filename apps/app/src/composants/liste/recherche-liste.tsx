"use client";

import { ChampRecherche, type ChampRechercheProps } from "@emine/ui";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useTransition } from "react";
import { memoriserRecente, useRecentes } from "../recherche/recentes";

/**
 * Recherche d'une liste de l'application : résultats mis à jour pendant la saisie (court délai),
 * en remplaçant le paramètre `q` de l'adresse ; retour en page 1, filtres et tri conservés. Les
 * recherches récentes de la liste (ce navigateur) sont proposées avant les autres suggestions ;
 * une croix vide le champ.
 */
export function RechercheListe(
  props: Omit<ChampRechercheProps, "surRecherche" | "enCours" | "libelleVider">,
) {
  const t = useTranslations("rechercheListe");
  const router = useRouter();
  const chemin = usePathname();
  const parametres = useSearchParams();
  const [enCours, demarrer] = useTransition();
  const cle = `emine.recherches-recentes:${chemin}`;
  const recentes = useRecentes(cle);

  function rechercher(texte: string) {
    const q = new URLSearchParams(parametres.toString());
    if ((q.get("q") ?? "") === texte) return;
    if (texte) q.set("q", texte.slice(0, 100));
    else q.delete("q");
    q.delete("page");
    memoriserRecente(cle, texte);
    const chaine = q.toString();
    demarrer(() => router.replace(chaine ? `${chemin}?${chaine}` : chemin, { scroll: false }));
  }

  return (
    <ChampRecherche
      {...props}
      suggestions={[...recentes, ...(props.suggestions ?? [])]}
      libelleVider={t("vider")}
      surRecherche={rechercher}
      enCours={enCours}
    />
  );
}
