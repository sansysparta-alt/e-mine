/** Configuration du BFF, lue dans l'environnement (fichier .env à la racine, jamais versionné). */
function requis(nom: string): string {
  const valeur = process.env[nom];
  if (!valeur) {
    throw new Error(`Variable d'environnement manquante : ${nom} (voir .env.example)`);
  }
  return valeur;
}

export function configuration() {
  const secretSession = requis("SESSION_SECRET");
  if (secretSession.length < 32) {
    throw new Error("SESSION_SECRET doit contenir au moins 32 caractères");
  }
  const urlApplication = (process.env.APP_URL ?? "http://localhost:3001").replace(/\/$/, "");
  return {
    emetteur: requis("OIDC_ISSUER"),
    clientId: requis("OIDC_CLIENT_ID_APP"),
    secretClient: requis("OIDC_CLIENT_SECRET_APP"),
    secretSession,
    urlApplication,
    urlRetourOidc: `${urlApplication}/api/auth/callback`,
    production: process.env.NODE_ENV === "production",
  };
}
