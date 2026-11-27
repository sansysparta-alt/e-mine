package gn.gov.mines.emine.paiement;

import static gn.gov.mines.emine.support.Personas.ADMIN_TECH;
import static gn.gov.mines.emine.support.Personas.DIRECTEUR;
import static gn.gov.mines.emine.support.Personas.FISCAL;
import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.MINISTRE;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.StockageEnMemoire;
import gn.gov.mines.emine.support.TestIntegration;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
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
 * US-04.10 : le Responsable fiscal valide (ou refuse avec motif) un remboursement, puis enregistre
 * l'ordre du Trésor (Remboursement demandé → Remboursé) ; tout est tracé et le demandeur est
 * informé. Règle V1-4 : les frais d'une demande rejetée ne sont remboursés que si la règle de son
 * dépôt le prévoit ; la règle est proposée par l'administrateur et validée par le Ministre.
 */
@AutoConfigureMockMvc
@Import(StockageEnMemoire.Config.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RemboursementsTest extends TestIntegration {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final byte[] PDF =
      "%PDF-1.7\n1 0 obj << >> endobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
  private static final String EN_TETE = "date_operation;libelle;montant_gnf;sens\n";
  private static final AtomicInteger UNIQUE = new AtomicInteger();

  @Autowired MockMvc mvc;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @Test
  void unDoublonRembourseSuitLeCircuitDemandeValideExecute() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String ref = virementDeclare(demande);
    ligneImportee("26/11/2026;VIR " + ref + ";5000000;C");
    long doublon = ligneImportee("27/11/2026;VIR " + ref + " BIS;5000000;C");
    decider(doublon, "{\"decision\":\"REMBOURSER\"}").andExpect(status().isNoContent());

    long id = remboursementDeLaLigne(doublon);
    mvc.perform(get("/api/v1/remboursements/" + id).with(fiscal()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.numero").value(matchesPattern("RMB-\\d{4}-\\d{6}")))
        .andExpect(jsonPath("$.statut").value("demande"))
        .andExpect(jsonPath("$.motif").value("double_paiement"))
        .andExpect(jsonPath("$.montantGnf").value(5_000_000))
        .andExpect(jsonPath("$.referencePaiement").value(ref));
    // Une exécution n'est possible qu'après validation.
    executer(id, "{\"referenceOrdre\":\"OR-2026-0042\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PAY-006"));

    mvc.perform(post("/api/v1/remboursements/" + id + "/validation").with(directeur()))
        .andExpect(status().isNoContent());
    executer(id, "{\"referenceOrdre\":\"\"}").andExpect(status().isBadRequest());
    executer(id, "{\"referenceOrdre\":\"OR-2026-0042\"}").andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/remboursements/" + id).with(fiscal()))
        .andExpect(jsonPath("$.statut").value("execute"))
        .andExpect(jsonPath("$.referenceOrdre").value("OR-2026-0042"))
        .andExpect(jsonPath("$.decidePar").isNotEmpty())
        .andExpect(jsonPath("$.executeLe").isNotEmpty());
    assertThat(DemandesDeTest.notifications(demande, "REMBOURSEMENT")).isPositive();
    // Le demandeur suit son remboursement depuis le détail du paiement.
    mvc.perform(get("/api/v1/paiements/" + ref).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(jsonPath("$.remboursements[0].statut").value("execute"))
        .andExpect(jsonPath("$.remboursements[0].referenceOrdre").value("OR-2026-0042"));
    // Le paiement d'origine reste payé : seul le versement en double est rendu.
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("paye");
  }

  @Test
  void toutSommeRecueSansContrepartieEstProposeeAuRemboursement() throws Exception {
    String superieur = virementDeclare(DemandesDeTest.deposee(INVESTISSEUR, "PEX"));
    long ligneSup = ligneImportee("26/11/2026;VIR " + superieur + ";6000000;C");
    decider(ligneSup, "{\"decision\":\"VALIDER\"}").andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/remboursements/" + remboursementDeLaLigne(ligneSup)).with(fiscal()))
        .andExpect(jsonPath("$.motif").value("excedent"))
        .andExpect(jsonPath("$.montantGnf").value(1_000_000));

    String inferieur = virementDeclare(DemandesDeTest.deposee(INVESTISSEUR, "PEX"));
    long ligneInf = ligneImportee("26/11/2026;VIR " + inferieur + ";4000000;C");
    decider(
            ligneInf,
            "{\"decision\":\"REJETER\",\"motif\":\"Montant reçu incomplet, à reprendre.\"}")
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/remboursements/" + remboursementDeLaLigne(ligneInf)).with(fiscal()))
        .andExpect(jsonPath("$.motif").value("autre"))
        .andExpect(jsonPath("$.montantGnf").value(4_000_000));
  }

  @Test
  void unRefusEstMotiveEtTrace() throws Exception {
    String ref = virementDeclare(DemandesDeTest.deposee(INVESTISSEUR, "PEX"));
    ligneImportee("26/11/2026;VIR " + ref + ";5000000;C");
    long doublon = ligneImportee("27/11/2026;VIR " + ref + " TER;5000000;C");
    decider(doublon, "{\"decision\":\"REMBOURSER\"}").andExpect(status().isNoContent());
    long id = remboursementDeLaLigne(doublon);
    mvc.perform(
            post("/api/v1/remboursements/" + id + "/refus")
                .with(directeur())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motif\":\"court\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/remboursements/" + id + "/refus")
                .with(directeur())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motif\":\"Versement déjà rendu par la banque émettrice.\"}"))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/remboursements/" + id).with(fiscal()))
        .andExpect(jsonPath("$.statut").value("refuse"))
        .andExpect(jsonPath("$.motifRefus").value("Versement déjà rendu par la banque émettrice."));
    mvc.perform(post("/api/v1/remboursements/" + id + "/validation").with(directeur()))
        .andExpect(status().isConflict());
  }

  @Test
  void laRegleV14GouverneLeRemboursementDesFraisSurRejet() throws Exception {
    // Règle initiale : pas de remboursement des frais d'une demande rejetée.
    long avant = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.payer(avant, "FRAIS_DOSSIER", 5_000_000);
    rejeter(avant);
    assertThat(remboursementsDeLaDemande(avant)).isZero();

    mvc.perform(get("/api/v1/parametres/remboursement").with(admin()))
        .andExpect(jsonPath("$.rembourserSiRejet").value(false))
        .andExpect(jsonPath("$.peutProposer").value(true))
        .andExpect(jsonPath("$.peutDecider").value(false));
    // L'administrateur propose, rien ne change avant la validation du Ministre.
    int version = proposer(true);
    mvc.perform(
            post("/api/v1/parametres/remboursement/versions/" + version + "/approbation")
                .with(admin()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/parametres/remboursement").with(ministre()))
        .andExpect(jsonPath("$.rembourserSiRejet").value(false))
        .andExpect(jsonPath("$.versions[0].etat").value("a_valider"));
    try {
      mvc.perform(
              post("/api/v1/parametres/remboursement/versions/" + version + "/approbation")
                  .with(ministre()))
          .andExpect(status().isOk());
      mvc.perform(get("/api/v1/parametres/remboursement").with(admin()))
          .andExpect(jsonPath("$.rembourserSiRejet").value(true));

      // La demande déjà déposée garde la règle de son dépôt.
      assertThat(remboursementsDeLaDemande(avant)).isZero();
      long apres = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
      DemandesDeTest.payer(apres, "FRAIS_DOSSIER", 5_000_000);
      rejeter(apres);
      assertThat(remboursementsDeLaDemande(apres)).isEqualTo(1);
      assertThat(statutPaiementDeLaDemande(apres)).isEqualTo("remboursement_demande");
    } finally {
      // Retour à la règle V1-4 pour les autres tests.
      int retour = proposer(false);
      mvc.perform(
              post("/api/v1/parametres/remboursement/versions/" + retour + "/approbation")
                  .with(ministre()))
          .andExpect(status().isOk());
    }
  }

  /**
   * US-04.18, décision V1-13 : le Responsable fiscal propose, le Directeur valide ou refuse ;
   * l'ordre du Trésor reste enregistré par le Responsable fiscal. Chacun voit ce qu'il doit faire.
   */
  @Test
  void leDirecteurValideLesPropositionsDuResponsableFiscal() throws Exception {
    String ref = virementDeclare(DemandesDeTest.deposee(INVESTISSEUR, "PEX"));
    ligneImportee("26/11/2026;VIR " + ref + ";5000000;C");
    long doublon = ligneImportee("27/11/2026;VIR " + ref + " QUATER;5000000;C");
    decider(doublon, "{\"decision\":\"REMBOURSER\"}").andExpect(status().isNoContent());
    long id = remboursementDeLaLigne(doublon);

    // Le Responsable fiscal a proposé : il ne valide pas, ce n'est pas à lui de le faire.
    mvc.perform(post("/api/v1/remboursements/" + id + "/validation").with(fiscal()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/remboursements/" + id).with(fiscal()))
        .andExpect(jsonPath("$.aTraiterParMoi").value(false));
    mvc.perform(get("/api/v1/remboursements/" + id).with(directeur()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.aTraiterParMoi").value(true));
    long aTraiterAvant = compteurATraiter(directeur());
    assertThat(aTraiterAvant).isPositive();

    mvc.perform(post("/api/v1/remboursements/" + id + "/validation").with(directeur()))
        .andExpect(status().isNoContent());
    assertThat(compteurATraiter(directeur())).isEqualTo(aTraiterAvant - 1);
    mvc.perform(get("/api/v1/remboursements/" + id).with(fiscal()))
        .andExpect(jsonPath("$.statut").value("valide"))
        .andExpect(jsonPath("$.aTraiterParMoi").value(true))
        .andExpect(jsonPath("$.decidePar").isNotEmpty());
    // L'ordre du Trésor : le Responsable fiscal, pas le Directeur.
    mvc.perform(
            post("/api/v1/remboursements/" + id + "/execution")
                .with(directeur())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"referenceOrdre\":\"OR-2026-0077\"}"))
        .andExpect(status().isForbidden());
    executer(id, "{\"referenceOrdre\":\"OR-2026-0077\"}").andExpect(status().isNoContent());
  }

  /** Séparation des tâches, contrôlée par l'API (PAY-018) et par la base (V34). */
  @Test
  void celuiQuiProposeUnRemboursementNeLeDecidePas() throws Exception {
    String ref = virementDeclare(DemandesDeTest.deposee(INVESTISSEUR, "PEX"));
    long id;
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO remboursement (paiement_id, motif, montant_gnf, demande_par)"
                    + " SELECT p.id, 'autre', 1000, u.id FROM paiement p, utilisateur u"
                    + " WHERE p.reference = ? AND u.keycloak_id = ?::uuid RETURNING id")) {
      ps.setString(1, ref);
      ps.setString(2, DIRECTEUR);
      ResultSet rs = ps.executeQuery();
      rs.next();
      id = rs.getLong(1);
    }
    mvc.perform(get("/api/v1/remboursements/" + id).with(directeur()))
        .andExpect(jsonPath("$.aTraiterParMoi").value(false));
    mvc.perform(post("/api/v1/remboursements/" + id + "/validation").with(directeur()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PAY-018"));

    // La base refuse aussi, même sans passer par l'API ; et un Responsable fiscal ne décide pas.
    try (Connection c = BaseDeDonneesDeTest.connexionOwner()) {
      for (String qui : new String[] {DIRECTEUR, FISCAL}) {
        try (PreparedStatement session =
            c.prepareStatement(
                "SELECT set_config('emine.user_id', id::text, false) FROM utilisateur"
                    + " WHERE keycloak_id = ?::uuid")) {
          session.setString(1, qui);
          session.executeQuery();
        }
        try (PreparedStatement maj =
            c.prepareStatement(
                "UPDATE remboursement SET statut = 'valide', valide_par = fn_current_user_id(),"
                    + " date_validation = now() WHERE id = ?")) {
          maj.setLong(1, id);
          assertThatThrownBy(maj::executeUpdate)
              .as(qui)
              .isInstanceOf(java.sql.SQLException.class)
              .hasMessageContaining(DIRECTEUR.equals(qui) ? "propose" : "Directeur");
        }
      }
    }
  }

  @Test
  void seulLeResponsableFiscalTraiteLesRemboursements() throws Exception {
    mvc.perform(get("/api/v1/remboursements").with(jeton(GUICHET, "GUICHET")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/remboursements").with(directeur())).andExpect(status().isOk());
    mvc.perform(get("/api/v1/remboursements").with(fiscal()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.compteurs.tous").isNumber());
    mvc.perform(get("/api/v1/remboursements/999999").with(fiscal()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PAY-015"));
  }

  // --- Outils -----------------------------------------------------------------------------

  private int proposer(boolean rembourser) throws Exception {
    String date = LocalDate.now(ZoneId.of("Africa/Conakry")).toString();
    String corps =
        mvc.perform(
                post("/api/v1/parametres/remboursement/versions")
                    .with(admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"rembourserSiRejet\":"
                            + rembourser
                            + ",\"dateEffet\":\""
                            + date
                            + "\",\"motif\":\"Test de la gouvernance de la règle V1-4\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(corps).path("version").asInt();
  }

  private long compteurATraiter(RequestPostProcessor qui) throws Exception {
    String corps =
        mvc.perform(get("/api/v1/remboursements").with(qui))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(corps).path("compteurs").path("aTraiter").asLong();
  }

  private static RequestPostProcessor directeur() {
    return jeton(DIRECTEUR, "DIRECTEUR");
  }

  private ResultActions executer(long id, String corps) throws Exception {
    return mvc.perform(
        post("/api/v1/remboursements/" + id + "/execution")
            .with(fiscal())
            .contentType(MediaType.APPLICATION_JSON)
            .content(corps));
  }

  private ResultActions decider(long ligne, String corps) throws Exception {
    return mvc.perform(
        post("/api/v1/encaissements/lignes/" + ligne + "/decision")
            .with(fiscal())
            .contentType(MediaType.APPLICATION_JSON)
            .content(corps));
  }

  private long ligneImportee(String ligneCsv) throws Exception {
    String csv = EN_TETE + ligneCsv + "\n";
    String corps =
        mvc.perform(
                multipart("/api/v1/releves")
                    .file(
                        new MockMultipartFile(
                            "fichier",
                            "rb" + UNIQUE.incrementAndGet() + ".csv",
                            "text/csv",
                            csv.getBytes(StandardCharsets.UTF_8)))
                    .with(fiscal()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long releve = JSON.readTree(corps).path("id").asLong();
    JsonNode lignes =
        JSON.readTree(
            mvc.perform(get("/api/v1/releves/" + releve + "/lignes").with(fiscal()))
                .andReturn()
                .getResponse()
                .getContentAsString());
    return lignes.path("elements").get(0).path("id").asLong();
  }

  /** Rejet motivé de la demande (agent interne, comme au guichet). */
  private static void rejeter(long demande) throws Exception {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "UPDATE demande SET statut = 'rejetee', motif_rejet = 'Dossier non conforme au"
                    + " cahier des charges' WHERE id = ?")) {
      ps.setLong(1, demande);
      ps.executeUpdate();
    }
  }

  private static long remboursementDeLaLigne(long ligne) throws Exception {
    return entier("SELECT id FROM remboursement WHERE ligne_releve_id = " + ligne);
  }

  private static long remboursementsDeLaDemande(long demande) throws Exception {
    return entier(
        "SELECT count(*) FROM remboursement r JOIN paiement p ON p.id = r.paiement_id"
            + " WHERE p.demande_id = "
            + demande);
  }

  private static String statutPaiementDeLaDemande(long demande) throws Exception {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement("SELECT statut::text FROM paiement WHERE demande_id = ?")) {
      ps.setLong(1, demande);
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getString(1);
    }
  }

  private static long entier(String sql) throws Exception {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps = c.prepareStatement(sql)) {
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getLong(1);
    }
  }

  private String virementDeclare(long demande) throws Exception {
    String ref =
        JSON.readTree(
                mvc.perform(
                        post("/api/v1/demandes/" + demande + "/paiements")
                            .with(jeton(INVESTISSEUR, "INVESTISSEUR"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"moyen\":\"VIREMENT\"}"))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("paiement")
            .path("reference")
            .asString();
    mvc.perform(
            multipart("/api/v1/paiements/" + ref + "/virement")
                .file(new MockMultipartFile("fichier", "ordre.pdf", "application/pdf", PDF))
                .with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(status().isOk());
    return ref;
  }

  private static RequestPostProcessor fiscal() {
    return jeton(FISCAL, "FISCAL");
  }

  private static RequestPostProcessor admin() {
    return jeton(ADMIN_TECH, "ADMIN_TECH");
  }

  private static RequestPostProcessor ministre() {
    return jeton(MINISTRE, "MINISTRE");
  }
}
