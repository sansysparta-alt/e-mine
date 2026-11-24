package gn.gov.mines.emine.notification.domaine;

import java.time.Instant;
import java.util.Map;

/**
 * Notification du centre de notifications de l'application (canal {@code interne}, US-07.2).
 *
 * @param id identifiant
 * @param modele code de l'événement
 * @param contenu valeurs du modèle
 * @param demandeId demande concernée, ou {@code null}
 * @param numeroDemande numéro de la demande, ou {@code null}
 * @param creeLe date de l'événement
 * @param lueLe date de lecture, ou {@code null} si non lue
 * @param action action encore attendue de l'usager sur le dossier ({@code payer-droits}, {@code
 *     completer}), ou {@code null}
 */
public record NotificationInterne(
    long id,
    String modele,
    Map<String, Object> contenu,
    Long demandeId,
    String numeroDemande,
    Instant creeLe,
    Instant lueLe,
    String action) {

  public NotificationInterne {
    contenu = Map.copyOf(contenu);
  }
}
