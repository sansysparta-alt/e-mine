import { waitFor } from "@testing-library/react";
import { getDocument } from "pdfjs-dist";
import { expect } from "vitest";

/**
 * Le visualiseur a demandé ce PDF au lecteur du socle (pdf.js, simulé dans vitest.setup.ts) :
 * l'adresse d'affichage passe par le relais BFF.
 */
export async function attendrePdf(url: string) {
  await waitFor(() => expect(getDocument).toHaveBeenCalledWith(expect.objectContaining({ url })));
}
