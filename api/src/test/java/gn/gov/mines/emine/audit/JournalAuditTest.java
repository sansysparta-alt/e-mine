package gn.gov.mines.emine.audit;

import static gn.gov.mines.emine.support.Personas.ADMIN_TECH;
import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-11.7 (partiel V1) : l'administrateur consulte le journal alimenté par les triggers — auteur,
 * action, objet, champs modifiés — filtré par catégorie, recherche et période ; les secrets sont
 * masqués ; les autres profils n'y ont pas accès.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JournalAuditTest extends TestIntegration {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String URL = "/api/v1/journal-audit";

  @Autowired MockMvc mvc;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @Test
  void lAdministrateurVoitQuiACreeEtModifieUneDemandeEtCeQuiAChange() throws Exception {
    long id = brouillon("12.50");
    mvc.perform(
            put("/api/v1/demandes/" + id)
                .with(investisseur())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpsBrouillon("40.00")))
        .andExpect(status().isOk());

    mvc.perform(
            get(URL).param("categorie", "demandes").param("q", String.valueOf(id)).with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        // Du plus récent au plus ancien : la modification, puis la création.
        .andExpect(jsonPath("$.elements[0].operation").value("U"))
        .andExpect(jsonPath("$.elements[0].auteur").value("Fodé Bangoura"))
        .andExpect(jsonPath("$.elements[0].auteurProfil").value("Investisseur Extérieur"))
        .andExpect(jsonPath("$.elements[0].categorie").value("demandes"))
        .andExpect(jsonPath("$.elements[0].demandeId").value(id))
        .andExpect(jsonPath("$.elements[0].changements.length()").value(1))
        .andExpect(jsonPath("$.elements[0].changements[0].champ").value("superficie_ha"))
        .andExpect(jsonPath("$.elements[0].changements[0].avant").value("12.50"))
        .andExpect(jsonPath("$.elements[0].changements[0].apres").value("40.00"))
        .andExpect(jsonPath("$.elements[1].operation").value("I"))
        .andExpect(jsonPath("$.elements[1].changements.length()").value(0))
        .andExpect(jsonPath("$.compteurs.demandes").value(2))
        .andExpect(jsonPath("$.compteurs.paiements").value(0));
  }

  @Test
  void laFicheDUneCreationDetailleSesValeursEtMasqueLIdentifiantDeCompte() throws Exception {
    String page =
        mvc.perform(
                get(URL)
                    .param("categorie", "comptes")
                    .param("q", "f.bangoura@horizon-invest.test")
                    .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total", greaterThanOrEqualTo(1)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode creation = null;
    for (JsonNode e : JSON.readTree(page).path("elements")) {
      if ("I".equals(e.path("operation").asString())) {
        creation = e;
      }
    }
    mvc.perform(get(URL + "/" + creation.path("id").asLong()).with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.table").value("utilisateur"))
        .andExpect(jsonPath("$.objetLibelle").value("f.bangoura@horizon-invest.test"))
        .andExpect(jsonPath("$.changements[?(@.champ == 'keycloak_id')].apres").value("••••••"))
        .andExpect(jsonPath("$.changements[*].apres", not(hasItem(Personas.INVESTISSEUR))));
  }

  @Test
  void laPeriodeEtLaCategorieFiltrentLeJournal() throws Exception {
    brouillon("3.00");
    String demain = LocalDate.now().plusDays(2).toString();
    mvc.perform(get(URL).param("du", demain).with(admin())).andExpect(jsonPath("$.total").value(0));
    mvc.perform(get(URL).param("categorie", "paiements").with(admin()))
        .andExpect(jsonPath("$.elements[*].table", everyItem(is("paiement"))));
  }

  @Test
  void uneCategorieInconnueOuUnePeriodeInverseeSontRefuseesVal001() throws Exception {
    mvc.perform(get(URL).param("categorie", "inconnue").with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"));
    mvc.perform(get(URL).param("du", "2026-11-20").param("au", "2026-11-01").with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"))
        .andExpect(jsonPath("$.details.champs[0].champ").value("au"));
  }

  @Test
  void seulLAdministrateurConsulteLeJournalPerm001() throws Exception {
    mvc.perform(get(URL).with(investisseur()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERM-001"));
    mvc.perform(get(URL).with(jeton(GUICHET, "GUICHET")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERM-001"));
  }

  @Test
  void uneLigneInconnueDonneSys002() throws Exception {
    mvc.perform(get(URL + "/999999999").with(admin()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SYS-002"));
  }

  private long brouillon(String superficie) throws Exception {
    String reponse =
        mvc.perform(
                post("/api/v1/demandes")
                    .with(investisseur())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(corpsBrouillon(superficie)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(reponse).path("id").asLong();
  }

  private static String corpsBrouillon(String superficie) {
    return "{\"typeDocumentCode\":\"PEX\",\"superficieHa\":" + superficie + "}";
  }

  private static RequestPostProcessor admin() {
    return jeton(ADMIN_TECH, "ADMIN_TECH");
  }

  private static RequestPostProcessor investisseur() {
    return jeton(INVESTISSEUR, "INVESTISSEUR");
  }
}
