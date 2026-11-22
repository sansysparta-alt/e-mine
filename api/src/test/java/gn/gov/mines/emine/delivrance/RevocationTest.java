package gn.gov.mines.emine.delivrance;

import static gn.gov.mines.emine.support.Personas.DIRECTEUR;
import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.MINISTRE;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.StockageEnMemoire;
import gn.gov.mines.emine.support.TestIntegration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * US-06.6 : le Directeur ou la Brigade révoque un document délivré avec un motif obligatoire ;
 * effet immédiat (« non valide »), révocation définitive, tracée au journal d'audit ; les autres
 * profils ne le peuvent pas.
 */
@AutoConfigureMockMvc
@Import(StockageEnMemoire.Config.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RevocationTest extends TestIntegration {

  @Autowired MockMvc mvc;

  private String brigade;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
    brigade = UUID.randomUUID().toString();
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO utilisateur (profil_id, nom, prenom, email, telephone, keycloak_id)"
                    + " SELECT id, 'Fictif', 'Brigade', ?, '+224600000014', ?::uuid FROM profil"
                    + " WHERE code = 'BRIGADE'")) {
      ps.setString(1, "brigade." + brigade + "@exemple.test");
      ps.setString(2, brigade);
      ps.executeUpdate();
    }
  }

  @Test
  void leDirecteurRevoqueLeDocumentQuiDevientNonValideEtTraceAuJournal() throws Exception {
    long id = documentDelivre();
    revoquer(id, "  Fraude constatée sur l'étude de faisabilité.  ", directeur())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valide").value(false))
        .andExpect(
            jsonPath("$.motifInvalidation").value("Fraude constatée sur l'étude de faisabilité."));
    mvc.perform(
            get("/api/v1/demandes/" + id + "/document").with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(jsonPath("$.valide").value(false));
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT u.keycloak_id::text FROM journal_audit j JOIN utilisateur u"
                    + " ON u.id = j.utilisateur_id WHERE j.table_nom = 'document_delivre'"
                    + " AND j.operation = 'U' AND j.nouvelles_valeurs->>'demande_id' = ?")) {
      ps.setString(1, String.valueOf(id));
      ResultSet rs = ps.executeQuery();
      assertThat(rs.next()).as("révocation journalisée").isTrue();
      assertThat(rs.getString(1)).isEqualTo(DIRECTEUR);
    }
  }

  @Test
  void laBrigadePeutRevoquerEtLaRevocationEstDefinitiveDoc006() throws Exception {
    long id = documentDelivre();
    revoquer(id, "Document présenté lors d'un contrôle frauduleux.", jeton(brigade, "BRIGADE"))
        .andExpect(status().isOk());
    revoquer(id, "Seconde révocation du même document.", directeur())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DOC-006"));
  }

  @Test
  void lesAutresProfilsNePeuventPasRevoquerPerm001() throws Exception {
    long id = documentDelivre();
    for (RequestPostProcessor qui :
        new RequestPostProcessor[] {
          jeton(MINISTRE, "MINISTRE"),
          jeton(GUICHET, "GUICHET"),
          jeton(INVESTISSEUR, "INVESTISSEUR")
        }) {
      revoquer(id, "Tentative de révocation non autorisée.", qui)
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("PERM-001"));
    }
  }

  @Test
  void leMotifEstObligatoireVal001() throws Exception {
    long id = documentDelivre();
    revoquer(id, "  court ", directeur())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"))
        .andExpect(jsonPath("$.details.champs[0].champ").value("motif"));
    mvc.perform(get("/api/v1/demandes/" + id + "/document").with(directeur()))
        .andExpect(jsonPath("$.valide").value(true));
  }

  private long documentDelivre() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.pretePourLaDelivrance(id, MINISTRE, "FRAIS_DOSSIER", 5_000_000, 15_000_000L);
    mvc.perform(post("/api/v1/demandes/" + id + "/delivrance").with(jeton(MINISTRE, "MINISTRE")))
        .andExpect(status().isOk());
    return id;
  }

  private ResultActions revoquer(long id, String motif, RequestPostProcessor qui) throws Exception {
    return mvc.perform(
        post("/api/v1/demandes/" + id + "/document/revocation")
            .with(qui)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"motif\":\"" + motif.replace("\"", "\\\"") + "\"}"));
  }

  private static RequestPostProcessor directeur() {
    return jeton(DIRECTEUR, "DIRECTEUR");
  }
}
