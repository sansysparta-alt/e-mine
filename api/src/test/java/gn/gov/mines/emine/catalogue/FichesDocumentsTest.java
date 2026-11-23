package gn.gov.mines.emine.catalogue;

import static gn.gov.mines.emine.support.Personas.ADMIN_TECH;
import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.MINISTRE;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-02.2 et US-02.4 (V18) : la fiche d'un document (pièces, délai, signataire, avis requis,
 * activation) est modifiée par une version que propose l'administrateur et qu'approuve le Ministre
 * (cahier des charges : « paramétrés par l'Administrateur et approuvés par le Ministère ») ; chaque
 * modification est tracée ; une demande garde la fiche de son dépôt. Documents KIM et CCC, que les
 * autres tests n'utilisent pas ; le catalogue est restauré à la fin.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FichesDocumentsTest extends TestIntegration {

  private static final String FICHES = "/api/v1/fiches-documents";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired Catalogue catalogue;
  final JsonMapper json = JsonMapper.builder().build();

  final RequestPostProcessor admin = jeton(ADMIN_TECH, "ADMIN_TECH");
  final RequestPostProcessor ministre = jeton(MINISTRE, "MINISTRE");

  long kimAvant;

  @BeforeAll
  void donnees() throws Exception {
    Personas.charger();
    kimAvant = DemandesDeTest.deposee(INVESTISSEUR, "KIM");
  }

  @AfterAll
  void restaurer() throws Exception {
    // Remet KIM et CCC comme au catalogue initial, par le même circuit.
    for (String code : List.of("KIM", "CCC")) {
      var v1 =
          json.readTree(
                  mvc.perform(get(FICHES + "/" + code).with(admin))
                      .andReturn()
                      .getResponse()
                      .getContentAsString())
              .get("historique");
      var initiale = v1.get(v1.size() - 1);
      var propo =
          Map.of(
              "libelleFr", initiale.get("libelleFr").asString(),
              "pieces", json.convertValue(initiale.get("pieces"), List.class),
              "delaiJours", initiale.get("delaiJours").asInt(),
              "instruitPar", initiale.get("instruitPar").asString(),
              "signataire", initiale.get("signataire").asString(),
              "avis", List.of(),
              "actif", true,
              "motif", "Retour au catalogue initial après les tests");
      var r =
          mvc.perform(
                  post(FICHES + "/" + code + "/versions")
                      .with(admin)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(json.writeValueAsString(propo)))
              .andReturn()
              .getResponse();
      if (r.getStatus() == 201) {
        int n = json.readTree(r.getContentAsString()).get("numero").asInt();
        mvc.perform(post(FICHES + "/" + code + "/versions/" + n + "/approbation").with(ministre));
      }
    }
  }

  @Test
  @Order(1)
  void seulsLAdministrateurEtLeMinistreConsultentLAdministrationDuCatalogue() throws Exception {
    mvc.perform(get(FICHES).with(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(15))
        .andExpect(jsonPath("$.compteurs.TOUS").value(15));
    mvc.perform(get(FICHES).with(ministre)).andExpect(status().isOk());
    mvc.perform(get(FICHES).with(jeton(GUICHET, "GUICHET")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERM-001"));
    mvc.perform(get(FICHES).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(2)
  void lAdministrateurProposeEtRienNeChangeAvantLApprobation() throws Exception {
    proposer(admin, "KIM", kim(4, "Directeur général", List.of(), true, "Délai porté à 4 jours"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.numero").value(2));

    mvc.perform(get(FICHES + "/KIM").with(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.proposition.numero").value(2))
        .andExpect(jsonPath("$.proposition.statut").value("a_valider"))
        .andExpect(jsonPath("$.proposition.proposeePar").isNotEmpty())
        .andExpect(
            jsonPath("$.proposition.changements[?(@.champ == 'delaiJours')].avant").value("2"))
        .andExpect(
            jsonPath("$.proposition.changements[?(@.champ == 'delaiJours')].apres").value("4"))
        .andExpect(
            jsonPath("$.proposition.changements[?(@.champ == 'signataire')].apres")
                .value("Directeur général"))
        .andExpect(jsonPath("$.action").value("ATTENDRE"))
        .andExpect(jsonPath("$.peutProposer").value(false));
    // Le Ministre voit la proposition comme une action à faire.
    mvc.perform(get(FICHES).param("filtre", "A_VALIDER").with(ministre))
        .andExpect(jsonPath("$.elements[0].code").value("KIM"))
        .andExpect(jsonPath("$.elements[0].action").value("DECIDER"))
        .andExpect(jsonPath("$.compteurs.A_VALIDER").value(1));
    // Le catalogue public n'a pas changé.
    mvc.perform(get("/api/v1/types-documents/KIM")).andExpect(jsonPath("$.delaiJours").value(2));
  }

  @Test
  @Order(3)
  void uneSeuleProposition_ouverteParDocument_etLeMinistreNePropose_pas() throws Exception {
    proposer(admin, "KIM", kim(5, "Directeur national", List.of(), true, "Autre proposition"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DOC-008"));
    proposer(ministre, "CCC", ccc(5, List.of(), "Proposition du Ministre"))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(4)
  void seulLeMinistreApprouve() throws Exception {
    mvc.perform(post(FICHES + "/KIM/versions/2/approbation").with(admin))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(5)
  void lApprobationAppliqueLaFicheEtTraceAncienneEtNouvelleValeur() throws Exception {
    mvc.perform(post(FICHES + "/KIM/versions/2/approbation").with(ministre))
        .andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/types-documents/KIM"))
        .andExpect(jsonPath("$.delaiJours").value(4))
        .andExpect(jsonPath("$.signataire").value("Directeur général"));
    mvc.perform(get(FICHES + "/KIM").with(admin))
        .andExpect(jsonPath("$.enVigueur.numero").value(2))
        .andExpect(jsonPath("$.enVigueur.decideePar").isNotEmpty())
        .andExpect(jsonPath("$.proposition").doesNotExist())
        .andExpect(jsonPath("$.historique[0].statut").value("publiee"))
        .andExpect(jsonPath("$.historique[1].statut").value("remplacee"))
        .andExpect(jsonPath("$.peutProposer").value(true));
    // Journal d'audit : la fiche, avec l'ancienne et la nouvelle valeur, au nom du Ministre.
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT j.anciennes_valeurs->>'delai_jours', j.nouvelles_valeurs->>'delai_jours',"
                    + " p.code FROM journal_audit j JOIN utilisateur u ON u.id = j.utilisateur_id"
                    + " JOIN profil p ON p.id = u.profil_id WHERE j.table_nom = 'type_document'"
                    + " AND j.nouvelles_valeurs->>'code' = 'KIM' ORDER BY j.id DESC LIMIT 1")) {
      ResultSet rs = ps.executeQuery();
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).isEqualTo("2");
      assertThat(rs.getString(2)).isEqualTo("4");
      assertThat(rs.getString(3)).isEqualTo("MINISTRE");
    }
    mvc.perform(post(FICHES + "/KIM/versions/2/approbation").with(ministre))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DOC-009"));
  }

  @Test
  @Order(6)
  void uneDemandeGardeLeDelaiEtLeSignataireDeSonDepot() throws Exception {
    long kimApres = DemandesDeTest.deposee(INVESTISSEUR, "KIM");
    assertThat(fiche(kimAvant)).containsExactly(2, "DIRECTEUR");
    assertThat(fiche(kimApres)).containsExactly(4, "DIRECTEUR");
    assertThat(catalogue.pourDemande("KIM", version(kimAvant)))
        .get()
        .extracting(TypeDocument::delaiJours, TypeDocument::signataire)
        .containsExactly(2, "Directeur national");
  }

  @Test
  @Order(7)
  void lesAvisRequisSontCeuxDeLaFicheAuDepot() throws Exception {
    long cccAvant = DemandesDeTest.deposee(INVESTISSEUR, "CCC");
    int n = numero(proposer(admin, "CCC", ccc(3, List.of("DNM"), "Avis de la DNM requis")));
    mvc.perform(post(FICHES + "/CCC/versions/" + n + "/approbation").with(ministre))
        .andExpect(status().isNoContent());
    long cccApres = DemandesDeTest.deposee(INVESTISSEUR, "CCC");
    assertThat(DemandesDeTest.avisRequis(cccAvant)).isZero();
    assertThat(DemandesDeTest.avisRequis(cccApres)).isEqualTo(1);
    mvc.perform(get("/api/v1/types-documents/CCC"))
        .andExpect(jsonPath("$.avisRequis[0].profil").value("DNM"));
  }

  @Test
  @Order(8)
  void uneProposition_sansChangement_estRefusee_etLesChampsSontControles() throws Exception {
    proposer(admin, "CCC", ccc(3, List.of("DNM"), "Aucune modification réelle"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("DOC-010"));
    var piecesAvecVirgule =
        new java.util.HashMap<>(ccc(4, List.of(), "Pièce mal formée pour le test"));
    piecesAvecVirgule.put("pieces", List.of("Numéro, identité"));
    proposer(admin, "CCC", piecesAvecVirgule)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"));
    var signataireInconnu = new java.util.HashMap<>(ccc(4, List.of(), "Signataire inconnu ici"));
    signataireInconnu.put("signataire", "Gouverneur");
    proposer(admin, "CCC", signataireInconnu).andExpect(jsonPath("$.code").value("VAL-001"));
    var avisGuichet = new java.util.HashMap<>(ccc(4, List.of("GUICHET"), "Avis du guichet"));
    proposer(admin, "CCC", avisGuichet).andExpect(jsonPath("$.code").value("VAL-001"));
  }

  @Test
  @Order(9)
  void leMinistreRefuseAvecUnMotif_etLAdministrateurPeutRetirer() throws Exception {
    int n = numero(proposer(admin, "CCC", ccc(10, List.of("DNM"), "Délai porté à 10 jours")));
    mvc.perform(
            post(FICHES + "/CCC/versions/" + n + "/refus")
                .with(ministre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motif\":\"court\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post(FICHES + "/CCC/versions/" + n + "/refus")
                .with(ministre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motif\":\"Délai trop long pour une copie certifiée\"}"))
        .andExpect(status().isNoContent());
    mvc.perform(get(FICHES + "/CCC").with(admin))
        .andExpect(jsonPath("$.enVigueur.delaiJours").value(3))
        .andExpect(jsonPath("$.historique[0].statut").value("refusee"))
        .andExpect(
            jsonPath("$.historique[0].motifRefus")
                .value("Délai trop long pour une copie certifiée"));

    int m = numero(proposer(admin, "CCC", ccc(4, List.of("DNM"), "Délai porté à 4 jours")));
    mvc.perform(post(FICHES + "/CCC/versions/" + m + "/retrait").with(ministre))
        .andExpect(status().isForbidden());
    mvc.perform(post(FICHES + "/CCC/versions/" + m + "/retrait").with(admin))
        .andExpect(status().isNoContent());
    mvc.perform(get(FICHES + "/CCC").with(admin))
        .andExpect(jsonPath("$.historique[0].statut").value("retiree"))
        .andExpect(jsonPath("$.enVigueur.delaiJours").value(3));
  }

  @Test
  @Order(10)
  void unDocumentDesactiveQuitteLeCatalogue_etSesDemandesEnCoursVontASonTerme()
      throws Exception {
    long brouillon = DemandesDeTest.brouillon(INVESTISSEUR, "KIM");
    int n =
        numero(
            proposer(
                admin,
                "KIM",
                kim(
                    4,
                    "Directeur général",
                    List.of(),
                    false,
                    "Retiré du catalogue pour les tests")));
    mvc.perform(post(FICHES + "/KIM/versions/" + n + "/approbation").with(ministre))
        .andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/types-documents"))
        .andExpect(jsonPath("$[*].code", not(hasItem("KIM"))));
    mvc.perform(get(FICHES).param("filtre", "INACTIFS").with(admin))
        .andExpect(jsonPath("$.elements[0].code").value("KIM"));
    // Un brouillon ne se dépose plus ; une demande déposée garde sa fiche.
    assertThatThrownBy(() -> DemandesDeTest.changerStatut(brouillon, "deposee"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("retiré du catalogue");
    assertThat(catalogue.pourDemande("KIM", version(kimAvant))).isPresent();
    assertThat(catalogue.trouver("KIM")).get().extracting(TypeDocument::actif).isEqualTo(false);
  }

  @Test
  @Order(11)
  void lApplicationNeModifiePlusLaFicheDirectement() {
    assertThatThrownBy(
            () -> jdbc.update("UPDATE type_document SET delai_jours = 9 WHERE code = 'KIM'"))
        .rootCause()
        .hasMessageContaining("permission denied");
    // Même en propriétaire du schéma, une version proposée ne se modifie pas : on en propose une
    // nouvelle.
    assertThatThrownBy(
            () -> {
              try (Connection c = BaseDeDonneesDeTest.connexionOwner()) {
                c.createStatement()
                    .executeUpdate(
                        "UPDATE version_document SET delai_jours = 9 WHERE numero = 1"
                            + " AND type_document_id = (SELECT id FROM type_document"
                            + " WHERE code = 'KIM')");
              }
            })
        .hasMessageContaining("ne se modifie pas");
  }

  @Test
  @Order(12)
  void lesReferencesDuFormulaire() throws Exception {
    mvc.perform(get(FICHES + "/references").with(admin))
        .andExpect(jsonPath("$.signataires[0]").value("Ministre"))
        .andExpect(jsonPath("$.profilsAvis[*].code", hasItem("GEOLOGUE")))
        .andExpect(jsonPath("$.profilsAvis[*].code", not(hasItem("GUICHET"))));
  }

  private ResultActions proposer(RequestPostProcessor qui, String code, Map<String, Object> p)
      throws Exception {
    return mvc.perform(
        post(FICHES + "/" + code + "/versions")
            .with(qui)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(p)));
  }

  private int numero(ResultActions r) throws Exception {
    return json.readTree(
            r.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
        .get("numero")
        .asInt();
  }

  private static Map<String, Object> kim(
      int delai, String signataire, List<String> avis, boolean actif, String motif) {
    return Map.of(
        "libelleFr", "Certificat de conformité export (diamant)",
        "pieces", List.of("Certificat d'expertise", "Preuve d'origine licite"),
        "delaiJours", delai,
        "instruitPar", "BNE + Brigade",
        "signataire", signataire,
        "avis", avis,
        "actif", actif,
        "motif", motif);
  }

  private static Map<String, Object> ccc(int delai, List<String> avis, String motif) {
    return Map.of(
        "libelleFr",
        "Copie certifiée conforme de titre",
        "pieces",
        List.of("Numéro du titre", "Identité du titulaire"),
        "delaiJours",
        delai,
        "instruitPar",
        "Guichet unique",
        "signataire",
        "Directeur national",
        "avis",
        avis,
        "actif",
        true,
        "motif",
        motif);
  }

  private static List<Object> fiche(long demande) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT d.delai_jours, p.code FROM demande d"
                    + " JOIN profil p ON p.id = d.signataire_profil_id WHERE d.id = ?")) {
      ps.setLong(1, demande);
      ResultSet rs = ps.executeQuery();
      rs.next();
      return List.of(rs.getInt(1), rs.getString(2));
    }
  }

  private static Long version(long demande) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement("SELECT version_document_id FROM demande WHERE id = ?")) {
      ps.setLong(1, demande);
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getLong(1);
    }
  }
}
