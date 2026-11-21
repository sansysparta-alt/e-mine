package gn.gov.mines.emine.simulateur.tresorpay;

import java.util.Arrays;
import java.util.List;

/** Scénarios de test du simulateur (contrat §6), choisis sur la page ou par l'adresse. */
public enum Scenario {
  SUCCES("succes", "Paiement accepté", "Notification « payé », montant exact."),
  REFUS(
      "refus",
      "Refus de la banque",
      "Notification « refusé » : une nouvelle tentative reste possible."),
  SIGNATURE_INVALIDE(
      "signature-invalide",
      "Signature invalide",
      "Notification « payé » avec une signature fausse : e-mine doit la rejeter."),
  DOUBLE_NOTIFICATION(
      "double-notification",
      "Double notification",
      "La même notification envoyée deux fois : e-mine ne la traite qu’une fois."),
  MONTANT_DIVERGENT(
      "montant-divergent",
      "Montant divergent",
      "Notification « payé » avec 1 000 GNF de moins : paiement à vérifier."),
  SANS_NOTIFICATION(
      "sans-notification",
      "Absence de notification",
      "Retour sur e-mine sans notification : le paiement reste en attente."),
  ABANDON("abandon", "Abandon", "L’usager annule : notification « annulé ».");

  private final String code;
  private final String libelle;
  private final String description;

  Scenario(String code, String libelle, String description) {
    this.code = code;
    this.libelle = libelle;
    this.description = description;
  }

  public String code() {
    return code;
  }

  public String libelle() {
    return libelle;
  }

  public String description() {
    return description;
  }

  /** Scénarios proposés sur la page de paiement (l'abandon a son propre bouton). */
  public static List<Scenario> proposes() {
    return Arrays.stream(values()).filter(s -> s != ABANDON).toList();
  }

  /** Scénario correspondant au code, succès par défaut. */
  public static Scenario depuisCode(String code) {
    if (code == null) {
      return SUCCES;
    }
    return Arrays.stream(values()).filter(s -> s.code.equals(code)).findFirst().orElse(SUCCES);
  }
}
