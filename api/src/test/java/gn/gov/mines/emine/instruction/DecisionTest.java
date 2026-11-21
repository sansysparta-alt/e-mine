package gn.gov.mines.emine.instruction;

import static gn.gov.mines.emine.support.Personas.DIRECTEUR;
import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.MINISTRE;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * US-05.4 : le signataire désigné par le catalogue (D4) décide d'une demande recevable ; favorable
 * → droits à payer (ou droits payés s'il n'y en a pas) ; rejet → motif obligatoire ; la décision
 * est inscrite dans {@code avis_decision} et visible par le demandeur.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DecisionTest extends TestIntegration {

  @Autowired MockMvc mvc;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @Test
  void leMinistreSignataireDuPexRendUneDecisionFavorableLesDroitsSontAttendus() throws Exception {
    long id = recevable("PEX", "FRAIS_DOSSIER", 5_000_000);
    mvc.perform(get(adresse(id)).with(ministre()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.aDecider").value(true))
        .andExpect(jsonPath("$.signataire").value("Ministre"))
        .andExpect(jsonPath("$.signataireProfil").value("MINISTRE"))
        .andExpect(jsonPath("$.peutDecider").value(true))
        .andExpect(jsonPath("$.droitsGnf").value(15_000_000))
        .andExpect(jsonPath("$.decisionFavorable").value(nullValue()));
    decider(id, true, null, ministre())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statut").value("decision"));
    assertThat(DemandesDeTest.statutEtMotif(id)[0]).isEqualTo("decision");
    assertThat(DemandesDeTest.auteurDernierStatut(id)).isEqualTo(MINISTRE);
    mvc.perform(get(adresse(id)).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(jsonPath("$.aDecider").value(false))
        .andExpect(jsonPath("$.peutDecider").value(false))
        .andExpect(jsonPath("$.decisionFavorable").value(true))
        .andExpect(jsonPath("$.decisionAuteur").value("Ibrahima Soumah"))
        .andExpect(jsonPath("$.decisionRenduLe").isNotEmpty());
    mvc.perform(
            get("/api/v1/demandes/" + id + "/paiements/a-payer")
                .with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(jsonPath("$.objet").value("DROITS"))
        .andExpect(jsonPath("$.montantGnf").value(15_000_000));
  }

  @Test
  void leDirecteurNePeutPasDeciderDunDocumentSigneParLeMinistreDem008() throws Exception {
    long id = recevable("PEX", "FRAIS_DOSSIER", 5_000_000);
    mvc.perform(get(adresse(id)).with(directeur()))
        .andExpect(jsonPath("$.peutDecider").value(false));
    decider(id, true, null, directeur())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("DEM-008"))
        .andExpect(jsonPath("$.details.signataire").value("Ministre"));
    assertThat(DemandesDeTest.statutEtMotif(id)[0]).isEqualTo("recevable");
  }

  @Test
  void leDirecteurDecideDuneAutorisationDeCarriereQuIlSigne() throws Exception {
    long id = recevable("CAR", "FRAIS_DOSSIER", 1_000_000);
    decider(id, true, "Dossier complet et conforme.", directeur())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statut").value("decision"));
    mvc.perform(get(adresse(id)).with(directeur()))
        .andExpect(jsonPath("$.decisionMotif").value("Dossier complet et conforme."));
  }

  @Test
  void unDocumentSansDroitsPasseDirectementADroitsPayes() throws Exception {
    long id = recevable("EXC", "ATTESTATION", 200_000);
    mvc.perform(get(adresse(id)).with(directeur()))
        .andExpect(jsonPath("$.droitsGnf").value(nullValue()));
    decider(id, true, null, directeur())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statut").value("droits_payes"));
    assertThat(DemandesDeTest.statutEtMotif(id)[0]).isEqualTo("droits_payes");
  }

  @Test
  void unRejetExigeUnMotifPuisRejetteLaDemandeAvecCeMotif() throws Exception {
    long id = recevable("PEX", "FRAIS_DOSSIER", 5_000_000);
    decider(id, false, "  ", ministre())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"))
        .andExpect(jsonPath("$.details.champs[0].champ").value("motif"));
    assertThat(DemandesDeTest.statutEtMotif(id)[0]).isEqualTo("recevable");
    decider(id, false, " Le périmètre chevauche un titre existant. ", ministre())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statut").value("rejetee"));
    String[] etat = DemandesDeTest.statutEtMotif(id);
    assertThat(etat[0]).isEqualTo("rejetee");
    assertThat(etat[1]).isEqualTo("Le périmètre chevauche un titre existant.");
    mvc.perform(get(adresse(id)).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(jsonPath("$.decisionFavorable").value(false))
        .andExpect(jsonPath("$.decisionMotif").value("Le périmètre chevauche un titre existant."));
  }

  @Test
  void uneDemandeQuiNestPasRecevableNePeutPasEtreDecideeDem001() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    decider(id, true, null, ministre())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEM-001"));
    long decidee = recevable("PEX", "FRAIS_DOSSIER", 5_000_000);
    decider(decidee, true, null, ministre()).andExpect(status().isOk());
    decider(decidee, false, "Revirement après la décision.", ministre())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEM-001"));
  }

  @Test
  void sansLeDroitSignerLaDecisionEstRefuseePerm001() throws Exception {
    long id = recevable("PEX", "FRAIS_DOSSIER", 5_000_000);
    decider(id, true, null, jeton(GUICHET, "GUICHET"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERM-001"));
    decider(id, true, null, jeton(INVESTISSEUR, "INVESTISSEUR"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERM-001"));
    assertThat(DemandesDeTest.statutEtMotif(id)[0]).isEqualTo("recevable");
  }

  @Test
  void laFileDesDecisionsNeMontreQueLesDocumentsDuSignataire() throws Exception {
    long pex = recevable("PEX", "FRAIS_DOSSIER", 5_000_000);
    long car = recevable("CAR", "FRAIS_DOSSIER", 1_000_000);
    mvc.perform(
            get("/api/v1/demandes")
                .param("statut", "recevable")
                .param("signataireProfil", "MINISTRE")
                .param("taille", "100")
                .with(ministre()))
        .andExpect(jsonPath("$.elements[*].id", hasItem((int) pex)))
        .andExpect(jsonPath("$.elements[*].id", not(hasItem((int) car))));
  }

  /** Demande déposée par l'investisseur, frais payés, déclarée recevable. */
  private static long recevable(String type, String objet, long montant) throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, type);
    DemandesDeTest.payer(id, objet, montant);
    DemandesDeTest.changerStatut(id, "recevable");
    return id;
  }

  private ResultActions decider(long id, boolean favorable, String motif, RequestPostProcessor qui)
      throws Exception {
    String corps =
        motif == null
            ? "{\"favorable\":" + favorable + "}"
            : "{\"favorable\":" + favorable + ",\"motif\":\"" + motif + "\"}";
    return mvc.perform(
        post(adresse(id)).with(qui).contentType(MediaType.APPLICATION_JSON).content(corps));
  }

  private static String adresse(long id) {
    return "/api/v1/demandes/" + id + "/decision";
  }

  private static RequestPostProcessor ministre() {
    return jeton(MINISTRE, "MINISTRE");
  }

  private static RequestPostProcessor directeur() {
    return jeton(DIRECTEUR, "DIRECTEUR");
  }
}
