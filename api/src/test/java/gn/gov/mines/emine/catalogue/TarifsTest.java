package gn.gov.mines.emine.catalogue;

import static gn.gov.mines.emine.support.Personas.ADMIN_TECH;
import static gn.gov.mines.emine.support.Personas.MINISTRE;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-02.3 et US-02.4 (V19) : un tarif a une date d'effet et une version ; seule une version
 * approuvée par le Ministre s'applique, à sa date d'effet ; les objets payés d'un document ne
 * changent pas ; l'application n'écrit plus les tarifs directement. Document AEX (frais 400 000,
 * droits 800 000), que les autres tests n'utilisent pas ; barème restauré à la fin.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TarifsTest extends TestIntegration {

  private static final String AEX = "/api/v1/fiches-documents/AEX";
  private static final LocalDate AUJOURDHUI = LocalDate.now(ZoneId.of("Africa/Conakry"));

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  final JsonMapper json = JsonMapper.builder().build();

  @BeforeAll
  void donnees() throws Exception {
    Personas.charger();
  }

  @AfterAll
  void restaurer() throws Exception {
    var r =
        proposer(bareme(400_000L, 800_000L, null, AUJOURDHUI, "Retour au barème initial"))
            .andReturn()
            .getResponse();
    if (r.getStatus() == 201) {
      approuver(json.readTree(r.getContentAsString()).get("numero").asInt());
    }
  }

  @Test
  @Order(1)
  void unMontantChangeExigeUneDateDEffetAPartirDAujourdhui() throws Exception {
    proposer(bareme(400_000L, 900_000L, null, null, "Droits portés à 900 000 GNF"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"))
        .andExpect(jsonPath("$.details.champs[0].champ").value("tarifsDateEffet"));
    proposer(
            bareme(
                400_000L, 900_000L, null, AUJOURDHUI.minusDays(1), "Droits portés à 900 000 GNF"))
        .andExpect(jsonPath("$.details.champs[0].champ").value("tarifsDateEffet"));
  }

  @Test
  @Order(2)
  void lesObjetsPayesNeChangentPas() throws Exception {
    proposer(bareme(400_000L, 800_000L, 50_000L, AUJOURDHUI, "Ajout d'une attestation"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.champs[0].champ").value("tarifs"));
    proposer(bareme(400_000L, null, null, AUJOURDHUI, "Suppression des droits"))
        .andExpect(jsonPath("$.details.champs[0].champ").value("tarifs"));
  }

  @Test
  @Order(3)
  void leNouveauTarifNeSAppliqueQuApresLApprobation() throws Exception {
    int n = numero(proposer(bareme(400_000L, 900_000L, null, AUJOURDHUI, "Arrêté de test n°1")));
    mvc.perform(get(AEX).with(jeton(MINISTRE, "MINISTRE")))
        .andExpect(
            jsonPath("$.proposition.changements[?(@.champ == 'droitsGnf')].apres").value("900000"))
        .andExpect(jsonPath("$.proposition.tarifsDateEffet").value(AUJOURDHUI.toString()));
    mvc.perform(get("/api/v1/types-documents/AEX"))
        .andExpect(jsonPath("$.tarifs.droitsGnf").value(800_000));

    approuver(n);
    mvc.perform(get("/api/v1/types-documents/AEX"))
        .andExpect(jsonPath("$.tarifs.droitsGnf").value(900_000))
        .andExpect(jsonPath("$.tarifs.fraisDossierGnf").value(400_000));
    // Une version de tarif pour le seul montant changé, au nom du Ministre, datée.
    assertThat(versionsTarif("DROITS")).hasSize(2);
    assertThat(versionsTarif("FRAIS_DOSSIER")).hasSize(1);
    assertThat(versionsTarif("DROITS").get(1)).contains("Ministre").contains("fiche v" + n);
  }

  @Test
  @Order(4)
  void unTarifAVenirNeSAppliquePasAvantSaDate() throws Exception {
    LocalDate plusTard = AUJOURDHUI.plusDays(10);
    approuver(numero(proposer(bareme(450_000L, 900_000L, null, plusTard, "Frais au 1er du mois"))));
    mvc.perform(get("/api/v1/types-documents/AEX"))
        .andExpect(jsonPath("$.tarifs.fraisDossierGnf").value(400_000));
    mvc.perform(get(AEX).with(jeton(ADMIN_TECH, "ADMIN_TECH")))
        .andExpect(jsonPath("$.tarifsAVenir[0].objet").value("FRAIS_DOSSIER"))
        .andExpect(jsonPath("$.tarifsAVenir[0].montantGnf").value(450_000))
        .andExpect(jsonPath("$.tarifsAVenir[0].dateEffet").value(plusTard.toString()))
        // La fiche en vigueur montre le montant appliqué aujourd'hui.
        .andExpect(jsonPath("$.enVigueur.fraisDossierGnf").value(400_000));
  }

  @Test
  @Order(5)
  void uneVersionPlusRecenteRemplaceUnTarifAVenir() throws Exception {
    approuver(
        numero(proposer(bareme(420_000L, 900_000L, null, AUJOURDHUI, "Frais revus à la baisse"))));
    mvc.perform(get("/api/v1/types-documents/AEX"))
        .andExpect(jsonPath("$.tarifs.fraisDossierGnf").value(420_000));
    mvc.perform(get(AEX).with(jeton(ADMIN_TECH, "ADMIN_TECH")))
        .andExpect(jsonPath("$.tarifsAVenir").isEmpty());
  }

  @Test
  @Order(6)
  void lApplicationNEcritPlusLesTarifsDirectement() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO tarif (type_document_id, objet, montant_gnf, date_effet, version,"
                        + " valide_par) SELECT id, 'DROITS', 1, current_date, 99, 'x'"
                        + " FROM type_document WHERE code = 'AEX'"))
        .rootCause()
        .hasMessageContaining("permission denied");
  }

  private ResultActions proposer(Map<String, Object> p) throws Exception {
    return mvc.perform(
        post(AEX + "/versions")
            .with(jeton(ADMIN_TECH, "ADMIN_TECH"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(p)));
  }

  private void approuver(int numero) throws Exception {
    mvc.perform(
            post(AEX + "/versions/" + numero + "/approbation").with(jeton(MINISTRE, "MINISTRE")))
        .andExpect(status().isNoContent());
  }

  private int numero(ResultActions r) throws Exception {
    return json.readTree(
            r.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
        .get("numero")
        .asInt();
  }

  private static Map<String, Object> bareme(
      Long frais, Long droits, Long attestation, LocalDate effet, String motif) {
    Map<String, Object> p = new HashMap<>();
    p.put("libelleFr", "Autorisation d'exportation");
    p.put("pieces", List.of("Certificat d'expertise", "Déclaration en douane"));
    p.put("delaiJours", 3);
    p.put("instruitPar", "DNM + Fiscalité");
    p.put("signataire", "Directeur général");
    p.put("avis", List.of("DNM"));
    p.put("actif", true);
    p.put("fraisDossierGnf", frais);
    p.put("droitsGnf", droits);
    p.put("attestationGnf", attestation);
    p.put("tarifsDateEffet", effet == null ? null : effet.toString());
    p.put("motif", motif);
    return p;
  }

  private static List<String> versionsTarif(String objet) throws Exception {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT t.valide_par FROM tarif t JOIN type_document td ON td.id ="
                    + " t.type_document_id WHERE td.code = 'AEX' AND t.objet = ?::objet_paiement"
                    + " ORDER BY t.version")) {
      ps.setString(1, objet);
      ResultSet rs = ps.executeQuery();
      List<String> l = new java.util.ArrayList<>();
      while (rs.next()) {
        l.add(rs.getString(1));
      }
      return l;
    }
  }
}
