package gn.gov.mines.emine.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gn.gov.mines.emine.notification.domaine.Message;
import gn.gov.mines.emine.notification.domaine.NotificationAEnvoyer;
import gn.gov.mines.emine.notification.domaine.RegleEssais;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * US-07.1 / US-07.2 : chaque modèle existe en français et en anglais, se remplit sans variable
 * manquante, et son SMS tient en 160 caractères avec des valeurs de longueur maximale.
 */
class RedacteurNotificationsTest {

  private final RedacteurNotifications redacteur =
      new RedacteurNotifications("http://localhost:3001/");

  /** Valeurs les plus longues possibles de chaque variable des modèles. */
  static final Map<String, Object> CONTENU_MAX =
      Map.ofEntries(
          Map.entry("reference", "PAY-2026-000001"),
          Map.entry("recu", "REC-2026-000001"),
          Map.entry("montant_gnf", 999_999_999_999L),
          Map.entry("moyen", "TRESORPAY"),
          Map.entry("dateLimite", "2026-11-27T10:00:00.000+00:00"),
          Map.entry("dateExpiration", "2026-11-27T10:00:00.000+00:00"),
          Map.entry("pieces", List.of("EIES", "PLAN_SITUATION")),
          Map.entry("document", "Permis d'exploitation industrielle de grande mine"),
          Map.entry("sens", "favorable"),
          Map.entry("numeroDocument", "PEX-2026-0001"),
          Map.entry("motif", "Motif"),
          Map.entry("objet", "FRAIS_DOSSIER"));

  static Set<String> modeles() {
    Set<String> modeles = new TreeSet<>();
    for (String cle : RedacteurNotifications.modeles("fr").keySet()) {
      if (!cle.startsWith("commun.")) {
        modeles.add(cle.substring(0, cle.indexOf('.')));
      }
    }
    return modeles;
  }

  @ParameterizedTest
  @MethodSource("modeles")
  void chaqueModeleSeRedigeEnFrancaisEtEnAnglaisAvecUnSmsDe160CaracteresAuPlus(String modele) {
    for (String langue : List.of("fr", "en")) {
      Message m = redacteur.rediger(notification(modele, langue, CONTENU_MAX));
      assertThat(m.sujet()).isNotBlank().doesNotContain("{");
      assertThat(m.texte())
          .startsWith("en".equals(langue) ? "Dear Aïssatou," : "Bonjour Aïssatou,");
      assertThat(m.sms())
          .hasSizeLessThanOrEqualTo(RegleEssais.LONGUEUR_SMS_MAX)
          .startsWith("e-mine");
      assertThat(m.lienSuivi()).isEqualTo("http://localhost:3001/demandes/42");
    }
  }

  @Test
  void lesModelesFrancaisEtAnglaisOntExactementLesMemesCles() throws IOException {
    assertThat(cles("messages/notifications_en.properties"))
        .isEqualTo(cles("messages/notifications.properties"));
  }

  private static Set<String> cles(String fichier) throws IOException {
    Properties p = new Properties();
    try (Reader r =
        new InputStreamReader(
            RedacteurNotificationsTest.class.getClassLoader().getResourceAsStream(fichier),
            StandardCharsets.UTF_8)) {
      p.load(r);
    }
    return new TreeSet<>(p.stringPropertyNames());
  }

  @Test
  void lesMontantsSontEnGnfEntiersEtLesDatesAuFormatJjMmAaaaAConakry() {
    assertThat(RedacteurNotifications.montant(5_000_000)).isEqualTo("5 000 000 GNF");
    assertThat(RedacteurNotifications.montant(200)).isEqualTo("200 GNF");
    // 23 h 30 UTC le 26/11 : Conakry est en UTC toute l'année.
    assertThat(RedacteurNotifications.date("2026-11-26T23:30:00+00:00")).isEqualTo("26/11/2026");
    assertThat(RedacteurNotifications.formater("pieces", List.of("EIES", "PLAN")))
        .isEqualTo("EIES, PLAN");
  }

  @Test
  void uneValeurManquanteEmpecheLEnvoi() {
    Map<String, Object> incomplet = new HashMap<>(CONTENU_MAX);
    incomplet.remove("dateLimite");
    assertThatThrownBy(() -> redacteur.rediger(notification("COMPLEMENT_DEMANDE", "fr", incomplet)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("dateLimite");
  }

  @Test
  void leTexteEstEchappeDansLeHtml() {
    MiseEnPageCourriel page = new MiseEnPageCourriel(redacteur);
    Map<String, Object> contenu = new HashMap<>(CONTENU_MAX);
    contenu.put("pieces", List.of("<script>alert(1)</script>"));
    Message m = redacteur.rediger(notification("COMPLEMENT_DEMANDE", "fr", contenu));
    String html = page.html(m, "fr");
    assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
    assertThat(html).contains("lang=\"fr\"").contains("Suivre ma demande");
    assertThat(page.texte(m, "fr"))
        .contains("Suivre ma demande : http://localhost:3001/demandes/42");
  }

  private static NotificationAEnvoyer notification(
      String modele, String langue, Map<String, Object> contenu) {
    return new NotificationAEnvoyer(
        1L,
        "email",
        modele,
        contenu,
        1,
        "a.diallo@exemple.test",
        "+224600000000",
        langue,
        "Aïssatou",
        42L,
        "DEM-2026-000123");
  }
}
