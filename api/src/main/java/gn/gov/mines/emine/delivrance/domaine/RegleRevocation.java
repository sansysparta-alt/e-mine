package gn.gov.mines.emine.delivrance.domaine;

import java.util.Optional;
import java.util.Set;

/**
 * Révocation d'un document délivré (US-06.6, décision du porteur du projet du 22/11/2026) :
 * réservée aux profils Directeur et Brigade anti-fraude, motif obligatoire de 10 à 2000 caractères,
 * effet immédiat sur la vérification publique (« NON VALIDE »). Un document révoqué ne revient pas
 * valide.
 */
public final class RegleRevocation {

  /** Profils autorisés à révoquer (la matrice des droits n'a pas de droit « révoquer »). */
  public static final Set<String> PROFILS = Set.of("DIRECTEUR", "BRIGADE");

  public static final int MOTIF_MIN = 10;
  public static final int MOTIF_MAX = 2000;

  private RegleRevocation() {}

  public static boolean peutRevoquer(String profilCode) {
    return profilCode != null && PROFILS.contains(profilCode);
  }

  /** Problème du motif, ou vide s'il convient (jugé sans les espaces de bord). */
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
}
