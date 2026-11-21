package gn.gov.mines.emine.shared.stockage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connexion au stockage objet S3 (MinIO en développement). Les identifiants viennent du fichier
 * {@code .env} (jamais versionné).
 *
 * @param endpoint adresse de l'API S3, par exemple {@code http://localhost:9000}
 * @param region région S3 (MinIO l'ignore, le SDK l'exige)
 * @param cleAcces identifiant d'accès
 * @param cleSecrete clé secrète
 * @param compartimentPieces compartiment des pièces jointes
 */
@ConfigurationProperties("emine.stockage")
public record ProprietesStockage(
    String endpoint,
    String region,
    String cleAcces,
    String cleSecrete,
    String compartimentPieces) {}
