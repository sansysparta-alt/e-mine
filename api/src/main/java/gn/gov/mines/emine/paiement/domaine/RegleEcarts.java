package gn.gov.mines.emine.paiement.domaine;

import java.util.List;

/**
 * Décisions possibles du Responsable fiscal sur un élément « À vérifier » (US-04.9). Une seule
 * règle, côté API : l'écran affiche les actions qu'elle renvoie et n'en recode aucune.
 *
 * <ul>
 *   <li>montant <b>supérieur</b> au montant attendu : valider le paiement (l'excédent pourra être
 *       remboursé, US-04.10) ou le rejeter ;
 *   <li>montant <b>inférieur</b> : rejeter le paiement (le demandeur paie à nouveau) ;
 *   <li>ligne <b>sans référence</b> reconnue, ou versement <b>tardif</b> sur une référence expirée
 *       : l'associer au paiement en cours du même montant, ou la classer ;
 *   <li><b>doublon</b> : demander son remboursement (US-04.10) ou le classer ; versement
 *       <b>tardif</b> : aussi remboursable ; <b>retour</b> bancaire : classer (avec motif) ;
 *   <li>paiement en ligne au montant divergent (sans ligne de relevé) : valider s'il a reçu au
 *       moins le montant attendu, sinon rejeter.
 * </ul>
 *
 * Rejeter et classer exigent un motif ; valider, associer et rembourser le permettent. Toute somme
 * reçue sans contrepartie est proposée au remboursement : l'excédent d'un paiement validé, la somme
 * reçue d'un paiement rejeté (demande créée avec la décision, validée ensuite).
 */
public final class RegleEcarts {

  private RegleEcarts() {}

  /** Décision du Responsable fiscal. */
  public enum Decision {
    VALIDER("valide"),
    REJETER("rejete"),
    ASSOCIER("associe"),
    CLASSER("classe"),
    REMBOURSER("rembourse");

    private final String code;

    Decision(String code) {
      this.code = code;
    }

    /** Valeur enregistrée en base ({@code ligne_releve.decision}). */
    public String code() {
      return code;
    }

    /** Le motif est obligatoire (rejet communiqué au demandeur, classement sans suite). */
    public boolean motifObligatoire() {
      return this == REJETER || this == CLASSER;
    }

    public static java.util.Optional<Decision> depuis(String valeur) {
      for (Decision d : values()) {
        if (d.name().equalsIgnoreCase(valeur)) {
          return java.util.Optional.of(d);
        }
      }
      return java.util.Optional.empty();
    }
  }

  /**
   * Décisions possibles sur une ligne de relevé non traitée.
   *
   * @param nature résultat du rapprochement ({@code inferieur}, {@code inconnue}…)
   * @param paiementAVerifier le paiement rapproché attend la décision ({@code a_verifier})
   */
  public static List<Decision> pourLigne(String nature, boolean paiementAVerifier) {
    if (nature == null) {
      return List.of();
    }
    return switch (nature) {
      case "superieur" ->
          paiementAVerifier
              ? List.of(Decision.VALIDER, Decision.REJETER)
              : List.of(Decision.CLASSER);
      case "inferieur" -> paiementAVerifier ? List.of(Decision.REJETER) : List.of(Decision.CLASSER);
      case "inconnue" -> List.of(Decision.ASSOCIER, Decision.CLASSER);
      case "tardif" -> List.of(Decision.ASSOCIER, Decision.REMBOURSER, Decision.CLASSER);
      case "doublon" -> List.of(Decision.REMBOURSER, Decision.CLASSER);
      case "retour" -> List.of(Decision.CLASSER);
      default -> List.of();
    };
  }

  /** Décisions possibles sur un paiement en ligne « à vérifier » sans ligne de relevé. */
  public static List<Decision> pourPaiement(long montantGnf, Long montantRecuGnf) {
    return montantRecuGnf != null && montantRecuGnf >= montantGnf
        ? List.of(Decision.VALIDER, Decision.REJETER)
        : List.of(Decision.REJETER);
  }
}
