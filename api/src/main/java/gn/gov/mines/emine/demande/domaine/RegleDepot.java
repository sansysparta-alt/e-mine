package gn.gov.mines.emine.demande.domaine;

import java.util.ArrayList;
import java.util.List;

/**
 * Conditions du dépôt d'une demande (US-03.4, V1) : déclaration sur l'honneur acceptée et au moins
 * une pièce jointe. La complétude des pièces au regard du catalogue est jugée par l'agent du
 * guichet à la recevabilité (US-05.1), selon la décision du porteur du projet du 20/11/2026.
 */
public final class RegleDepot {

  /** Élément manquant signalé dans {@code details.manques} de l'erreur DEM-004. */
  public static final String DECLARATION = "declarationHonneur";

  public static final String PIECES = "pieces";

  private RegleDepot() {}

  /** Éléments manquants ; vide si la demande peut être déposée. */
  public static List<String> manques(boolean declarationAcceptee, int nombrePieces) {
    List<String> manques = new ArrayList<>();
    if (!declarationAcceptee) {
      manques.add(DECLARATION);
    }
    if (nombrePieces < 1) {
      manques.add(PIECES);
    }
    return manques;
  }
}
