package gn.gov.mines.emine.delivrance.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Réglages de la délivrance (US-06.3).
 *
 * @param compartiment compartiment de stockage des documents signés ({@code S3_BUCKET_DOCUMENTS})
 * @param urlPublique adresse du site public, base de la page de vérification encodée dans le QR
 *     ({@code PUBLIC_WEB_URL})
 */
@ConfigurationProperties("emine.delivrance")
public record ProprietesDelivrance(
    @DefaultValue("emine-documents") String compartiment,
    @DefaultValue("http://localhost:3000") String urlPublique) {

  /** Adresse publique de vérification d'un document. */
  public String urlVerification(String code) {
    String base =
        urlPublique.endsWith("/")
            ? urlPublique.substring(0, urlPublique.length() - 1)
            : urlPublique;
    return base + "/verifier/" + code;
  }
}
