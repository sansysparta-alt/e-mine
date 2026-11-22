/**
 * Vérification publique d'un document (US-06.5) : lecture du code de vérification (saisi, ou dans
 * l'adresse lue par le QR) et empreinte SHA-256 d'un fichier, calculée dans le navigateur : le
 * fichier n'est jamais envoyé.
 */
const CODE = /[0-9a-f]{32}/i;

/** Code de vérification trouvé dans le texte (code seul ou adresse complète), ou null. */
export function extraireCode(texte: string | undefined | null): string | null {
  const trouve = texte?.trim().match(CODE);
  return trouve ? trouve[0].toLowerCase() : null;
}

/** Empreinte SHA-256 hexadécimale du contenu (Web Crypto). */
export async function empreinteSha256(contenu: ArrayBuffer): Promise<string> {
  const condense = await crypto.subtle.digest("SHA-256", contenu);
  return Array.from(new Uint8Array(condense))
    .map((o) => o.toString(16).padStart(2, "0"))
    .join("");
}
