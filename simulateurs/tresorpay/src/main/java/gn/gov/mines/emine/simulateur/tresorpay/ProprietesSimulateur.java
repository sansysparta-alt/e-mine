package gn.gov.mines.emine.simulateur.tresorpay;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Réglages du simulateur, lus dans {@code .env} (valeurs fictives, jamais versionnées).
 *
 * @param secretHmac secret partagé avec e-mine pour signer les échanges (HMAC-SHA256)
 * @param marchandId identifiant du marchand e-mine
 * @param urlNotification adresse de notification enregistrée pour le marchand
 * @param urlRetourAutorisee préfixe obligatoire des adresses de retour (pas de redirection ouverte)
 * @param dureeSession durée de validité d'une session de paiement (15 min)
 * @param toleranceHorodatage écart d'horloge admis sur un échange signé (anti-rejeu)
 */
@ConfigurationProperties("tresorpay")
public record ProprietesSimulateur(
    String secretHmac,
    String marchandId,
    String urlNotification,
    String urlRetourAutorisee,
    @DefaultValue("15m") Duration dureeSession,
    @DefaultValue("5m") Duration toleranceHorodatage) {

  public ProprietesSimulateur {
    if (secretHmac == null || secretHmac.isBlank()) {
      throw new IllegalStateException(
          "TRESORPAY_HMAC_SECRET manquant : copier .env.example en .env à la racine du dépôt");
    }
  }
}
