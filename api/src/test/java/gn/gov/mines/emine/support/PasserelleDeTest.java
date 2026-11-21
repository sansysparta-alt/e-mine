package gn.gov.mines.emine.support;

import gn.gov.mines.emine.paiement.domaine.PaiementGateway;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Passerelle de paiement des tests d'intégration : enregistre les sessions demandées et peut se
 * déclarer indisponible. Remplace l'adaptateur TrésorPay (aucun appel réseau pendant les tests).
 */
public class PasserelleDeTest implements PaiementGateway {

  private final List<DemandeSession> recues = new CopyOnWriteArrayList<>();
  private final AtomicBoolean indisponible = new AtomicBoolean(false);

  @Override
  public SessionOuverte ouvrirSession(DemandeSession demande) {
    if (indisponible.get()) {
      throw new PasserelleIndisponible("indisponible (test)", null);
    }
    recues.add(demande);
    String id = "session-" + demande.reference();
    return new SessionOuverte(
        id, "http://tresorpay.test/payer/" + id, Instant.now().plusSeconds(15 * 60));
  }

  public List<DemandeSession> recues() {
    return recues;
  }

  public void definirIndisponible(boolean valeur) {
    indisponible.set(valeur);
  }

  /** Déclare la passerelle de test comme passerelle principale du contexte. */
  @TestConfiguration(proxyBeanMethods = false)
  public static class Config {
    @Bean
    @Primary
    PasserelleDeTest passerelleDeTest() {
      return new PasserelleDeTest();
    }
  }
}
