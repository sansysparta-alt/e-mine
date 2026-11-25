// Matchers jest-dom enregistrés sur l'instance de `expect` du lanceur de tests. L'import
// « @testing-library/jest-dom/vitest » résout parfois une autre instance de vitest via les liens
// pnpm (sous Windows), d'où des échecs intermittents « Invalid Chai property ».
// Import de types seulement (effacé à l'exécution) : déclare les matchers pour TypeScript.
import type {} from "@testing-library/jest-dom/vitest";
import * as matchers from "@testing-library/jest-dom/matchers";
import { expect, vi } from "vitest";

expect.extend(matchers);

// pdf.js (lecteur PDF du socle) simulé : un document d'une page, sans moteur de rendu (jsdom).
vi.mock("pdfjs-dist", () => ({
  GlobalWorkerOptions: {},
  getDocument: vi.fn(() => ({
    destroy: vi.fn(() => Promise.resolve()),
    promise: Promise.resolve({
      numPages: 1,
      destroy: vi.fn(() => Promise.resolve()),
      getPage: vi.fn(() =>
        Promise.resolve({
          getViewport: ({ scale }: { scale: number }) => ({
            width: 595 * scale,
            height: 842 * scale,
          }),
          render: vi.fn(() => ({ promise: Promise.resolve(), cancel: vi.fn() })),
        }),
      ),
    }),
  })),
}));
