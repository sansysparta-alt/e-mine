package gn.gov.mines.emine.demande;

import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR_2;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Year;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-03.4 : après relecture, le demandeur accepte la déclaration sur l'honneur et dépose ; la base
 * attribue le numéro DEM-AAAA-NNNNNN (D3) et passe la demande au statut « déposée ».
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DepotTest extends TestIntegration {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String NUMERO = "DEM-" + Year.now().getValue() + "-\\d{6}";

  @Autowired MockMvc mvc;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @Test
  void leDepotAttribueLeNumeroEtPasseAuStatutDeposee() throws Exception {
    long id = brouillonAvecPieces(2);
    JsonNode demande =
        lire(
            deposer(id, INVESTISSEUR, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("deposee"))
                .andExpect(jsonPath("$.numero").value(matchesPattern(NUMERO)))
                .andExpect(jsonPath("$.deposeLe").isNotEmpty()));
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT d.declaration_honneur_le IS NOT NULL, h.ancien_statut::text,"
                    + " u.keycloak_id::text FROM demande d"
                    + " JOIN historique_statut_demande h ON h.demande_id = d.id"
                    + " AND h.nouveau_statut = 'deposee'"
                    + " JOIN utilisateur u ON u.id = h.auteur_id WHERE d.id = ?")) {
      ps.setLong(1, id);
      ResultSet rs = ps.executeQuery();
      assertThat(rs.next()).isTrue();
      assertThat(rs.getBoolean(1)).as("déclaration horodatée").isTrue();
      assertThat(rs.getString(2)).isEqualTo("brouillon");
      assertThat(rs.getString(3)).as("auteur du dépôt").isEqualTo(INVESTISSEUR);
    }
    assertThat(demande.path("numero").asString()).matches(NUMERO);
  }

  @Test
  void deuxDepotsSuccessifsOntDesNumerosConsecutifs() throws Exception {
    String premier =
        lire(deposer(brouillonAvecPieces(1), INVESTISSEUR, true)).path("numero").asString();
    String second =
        lire(deposer(brouillonAvecPieces(1), INVESTISSEUR, true)).path("numero").asString();
    assertThat(sequence(second)).isEqualTo(sequence(premier) + 1);
  }

  @Test
  void sansDeclarationNiPieceLeDepotEstRefuseAvecLesManques() throws Exception {
    long id = brouillonAvecPieces(0);
    deposer(id, INVESTISSEUR, false)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("DEM-004"))
        .andExpect(
            jsonPath("$.details.manques", containsInAnyOrder("declarationHonneur", "pieces")));
    deposer(brouillonAvecPieces(1), INVESTISSEUR, false)
        .andExpect(jsonPath("$.details.manques[0]").value("declarationHonneur"));
  }

  @Test
  void uneDemandeNeSeDeposeQuUneFois() throws Exception {
    long id = brouillonAvecPieces(1);
    deposer(id, INVESTISSEUR, true).andExpect(status().isOk());
    deposer(id, INVESTISSEUR, true)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEM-003"));
  }

  @Test
  void personneNeDeposeLaDemandeDUnAutre() throws Exception {
    long id = brouillonAvecPieces(1);
    deposer(id, INVESTISSEUR_2, true)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DEM-002"));
    deposer(id, GUICHET, true)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERM-001"));
  }

  @Test
  void laDeclarationEstObligatoireDansLaRequete() throws Exception {
    mvc.perform(
            post("/api/v1/demandes/" + brouillonAvecPieces(1) + "/depot")
                .with(jeton(INVESTISSEUR, "INVESTISSEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"));
  }

  private ResultActions deposer(long id, String qui, boolean declaration) throws Exception {
    String role = qui.equals(GUICHET) ? "GUICHET" : "INVESTISSEUR";
    return mvc.perform(
        post("/api/v1/demandes/" + id + "/depot")
            .with(jeton(qui, role))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"declarationHonneur\": " + declaration + "}"));
  }

  private long brouillonAvecPieces(int nombre) throws Exception {
    long id =
        lire(mvc.perform(
                post("/api/v1/demandes")
                    .with(jeton(INVESTISSEUR, "INVESTISSEUR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"typeDocumentCode\": \"PEX\"}")))
            .path("id")
            .asLong();
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO piece_jointe (demande_id, type_piece, nom_fichier, mime_type,"
                    + " taille_octets, empreinte_sha256, stockage_cle, depose_par)"
                    + " SELECT ?, 'EIES', 'eies.pdf', 'application/pdf', 1000, repeat('c', 64), ?,"
                    + " demandeur_id FROM demande WHERE id = ?")) {
      for (int i = 0; i < nombre; i++) {
        ps.setLong(1, id);
        ps.setString(2, "depot/" + id + "/" + i + "/" + System.nanoTime());
        ps.setLong(3, id);
        ps.executeUpdate();
      }
    }
    return id;
  }

  private static JsonNode lire(ResultActions r) throws Exception {
    return JSON.readTree(r.andReturn().getResponse().getContentAsString());
  }

  private static int sequence(String numero) {
    return Integer.parseInt(numero.substring(numero.lastIndexOf('-') + 1));
  }
}
