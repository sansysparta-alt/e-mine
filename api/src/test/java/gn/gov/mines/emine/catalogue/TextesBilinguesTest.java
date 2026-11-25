package gn.gov.mines.emine.catalogue;

import static gn.gov.mines.emine.support.Personas.ADMIN_TECH;
import static gn.gov.mines.emine.support.Personas.MINISTRE;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * US-02.9 (V28) : les textes saisis par l'administrateur existent en français et en anglais (pièces
 * requises et service instructeur d'une fiche, nom, paiements et étapes d'un workflow) ; l'anglais
 * fait partie de la version validée par le Ministre ; les textes initiaux sont traduits. Document
 * PME et workflow CONTENU_LOCAL (inutilisés par les autres tests).
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TextesBilinguesTest extends TestIntegration {

  @Autowired MockMvc mvc;
  final JsonMapper json = JsonMapper.builder().build();

  @BeforeAll
  void donnees() throws Exception {
    Personas.charger();
  }

  @Test
  void leCatalogueInitialEstTraduit() throws Exception {
    mvc.perform(get("/api/v1/types-documents/PEX"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.piecesRequises[1]").value("EIES"))
        .andExpect(jsonPath("$.piecesRequisesEn[1]").value("ESIA"))
        .andExpect(jsonPath("$.instruitParEn").value("DNM + Inspection"));
    mvc.perform(get("/api/v1/workflows/TITRES").with(jeton(ADMIN_TECH, "ADMIN_TECH")))
        .andExpect(jsonPath("$.workflow.enVigueur.libelleEn").value("Mining titles"))
        .andExpect(jsonPath("$.workflow.enVigueur.etapes[0].acteurEn").value("One-stop shop"));
  }

  @Test
  void lAnglaisDUneFicheEstProposeValideEtPublie() throws Exception {
    var admin = jeton(ADMIN_TECH, "ADMIN_TECH");
    // Traduction incomplète : refusée, rien n'est créé.
    proposerFiche(List.of("Trade register (RCCM)"), "Local content department")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"))
        .andExpect(jsonPath("$.details.champs[0].champ").value("piecesEn"));
    int n =
        json.readTree(
                proposerFiche(
                        List.of("Trade register", "Tax certificate", "Local workforce"),
                        "Local content department")
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("numero")
            .asInt();
    mvc.perform(get("/api/v1/fiches-documents/PME").with(admin))
        // Seul l'anglais change : c'est ce que le Ministre voit avant de valider.
        .andExpect(jsonPath("$.proposition.changements.length()").value(2))
        .andExpect(jsonPath("$.proposition.changements[0].champ").value("piecesEn"))
        .andExpect(jsonPath("$.proposition.changements[1].champ").value("instruitParEn"));
    mvc.perform(
            post("/api/v1/fiches-documents/PME/versions/" + n + "/approbation")
                .with(jeton(MINISTRE, "MINISTRE")))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/types-documents/PME"))
        .andExpect(jsonPath("$.piecesRequises[0]").value("RCCM"))
        .andExpect(jsonPath("$.piecesRequisesEn[0]").value("Trade register"))
        .andExpect(jsonPath("$.instruitParEn").value("Local content department"));
  }

  @Test
  void lAnglaisDUnWorkflowFaitPartieDeLaProposition() throws Exception {
    var admin = jeton(ADMIN_TECH, "ADMIN_TECH");
    Map<String, Object> m = new HashMap<>();
    m.put("libelle", "Contenu local");
    m.put("libelleEn", "Local content (SMEs)");
    m.put("reglePaiement", "Paiement unique, renouvellement avec alerte");
    m.put("reglePaiementEn", "Single payment, renewal with reminder");
    m.put(
        "etapes",
        List.of(
            Map.of(
                "acteur",
                "Contenu local",
                "libelle",
                "Instruction",
                "acteurEn",
                "Local content",
                "libelleEn",
                "Review"),
            Map.of("acteur", "Directeur", "libelle", "Signature")));
    m.put("motif", "Traduction du nom de la famille (test)");
    m.put("dateEffet", LocalDate.now(ZoneId.of("Africa/Conakry")).toString());
    int v =
        json.readTree(
                mvc.perform(
                        post("/api/v1/workflows/CONTENU_LOCAL/versions")
                            .with(admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(m)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("version")
            .asInt();
    mvc.perform(get("/api/v1/workflows/CONTENU_LOCAL").with(admin))
        .andExpect(jsonPath("$.workflow.aValider.libelleEn").value("Local content (SMEs)"))
        .andExpect(jsonPath("$.workflow.aValider.etapes[0].libelleEn").value("Review"))
        // Étape sans traduction : repli sur le français à l'affichage.
        .andExpect(jsonPath("$.workflow.aValider.etapes[1].acteurEn").isEmpty());
    mvc.perform(post("/api/v1/workflows/CONTENU_LOCAL/versions/" + v + "/retrait").with(admin))
        .andExpect(status().isNoContent());
  }

  private ResultActions proposerFiche(List<String> piecesEn, String instruitParEn)
      throws Exception {
    Map<String, Object> p = new HashMap<>();
    p.put("libelleFr", "Agrément PME / contenu local");
    p.put("libelleEn", "SME / local content approval");
    p.put("pieces", List.of("RCCM", "attestation fiscale", "effectifs locaux"));
    p.put("piecesEn", piecesEn);
    p.put("delaiJours", 15);
    p.put("instruitPar", "Contenu local");
    p.put("instruitParEn", instruitParEn);
    p.put("signataire", "Directeur national");
    p.put("avis", List.of());
    p.put("actif", true);
    p.put("attestationGnf", 250_000);
    p.put("motif", "Traduction anglaise de la fiche (test)");
    return mvc.perform(
        post("/api/v1/fiches-documents/PME/versions")
            .with(jeton(ADMIN_TECH, "ADMIN_TECH"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(p)));
  }
}
