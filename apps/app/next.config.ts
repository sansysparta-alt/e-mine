import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

// Variables du BFF (OIDC, session, URL de l'API) : fichier .env à la racine du dépôt, jamais versionné.
const envRacine = fileURLToPath(new URL("../../.env", import.meta.url));
if (existsSync(envRacine)) {
  process.loadEnvFile(envRacine);
}

const avecTraductions = createNextIntlPlugin("./src/i18n/request.ts");

const config: NextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  // L'indicateur de développement de Next.js (le rond « N ») masquait « Réduire le menu ».
  devIndicators: false,
  transpilePackages: ["@emine/ui", "@emine/api-client"],
  async headers() {
    return [
      {
        source: "/:chemin*",
        headers: [
          { key: "X-Frame-Options", value: "DENY" },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
        ],
      },
      {
        // Visualiseur de documents (D9) : une pièce peut s'afficher dans un cadre de l'application
        // elle-même, jamais dans un autre site. Règle placée après la précédente : elle l'emporte.
        source: "/api/demandes/:id/pieces/:pieceId/contenu",
        headers: [
          { key: "X-Frame-Options", value: "SAMEORIGIN" },
          { key: "Content-Security-Policy", value: "frame-ancestors 'self'" },
        ],
      },
      {
        // Reçus de paiement, consultés eux aussi dans le visualiseur (correctifs J4).
        source: "/api/paiements/:reference/recu",
        headers: [
          { key: "X-Frame-Options", value: "SAMEORIGIN" },
          { key: "Content-Security-Policy", value: "frame-ancestors 'self'" },
        ],
      },
    ];
  },
};

export default avecTraductions(config);
