package gn.gov.mines.emine.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Classe de base des tests d'intégration : contexte Spring complet sur PostgreSQL de test, avec la
 * passerelle de paiement de test à la place de TrésorPay et des ports SMS et e-mail de test (aucun
 * message réel).
 */
@SpringBootTest
@Import({PasserelleDeTest.Config.class, PasserellesNotificationDeTest.class})
public abstract class TestIntegration {

  /** Secret HMAC TrésorPay des tests uniquement (valeur fictive). */
  public static final String SECRET_TRESORPAY = "secret-hmac-tests-integration";

  public static final String MARCHAND_TRESORPAY = "MARCHAND-TEST-0001";

  @DynamicPropertySource
  static void baseDeDonnees(DynamicPropertyRegistry registry) {
    BaseDeDonneesDeTest.proprietes(registry);
    registry.add("emine.tresorpay.secret-hmac", () -> SECRET_TRESORPAY);
    // Les tests appellent la relance des compléments eux-mêmes (US-05.2).
    registry.add("emine.complements.relance.active", () -> "false");
    // Pas de planificateur : les tests appellent eux-mêmes les tâches (envoi des notifications…).
    registry.add("emine.planification.active", () -> "false");
    registry.add("emine.tresorpay.marchand-id", () -> MARCHAND_TRESORPAY);
    registry.add("emine.signature.pkcs12-chemin", () -> CertificatDeTest.chemin().toString());
    registry.add("emine.signature.pkcs12-mot-de-passe", () -> CertificatDeTest.MOT_DE_PASSE);
  }
}
