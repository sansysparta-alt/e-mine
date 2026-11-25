package gn.gov.mines.emine.demande;

import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-05.6 : règle unique des délais ({@code RegleDelais}, V17). Le délai du catalogue court à
 * partir du paiement des frais, jour de départ inclus, en jours calendaires ; il est en pause
 * pendant un complément et pendant l'attente des droits ; « proche du dépassement » = 20 % du délai
 * restant, 2 jours au moins. Les mêmes chiffres sur la fiche, la liste filtrée et l'indicateur, et
 * tout est paramétrable.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DelaisTest extends TestIntegration {

  @Autowired MockMvc mvc;
  final JsonMapper json = JsonMapper.builder().build();

  long pexProche;
  long pexLarge;
  long pexEnRetard;
  long pexDernierJour;
  long corProche;
  long corLarge;
  long pexComplement;
  long pexDroits;
  long sansFrais;

  @BeforeAll
  void donnees() throws Exception {
    Personas.charger();
    // PEX : 60 jours au catalogue (seuil « proche » : 12). Frais payés il y a N jours = N + 1
    // jours de traitement (jour du paiement compté).
    pexProche = fraisPayesIlYA("PEX", 49); // 50 jours : reste 10 ≤ 12
    pexLarge = fraisPayesIlYA("PEX", 39); // 40 jours : reste 20
    pexDernierJour = fraisPayesIlYA("PEX", 59); // 60 jours : reste 0, dernier jour
    pexEnRetard = fraisPayesIlYA("PEX", 60); // 61 jours : dépassé de 1
    // COR : 5 jours (seuil 2).
    corProche = fraisPayesIlYA("COR", 2); // 3 jours : reste 2
    corLarge = fraisPayesIlYA("COR", 1); // 2 jours : reste 3
    // Complément ouvert il y a 10 jours, sans réponse : 50 - 10 = 40 jours décomptés, reste 20.
    pexComplement = fraisPayesIlYA("PEX", 49);
    DemandesDeTest.complementOuvert(pexComplement, GUICHET, "deposee", 10);
    // Droits attendus depuis 10 jours (décision favorable) : 56 - 10 = 46 jours, reste 14.
    pexDroits = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.payer(pexDroits, "FRAIS_DOSSIER", 5_000_000);
    DemandesDeTest.changerStatut(pexDroits, "recevable");
    DemandesDeTest.avisRendus(pexDroits);
    DemandesDeTest.changerStatut(pexDroits, "decision");
    DemandesDeTest.fraisPayesIlYA(pexDroits, 55);
    DemandesDeTest.statutIlYA(pexDroits, "decision", 10);
    // Déposé il y a 100 jours, frais jamais payés : le délai n'a pas commencé.
    sansFrais = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.deposeeIlYA(sansFrais, 100);
  }

  @Test
  void leSeuilVautVingtPourCentDuDelaiEtDeuxJoursAuMoins() throws Exception {
    // Seuil de la règle du dépôt de la demande, sur le délai de sa fiche.
    assertThat(seuil(pexLarge)).isEqualTo(12); // PEX : 20 % de 60 jours
    assertThat(seuil(corLarge)).isEqualTo(2); // COR : 20 % de 5 jours = 1, mais 2 jours au moins
  }

  @Test
  void leDelaiCourtDepuisLePaiementDesFraisJourDuPaiementCompte() throws Exception {
    verifier(pexProche, 10, true, false, null);
    verifier(pexLarge, 20, false, false, null);
    verifier(pexDernierJour, 0, true, false, null);
    verifier(pexEnRetard, -1, false, true, null);
    verifier(corProche, 2, true, false, null);
    verifier(corLarge, 3, false, false, null);
  }

  @Test
  void leDelaiEstEnPausePendantUnComplementEtPendantLAttenteDesDroits() throws Exception {
    verifier(pexComplement, 20, false, false, "complement");
    verifier(pexDroits, 14, false, false, "droits");
  }

  @Test
  void tantQueLesFraisNeSontPasPayesLeDelaiNaPasCommence() throws Exception {
    mvc.perform(get("/api/v1/demandes/" + sansFrais).with(guichet()))
        .andExpect(jsonPath("$.delaiDemarre").value(false))
        .andExpect(jsonPath("$.joursRestants").isEmpty())
        .andExpect(jsonPath("$.enRetard").value(false))
        .andExpect(jsonPath("$.procheDepassement").value(false));
    mvc.perform(get("/api/v1/demandes/" + pexLarge).with(guichet()))
        .andExpect(jsonPath("$.delaiDemarre").value(true));
  }

  @Test
  void unDossierTermineOuUnBrouillonNAPasDeJoursRestants() throws Exception {
    long brouillon = DemandesDeTest.brouillon(INVESTISSEUR, "PEX");
    mvc.perform(get("/api/v1/demandes/" + brouillon).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(jsonPath("$.joursRestants").isEmpty())
        .andExpect(jsonPath("$.procheDepassement").value(false));
  }

  @Test
  void leFiltreEtLIndicateurDonnentLeMemeNombre() throws Exception {
    JsonNode page =
        lire(
            get("/api/v1/demandes").param("procheDepassement", "true").param("taille", "100"),
            guichet());
    assertThat(ids(page))
        .contains(pexProche, pexDernierJour, corProche)
        .doesNotContain(pexLarge, pexEnRetard, corLarge, pexComplement, pexDroits, sansFrais);
    page.path("elements")
        .forEach(e -> assertThat(e.path("procheDepassement").asBoolean()).isTrue());
    JsonNode indicateurs = lire(get("/api/v1/demandes/indicateurs"), guichet());
    assertThat(indicateurs.path("procheDepassement").asLong())
        .as("indicateur = liste filtrée")
        .isEqualTo(page.path("total").asLong());
    assertThat(indicateurs.path("parametresDelai").path("depart").asString())
        .isEqualTo("paiement_frais");
    assertThat(indicateurs.path("parametresDelai").path("seuilProchePourcent").asInt())
        .isEqualTo(20);
  }

  @Test
  void leTriParEcheanceMetLesRetardsPuisLesPlusProchesEnTeteEtLesDelaisNonCommencesALaFin()
      throws Exception {
    JsonNode page =
        lire(
            get("/api/v1/demandes")
                .param("statut", "deposee")
                .param("tri", "echeance")
                .param("sens", "asc")
                .param("taille", "100"),
            guichet());
    List<Long> ordre = ids(page);
    assertThat(ordre.indexOf(pexEnRetard)).isLessThan(ordre.indexOf(pexDernierJour));
    assertThat(ordre.indexOf(pexDernierJour)).isLessThan(ordre.indexOf(corProche));
    assertThat(ordre.indexOf(corProche)).isLessThan(ordre.indexOf(pexLarge));
    assertThat(ordre.indexOf(pexLarge)).isLessThan(ordre.indexOf(sansFrais));
  }

  @Test
  void laRegleEstParametrableDepartAuDepotSansPauseEtSeuil() throws Exception {
    // Les calculs suivent la règle du dépôt de chaque demande (V27) : on modifie ici, en
    // propriétaire et pour ce seul test, la version de la règle qu'elles portent.
    String version = "(SELECT regle_delai_version FROM demande WHERE id = " + sansFrais + ")";
    executer("ALTER TABLE version_regle_delai DISABLE TRIGGER trg_version_regle_delai_fige");
    try {
      executer(
          "UPDATE version_regle_delai SET depart = 'depot', pause_complement = false,"
              + " seuil_proche_pourcent = 50 WHERE version = "
              + version);
      assertThat(seuil(pexLarge)).isEqualTo(30);
      // Départ au dépôt (il y a 100 jours) : 101 jours, dépassé de 41.
      verifier(sansFrais, -41, false, true, null);
      // Sans pause pendant le complément : le délai n'est plus suspendu.
      mvc.perform(get("/api/v1/demandes/" + pexComplement).with(guichet()))
          .andExpect(jsonPath("$.delaiSuspendu").value(false));
    } finally {
      executer(
          "UPDATE version_regle_delai SET depart = 'paiement_frais', pause_complement = true,"
              + " pause_droits = true, seuil_proche_pourcent = 20, seuil_proche_min_jours = 2"
              + " WHERE version = "
              + version);
      executer("ALTER TABLE version_regle_delai ENABLE TRIGGER trg_version_regle_delai_fige");
    }
  }

  private void verifier(long id, int restants, boolean proche, boolean enRetard, String pause)
      throws Exception {
    mvc.perform(get("/api/v1/demandes/" + id).with(guichet()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.joursRestants").value(restants))
        .andExpect(jsonPath("$.procheDepassement").value(proche))
        .andExpect(jsonPath("$.enRetard").value(enRetard))
        .andExpect(jsonPath("$.delaiSuspendu").value(pause != null))
        .andExpect(
            pause == null
                ? jsonPath("$.motifSuspension").isEmpty()
                : jsonPath("$.motifSuspension").value(pause));
  }

  private long fraisPayesIlYA(String type, int jours) throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, type);
    DemandesDeTest.fraisPayesIlYA(id, jours);
    return id;
  }

  /** Seuil « proche du dépassement » d'une demande, selon la règle de son dépôt. */
  private static int seuil(long demande) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps = c.prepareStatement("SELECT fn_seuil_proche(?)")) {
      ps.setLong(1, demande);
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getInt(1);
    }
  }

  private static void executer(String sql) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps = c.prepareStatement(sql)) {
      ps.execute();
    }
  }

  private static List<Long> ids(JsonNode page) {
    List<Long> ids = new ArrayList<>();
    page.path("elements").forEach(e -> ids.add(e.path("id").asLong()));
    return ids;
  }

  private JsonNode lire(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requete,
      RequestPostProcessor qui)
      throws Exception {
    String corps =
        mvc.perform(requete.with(qui))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    return json.readTree(corps);
  }

  private static RequestPostProcessor guichet() {
    return jeton(GUICHET, "GUICHET");
  }
}
