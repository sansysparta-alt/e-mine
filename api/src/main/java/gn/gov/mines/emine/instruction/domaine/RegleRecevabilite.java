package gn.gov.mines.emine.instruction.domaine;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Recevabilité d'une demande (US-05.1, V1) : seule une demande déposée s'examine ; elle est
 * recevable si ses frais de dossier (ou le paiement unique d'une attestation) sont payés <b>et</b>
 * si chaque pièce obligatoire du catalogue a un fichier. L'agent du guichet juge ensuite la qualité
 * des pièces ; s'il déclare la demande non recevable, un motif est obligatoire.
 */
public final class RegleRecevabilite {

  /** Statut de la demande à examiner. */
  public static final String STATUT_A_EXAMINER = "deposee";

  /** Longueur minimale du motif de non-recevabilité (un motif qui explique quelque chose). */
  public static final int MOTIF_MIN = 10;

  /** Longueur maximale du motif. */
  public static final int MOTIF_MAX = 2000;

  private RegleRecevabilite() {}

  /** Examen des conditions de la recevabilité. */
  public static Examen examiner(
      String statut,
      boolean fraisPayes,
      List<String> piecesRequises,
      Collection<String> piecesFournies) {
    Set<String> fournies = new HashSet<>(piecesFournies);
    List<String> manquantes = piecesRequises.stream().filter(p -> !fournies.contains(p)).toList();
    return new Examen(STATUT_A_EXAMINER.equals(statut), fraisPayes, manquantes);
  }

  /**
   * Problème du motif (absent, trop court, trop long), ou vide s'il convient. Le motif est jugé
   * sans les espaces de début et de fin.
   */
  public static Optional<String> problemeMotif(String motif) {
    int longueur = motif == null ? 0 : motif.strip().length();
    if (longueur < MOTIF_MIN) {
      return Optional.of("Expliquez le motif en " + MOTIF_MIN + " caractères au moins.");
    }
    if (longueur > MOTIF_MAX) {
      return Optional.of("Le motif dépasse " + MOTIF_MAX + " caractères.");
    }
    return Optional.empty();
  }

  /**
   * Résultat de l'examen.
   *
   * @param aExaminer la demande est au statut « déposée »
   * @param fraisPayes frais de dossier (ou attestation) payés
   * @param piecesManquantes pièces obligatoires du catalogue sans fichier
   */
  public record Examen(boolean aExaminer, boolean fraisPayes, List<String> piecesManquantes) {

    public Examen {
      piecesManquantes = List.copyOf(piecesManquantes);
    }

    /** Toutes les conditions de la recevabilité sont réunies. */
    public boolean peutEtreRecevable() {
      return aExaminer && fraisPayes && piecesManquantes.isEmpty();
    }
  }
}
