package gn.gov.mines.emine.paiement.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Réglages des reçus (US-04.14).
 *
 * @param urlPublique adresse du site public, base de la page de vérification encodée dans le QR du
 *     reçu ({@code PUBLIC_WEB_URL}, la même que pour les documents délivrés)
 */
@ConfigurationProperties("emine.recus")
public record ProprietesRecus(@DefaultValue("http://localhost:3000") String urlPublique) {

  /** Adresse publique de vérification d'un reçu. */
  public String urlVerification(String code) {
    String base =
        urlPublique.endsWith("/")
            ? urlPublique.substring(0, urlPublique.length() - 1)
            : urlPublique;
    return base + "/verifier/recu/" + code;
  }
}
