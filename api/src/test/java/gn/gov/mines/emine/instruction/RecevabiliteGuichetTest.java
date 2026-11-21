package gn.gov.mines.emine.instruction;

import static gn.gov.mines.emine.support.Personas.DIRECTEUR;
import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
 * US-05.1 : l'agent du guichet déclare une demande recevable seulement si les pièces obligatoires
 * sont fournies <b>et</b> les frais payés ; il peut la déclarer non recevable avec un motif ; seul
 * un profil ayant le droit « instruire » le peut ; la file du guichet se filtre sur les frais.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecevabiliteGuichetTest extends TestIntegration {

  @Autowired MockMvc mvc;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @Test
  void piecesCompletesEtFraisPayesLaDemandeDevientRecevable() throws Exception {
    long id = pexComplete(true);
    mvc.perform(get(adresse(id)).with(guichet()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.aExaminer").value(true))
        .andExpect(jsonPath("$.fraisPayes").value(true))
        .andExpect(jsonPath("$.piecesManquantes.length()").value(0))
        .andExpect(jsonPath("$.peutEtreRecevable").value(true));
    declarer(id, true, null, guichet())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statut").value("recevable"));
    String[] etat = DemandesDeTest.statutEtMotif(id);
    assertThat(etat[0]).isEqualTo("recevable");
    assertThat(etat[2]).as("date de recevabilité").isEqualTo("true");
    assertThat(DemandesDeTest.auteurDernierStatut(id)).isEqualTo(GUICHET);
  }

  @Test
  void sansFraisPayesLaRecevabiliteEstRefuseeAvecDem006() throws Exception {
    long id = pexComplete(false);
    mvc.perform(get(adresse(id)).with(guichet()))
        .andExpect(jsonPath("$.fraisPayes").value(false))
        .andExpect(jsonPath("$.peutEtreRecevable").value(false));
    declarer(id, true, null, guichet())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEM-006"));
    assertThat(DemandesDeTest.statutEtMotif(id)[0]).isEqualTo("deposee");
  }

  @Test
  void unePieceObligatoireSansFichierEstRefuseeAvecDem007() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.piece(id, "Étude de faisabilité");
    DemandesDeTest.payer(id, "FRAIS_DOSSIER", 5_000_000);
    mvc.perform(get(adresse(id)).with(guichet()))
        .andExpect(
            jsonPath("$.piecesManquantes", containsInAnyOrder("EIES", "Plan de restauration")));
    declarer(id, true, null, guichet())
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("DEM-007"))
        .andExpect(
            jsonPath(
                "$.details.piecesManquantes", containsInAnyOrder("EIES", "Plan de restauration")));
    assertThat(DemandesDeTest.statutEtMotif(id)[0]).isEqualTo("deposee");
  }

  @Test
  void nonRecevableAvecMotifLaDemandeEstRejeteeEtLeMotifConserve() throws Exception {
    long id = pexComplete(false);
    declarer(id, false, "  L'étude de faisabilité n'est pas signée.  ", guichet())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statut").value("rejetee"));
    String[] etat = DemandesDeTest.statutEtMotif(id);
    assertThat(etat[0]).isEqualTo("rejetee");
    assertThat(etat[1]).isEqualTo("L'étude de faisabilité n'est pas signée.");
    mvc.perform(get("/api/v1/demandes/" + id).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(jsonPath("$.statut").value("rejetee"))
        .andExpect(jsonPath("$.motifRejet").value("L'étude de faisabilité n'est pas signée."));
  }

  @Test
  void unMotifAbsentOuTropCourtEstRefuseAvecVal001() throws Exception {
    long id = pexComplete(true);
    declarer(id, false, null, guichet())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"))
        .andExpect(jsonPath("$.details.champs[0].champ").value("motif"));
    declarer(id, false, "   Court   ", guichet())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"));
    assertThat(DemandesDeTest.statutEtMotif(id)[0]).isEqualTo("deposee");
  }

  @Test
  void uneDemandeDejaRecevableNePeutPlusEtreExamineeDem001() throws Exception {
    long id = pexComplete(true);
    declarer(id, true, null, guichet()).andExpect(status().isOk());
    mvc.perform(get(adresse(id)).with(guichet())).andExpect(jsonPath("$.aExaminer").value(false));
    declarer(id, false, "Changement d'avis après coup.", guichet())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEM-001"));
    assertThat(DemandesDeTest.statutEtMotif(id)[0]).isEqualTo("recevable");
  }

  @Test
  void seulUnProfilQuiInstruitPeutDeclarerLaRecevabilitePerm001() throws Exception {
    long id = pexComplete(true);
    declarer(id, true, null, jeton(INVESTISSEUR, "INVESTISSEUR"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERM-001"));
    declarer(id, true, null, jeton(DIRECTEUR, "DIRECTEUR"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERM-001"));
    mvc.perform(get(adresse(id)).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(status().isForbidden());
    assertThat(DemandesDeTest.statutEtMotif(id)[0]).isEqualTo("deposee");
  }

  @Test
  void uneDemandeInconnueDonneDem002() throws Exception {
    declarer(999_999_999L, true, null, guichet())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DEM-002"));
  }

  @Test
  void laFileDuGuichetSeFiltreSurLesFraisPayes() throws Exception {
    long payee = pexComplete(true);
    long nonPayee = pexComplete(false);
    mvc.perform(
            get("/api/v1/demandes")
                .param("statut", "deposee")
                .param("fraisPayes", "true")
                .param("taille", "100")
                .with(guichet()))
        .andExpect(jsonPath("$.elements[*].id", hasItem((int) payee)))
        .andExpect(jsonPath("$.elements[*].id", not(hasItem((int) nonPayee))));
    mvc.perform(
            get("/api/v1/demandes")
                .param("statut", "deposee")
                .param("fraisPayes", "false")
                .param("taille", "100")
                .with(guichet()))
        .andExpect(jsonPath("$.elements[*].id", hasItem((int) nonPayee)))
        .andExpect(jsonPath("$.elements[*].id", not(hasItem((int) payee))));
  }

  /** PEX déposé par l'investisseur avec ses trois pièces obligatoires, frais payés ou non. */
  private static long pexComplete(boolean fraisPayes) throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    for (String p : new String[] {"Étude de faisabilité", "EIES", "Plan de restauration"}) {
      DemandesDeTest.piece(id, p);
    }
    if (fraisPayes) {
      DemandesDeTest.payer(id, "FRAIS_DOSSIER", 5_000_000);
    }
    return id;
  }

  private ResultActions declarer(long id, boolean recevable, String motif, RequestPostProcessor qui)
      throws Exception {
    String corps =
        motif == null
            ? "{\"recevable\":" + recevable + "}"
            : "{\"recevable\":" + recevable + ",\"motif\":\"" + motif.replace("\"", "\\\"") + "\"}";
    return mvc.perform(
        post(adresse(id)).with(qui).contentType(MediaType.APPLICATION_JSON).content(corps));
  }

  private static String adresse(long id) {
    return "/api/v1/demandes/" + id + "/recevabilite";
  }

  private static RequestPostProcessor guichet() {
    return jeton(GUICHET, "GUICHET");
  }
}
