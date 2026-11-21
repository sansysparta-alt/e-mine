package gn.gov.mines.emine.demande.domaine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Conditions du dépôt d'une demande (US-03.4, correctif J2 B3) : déclaration sur l'honneur acceptée
 * et une pièce au moins pour <b>chaque</b> pièce obligatoire du catalogue. Les pièces
 * supplémentaires (« Autre pièce ») sont facultatives. L'agent du guichet juge ensuite la qualité
 * des pièces (complément requis si besoin).
 */
public final class RegleDepot {

  /** Élément manquant signalé dans {@code details.manques} de l'erreur DEM-004. */
  public static final String DECLARATION = "declarationHonneur";

  public static final String PIECES = "pieces";

  private RegleDepot() {}

  /** Pièces obligatoires du catalogue sans aucun fichier joint, dans l'ordre du catalogue. */
  public static List<String> piecesManquantes(
      List<String> piecesObligatoires, Collection<String> typesFournis) {
    Set<String> fournis = Set.copyOf(typesFournis);
    return piecesObligatoires.stream().filter(p -> !fournis.contains(p)).toList();
  }

  /** Éléments manquants ; vide si la demande peut être déposée. */
  public static List<String> manques(boolean declarationAcceptee, List<String> piecesManquantes) {
    List<String> manques = new ArrayList<>();
    if (!declarationAcceptee) {
      manques.add(DECLARATION);
    }
    if (!piecesManquantes.isEmpty()) {
      manques.add(PIECES);
    }
    return manques;
  }
}
