package gn.gov.mines.emine.catalogue.api;

import gn.gov.mines.emine.catalogue.Catalogue;
import gn.gov.mines.emine.catalogue.TypeDocument;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catalogue public des 15 documents (US-02.1) : délais, pièces requises, signataire et tarifs.
 * Accessible sans connexion (site public et formulaire de demande).
 */
@RestController
@RequestMapping("/api/v1/types-documents")
@Tag(name = "Catalogue")
class CatalogueController {

  private final Catalogue catalogue;

  CatalogueController(Catalogue catalogue) {
    this.catalogue = catalogue;
  }

  @GetMapping
  @Operation(operationId = "listerTypesDocuments", summary = "Documents du catalogue (public)")
  List<TypeDocumentDto> lister() {
    return catalogue.lister().stream().map(TypeDocumentDto::depuis).toList();
  }

  @GetMapping("/{code}")
  @Operation(operationId = "lireTypeDocument", summary = "Un document du catalogue (public)")
  TypeDocumentDto lire(@PathVariable String code) {
    return catalogue
        .trouverActif(code)
        .map(TypeDocumentDto::depuis)
        .orElseThrow(() -> new ErreurMetier(CodeErreur.SYS_002, Map.of("code", code)));
  }

  /**
   * Document du catalogue tel que publié par l'API.
   *
   * @param piecesRequisesEn pièces en anglais, dans le même ordre (vide : afficher le français)
   * @param instruitParEn service instructeur en anglais ({@code null} : afficher le français)
   */
  record TypeDocumentDto(
      String code,
      String libelleFr,
      @Schema(nullable = true) String libelleEn,
      Famille famille,
      int delaiJours,
      boolean renouvelable,
      List<String> piecesRequises,
      String instruitPar,
      String signataire,
      Tarifs tarifs,
      List<AvisRequis> avisRequis,
      List<String> piecesRequisesEn,
      @Schema(nullable = true) String instruitParEn) {

    record Famille(String code, String libelle) {}

    /** Avis technique motivé requis avant la décision (US-05.3), dans l'ordre du catalogue. */
    record AvisRequis(String profil, String libelle) {}

    /** Montants en GNF entiers ; {@code null} si le document n'a pas ce tarif. */
    record Tarifs(
        @Schema(nullable = true) Long fraisDossierGnf,
        @Schema(nullable = true) Long droitsGnf,
        @Schema(nullable = true) Long attestationGnf,
        @Schema(nullable = true) Long aPayerAuDepotGnf) {}

    static TypeDocumentDto depuis(TypeDocument t) {
      return new TypeDocumentDto(
          t.code(),
          t.libelleFr(),
          t.libelleEn(),
          new Famille(t.famille().code(), t.famille().libelle()),
          t.delaiJours(),
          t.renouvelable(),
          t.piecesRequises(),
          t.instruitPar(),
          t.signataire(),
          new Tarifs(
              t.tarifs().fraisDossierGnf(),
              t.tarifs().droitsGnf(),
              t.tarifs().attestationGnf(),
              t.tarifs().aPayerAuDepotGnf()),
          t.avisRequis().stream().map(a -> new AvisRequis(a.profilCode(), a.libelle())).toList(),
          t.piecesRequisesEn(),
          t.instruitParEn());
    }
  }
}
