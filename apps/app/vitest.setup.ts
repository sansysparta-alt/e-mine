// Matchers jest-dom enregistrés sur l'instance de `expect` du lanceur de tests. L'import
// « @testing-library/jest-dom/vitest » résout parfois une autre instance de vitest via les liens
// pnpm (sous Windows), d'où des échecs intermittents « Invalid Chai property ».
import * as matchers from "@testing-library/jest-dom/matchers";
import { expect } from "vitest";

expect.extend(matchers);
