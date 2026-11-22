package gn.gov.mines.emine.notification.domaine;

import java.util.Map;

/**
 * Notification prise dans la boîte d'envoi pour un essai (US-07.1).
 *
 * @param id identifiant de la notification
 * @param canal {@code sms} ou {@code email}
 * @param modele code du modèle ({@code PAIEMENT_CONFIRME}…)
 * @param contenu valeurs du modèle, écrites par le trigger métier
 * @param tentative numéro de cet essai (1 à 4)
 * @param email adresse du destinataire
 * @param telephone numéro du destinataire ({@code +224…})
 * @param langue langue du destinataire ({@code fr} ou {@code en})
 * @param prenom prénom du destinataire
 * @param demandeId demande concernée, ou {@code null}
 * @param numeroDemande numéro de la demande ({@code DEM-AAAA-NNNNNN}), ou {@code null}
 */
public record NotificationAEnvoyer(
    long id,
    String canal,
    String modele,
    Map<String, Object> contenu,
    int tentative,
    String email,
    String telephone,
    String langue,
    String prenom,
    Long demandeId,
    String numeroDemande) {

  public NotificationAEnvoyer {
    contenu = Map.copyOf(contenu);
  }
}
