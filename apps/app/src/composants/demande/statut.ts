import type { TonBadge } from "@emine/ui";

/** Ton du badge de chaque statut de demande (le libellé vient des traductions `statuts.*`). */
export function tonStatut(statut: string): TonBadge {
  switch (statut) {
    case "complement_requis":
      return "avertissement";
    case "rejetee":
      return "danger";
    case "droits_payes":
    case "delivree":
      return "succes";
    case "brouillon":
    case "archivee":
      return "neutre";
    default:
      return "info";
  }
}
