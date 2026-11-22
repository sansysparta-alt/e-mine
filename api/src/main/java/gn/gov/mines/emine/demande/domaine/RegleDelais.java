package gn.gov.mines.emine.demande.domaine;

/**
 * Délais de traitement par dossier (US-05.6) : le délai du catalogue ({@code
 * type_document.delai_jours}) se compte en jours calendaires depuis le dépôt, à l'heure de Conakry,
 * hors jours passés en complément (délai suspendu, US-05.2). Un dossier en cours est :
 *
 * <ul>
 *   <li><b>en retard</b> quand il ne lui reste plus de jour (jours restants &lt; 0) ;
 *   <li><b>proche du dépassement</b> quand il lui reste au plus {@value #PROPORTION_POURCENT} % du
 *       délai, et au moins {@value #SEUIL_MIN_JOURS} jours (pour les délais courts), sans être en
 *       retard ni suspendu.
 * </ul>
 *
 * Le calcul est fait par la base (même condition pour la fiche, les listes, les filtres et les
 * indicateurs) ; cette classe en donne les paramètres.
 */
public final class RegleDelais {

  /** Seuil « proche du dépassement » : part du délai du catalogue restant, en pourcentage. */
  public static final int PROPORTION_POURCENT = 20;

  /** Seuil « proche du dépassement » minimal, en jours (délais de 2 à 10 jours). */
  public static final int SEUIL_MIN_JOURS = 2;

  private RegleDelais() {}

  /** Seuil en jours pour un délai du catalogue donné. */
  public static int seuilProcheJours(int delaiJours) {
    return Math.max(SEUIL_MIN_JOURS, (int) Math.ceil(delaiJours * PROPORTION_POURCENT / 100.0));
  }
}
