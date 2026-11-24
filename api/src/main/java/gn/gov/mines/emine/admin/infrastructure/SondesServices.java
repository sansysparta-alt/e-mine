package gn.gov.mines.emine.admin.infrastructure;

import gn.gov.mines.emine.admin.domaine.Supervision;
import gn.gov.mines.emine.shared.stockage.StockageObjets;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Disponibilité des services dont dépend la plateforme (« Disponibilité » de l'espace de
 * l'administrateur technique) : base de données, service de connexion (Keycloak), stockage des
 * documents, messagerie. Chaque sonde est courte (2 s au plus) et ne lève jamais d'erreur.
 */
@Component
public class SondesServices {

  private static final Logger LOG = LoggerFactory.getLogger(SondesServices.class);
  private static final Duration DELAI = Duration.ofSeconds(2);

  private final AdministrationRepository base;
  private final StockageObjets stockage;
  private final ObjectProvider<JavaMailSender> messagerie;
  private final RestClient http;
  private final String emetteur;

  public SondesServices(
      AdministrationRepository base,
      StockageObjets stockage,
      ObjectProvider<JavaMailSender> messagerie,
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String emetteur) {
    this.base = base;
    this.stockage = stockage;
    this.messagerie = messagerie;
    this.emetteur = emetteur;
    var fabrique = new SimpleClientHttpRequestFactory();
    fabrique.setConnectTimeout(DELAI);
    fabrique.setReadTimeout(DELAI);
    this.http = RestClient.builder().requestFactory(fabrique).build();
  }

  /** État de chaque service, dans un ordre stable. */
  public List<Supervision.Service> verifier() {
    return List.of(
        new Supervision.Service("base", sonder("base", base::baseDisponible)),
        new Supervision.Service("authentification", sonder("authentification", this::keycloak)),
        new Supervision.Service("stockage", sonder("stockage", stockage::disponible)),
        new Supervision.Service("messagerie", sonder("messagerie", this::smtp)));
  }

  private boolean keycloak() {
    if (emetteur.isBlank()) {
      return false;
    }
    http.get().uri(emetteur + "/.well-known/openid-configuration").retrieve().toBodilessEntity();
    return true;
  }

  private boolean smtp() throws Exception {
    JavaMailSender envoi = messagerie.getIfAvailable();
    if (envoi instanceof JavaMailSenderImpl smtp) {
      smtp.testConnection();
      return true;
    }
    return envoi != null;
  }

  private static boolean sonder(String nom, Sonde sonde) {
    try {
      return sonde.disponible();
    } catch (Exception e) {
      LOG.warn("Service {} indisponible : {}", nom, e.getMessage());
      return false;
    }
  }

  @FunctionalInterface
  private interface Sonde {
    boolean disponible() throws Exception;
  }
}
