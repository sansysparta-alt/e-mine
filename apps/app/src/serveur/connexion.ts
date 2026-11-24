import { langueValide } from "@/i18n/langues";

/** Messages que la page de connexion e-mine sait afficher (thème Keycloak, `js/motif.js`). */
export const MOTIFS = ["deconnecte", "expiree", "erreur"] as const;
export type Motif = (typeof MOTIFS)[number];

/** Actions demandées à Keycloak depuis l'application (page « Mon profil »). */
const ACTIONS: Record<string, string> = { "mot-de-passe": "UPDATE_PASSWORD" };

/**
 * Paramètres ajoutés à l'URL de connexion Keycloak : langue de l'interface, motif du message
 * (liste fermée : rien d'autre ne passe), action demandée. Tout paramètre inconnu est ignoré.
 */
export function supplementsConnexion(parametres: {
  motif?: string | null;
  action?: string | null;
  langue?: string | null;
}): Record<string, string> {
  // `kc_locale` et `ui_locales` : la page Keycloak s'affiche dans la langue de l'application, qui
  // reste seule maîtresse de la langue (le choix fait sur la page de connexion revient vers elle).
  const langue = langueValide(parametres.langue);
  const s: Record<string, string> = { ui_locales: langue, kc_locale: langue };
  if ((MOTIFS as readonly string[]).includes(parametres.motif ?? "")) {
    s.motif = parametres.motif as Motif;
  }
  const action = ACTIONS[parametres.action ?? ""];
  if (action) s.kc_action = action;
  return s;
}

/** Adresse de connexion avec un motif et, si utile, la page où revenir ensuite. */
export function adresseConnexion(motif: Motif, retour?: string): string {
  const q = new URLSearchParams({ motif });
  if (retour) q.set("retour", retour);
  return `/api/auth/login?${q.toString()}`;
}
