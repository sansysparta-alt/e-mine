package gn.gov.mines.emine.demande.domaine;

import java.time.Instant;

/**
 * Pièce jointe d'une demande (table {@code piece_jointe}).
 *
 * @param typePiece pièce du catalogue à laquelle elle répond (« EIES »…) ou « Autre pièce »
 * @param stockageCle clé de l'objet dans le compartiment des pièces
 * @param remplaceeLe remplacée par une nouvelle version en réponse à un complément (V14), sinon
 *     {@code null}
 * @param remplaceeParId pièce qui la remplace, sinon {@code null}
 */
public record Piece(
    long id,
    long demandeId,
    String typePiece,
    String nomFichier,
    String typeMime,
    long tailleOctets,
    String empreinteSha256,
    String stockageCle,
    Instant deposeLe,
    Instant remplaceeLe,
    Long remplaceeParId) {

  /**
   * Type réservé au justificatif d'un virement (US-04.7) : pièce de la demande rattachée au
   * paiement, jamais listée parmi les pièces du dossier.
   */
  public static final String JUSTIFICATIF_VIREMENT = "JUSTIFICATIF_VIREMENT";

  /** Taille maximale d'un justificatif de virement (5 Mo, cahier des charges). */
  public static final long TAILLE_MAX_JUSTIFICATIF = 5L * 1024 * 1024;

  /** Version en vigueur (pas remplacée). */
  public boolean enVigueur() {
    return remplaceeLe == null;
  }
}
