package gn.gov.mines.emine.demande;

import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR_2;
import static gn.gov.mines.emine.support.Personas.MINISTRE;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.demande.domaine.Periode;
import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Indicateurs du tableau de bord (correctifs ergonomie) : retard sur le délai du catalogue en jours
 * calendaires depuis le dépôt, traités aujourd'hui, délivrances, délai moyen et encaissements de la
 * période ; chaque carte et la liste qu'elle ouvre donnent le même nombre.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IndicateursTest extends TestIntegration {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired MockMvc mvc;

  private JsonNode avantMinistre;
  private JsonNode avantGuichet;
  private JsonNode avantInvestisseur2;
  private long enRetard;
  private long dansLeDelai;
  private long aLaLimite;
  private long traiteParLeGuichet;
  private long delivree;

  @BeforeAll
  void dossiers() throws Exception {
    Personas.charger();
    avantMinistre = indicateurs(jeton(MINISTRE, "MINISTRE"), null);
    avantGuichet = indicateurs(jeton(GUICHET, "GUICHET"), null);
    avantInvestisseur2 = indicateurs(jeton(INVESTISSEUR_2, "INVESTISSEUR"), null);

    // PEX : délai de 60 jours au catalogue. Déposé il y a 61 jours → en retard ; 59 → dans le
    // délai.
    enRetard = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    deposeeIlYA(enRetard, 61);
    dansLeDelai = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    deposeeIlYA(dansLeDelai, 59);
    aLaLimite = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    deposeeIlYA(aLaLimite, 60);

    traiteParLeGuichet = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.payer(traiteParLeGuichet, "FRAIS_DOSSIER", 5_000_000);
    changerStatutPar(traiteParLeGuichet, GUICHET, "recevable");

    delivree = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    deposeeIlYA(delivree, 10);
    DemandesDeTest.pretePourLaDelivrance(
        delivree, MINISTRE, "FRAIS_DOSSIER", 5_000_000, 15_000_000L);
    DemandesDeTest.changerStatut(delivree, "delivree");
  }

  @Test
  void unDossierEstEnRetardAuDelaDuDelaiDuCatalogueCompteDepuisLeDepot() throws Exception {
    RequestPostProcessor ministre = jeton(MINISTRE, "MINISTRE");
    JsonNode apres = indicateurs(ministre, null);
    assertThat(apres.path("enRetard").asLong() - avantMinistre.path("enRetard").asLong())
        .as("seul le dossier déposé il y a 61 jours est en retard")
        .isEqualTo(1);
    JsonNode liste =
        lire(get("/api/v1/demandes").param("enRetard", "true").param("taille", "100"), ministre);
    assertThat(liste.path("total").asLong()).isEqualTo(apres.path("enRetard").asLong());
    assertThat(ids(liste)).contains(enRetard).doesNotContain(dansLeDelai, aLaLimite, delivree);
    // Chaque dossier porte la même information (revue B) : la ligne et la fiche disent « en
    // retard » pour exactement les dossiers que compte l'indicateur.
    for (JsonNode d : liste.path("elements")) {
      assertThat(d.path("enRetard").asBoolean()).as("dossier %s", d.path("id")).isTrue();
    }
    assertThat(lireDossier(enRetard, ministre).path("enRetard").asBoolean()).isTrue();
    assertThat(lireDossier(aLaLimite, ministre).path("enRetard").asBoolean()).isFalse();
    assertThat(lireDossier(delivree, ministre).path("enRetard").asBoolean()).isFalse();
  }

  private JsonNode lireDossier(long id, RequestPostProcessor qui) throws Exception {
    return lire(get("/api/v1/demandes/" + id), qui);
  }

  @Test
  void leGuichetVoitSesDossiersTraitesAujourdhuiEtLesFraisAttendus() throws Exception {
    RequestPostProcessor guichet = jeton(GUICHET, "GUICHET");
    JsonNode apres = indicateurs(guichet, null);
    assertThat(
            apres.path("traitesAujourdhui").asLong()
                - avantGuichet.path("traitesAujourdhui").asLong())
        .isEqualTo(1);
    JsonNode traites =
        lire(get("/api/v1/demandes").param("traiteParMoi", "true").param("taille", "100"), guichet);
    assertThat(traites.path("total").asLong()).isEqualTo(apres.path("traitesAujourdhui").asLong());
    assertThat(ids(traites)).contains(traiteParLeGuichet).doesNotContain(delivree);
    // Le ministre n'a rien traité lui-même : le dossier ne compte pas pour lui.
    JsonNode ministre =
        lire(
            get("/api/v1/demandes").param("traiteParMoi", "true").param("taille", "100"),
            jeton(MINISTRE, "MINISTRE"));
    assertThat(ids(ministre)).doesNotContain(traiteParLeGuichet);
    // Frais attendus : déposées sans frais payés (les trois dossiers sans paiement).
    assertThat(apres.path("fraisAttendus").asLong() - avantGuichet.path("fraisAttendus").asLong())
        .isEqualTo(3);
    JsonNode frais =
        lire(
            get("/api/v1/demandes")
                .param("statut", "deposee")
                .param("fraisPayes", "false")
                .param("taille", "100"),
            guichet);
    assertThat(frais.path("total").asLong()).isEqualTo(apres.path("fraisAttendus").asLong());
  }

  @Test
  void lesDelivrancesLeDelaiMoyenEtLesEncaissementsPortentSurLaPeriode() throws Exception {
    RequestPostProcessor ministre = jeton(MINISTRE, "MINISTRE");
    JsonNode apres = indicateurs(ministre, "mois");
    assertThat(apres.path("periode").asString()).isEqualTo("mois");
    assertThat(apres.path("delivrees").asLong() - avantMinistre.path("delivrees").asLong())
        .isEqualTo(1);
    assertThat(apres.path("delaiMoyenJours").isNull()).isFalse();
    // Frais de 2 dossiers (5 M chacun) et droits du dossier délivré (15 M), payés ce mois-ci.
    assertThat(
            apres.path("encaissementsGnf").asLong()
                - avantMinistre.path("encaissementsGnf").asLong())
        .isEqualTo(2 * 5_000_000L + 15_000_000L);
    JsonNode liste =
        lire(
            get("/api/v1/demandes")
                .param("statut", "delivree")
                .param("periode", "mois")
                .param("taille", "100"),
            ministre);
    assertThat(liste.path("total").asLong()).isEqualTo(apres.path("delivrees").asLong());
    assertThat(ids(liste)).contains(delivree);
    // Répartition par document : la somme des lignes redonne les totaux.
    long delivreesParDocument = 0;
    long encaissementsParDocument = 0;
    long retardsParDocument = 0;
    for (JsonNode d : apres.path("parDocument")) {
      delivreesParDocument += d.path("delivrees").asLong();
      encaissementsParDocument += d.path("encaissementsGnf").asLong();
      retardsParDocument += d.path("enRetard").asLong();
    }
    assertThat(delivreesParDocument).isEqualTo(apres.path("delivrees").asLong());
    assertThat(encaissementsParDocument).isEqualTo(apres.path("encaissementsGnf").asLong());
    assertThat(retardsParDocument).isEqualTo(apres.path("enRetard").asLong());
  }

  @Test
  void lInvestisseurNeVoitQueSesPropresChiffres() throws Exception {
    JsonNode apres = indicateurs(jeton(INVESTISSEUR_2, "INVESTISSEUR"), null);
    assertThat(apres.path("enRetard").asLong())
        .isEqualTo(avantInvestisseur2.path("enRetard").asLong());
    assertThat(apres.path("encaissementsGnf").asLong())
        .isEqualTo(avantInvestisseur2.path("encaissementsGnf").asLong());
  }

  @Test
  void unePeriodeInconnueEstRefusee() throws Exception {
    mvc.perform(
            get("/api/v1/demandes/indicateurs")
                .param("periode", "siecle")
                .with(jeton(MINISTRE, "MINISTRE")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void lesPeriodesCommencentAMinuitHeureDeConakry() {
    Instant maintenant = Instant.parse("2026-11-22T15:30:00Z");
    assertThat(Periode.MOIS.debut(maintenant)).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
    assertThat(Periode.TRENTE_JOURS.debut(maintenant))
        .isEqualTo(Instant.parse("2026-10-24T00:00:00Z"));
    assertThat(Periode.ANNEE.debut(maintenant)).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    assertThat(Periode.debutDuJour(maintenant)).isEqualTo(Instant.parse("2026-11-22T00:00:00Z"));
  }

  private JsonNode indicateurs(RequestPostProcessor qui, String periode) throws Exception {
    MockHttpServletRequestBuilder requete = get("/api/v1/demandes/indicateurs");
    if (periode != null) {
      requete = requete.param("periode", periode);
    }
    return lire(requete, qui);
  }

  private JsonNode lire(MockHttpServletRequestBuilder requete, RequestPostProcessor qui)
      throws Exception {
    String corps =
        mvc.perform(requete.with(qui))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(corps);
  }

  private static java.util.List<Long> ids(JsonNode page) {
    java.util.List<Long> ids = new java.util.ArrayList<>();
    page.path("elements").forEach(e -> ids.add(e.path("id").asLong()));
    return ids;
  }

  /** Recule la date de dépôt (le délai du catalogue court depuis le dépôt). */
  private static void deposeeIlYA(long demande, int jours) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "UPDATE demande SET date_depot = now() - make_interval(days => ?) WHERE id = ?")) {
      ps.setInt(1, jours);
      ps.setLong(2, demande);
      ps.executeUpdate();
    }
  }

  /** Change le statut au nom d'une persona (auteur inscrit dans l'historique par la base). */
  private static void changerStatutPar(long demande, String keycloakId, String statut)
      throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner()) {
      c.setAutoCommit(false);
      try (PreparedStatement contexte =
              c.prepareStatement(
                  "SELECT set_config('emine.user_id', id::text, true) FROM utilisateur"
                      + " WHERE keycloak_id = ?::uuid");
          PreparedStatement maj =
              c.prepareStatement("UPDATE demande SET statut = ?::statut_demande WHERE id = ?")) {
        contexte.setString(1, keycloakId);
        contexte.executeQuery();
        maj.setString(1, statut);
        maj.setLong(2, demande);
        maj.executeUpdate();
      }
      c.commit();
    }
  }
}
