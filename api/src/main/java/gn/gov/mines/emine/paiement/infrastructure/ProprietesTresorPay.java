package gn.gov.mines.emine.paiement.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Réglages de TrésorPay (simulé en développement), lus dans {@code .env} : valeurs fictives, jamais
 * versionnées.
 *
 * @param urlBase adresse de la passerelle ({@code TRESORPAY_BASE_URL})
 * @param secretHmac secret partagé des signatures ({@code TRESORPAY_HMAC_SECRET})
 * @param marchandId identifiant marchand d'e-mine ({@code TRESORPAY_MERCHANT_ID})
 * @param urlApplication adresse publique de l'application, pour l'adresse de retour ({@code
 *     APP_URL})
 * @param toleranceHorodatage écart d'horloge admis sur une notification (anti-rejeu)
 * @param delaiReponse délai maximal d'attente de la passerelle
 */
@ConfigurationProperties("emine.tresorpay")
public record ProprietesTresorPay(
    String urlBase,
    String secretHmac,
    String marchandId,
    String urlApplication,
    @DefaultValue("5m") Duration toleranceHorodatage,
    @DefaultValue("5s") Duration delaiReponse) {}
