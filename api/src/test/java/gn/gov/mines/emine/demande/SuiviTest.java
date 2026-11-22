package gn.gov.mines.emine.demande;

import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR_2;
import static gn.gov.mines.emine.support.Personas.MINISTRE;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * US-07.3 : la frise de suivi est calculée par l'API, avec la date de chaque étape franchie, pour
 * le demandeur et les agents ; un autre usager ne la voit pas.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SuiviTest extends TestIntegration {

  @Autowired MockMvc mvc;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @Test
  void leDemandeurSuitSaDemandeEtapeParEtapeAvecLesDates() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    mvc.perform(get(suivi(id)).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.etapes[*].cle",
                contains(
                    "depot",
                    "paiement-frais",
                    "controle",
                    "instruction",
                    "paiement-droits",
                    "signe")))
        .andExpect(jsonPath("$.etapes[0].etat").value("faite"))
        .andExpect(jsonPath("$.etapes[0].date").value(notNullValue()))
        .andExpect(jsonPath("$.etapes[1].etat").value("en-cours"))
        .andExpect(jsonPath("$.etapes[1].actionDemandeur").value(true))
        .andExpect(jsonPath("$.etapes[1].date").value(nullValue()));

    DemandesDeTest.pretePourLaDelivrance(id, MINISTRE, "FRAIS_DOSSIER", 5_000_000, 15_000_000L);
    mvc.perform(get(suivi(id)).with(jeton(GUICHET, "GUICHET")))
        .andExpect(
            jsonPath(
                "$.etapes[*].etat",
                contains("faite", "faite", "faite", "faite", "faite", "en-cours")))
        .andExpect(jsonPath("$.etapes[1].date").value(notNullValue()))
        .andExpect(jsonPath("$.etapes[2].date").value(nullValue()))
        .andExpect(jsonPath("$.etapes[3].date").value(notNullValue()))
        .andExpect(jsonPath("$.etapes[4].date").value(notNullValue()));
  }

  @Test
  void unDocumentSansDroitsAfficheLePaiementDesDroitsSansObjet() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "COR");
    mvc.perform(get(suivi(id)).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(jsonPath("$.etapes[4].etat").value("sans-objet"));
  }

  @Test
  void unAutreUsagerNeVoitPasLaFrise() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    mvc.perform(get(suivi(id)).with(jeton(INVESTISSEUR_2, "INVESTISSEUR")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DEM-002"));
  }

  private static String suivi(long id) {
    return "/api/v1/demandes/" + id + "/suivi";
  }
}
