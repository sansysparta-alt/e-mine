package gn.gov.mines.emine.shared.stockage;

import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/**
 * Adaptateur S3 du stockage objet (MinIO). Accès par chemin ({@code path-style}), comme l'exige
 * MinIO. Une panne du stockage donne SYS-003, sans détail technique pour le client.
 */
@Component
@EnableConfigurationProperties(ProprietesStockage.class)
class StockageS3 implements StockageObjets, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(StockageS3.class);

  private final ProprietesStockage proprietes;
  private final Set<String> compartimentsPrets = ConcurrentHashMap.newKeySet();
  private volatile S3Client client;

  StockageS3(ProprietesStockage proprietes) {
    this.proprietes = proprietes;
  }

  /** Client créé à la première utilisation : l'API démarre même sans stockage configuré. */
  private S3Client s3() {
    S3Client c = client;
    if (c == null) {
      synchronized (this) {
        c = client;
        if (c == null) {
          c =
              S3Client.builder()
                  .endpointOverride(URI.create(proprietes.endpoint()))
                  .region(Region.of(proprietes.region()))
                  .credentialsProvider(
                      StaticCredentialsProvider.create(
                          AwsBasicCredentials.create(
                              proprietes.cleAcces(), proprietes.cleSecrete())))
                  .serviceConfiguration(
                      S3Configuration.builder().pathStyleAccessEnabled(true).build())
                  .httpClient(UrlConnectionHttpClient.create())
                  .build();
          client = c;
        }
      }
    }
    return c;
  }

  @Override
  public void deposer(String compartiment, String cle, byte[] contenu, String typeMime) {
    try {
      preparer(compartiment);
      s3().putObject(
              r -> r.bucket(compartiment).key(cle).contentType(typeMime),
              RequestBody.fromBytes(contenu));
    } catch (SdkException | IllegalArgumentException e) {
      throw indisponible(e);
    }
  }

  @Override
  public InputStream lire(String compartiment, String cle) {
    try {
      return s3().getObject(r -> r.bucket(compartiment).key(cle));
    } catch (SdkException | IllegalArgumentException e) {
      throw indisponible(e);
    }
  }

  @Override
  public boolean disponible() {
    try {
      s3().listBuckets();
      return true;
    } catch (RuntimeException e) {
      LOG.warn("Stockage objet injoignable : {}", e.getMessage());
      return false;
    }
  }

  private void preparer(String compartiment) {
    if (compartimentsPrets.contains(compartiment)) {
      return;
    }
    try {
      s3().headBucket(r -> r.bucket(compartiment));
    } catch (NoSuchBucketException e) {
      s3().createBucket(r -> r.bucket(compartiment));
      LOG.info("Compartiment de stockage {} créé", compartiment);
    }
    compartimentsPrets.add(compartiment);
  }

  private static ErreurMetier indisponible(RuntimeException e) {
    LOG.error("Stockage objet indisponible", e);
    return new ErreurMetier(CodeErreur.SYS_003, Map.of());
  }

  @Override
  public void close() {
    if (client != null) {
      client.close();
    }
  }
}
