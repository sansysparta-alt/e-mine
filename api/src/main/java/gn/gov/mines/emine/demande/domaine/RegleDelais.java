package gn.gov.mines.emine.demande.domaine;

/**
 * Délai de traitement d'un dossier (US-05.6) : <b>une seule règle</b>, calculée par la base ({@code
 * fn_jours_traitement}, V17) et réutilisée partout — jours restants de la fiche, filtres « en
 * retard » et « proche du dépassement », indicateurs, part des dossiers délivrés dans le délai et
 * délai moyen.
 *
 * <p>Le cahier des charges ne fixe que des « délais cibles » par document ({@code
 * type_document.delai_jours}). Choix du porteur du projet du 22/11/2026, paramétrables (table
 * {@code parametre_delai}) et à faire valider par le Ministère :
 *
 * <ul>
 *   <li>jours calendaires, à l'heure de Conakry, <b>jour de départ inclus</b> (délivré le jour même
 *       = 1 jour) ;
 *   <li>départ au <b>paiement des frais</b> de dossier (ou du paiement unique d'une attestation) :
 *       avant, le délai ne court pas ; au dépôt pour un document sans frais au barème ;
 *   <li><b>pauses</b> pendant un complément et pendant l'attente du paiement des droits ;
 *   <li>fin à la délivrance ;
 *   <li><b>en retard</b> : jours de traitement supérieurs au délai (jours restants &lt; 0) ;
 *   <li><b>proche du dépassement</b> : en cours, sans pause, et il reste au plus {@value
 *       #SEUIL_POURCENT_DEFAUT} % du délai, {@value #SEUIL_MIN_JOURS_DEFAUT} jours au moins
 *       (valeurs par défaut).
 * </ul>
 */
public final class RegleDelais {

  /** Seuil « proche du dépassement » par défaut : part du délai restant, en pourcentage. */
  public static final int SEUIL_POURCENT_DEFAUT = 20;

  /** Seuil « proche du dépassement » minimal par défaut, en jours. */
  public static final int SEUIL_MIN_JOURS_DEFAUT = 2;

  private RegleDelais() {}

  /**
   * Paramètres en vigueur (table {@code parametre_delai}).
   *
   * @param depart {@code paiement_frais} ou {@code depot}
   * @param pauseComplement le délai est en pause pendant un complément
   * @param pauseDroits le délai est en pause pendant l'attente du paiement des droits
   * @param seuilProchePourcent seuil « proche du dépassement », en % du délai
   * @param seuilProcheMinJours seuil « proche du dépassement » minimal, en jours
   */
  public record Parametres(
      String depart,
      boolean pauseComplement,
      boolean pauseDroits,
      int seuilProchePourcent,
      int seuilProcheMinJours) {}
}
