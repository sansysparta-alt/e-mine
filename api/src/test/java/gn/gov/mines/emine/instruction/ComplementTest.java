package gn.gov.mines.emine.instruction;

import static gn.gov.mines.emine.support.Personas.DIRECTEUR;
import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR_2;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.instruction.application.ServiceComplements;
import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.StockageEnMemoire;
import gn.gov.mines.emine.support.TestIntegration;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
 * US-05.2 : l'agent demande un complément (motif, pièces attendues) ; le demandeur a 5 jours et
 * répond depuis son suivi en joignant les pièces ; il est relancé à J+3 ; le délai de traitement
 * est suspendu pendant le complément ; sans réponse, rien n'est automatique : l'agent relance ou
 * clôt avec un motif.
 */
@AutoConfigureMockMvc
@Import(StockageEnMemoire.Config.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComplementTest extends TestIntegration {

  static final byte[] PDF =
      "%PDF-1.7\n1 0 obj << >> endobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

  @Autowired MockMvc mvc;
  @Autowired ServiceComplements service;
  final JsonMapper json = JsonMapper.builder().build();

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @Test
  void leGuichetDemandeUnComplementEtLeDemandeurEstNotifieAvecUnDelaiDeCinqJours()
      throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    demander(id, "L'EIES fournie n'est pas signée par le bureau d'études.", "EIES")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ouvert").value(true))
        .andExpect(jsonPath("$.statutOrigine").value("deposee"))
        .andExpect(jsonPath("$.piecesDemandees", contains("EIES")))
        .andExpect(jsonPath("$.auteurProfil").value("Agent du guichet unique (CPDM)"));
    assertThat(DemandesDeTest.statutEtMotif(id)[0]).isEqualTo("complement_requis");
    assertThat(DemandesDeTest.auteurDernierStatut(id)).isEqualTo(GUICHET);
    assertThat(DemandesDeTest.notifications(id, "COMPLEMENT_DEMANDE")).isEqualTo(1);

    JsonNode etat = lire(id, investisseur());
    JsonNode c = etat.path("complements").get(0);
    Instant demandeLe = Instant.parse(c.path("demandeLe").asString());
    Instant limite = Instant.parse(c.path("dateLimite").asString());
    assertThat(Duration.between(demandeLe, limite)).isEqualTo(Duration.ofDays(5));
    assertThat(etat.path("peutRepondre").asBoolean()).isTrue();
    assertThat(etat.path("peutDemander").asBoolean()).isFalse();
    assertThat(lire(id, guichet()).path("peutRepondre").asBoolean()).isFalse();
    mvc.perform(get(adresse(id)).with(jeton(INVESTISSEUR_2, "INVESTISSEUR")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DEM-002"));
  }

  @Test
  void motifEtDroitsSontControles() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    demander(id, "Court")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"))
        .andExpect(jsonPath("$.details.champs[0].champ").value("motif"));
    mvc.perform(
            post(adresse(id))
                .with(investisseur())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motif\":\"Je me demande un complément.\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERM-001"));
    DemandesDeTest.payer(id, "FRAIS_DOSSIER", 5_000_000);
    DemandesDeTest.changerStatut(id, "recevable");
    demander(id, "Merci de fournir le plan de restauration.")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEM-001"));
  }

  @Test
  void laReponseExigeChaquePieceDemandeeJointeDepuisLeComplement() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    // Pièce déjà au dossier avant le complément : elle ne vaut pas réponse.
    DemandesDeTest.piece(id, "EIES");
    demander(id, "L'EIES fournie n'est pas signée par le bureau d'études.", "EIES")
        .andExpect(status().isOk());
    repondre(id, null)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("DEM-010"))
        .andExpect(jsonPath("$.details.piecesManquantes", contains("EIES")));

    joindre(id, "EIES").andExpect(status().isCreated());
    assertThat(lire(id, investisseur()).path("piecesJointesDepuis").get(0).asString())
        .isEqualTo("EIES");
    repondre(id, "EIES signée jointe.")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statut").value("deposee"));
    assertThat(DemandesDeTest.statutEtMotif(id)[0]).isEqualTo("deposee");
    assertThat(DemandesDeTest.auteurDernierStatut(id)).isEqualTo(INVESTISSEUR);
    JsonNode c = lire(id, guichet()).path("complements").get(0);
    assertThat(c.path("ouvert").asBoolean()).isFalse();
    assertThat(c.path("reponse").asString()).isEqualTo("EIES signée jointe.");
    repondre(id, "Encore une fois.")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEM-001"));
  }

  @Test
  void unePieceDejaAuDossierEstRemplaceeEtConserveeUnePieceNouvelleSAjoute() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.piece(id, "Plan de restauration");
    long ancienne = pieces(id).get(0).path("id").asLong();
    demander(
            id,
            "Le plan de restauration ne couvre pas la zone nord ; joignez aussi l'accord foncier.",
            "Plan de restauration",
            "Accord foncier")
        .andExpect(status().isOk());
    joindre(id, "Plan de restauration").andExpect(status().isCreated());
    joindre(id, "Accord foncier").andExpect(status().isCreated());
    repondre(id, null).andExpect(status().isOk());

    java.util.List<JsonNode> liste = pieces(id);
    assertThat(liste).hasSize(3);
    JsonNode vieille =
        liste.stream().filter(p -> p.path("id").asLong() == ancienne).findFirst().orElseThrow();
    JsonNode nouvelle =
        liste.stream()
            .filter(p -> p.path("typePiece").asString().equals("Plan de restauration"))
            .filter(p -> p.path("id").asLong() != ancienne)
            .findFirst()
            .orElseThrow();
    assertThat(vieille.path("remplaceeLe").isNull())
        .as("ancienne version conservée, remplacée")
        .isFalse();
    assertThat(vieille.path("remplaceeParId").asLong()).isEqualTo(nouvelle.path("id").asLong());
    assertThat(nouvelle.path("remplaceeLe").isNull()).as("nouvelle version en vigueur").isTrue();
    JsonNode accord =
        liste.stream()
            .filter(p -> p.path("typePiece").asString().equals("Accord foncier"))
            .findFirst()
            .orElseThrow();
    assertThat(accord.path("remplaceeLe").isNull()).as("supplément").isTrue();
  }

  @Test
  void sansPieceDemandeeUneReponseEcriteEstExigee() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "COR");
    demander(id, "Précisez le site de rattachement de l'orpailleur.").andExpect(status().isOk());
    repondre(id, "  ")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("DEM-010"));
    repondre(id, "Site de rattachement : placer fictif de Kouroussa.")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statut").value("deposee"));
  }

  @Test
  void seulLeDemandeurRepondEtSeulesLesPiecesDuComplementSeRetirent() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.piece(id, "EIES");
    long ancienne = pieces(id).get(0).path("id").asLong();
    demander(id, "Merci de joindre l'étude de faisabilité complète.", "Étude de faisabilité")
        .andExpect(status().isOk());
    mvc.perform(
            post(adresse(id) + "/reponse")
                .with(jeton(INVESTISSEUR_2, "INVESTISSEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Je réponds à sa place.\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/api/v1/demandes/" + id + "/pieces/" + ancienne).with(investisseur()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DOC-003"));
    joindre(id, "Étude de faisabilité").andExpect(status().isCreated());
    long nouvelle = pieces(id).get(1).path("id").asLong();
    mvc.perform(delete("/api/v1/demandes/" + id + "/pieces/" + nouvelle).with(investisseur()))
        .andExpect(status().isNoContent());
  }

  @Test
  void avantLeDelaiLAgentNePeutNiRelancerNiClore() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.complementOuvert(id, GUICHET, "deposee", 2);
    mvc.perform(post(adresse(id) + "/relance").with(guichet()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEM-009"));
    clore(id, "Aucune réponse du demandeur.")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEM-009"));
    assertThat(lire(id, guichet()).path("delaiDepasse").asBoolean()).isFalse();
  }

  @Test
  void apresLeDelaiLAgentRelanceAvecUnNouveauDelaiOuClotAvecUnMotif() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.complementOuvert(id, GUICHET, "deposee", 6, "EIES");
    assertThat(lire(id, guichet()).path("delaiDepasse").asBoolean()).isTrue();
    assertThat(DemandesDeTest.statutEtMotif(id)[0])
        .as("rien d'automatique après le délai")
        .isEqualTo("complement_requis");
    String limite =
        lire(mvc.perform(post(adresse(id) + "/relance").with(guichet())))
            .path("dateLimite")
            .asString();
    assertThat(Instant.parse(limite)).isAfter(Instant.now().plus(Duration.ofDays(4)));
    assertThat(lire(id, guichet()).path("delaiDepasse").asBoolean()).isFalse();

    long autre = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.complementOuvert(autre, GUICHET, "deposee", 6, "EIES");
    clore(autre, "Court").andExpect(status().isBadRequest());
    clore(autre, "Aucune réponse au complément dans le délai.")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statut").value("rejetee"));
    String[] etat = DemandesDeTest.statutEtMotif(autre);
    assertThat(etat[0]).isEqualTo("rejetee");
    assertThat(etat[1]).isEqualTo("Aucune réponse au complément dans le délai.");
    assertThat(lire(autre, investisseur()).path("complements").get(0).path("closLe").isNull())
        .isFalse();
  }

  @Test
  void leDemandeurEstRelanceUneSeuleFoisATroisJours() throws Exception {
    long recent = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.complementOuvert(recent, GUICHET, "deposee", 1);
    long ancien = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.complementOuvert(ancien, GUICHET, "deposee", 4);
    assertThat(service.relancerAutomatiquement()).isPositive();
    service.relancerAutomatiquement();
    assertThat(DemandesDeTest.notifications(ancien, "COMPLEMENT_RELANCE")).isEqualTo(1);
    assertThat(DemandesDeTest.notifications(recent, "COMPLEMENT_RELANCE")).isZero();
    assertThat(lire(ancien, guichet()).path("complements").get(0).path("relanceLe").isNull())
        .isFalse();
  }

  @Test
  void unComplementSansReponseEstATraiterPourLeProfilQuiLaDemandeSeulement() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.complementOuvert(id, GUICHET, "deposee", 6);
    long enCours = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.complementOuvert(enCours, GUICHET, "deposee", 1);
    JsonNode page =
        lire(
            mvc.perform(
                get("/api/v1/demandes")
                    .param("groupe", "sans-reponse")
                    .param("taille", "100")
                    .with(guichet())));
    assertThat(page.findValuesAsString("numero")).isNotEmpty();
    assertThat(ids(page)).contains(id).doesNotContain(enCours);
    mvc.perform(get("/api/v1/demandes/" + id).with(guichet()))
        .andExpect(jsonPath("$.aTraiterParMoi").value(true));
    mvc.perform(get("/api/v1/demandes/" + enCours).with(guichet()))
        .andExpect(jsonPath("$.aTraiterParMoi").value(false));
    mvc.perform(get("/api/v1/demandes/" + id).with(jeton(DIRECTEUR, "DIRECTEUR")))
        .andExpect(jsonPath("$.aTraiterParMoi").value(false));
  }

  @Test
  void leDelaiDeTraitementEstSuspenduPendantLeComplement() throws Exception {
    // Carte d'orpailleur : délai de 5 jours au catalogue. Frais payés il y a 8 jours (9 jours de
    // traitement, jour du paiement compté), dont 4 en complément : 5 jours décomptés, pas de
    // retard ; sans complément, elle serait en retard.
    long suspendue = DemandesDeTest.deposee(INVESTISSEUR, "COR");
    DemandesDeTest.fraisPayesIlYA(suspendue, 8);
    DemandesDeTest.complementOuvert(suspendue, GUICHET, "deposee", 7);
    DemandesDeTest.complementRepondu(suspendue, 3);
    DemandesDeTest.changerStatut(suspendue, "deposee");
    long sansComplement = DemandesDeTest.deposee(INVESTISSEUR, "COR");
    DemandesDeTest.fraisPayesIlYA(sansComplement, 8);
    mvc.perform(get("/api/v1/demandes/" + suspendue).with(guichet()))
        .andExpect(jsonPath("$.enRetard").value(false));
    mvc.perform(get("/api/v1/demandes/" + sansComplement).with(guichet()))
        .andExpect(jsonPath("$.enRetard").value(true));
  }

  private static java.util.List<Long> ids(JsonNode page) {
    java.util.List<Long> ids = new java.util.ArrayList<>();
    page.path("elements").forEach(e -> ids.add(e.path("id").asLong()));
    return ids;
  }

  private java.util.List<JsonNode> pieces(long id) throws Exception {
    JsonNode liste =
        lire(mvc.perform(get("/api/v1/demandes/" + id + "/pieces").with(investisseur())));
    java.util.List<JsonNode> resultat = new java.util.ArrayList<>();
    liste.forEach(resultat::add);
    return resultat;
  }

  private ResultActions demander(long id, String motif, String... pieces) throws Exception {
    String corps =
        json.writeValueAsString(
            java.util.Map.of("motif", motif, "pieces", java.util.List.of(pieces)));
    return mvc.perform(
        post(adresse(id)).with(guichet()).contentType(MediaType.APPLICATION_JSON).content(corps));
  }

  private ResultActions repondre(long id, String message) throws Exception {
    java.util.Map<String, Object> corps = new java.util.HashMap<>();
    corps.put("message", message);
    return mvc.perform(
        post(adresse(id) + "/reponse")
            .with(investisseur())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(corps)));
  }

  private ResultActions clore(long id, String motif) throws Exception {
    return mvc.perform(
        post(adresse(id) + "/cloture")
            .with(guichet())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(java.util.Map.of("motif", motif))));
  }

  private ResultActions joindre(long id, String type) throws Exception {
    return mvc.perform(
        multipart("/api/v1/demandes/" + id + "/pieces")
            .file(new MockMultipartFile("fichier", "piece.pdf", "application/pdf", PDF))
            .param("typePiece", type)
            .with(investisseur()));
  }

  private JsonNode lire(long id, RequestPostProcessor qui) throws Exception {
    return lire(mvc.perform(get(adresse(id)).with(qui)).andExpect(status().isOk()));
  }

  private JsonNode lire(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
  }

  private static String adresse(long id) {
    return "/api/v1/demandes/" + id + "/complements";
  }

  private static RequestPostProcessor guichet() {
    return jeton(GUICHET, "GUICHET");
  }

  private static RequestPostProcessor investisseur() {
    return jeton(INVESTISSEUR, "INVESTISSEUR");
  }
}
