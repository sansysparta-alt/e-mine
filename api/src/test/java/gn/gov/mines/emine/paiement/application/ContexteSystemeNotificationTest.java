package gn.gov.mines.emine.paiement.application;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import gn.gov.mines.emine.paiement.domaine.NotificationVerifiee;
import gn.gov.mines.emine.paiement.domaine.SignatureTresorPay;
import gn.gov.mines.emine.paiement.domaine.VerificateurNotification;
import gn.gov.mines.emine.shared.session.ContexteSession;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Garde-fous du contexte système (décision du porteur du projet, US-04.4) : actif seulement avec
 * une notification vérifiée, le temps d'une transaction nouvelle, jamais imbriqué, retiré même en
 * cas d'erreur, et utilisé par le seul service des notifications.
 */
class ContexteSystemeNotificationTest {

  private final ContexteSystemeNotification contexte =
      new ContexteSystemeNotification(new GestionnaireFactice());

  @Test
  void inactifEnDehorsDuTraitement() {
    assertThat(contexte.contexteCourant()).isNull();
    assertThat(ContexteSystemeNotification.actif()).isFalse();
  }

  @Test
  void actifPendantLeTraitementAvecInterneEtSansUtilisateurPuisRetire() {
    AtomicReference<ContexteSession> vu = new AtomicReference<>();
    contexte.executer(
        notification(),
        "10.0.0.5",
        () -> {
          vu.set(contexte.contexteCourant());
          return null;
        });
    assertThat(vu.get().interne()).isTrue();
    assertThat(vu.get().utilisateurId()).isNull();
    assertThat(vu.get().ip()).isEqualTo("10.0.0.5");
    assertThat(contexte.contexteCourant()).isNull();
  }

  @Test
  void retireMemeSiLeTraitementEchoue() {
    assertThatThrownBy(
            () ->
                contexte.executer(
                    notification(),
                    null,
                    () -> {
                      throw new IllegalStateException("panne");
                    }))
        .hasMessage("panne");
    assertThat(contexte.contexteCourant()).isNull();
  }

  @Test
  void refuseSansNotificationVerifiee() {
    assertThatThrownBy(() -> contexte.executer(null, null, () -> null))
        .isInstanceOf(NullPointerException.class);
    assertThat(contexte.contexteCourant()).isNull();
  }

  @Test
  void refuseLImbrication() {
    assertThatThrownBy(
            () ->
                contexte.executer(
                    notification(),
                    null,
                    () -> contexte.executer(notification(), null, () -> null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("imbrication");
    assertThat(contexte.contexteCourant()).isNull();
  }

  @Test
  void refuseSiUneTransactionEstDejaEnCours() {
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      assertThatThrownBy(() -> contexte.executer(notification(), null, () -> null))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("transaction");
      assertThat(contexte.contexteCourant()).isNull();
    } finally {
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }
  }

  @Test
  void uneNotificationVerifieeNePeutPasEtreFabriqueeHorsDuVerificateur() {
    assertThat(NotificationVerifiee.class.getDeclaredConstructors())
        .allSatisfy(c -> assertThat(Modifier.isPublic(c.getModifiers())).isFalse())
        .allSatisfy(c -> assertThat(Modifier.isProtected(c.getModifiers())).isFalse());
    assertThat(Modifier.isFinal(NotificationVerifiee.class.getModifiers())).isTrue();
  }

  @Test
  void seulLeServiceDesNotificationsUtiliseLeContexteSysteme() {
    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("gn.gov.mines.emine");
    noClasses()
        .that()
        .doNotHaveFullyQualifiedName(ServiceNotifications.class.getName())
        .and()
        .doNotHaveFullyQualifiedName(ContexteSystemeNotification.class.getName())
        .should()
        .callMethodWhere(
            com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                com.tngtech.archunit.base.DescribedPredicate.describe(
                    "ContexteSystemeNotification.executer",
                    t ->
                        t.getOwner().isEquivalentTo(ContexteSystemeNotification.class)
                            && t.getName().equals("executer"))))
        .check(classes);
    assertThat(
            Arrays.stream(ContexteSystemeNotification.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("executer"))
                .allMatch(m -> !Modifier.isPublic(m.getModifiers())))
        .isTrue();
    assertThat(Modifier.isPublic(ContexteSystemeNotification.class.getModifiers())).isFalse();
  }

  /** Notification réellement vérifiée (seul moyen d'en obtenir une). */
  private static NotificationVerifiee notification() {
    byte[] corps =
        ("{\"transactionId\":\"TP-1\",\"reference\":\"PAY-2026-000001\",\"statut\":\"PAYE\","
                + "\"montantGnf\":1000}")
            .getBytes(StandardCharsets.UTF_8);
    Instant maintenant = Instant.now();
    String h = Long.toString(maintenant.getEpochSecond());
    return new VerificateurNotification(
            "s", "M", Duration.ofMinutes(5), JsonMapper.builder().build())
        .verifier(corps, "M", h, SignatureTresorPay.signer("s", h, corps), maintenant)
        .orElseThrow();
  }

  /** Gestionnaire de transactions sans base : exécute simplement le traitement. */
  private static final class GestionnaireFactice extends AbstractPlatformTransactionManager {
    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      assertThat(definition.getPropagationBehavior())
          .as("transaction nouvelle")
          .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}
  }
}
