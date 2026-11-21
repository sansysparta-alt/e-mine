package gn.gov.mines.emine.demande.domaine;

import java.time.Instant;

/**
 * Pièce jointe d'une demande (table {@code piece_jointe}).
 *
 * @param typePiece pièce du catalogue à laquelle elle répond (« EIES »…) ou « Autre pièce »
 * @param stockageCle clé de l'objet dans le compartiment des pièces
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
    Instant deposeLe) {}
