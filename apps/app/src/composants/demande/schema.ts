import type { BrouillonDto, DemandeDto } from "@emine/api-client";
import { z } from "zod";

/** Les quatre sections du formulaire guidé (V1 : sans périmètre cartographique). */
export const ETAPES = ["demandeur", "document", "pieces", "recapitulatif"] as const;
export type Etape = (typeof ETAPES)[number];

const SUPERFICIE = /^\d{1,10}([.,]\d{1,2})?$/;

/**
 * Saisie du brouillon, telle que tapée dans le formulaire (chaînes). Les messages sont des clés de
 * traduction (`demande.erreurs.<cle>`).
 */
export const schemaBrouillon = z.object({
  typeDocumentCode: z.string().regex(/^[A-Z]{3}$/, "documentObligatoire"),
  prefectureId: z.string(),
  substanceCode: z.string(),
  superficieHa: z
    .string()
    .trim()
    .refine(
      (v) => v === "" || (SUPERFICIE.test(v) && Number(v.replace(",", ".")) > 0),
      "superficieInvalide",
    ),
});

export type ValeursBrouillon = z.infer<typeof schemaBrouillon>;

/** Champs à valider avant de quitter chaque section. */
export const CHAMPS_PAR_ETAPE: Record<Etape, (keyof ValeursBrouillon)[]> = {
  demandeur: [],
  document: ["typeDocumentCode", "prefectureId", "substanceCode", "superficieHa"],
  pieces: [],
  recapitulatif: [],
};

export function versBrouillonDto(v: ValeursBrouillon): BrouillonDto {
  const superficie = v.superficieHa.trim();
  return {
    typeDocumentCode: v.typeDocumentCode,
    prefectureId: v.prefectureId === "" ? null : Number(v.prefectureId),
    substanceCode: v.substanceCode === "" ? null : v.substanceCode,
    superficieHa: superficie === "" ? null : Number(superficie.replace(",", ".")),
  };
}

export function depuisDemande(d: DemandeDto | null, typeParDefaut = ""): ValeursBrouillon {
  return {
    typeDocumentCode: d?.typeDocumentCode ?? typeParDefaut,
    prefectureId: d?.prefectureId == null ? "" : String(d.prefectureId),
    substanceCode: d?.substanceCode ?? "",
    superficieHa: d?.superficieHa == null ? "" : String(d.superficieHa).replace(".", ","),
  };
}
