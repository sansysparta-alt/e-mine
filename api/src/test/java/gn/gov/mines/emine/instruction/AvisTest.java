package gn.gov.mines.emine.instruction;

import static gn.gov.mines.emine.support.Personas.DNM;
import static gn.gov.mines.emine.support.Personas.GEOLOGUE;
import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INSPECTEUR;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.MINISTRE;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
 * US-05.3 : les avis techniques sont requis selon le catalogue ; chaque profil désigné (DNM,
 * Géologue, Inspection) rend un avis motivé ; le signataire ne décide qu'une fois tous les avis
 * rendus, et la base le garantit ; un document sans avis requis est décidé dès la recevabilité.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AvisTest extends TestIntegration {

  static final String PIECES_PEX[] = {"Étude de faisabilité", "EIES", "Plan de restauration"};

  @Autowired MockMvc mvc;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @Test
  void leCatalogueDitQuelsAvisSontRequis() throws Exception {
    mvc.perform(get("/api/v1/types-documents/PEX"))
        .andExpect(jsonPath("$.avisRequis[*].profil", contains("DNM", "INSPECTEUR")));
    mvc.perform(get("/api/v1/types-documents/PRE"))
        .andExpect(jsonPath("$.avisRequis[*].profil", contains("DNM", "GEOLOGUE")));
    mvc.perform(get("/api/v1/types-documents/COR"))
        .andExpect(jsonPath("$.avisRequis.length()").value(0));
  }

  @Test
  void uneDemandeRecevableQuiRequiertDesAvisPartALInstructionTechnique() throws Exception {
    long id = pexRecevableParLeGuichet();
    mvc.perform(get(avis(id)).with(dnm()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requis[*].profil", contains("DNM", "INSPECTEUR")))
        .andExpect(jsonPath("$.requis[0].rendu").value(false))
        .andExpect(jsonPath("$.peutRendre").value(true));
    mvc.perform(get(avis(id)).with(jeton(GEOLOGUE, "GEOLOGUE")))
        .andExpect(jsonPath("$.peutRendre").value(false));
    // Les avis sont des pièces de travail internes : le demandeur ne voit que la décision.
    mvc.perform(get(avis(id)).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERM-001"));
  }

  @Test
  void chaqueProfilRequisRendUnSeulAvisMotiveEtLeDernierOuvreLaDecision() throws Exception {
    long id = pexRecevableParLeGuichet();
    rendre(id, true, "Avis du géologue non requis.", jeton(GEOLOGUE, "GEOLOGUE"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("DEM-012"));
    rendre(id, true, null, dnm())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"));
    rendre(id, false, "Plan de restauration insuffisant sur la zone nord.", dnm())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statut").value("en_instruction"));
    rendre(id, true, "Je change d'avis après coup.", dnm())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("DEM-012"));
    mvc.perform(get(decision(id)).with(ministre())).andExpect(jsonPath("$.aDecider").value(false));

    rendre(id, true, "Visite de terrain conforme au plan d'exploitation.", inspecteur())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statut").value("avis_rendu"));
    mvc.perform(get(avis(id)).with(ministre()))
        .andExpect(jsonPath("$.requis[0].rendu").value(true))
        .andExpect(jsonPath("$.requis[0].favorable").value(false))
        .andExpect(
            jsonPath("$.requis[0].motif")
                .value("Plan de restauration insuffisant sur la zone nord."))
        .andExpect(jsonPath("$.requis[0].auteur").value("Nènè Sow"))
        .andExpect(jsonPath("$.requis[1].favorable").value(true));
    mvc.perform(get(decision(id)).with(ministre()))
        .andExpect(jsonPath("$.aDecider").value(true))
        .andExpect(jsonPath("$.peutDecider").value(true));
    // Un avis défavorable ne décide pas : le signataire tranche (ici, rejet motivé).
    mvc.perform(
            post(decision(id))
                .with(ministre())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"favorable\":false,\"motif\":\"Plan de restauration à reprendre (avis DNM).\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statut").value("rejetee"));
  }

  @Test
  void laDecisionEstImpossibleAvantLesAvisEtLaBaseLeGarantit() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.payer(id, "FRAIS_DOSSIER", 5_000_000);
    DemandesDeTest.changerStatut(id, "recevable");
    mvc.perform(get(decision(id)).with(ministre())).andExpect(jsonPath("$.aDecider").value(false));
    mvc.perform(
            post(decision(id))
                .with(ministre())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"favorable\":true}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEM-011"));
    assertThatThrownBy(() -> DemandesDeTest.changerStatut(id, "decision"))
        .hasMessageContaining("DEM-011");
    DemandesDeTest.changerStatut(id, "en_instruction");
    assertThatThrownBy(() -> DemandesDeTest.changerStatut(id, "avis_rendu"))
        .hasMessageContaining("DEM-011");
  }

  @Test
  void unDocumentSansAvisRequisEstDecideDesLaRecevabilite() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "COR");
    DemandesDeTest.piece(id, "Pièce d'identité");
    DemandesDeTest.piece(id, "Photo");
    DemandesDeTest.piece(id, "Site de rattachement");
    DemandesDeTest.payer(id, "FRAIS_DOSSIER", 100_000);
    mvc.perform(
            post("/api/v1/demandes/" + id + "/recevabilite")
                .with(jeton(GUICHET, "GUICHET"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recevable\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statut").value("recevable"));
    mvc.perform(get(decision(id)).with(jeton(Personas.DIRECTEUR, "DIRECTEUR")))
        .andExpect(jsonPath("$.aDecider").value(true));
  }

  @Test
  void laFileATraiterDonneAuxAvisLeurGroupeEtLaissExamenAuGuichet() throws Exception {
    long id = pexRecevableParLeGuichet();
    mvc.perform(get("/api/v1/demandes/a-traiter").with(dnm()))
        .andExpect(jsonPath("$.groupes[*].cle", contains("avis-a-rendre", "sans-reponse")));
    mvc.perform(get("/api/v1/demandes/a-traiter").with(jeton(GUICHET, "GUICHET")))
        .andExpect(jsonPath("$.groupes[*].cle", contains("a-examiner", "sans-reponse")));
    mvc.perform(
            get("/api/v1/demandes")
                .param("groupe", "avis-a-rendre")
                .param("taille", "100")
                .with(dnm()))
        .andExpect(jsonPath("$.elements[*].id", hasItem((int) id)));
    mvc.perform(
            get("/api/v1/demandes")
                .param("groupe", "avis-a-rendre")
                .param("taille", "100")
                .with(jeton(GEOLOGUE, "GEOLOGUE")))
        .andExpect(jsonPath("$.elements[*].id", not(hasItem((int) id))));
    rendre(id, true, "Dossier technique conforme.", dnm()).andExpect(status().isOk());
    mvc.perform(get("/api/v1/demandes/" + id).with(dnm()))
        .andExpect(jsonPath("$.aTraiterParMoi").value(false));
    mvc.perform(get("/api/v1/demandes/" + id).with(inspecteur()))
        .andExpect(jsonPath("$.aTraiterParMoi").value(true));
  }

  @Test
  void pendantLInstructionUnAvisPeutDemanderUnComplementQuiRevientALInstruction() throws Exception {
    long id = pexRecevableParLeGuichet();
    mvc.perform(
            post("/api/v1/demandes/" + id + "/complements")
                .with(dnm())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"motif\":\"Précisez les coordonnées du périmètre nord.\",\"pieces\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statutOrigine").value("en_instruction"));
    mvc.perform(
            post("/api/v1/demandes/" + id + "/complements/reponse")
                .with(jeton(INVESTISSEUR, "INVESTISSEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Coordonnées précisées dans l'annexe 2.\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statut").value("en_instruction"));
    assertThat(DemandesDeTest.statutEtMotif(id)[0]).isEqualTo("en_instruction");
  }

  private long pexRecevableParLeGuichet() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    for (String p : PIECES_PEX) {
      DemandesDeTest.piece(id, p);
    }
    DemandesDeTest.payer(id, "FRAIS_DOSSIER", 5_000_000);
    mvc.perform(
            post("/api/v1/demandes/" + id + "/recevabilite")
                .with(jeton(GUICHET, "GUICHET"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recevable\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statut").value("en_instruction"));
    return id;
  }

  private ResultActions rendre(long id, boolean favorable, String motif, RequestPostProcessor qui)
      throws Exception {
    String corps =
        motif == null
            ? "{\"favorable\":" + favorable + "}"
            : "{\"favorable\":" + favorable + ",\"motif\":\"" + motif + "\"}";
    return mvc.perform(
        post(avis(id)).with(qui).contentType(MediaType.APPLICATION_JSON).content(corps));
  }

  private static String avis(long id) {
    return "/api/v1/demandes/" + id + "/avis";
  }

  private static String decision(long id) {
    return "/api/v1/demandes/" + id + "/decision";
  }

  private static RequestPostProcessor dnm() {
    return jeton(DNM, "DNM");
  }

  private static RequestPostProcessor inspecteur() {
    return jeton(INSPECTEUR, "INSPECTEUR");
  }

  private static RequestPostProcessor ministre() {
    return jeton(MINISTRE, "MINISTRE");
  }
}
