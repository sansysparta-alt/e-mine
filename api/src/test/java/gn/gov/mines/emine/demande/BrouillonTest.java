package gn.gov.mines.emine.demande;

import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR_2;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-03.1 : l'investisseur crée et enregistre le brouillon de sa demande ; un autre investisseur ne
 * le voit pas ; un agent ne peut pas déposer ; une demande déposée n'est plus modifiable.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BrouillonTest extends TestIntegration {

  @Autowired MockMvc mvc;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @Test
  void lInvestisseurCreeUnBrouillonSansNumero() throws Exception {
    creer("{\"typeDocumentCode\": \"PEX\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.statut").value("brouillon"))
        .andExpect(jsonPath("$.numero").doesNotExist())
        .andExpect(jsonPath("$.typeDocumentCode").value("PEX"))
        .andExpect(jsonPath("$.demandeur.prenom").value("Fodé"))
        .andExpect(jsonPath("$.demandeur.organisation").value("Horizon Invest SARL (fictive)"));
  }

  @Test
  void leBrouillonSEnregistreEtSeRelit() throws Exception {
    long id = idCree("{\"typeDocumentCode\": \"PEX\"}");
    long conakry = prefecture("Conakry");
    mvc.perform(
            put("/api/v1/demandes/" + id)
                .with(jeton(INVESTISSEUR, "INVESTISSEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"typeDocumentCode\": \"PRE\", \"prefectureId\": "
                        + conakry
                        + ", \"substanceCode\": \"OR\", \"superficieHa\": 125.5}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.typeDocumentCode").value("PRE"))
        .andExpect(jsonPath("$.prefectureNom").value("Conakry"))
        .andExpect(jsonPath("$.substanceLibelle").value("Or"))
        .andExpect(jsonPath("$.superficieHa").value(125.5));
    mvc.perform(get("/api/v1/demandes/" + id).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.substanceCode").value("OR"));
  }

  @Test
  void unAutreInvestisseurNeVoitNiNeModifieLeBrouillon() throws Exception {
    long id = idCree("{\"typeDocumentCode\": \"PEX\"}");
    mvc.perform(get("/api/v1/demandes/" + id).with(jeton(INVESTISSEUR_2, "INVESTISSEUR")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DEM-002"));
    mvc.perform(
            put("/api/v1/demandes/" + id)
                .with(jeton(INVESTISSEUR_2, "INVESTISSEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeDocumentCode\": \"COR\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DEM-002"));
  }

  @Test
  void unAgentInterneNeVoitPasLeBrouillonEtNePeutPasDeposer() throws Exception {
    long id = idCree("{\"typeDocumentCode\": \"PEX\"}");
    // Règle du socle : un brouillon reste privé à son auteur (correctifs ergonomie).
    mvc.perform(get("/api/v1/demandes/" + id).with(jeton(GUICHET, "GUICHET")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DEM-002"));
    mvc.perform(
            post("/api/v1/demandes")
                .with(jeton(GUICHET, "GUICHET"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeDocumentCode\": \"PEX\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERM-001"));
  }

  @Test
  void uneDemandeDeposeeNEstPlusModifiable() throws Exception {
    long id = idCree("{\"typeDocumentCode\": \"COR\"}");
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        Statement st = c.createStatement()) {
      st.executeUpdate("UPDATE demande SET statut = 'deposee' WHERE id = " + id);
    }
    mvc.perform(
            put("/api/v1/demandes/" + id)
                .with(jeton(INVESTISSEUR, "INVESTISSEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeDocumentCode\": \"PEX\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEM-003"));
  }

  @Test
  void lUsagerSupprimeSonBrouillonQuiDisparaitDeSesDemandes() throws Exception {
    long id = idCree("{\"typeDocumentCode\": \"PRE\"}");
    mvc.perform(delete("/api/v1/demandes/" + id).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/demandes/" + id).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(status().isNotFound());
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        Statement st = c.createStatement()) {
      var rs = st.executeQuery("SELECT deleted_at IS NOT NULL FROM demande WHERE id = " + id);
      rs.next();
      assertThat(rs.getBoolean(1)).as("suppression logique, jamais physique").isTrue();
    }
  }

  @Test
  void uneDemandeDeposeeOuCelleDUnAutreNeSeSupprimePas() throws Exception {
    long depose = idCree("{\"typeDocumentCode\": \"COR\"}");
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        Statement st = c.createStatement()) {
      st.executeUpdate("UPDATE demande SET statut = 'deposee' WHERE id = " + depose);
    }
    mvc.perform(delete("/api/v1/demandes/" + depose).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEM-003"));
    long brouillon = idCree("{\"typeDocumentCode\": \"PRE\"}");
    mvc.perform(delete("/api/v1/demandes/" + brouillon).with(jeton(INVESTISSEUR_2, "INVESTISSEUR")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DEM-002"));
  }

  @Test
  void unTypeDeDocumentInconnuDonneDem005() throws Exception {
    creer("{\"typeDocumentCode\": \"ZZZ\"}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("DEM-005"));
  }

  @Test
  void desReferencesInvalidesDonnentVal001() throws Exception {
    creer("{\"typeDocumentCode\": \"PEX\", \"prefectureId\": 32000}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"))
        .andExpect(jsonPath("$.details.champs[0].champ").value("prefectureId"));
    creer("{\"typeDocumentCode\": \"pex\", \"superficieHa\": -3}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"))
        .andExpect(jsonPath("$.details.champs", hasSize(2)));
  }

  @Test
  void lesReferentielsSontPublics() throws Exception {
    mvc.perform(get("/api/v1/referentiels/prefectures"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(greaterThan(5)));
    mvc.perform(get("/api/v1/referentiels/substances"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.code == 'OR')].libelleFr").value("Or"));
  }

  private ResultActions creer(String corps) throws Exception {
    return mvc.perform(
        post("/api/v1/demandes")
            .with(jeton(INVESTISSEUR, "INVESTISSEUR"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(corps));
  }

  private long idCree(String corps) throws Exception {
    String reponse =
        creer(corps).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return JsonMapper.builder().build().readTree(reponse).path("id").asLong();
  }

  private long prefecture(String nom) throws Exception {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        Statement st = c.createStatement()) {
      var rs = st.executeQuery("SELECT id FROM prefecture WHERE nom = '" + nom + "'");
      rs.next();
      return rs.getLong(1);
    }
  }
}
