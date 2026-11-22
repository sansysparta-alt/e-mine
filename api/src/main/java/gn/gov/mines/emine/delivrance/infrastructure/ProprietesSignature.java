package gn.gov.mines.emine.delivrance.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Réglages de la signature (US-06.2), lus dans {@code .env} : jamais versionnés.
 *
 * @param mode {@code pkcs12} (poste de développement, V1) ou {@code pkcs11} (SoftHSM2, plan
 *     complet)
 * @param pkcs12Chemin fichier du certificat de test ({@code SIGNATURE_PKCS12_PATH}), relatif à la
 *     racine du dépôt ou au dossier de l'API
 * @param pkcs12MotDePasse mot de passe du fichier ({@code SIGNATURE_PKCS12_PASSWORD})
 * @param lieu lieu de signature inscrit dans le PDF
 */
@ConfigurationProperties("emine.signature")
public record ProprietesSignature(
    @DefaultValue("pkcs12") String mode,
    String pkcs12Chemin,
    String pkcs12MotDePasse,
    @DefaultValue("Conakry") String lieu) {}
