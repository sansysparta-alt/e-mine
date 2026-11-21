package gn.gov.mines.emine.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.catalogue.infrastructure.TypeDocumentRepositoryTestAcces;
import gn.gov.mines.emine.support.TestIntegration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * US-02.1 : le catalogue des 15 documents est public, avec les libellés de l'application (D2), le
 * délai, les pièces requises, le signataire paramétré (D4) et les tarifs en GNF entiers.
 */
@AutoConfigureMockMvc
class CatalogueTest extends TestIntegration {

  @Autowired MockMvc mvc;
  @Autowired Catalogue catalogue;

  @Test
  void leCatalogueEstPublicEtListeLesQuinzeDocuments() throws Exception {
    mvc.perform(get("/api/v1/types-documents"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(15)))
        .andExpect(jsonPath("$[0].code").value("CAR"))
        .andExpect(jsonPath("$[0].famille.libelle").value("Titres miniers"));
  }

  @Test
  void unDocumentDonneDelaiPiecesSignataireEtTarifs() throws Exception {
    mvc.perform(get("/api/v1/types-documents/PEX"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.libelleFr").value("Permis d'exploitation"))
        .andExpect(jsonPath("$.delaiJours").value(60))
        .andExpect(
            jsonPath("$.piecesRequises")
                .value(
                    org.hamcrest.Matchers.contains(
                        "Étude de faisabilité", "EIES", "Plan de restauration")))
        .andExpect(jsonPath("$.signataire").value("Ministre"))
        .andExpect(jsonPath("$.tarifs.fraisDossierGnf").value(5_000_000))
        .andExpect(jsonPath("$.tarifs.droitsGnf").value(15_000_000))
        .andExpect(jsonPath("$.tarifs.attestationGnf").doesNotExist())
        .andExpect(jsonPath("$.tarifs.aPayerAuDepotGnf").value(5_000_000));
  }

  @Test
  void uneAttestationSePaieEnUneFoisAuDepot() throws Exception {
    mvc.perform(get("/api/v1/types-documents/EXC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tarifs.fraisDossierGnf").doesNotExist())
        .andExpect(jsonPath("$.tarifs.aPayerAuDepotGnf").value(200_000));
  }

  @Test
  void unCodeInconnuDonneSys002() throws Exception {
    mvc.perform(get("/api/v1/types-documents/ZZZ"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SYS-002"));
  }

  @Test
  void leSignataireVientDuCatalogueEtNonDuCode() {
    // D4 : chaque document porte son signataire paramétré.
    assertThat(catalogue.lister()).allSatisfy(t -> assertThat(t.signataire()).isNotBlank());
    assertThat(catalogue.trouverActif("COR"))
        .get()
        .extracting(TypeDocument::signataire)
        .isEqualTo("Directeur national");
  }

  @Test
  void lesPiecesClesSontDecoupees() {
    assertThat(TypeDocumentRepositoryTestAcces.pieces(" statuts ,  capacités,, carte "))
        .containsExactly("Statuts", "Capacités", "Carte");
    assertThat(TypeDocumentRepositoryTestAcces.pieces(null)).isEmpty();
  }
}
