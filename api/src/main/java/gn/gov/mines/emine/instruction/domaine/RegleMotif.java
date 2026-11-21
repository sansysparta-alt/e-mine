package gn.gov.mines.emine.instruction.domaine;

import java.util.Optional;

/**
 * Motif communiqué au demandeur (non-recevabilité US-05.1, rejet US-05.4) : 10 à 2000 caractères
 * hors espaces de début et de fin, pour qu'il explique réellement la décision.
 */
public final class RegleMotif {

  /** Longueur minimale du motif. */
  public static final int MIN = 10;

  /** Longueur maximale du motif (et des observations d'une décision favorable). */
  public static final int MAX = 2000;

  private RegleMotif() {}

  /** Problème du motif obligatoire (absent, trop court, trop long), ou vide s'il convient. */
  public static Optional<String> probleme(String motif) {
    int longueur = motif == null ? 0 : motif.strip().length();
    if (longueur < MIN) {
      return Optional.of("Expliquez le motif en " + MIN + " caractères au moins.");
    }
    return trop(longueur);
  }

  /** Problème d'un texte facultatif (observations) : seulement s'il est trop long. */
  public static Optional<String> problemeFacultatif(String texte) {
    return trop(texte == null ? 0 : texte.strip().length());
  }

  /** Texte nettoyé, ou {@code null} s'il est vide. */
  public static String nettoyer(String texte) {
    return texte == null || texte.isBlank() ? null : texte.strip();
  }

  private static Optional<String> trop(int longueur) {
    return longueur > MAX
        ? Optional.of("Le texte dépasse " + MAX + " caractères.")
        : Optional.empty();
  }
}
