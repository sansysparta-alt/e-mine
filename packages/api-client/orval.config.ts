import { defineConfig } from "orval";

// openapi.json est produit par l'API (test OpenApiTest) et versionné ; la CI vérifie qu'il est à jour.
export default defineConfig({
  emine: {
    input: { target: "./openapi.json" },
    output: {
      target: "./src/generated/emine.ts",
      client: "fetch",
      mode: "single",
      clean: true,
      override: {
        mutator: { path: "./src/requete.ts", name: "requete" },
      },
    },
  },
});
