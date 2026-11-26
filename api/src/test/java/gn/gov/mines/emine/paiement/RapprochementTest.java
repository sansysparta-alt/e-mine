package gn.gov.mines.emine.paiement;

import static gn.gov.mines.emine.support.Personas.FISCAL;
import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.StockageEnMemoire;
import gn.gov.mines.emine.support.TestIntegration;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-04.8 : le Responsable fiscal importe un relevé CSV ; le rapprochement est automatique et
 * couvre les 7 cas du contrat (conforme, inférieur, supérieur, référence inconnue, doublon, tardif,
 * retour bancaire). Seul le cas conforme confirme un paiement ; un même fichier ne s'importe qu'une
 * fois.
 */
@AutoConfigureMockMvc
@Import(StockageEnMemoire.Config.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RapprochementTest extends TestIntegration {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final byte[] PDF =
      "%PDF-1.7\n1 0 obj << >> endobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
  private static final String EN_TETE = "date_operation;libelle;montant_gnf;sens\n";

  @Autowired MockMvc mvc;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @Test
  void lesSeptCasDuContratSontRapprochesSansDecisionAutomatiqueHorsCasConforme() throws Exception {
    long demandeA = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String conforme = virementDeclare(demandeA);
    String inferieur = virementDeclare(DemandesDeTest.deposee(INVESTISSEUR, "PEX"));
    String superieur = virementDeclare(DemandesDeTest.deposee(INVESTISSEUR, "PEX"));
    long demandeTardive = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String tardif = virement(demandeTardive);
    choisir(demandeTardive, "TRESORPAY"); // le virement est annulé avant d'arriver
    String csv =
        EN_TETE
            // Référence en minuscules, sans tirets : reconnue.
            + "26/11/2026;VIR HORIZON "
            + conforme.toLowerCase().replace("-", "")
            + ";5 000 000;C\n"
            + "26/11/2026;VIR "
            + inferieur
            + ";4000000;C\n"
            + "26/11/2026;VIR "
            + superieur
            + ";6000000;C\n"
            + "26/11/2026;VIR SANS REFERENCE;250000;C\n"
            + "27/11/2026;VIR "
            + conforme
            + ";5000000;C\n"
            + "27/11/2026;VIR "
            + tardif
            + ";5000000;C\n"
            + "28/11/2026;RETOUR VIR "
            + conforme
            + ";5000000;D\n";

    String corps =
        importer(csv, "releve-novembre.csv")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.lignes").value(7))
            .andExpect(jsonPath("$.rapprochees").value(1))
            .andExpect(jsonPath("$.ecarts").value(5))
            .andExpect(jsonPath("$.inconnues").value(1))
            .andExpect(jsonPath("$.totalCreditsGnf").value(25_250_000))
            // Date du relevé par défaut : la dernière date d'opération.
            .andExpect(jsonPath("$.dateReleve").value("2026-11-28"))
            .andExpect(jsonPath("$.nomFichier").value("releve-novembre.csv"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long releve = JSON.readTree(corps).path("id").asLong();

    Map<Integer, JsonNode> lignes = lignes(releve, null);
    assertThat(lignes.get(2).path("statut").asString()).isEqualTo("rapprochee");
    assertThat(lignes.get(2).path("nature").asString()).isEqualTo("conforme");
    assertThat(lignes.get(2).path("referencePaiement").asString()).isEqualTo(conforme);
    assertThat(lignes.get(2).path("numeroDemande").asString()).startsWith("DEM-");
    assertThat(lignes.get(3).path("nature").asString()).isEqualTo("inferieur");
    assertThat(lignes.get(3).path("ecartGnf").asLong()).isEqualTo(-1_000_000);
    assertThat(lignes.get(4).path("nature").asString()).isEqualTo("superieur");
    assertThat(lignes.get(4).path("ecartGnf").asLong()).isEqualTo(1_000_000);
    assertThat(lignes.get(5).path("statut").asString()).isEqualTo("inconnue");
    assertThat(lignes.get(6).path("nature").asString()).isEqualTo("doublon");
    assertThat(lignes.get(7).path("nature").asString()).isEqualTo("tardif");
    assertThat(lignes.get(8).path("nature").asString()).isEqualTo("retour");
    assertThat(lignes.get(8).path("sens").asString()).isEqualTo("D");

    // Seul le cas conforme confirme un paiement : reçu, frais payés, demande au guichet.
    assertThat(DemandesDeTest.statutPaiement(conforme)).isEqualTo("paye");
    assertThat(DemandesDeTest.statutPaiement(inferieur)).isEqualTo("a_verifier");
    assertThat(DemandesDeTest.statutPaiement(superieur)).isEqualTo("a_verifier");
    assertThat(DemandesDeTest.statutPaiement(tardif)).isEqualTo("annule");
    mvc.perform(get("/api/v1/paiements/" + conforme).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(jsonPath("$.recuNumero").isNotEmpty())
        .andExpect(jsonPath("$.datePaiement").value(containsString("2026-11-2")));
    mvc.perform(get("/api/v1/demandes/" + demandeA).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(jsonPath("$.fraisPayes").value(true))
        .andExpect(jsonPath("$.paiementEnVerification").value(false));

    // Filtre « à vérifier » : écarts et lignes inconnues.
    assertThat(lignes(releve, "a-verifier")).hasSize(6);
    assertThat(lignes(releve, "rapprochees")).hasSize(1);
  }

  @Test
  void unMemeFichierNeSImporteQuUneFois() throws Exception {
    String csv = EN_TETE + "26/11/2026;VIR SANS REFERENCE UNIQUE 1;1000;C\n";
    importer(csv, "a.csv").andExpect(status().isCreated());
    importer(csv, "copie.csv")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PAY-011"));
  }

  @Test
  void unFormatInvalideEstRefuseEnCitantLesLignes() throws Exception {
    importer("date;libelle\n26/11/2026;VIR\n", "faux.csv")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"));
    importer(EN_TETE + "26/11/2026;VIR;100;C\n31/13/2026;VIR;100;C\n", "faux2.csv")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.champs[0].message").value(containsString("Ligne 3")));
  }

  @Test
  void leRelevePeutPorterUneBanqueEtUneDateChoisies() throws Exception {
    mvc.perform(
            multipart("/api/v1/releves")
                .file(fichier(EN_TETE + "26/11/2026;VIR AUTRE BANQUE;1000;C\n", "b.csv"))
                .param("banque", "Banque de test")
                .param("dateReleve", "2026-11-30")
                .with(fiscal()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.banque").value("Banque de test"))
        .andExpect(jsonPath("$.dateReleve").value("2026-11-30"));
    mvc.perform(get("/api/v1/releves").with(fiscal()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.elements[0].banque").isNotEmpty())
        .andExpect(jsonPath("$.exempleDisponible").value(true));
  }

  @Test
  void leReleveDExempleProposeUneLigneConformeParVirementDeclare() throws Exception {
    String ref = virementDeclare(DemandesDeTest.deposee(INVESTISSEUR, "PEX"));
    String csv =
        mvc.perform(get("/api/v1/releves/exemple").with(fiscal()))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/csv"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    assertThat(csv).startsWith(EN_TETE).contains(ref + ";5000000;C");
    // Il se réimporte tel quel et confirme le virement.
    importer(csv, "releve-exemple.csv").andExpect(status().isCreated());
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("paye");
  }

  @Test
  void seulLeResponsableFiscalImporteEtConsulteLesReleves() throws Exception {
    mvc.perform(get("/api/v1/releves").with(jeton(GUICHET, "GUICHET")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERM-001"));
    mvc.perform(
            multipart("/api/v1/releves")
                .file(fichier(EN_TETE + "26/11/2026;VIR;1000;C\n", "c.csv"))
                .with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/releves/999999").with(fiscal()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PAY-012"));
  }

  private Map<Integer, JsonNode> lignes(long releve, String resultat) throws Exception {
    String url =
        "/api/v1/releves/"
            + releve
            + "/lignes?taille=100"
            + (resultat == null ? "" : "&resultat=" + resultat);
    JsonNode page =
        JSON.readTree(
            mvc.perform(get(url).with(fiscal()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    Map<Integer, JsonNode> parNumero = new HashMap<>();
    for (JsonNode l : page.path("elements")) {
      parNumero.put(l.path("numero").asInt(), l);
    }
    return parNumero;
  }

  private ResultActions importer(String csv, String nom) throws Exception {
    return mvc.perform(multipart("/api/v1/releves").file(fichier(csv, nom)).with(fiscal()));
  }

  private static MockMultipartFile fichier(String csv, String nom) {
    return new MockMultipartFile("fichier", nom, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
  }

  private String virement(long demande) throws Exception {
    return reference(choisir(demande, "VIREMENT").andExpect(status().isCreated()));
  }

  private String virementDeclare(long demande) throws Exception {
    String ref = virement(demande);
    mvc.perform(
            multipart("/api/v1/paiements/" + ref + "/virement")
                .file(new MockMultipartFile("fichier", "ordre.pdf", "application/pdf", PDF))
                .with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(status().isOk());
    return ref;
  }

  private ResultActions choisir(long demande, String moyen) throws Exception {
    return mvc.perform(
        post("/api/v1/demandes/" + demande + "/paiements")
            .with(jeton(INVESTISSEUR, "INVESTISSEUR"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"moyen\":\"" + moyen + "\"}"));
  }

  private static RequestPostProcessor fiscal() {
    return jeton(FISCAL, "FISCAL");
  }

  private static String reference(ResultActions r) throws Exception {
    return JSON.readTree(r.andReturn().getResponse().getContentAsString())
        .path("paiement")
        .path("reference")
        .asString();
  }
}
