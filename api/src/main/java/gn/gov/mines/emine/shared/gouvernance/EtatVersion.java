package gn.gov.mines.emine.shared.gouvernance;

/**
 * Gouvernance des paramètres (lot 4, V27) : une version est proposée par l'administrateur, validée
 * par le Ministre puis appliquée à sa date d'effet. État affiché d'une version, déduit de son
 * statut en base et de la version en vigueur (calculée par la base : la plus récente version
 * approuvée dont la date d'effet est arrivée). Une seule règle pour les workflows et la règle des
 * délais.
 */
public final class EtatVersion {

  public static final String A_VALIDER = "a_valider";
  public static final String EN_VIGUEUR = "en_vigueur";
  public static final String PROGRAMMEE = "programmee";
  public static final String REMPLACEE = "remplacee";
  public static final String REFUSEE = "refusee";
  public static final String RETIREE = "retiree";

  /** Statut en base d'une version approuvée. */
  public static final String PUBLIEE = "publiee";

  private EtatVersion() {}

  /**
   * État d'une version.
   *
   * @param statut statut en base ({@code a_valider}, {@code publiee}, {@code refusee}, {@code
   *     retiree})
   * @param version numéro de la version
   * @param enVigueur numéro de la version en vigueur, ou {@code null} s'il n'y en a pas encore
   */
  public static String etat(String statut, int version, Integer enVigueur) {
    if (!PUBLIEE.equals(statut)) {
      return statut;
    }
    if (enVigueur == null || version > enVigueur) {
      return PROGRAMMEE;
    }
    return version == enVigueur ? EN_VIGUEUR : REMPLACEE;
  }

  /** Décision sur une proposition. */
  public enum Decision {
    /** Le Ministre valide : la version s'applique à sa date d'effet. */
    APPROUVER("approuver"),
    /** Le Ministre refuse, avec un motif. */
    REFUSER("refuser"),
    /** L'administrateur retire sa proposition. */
    RETIRER("retirer");

    private final String code;

    Decision(String code) {
      this.code = code;
    }

    /** Valeur attendue par les fonctions de décision de la base. */
    public String code() {
      return code;
    }
  }
}
