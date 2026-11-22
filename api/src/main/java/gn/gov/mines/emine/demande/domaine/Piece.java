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

  /** Version en vigueur (pas remplacée). */
  public boolean enVigueur() {
    return remplaceeLe == null;
  }
}
