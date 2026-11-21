package gn.gov.mines.emine.paiement.infrastructure;

import gn.gov.mines.emine.paiement.domaine.PaiementGateway;
import gn.gov.mines.emine.paiement.domaine.SignatureTresorPay;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adaptateur TrésorPay (contrat {@code docs/contrats/tresorpay.md} §3) : ouverture d'une session
 * signée HMAC-SHA256. Client HTTP du JDK, sans dépendance ajoutée.
 */
@Component
@EnableConfigurationProperties(ProprietesTresorPay.class)
class TresorPayGateway implements PaiementGateway {

  private static final Logger LOG = LoggerFactory.getLogger(TresorPayGateway.class);

  private final ProprietesTresorPay proprietes;
  private final JsonMapper json;
  private final Clock horloge;
  private final HttpClient client;

  TresorPayGateway(ProprietesTresorPay proprietes, JsonMapper json, Clock horloge) {
    this.proprietes = proprietes;
    this.json = json;
    this.horloge = horloge;
    this.client = HttpClient.newBuilder().connectTimeout(proprietes.delaiReponse()).build();
  }

  @Override
  public SessionOuverte ouvrirSession(DemandeSession demande) {
    if (proprietes.urlBase() == null || proprietes.urlBase().isBlank()) {
      throw new PasserelleIndisponible("TRESORPAY_BASE_URL non configurée", null);
    }
    Map<String, Object> corpsJson = new LinkedHashMap<>();
    corpsJson.put("reference", demande.reference());
    corpsJson.put("montantGnf", demande.montantGnf());
    corpsJson.put("libelle", demande.libelle());
    corpsJson.put("urlRetour", demande.urlRetour());
    byte[] corps = json.writeValueAsBytes(corpsJson);
    String horodatage = Long.toString(Instant.now(horloge).getEpochSecond());
    HttpRequest requete =
        HttpRequest.newBuilder(
                URI.create(proprietes.urlBase().replaceAll("/$", "") + "/api/v1/sessions"))
            .timeout(proprietes.delaiReponse())
            .header("Content-Type", "application/json")
            .header("X-TresorPay-Marchand", proprietes.marchandId())
            .header("X-TresorPay-Horodatage", horodatage)
            .header(
                "X-TresorPay-Signature",
                SignatureTresorPay.signer(proprietes.secretHmac(), horodatage, corps))
            .POST(HttpRequest.BodyPublishers.ofByteArray(corps))
            .build();
    HttpResponse<byte[]> reponse;
    try {
      reponse = client.send(requete, HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException e) {
      LOG.warn("TrésorPay injoignable pour {} : {}", demande.reference(), e.getMessage());
      throw new PasserelleIndisponible("TrésorPay injoignable", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new PasserelleIndisponible("Appel à TrésorPay interrompu", e);
    }
    if (reponse.statusCode() == 503 || reponse.statusCode() >= 500) {
      LOG.warn("TrésorPay indisponible ({}) pour {}", reponse.statusCode(), demande.reference());
      throw new PasserelleIndisponible("TrésorPay indisponible", null);
    }
    if (reponse.statusCode() != 201) {
      // Signature ou requête refusée : erreur de configuration d'e-mine, pas de l'usager.
      throw new IllegalStateException(
          "TrésorPay a refusé l'ouverture de session (HTTP " + reponse.statusCode() + ")");
    }
    try {
      JsonNode n = json.readTree(reponse.body());
      return new SessionOuverte(
          n.path("sessionId").asString(),
          n.path("urlPaiement").asString(),
          Instant.parse(n.path("expireLe").asString()));
    } catch (JacksonException | java.time.format.DateTimeParseException e) {
      throw new IllegalStateException("Réponse de TrésorPay illisible", e);
    }
  }
}
