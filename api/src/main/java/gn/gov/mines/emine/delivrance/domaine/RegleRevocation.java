package gn.gov.mines.emine.delivrance.domaine;

import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Annulation (révocation) d'un document délivré (US-06.6, US-06.8, décision V1-11 du Ministère) :
 * <b>seul le signataire du document l'annule</b> (profil et titre de la fiche figée au dépôt,
 * contrôlés par la base, V35) ; la <b>Brigade anti-fraude le signale</b>, sans effet sur le
 * document. Motif obligatoire de 10 à 2000 caractères ; effet immédiat sur la vérification publique
 * (« NON VALIDE ») ; un document annulé ne revient pas valide. Un document signé par le Président
 * (hors plateforme) n'est jamais annulé par la plateforme : le décret d'abrogation est enregistré
 * (lot 7).
 */
public final class RegleRevocation {

  /** Profil qui signale un document (la matrice des droits n'a pas de droit « signaler »). */
  public static final String BRIGADE = "BRIGADE";

  public static final int MOTIF_MIN = 10;
  public static final int MOTIF_MAX = 2000;

  private RegleRevocation() {}

  public static boolean peutSignaler(String profilCode) {
    return BRIGADE.equals(profilCode);
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

  /** Le motif sans ses espaces de bord, ou VAL-001 sur le champ « motif ». */
  public static String motifValide(String motif) {
    problemeMotif(motif)
        .ifPresent(
            message -> {
              throw new ErreurMetier(
                  CodeErreur.VAL_001,
                  Map.of("champs", List.of(Map.of("champ", "motif", "message", message))));
            });
    return motif.strip();
  }
}
