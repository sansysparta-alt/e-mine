package gn.gov.mines.emine.instruction.domaine;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Compléments (US-05.2, choix du porteur du projet du 22/11/2026) :
 *
 * <ul>
 *   <li>un agent qui instruit demande un complément à l'examen de la recevabilité (demande déposée)
 *       ou pendant l'instruction ; motif obligatoire, pièces attendues facultatives ;
 *   <li>le demandeur a 5 jours ; il est relancé automatiquement à J+3 ; le délai de traitement du
 *       catalogue est suspendu pendant le complément ;
 *   <li>pour répondre, il joint chaque pièce demandée, ou écrit sa réponse si aucune pièce n'est
 *       demandée ; la demande revient à l'étape d'où le complément a été demandé ;
 *   <li>sans réponse dans le délai, <b>rien n'est automatique</b> : le dossier est signalé à
 *       l'agent, qui relance (nouveau délai de 5 jours) ou clôt le dossier avec un motif (rejet).
 * </ul>
 */
public final class RegleComplement {

  /** Délai de réponse du demandeur, en jours (posé par la base, V12). */
  public static final int DELAI_REPONSE_JOURS = 5;

  /** Relance automatique, en jours après la demande de complément. */
  public static final int RELANCE_JOURS = 3;

  /** Nombre maximal de pièces demandées dans un complément. */
  public static final int PIECES_MAX = 20;

  /** Longueur maximale du nom d'une pièce demandée. */
  public static final int PIECE_LONGUEUR_MAX = 200;

  /** Étapes d'où un complément peut être demandé. */
  public static final Set<String> STATUTS_ORIGINE = Set.of("deposee", "en_instruction");

  private RegleComplement() {}

  /** Un complément peut-il être demandé à cette étape ? */
  public static boolean peutEtreDemande(String statut) {
    return STATUTS_ORIGINE.contains(statut);
  }

  /** Pièces demandées nettoyées : sans vide ni doublon, dans l'ordre de saisie. */
  public static List<String> piecesNettoyees(Collection<String> pieces) {
    if (pieces == null) {
      return List.of();
    }
    Set<String> uniques = new LinkedHashSet<>();
    for (String p : pieces) {
      if (p != null && !p.isBlank()) {
        uniques.add(p.strip());
      }
    }
    return List.copyOf(uniques);
  }

  /** Problème de la liste des pièces demandées, ou vide si elle convient. */
  public static Optional<String> problemePieces(List<String> pieces) {
    if (pieces.size() > PIECES_MAX) {
      return Optional.of("Demandez " + PIECES_MAX + " pièces au plus.");
    }
    if (pieces.stream().anyMatch(p -> p.length() > PIECE_LONGUEUR_MAX)) {
      return Optional.of("Le nom d'une pièce dépasse " + PIECE_LONGUEUR_MAX + " caractères.");
    }
    return Optional.empty();
  }

  /** Pièces demandées qui n'ont pas encore été jointes depuis la demande de complément. */
  public static List<String> piecesManquantes(
      List<String> demandees, Collection<String> jointesDepuis) {
    Set<String> jointes = new HashSet<>(jointesDepuis);
    return demandees.stream().filter(p -> !jointes.contains(p)).toList();
  }

  /**
   * La réponse est-elle complète ? Chaque pièce demandée est jointe ; sans pièce demandée, une
   * réponse écrite de 10 caractères au moins est exigée.
   */
  public static boolean reponseComplete(
      List<String> demandees, Collection<String> jointesDepuis, String message) {
    if (!demandees.isEmpty()) {
      return piecesManquantes(demandees, jointesDepuis).isEmpty();
    }
    return message != null && message.strip().length() >= RegleMotif.MIN;
  }
}
