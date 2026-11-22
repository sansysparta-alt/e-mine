package gn.gov.mines.emine.demande.domaine;

import java.time.Instant;
import java.util.List;

/**
 * Dossier passé sans action dans le traitement en série (correctifs ergonomie, V11) : qui, quand, à
 * quelle étape, pourquoi.
 *
 * @param auteur prénom et nom de l'agent
 * @param statut statut de la demande au moment du passage
 * @param motif motif court donné par l'agent
 * @param le instant du passage
 */
public record Passage(String auteur, String statut, String motif, Instant le) {

  /** Nombre de passages à la même étape à partir duquel le dossier est signalé « Bloqué ». */
  public static final int SEUIL_BLOCAGE = 3;

  /**
   * Passages d'un dossier.
   *
   * @param aLEtape nombre de passages depuis l'entrée dans le statut actuel
   * @param bloque le dossier, en cours, a été passé au moins {@link #SEUIL_BLOCAGE} fois à cette
   *     étape
   * @param passages tous les passages, du plus récent au plus ancien
   */
  public record Historique(int aLEtape, boolean bloque, List<Passage> passages) {

    public Historique {
      passages = List.copyOf(passages);
    }
  }
}
