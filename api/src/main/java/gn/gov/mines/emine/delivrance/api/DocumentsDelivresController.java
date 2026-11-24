package gn.gov.mines.emine.delivrance.api;

import gn.gov.mines.emine.delivrance.application.ServiceDelivrance;
import gn.gov.mines.emine.delivrance.infrastructure.ProprietesDelivrance;
import gn.gov.mines.emine.shared.pagination.Pagination;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * « Documents délivrés » (espace de l'investisseur, cahier des charges) : les documents signés des
 * demandes visibles du profil connecté, avec leur validité et leur page de vérification.
 */
@RestController
@Tag(name = "Délivrance")
class DocumentsDelivresController {

  private final ServiceDelivrance service;
  private final ProprietesDelivrance proprietes;

  DocumentsDelivresController(ServiceDelivrance service, ProprietesDelivrance proprietes) {
    this.service = service;
    this.proprietes = proprietes;
  }

  @GetMapping("/api/v1/documents-delivres")
  @Operation(
      operationId = "listerMesDocuments",
      summary =
          "Documents délivrés des demandes visibles du profil connecté (les siennes pour un"
              + " usager), du plus récent au plus ancien, paginés")
  PageDocumentsDto lister(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer taille) {
    var p = service.listerVisibles(Pagination.de(page, taille));
    return new PageDocumentsDto(
        p.elements().stream()
            .map(
                l ->
                    new LigneDocumentDto(
                        l.document().numero(),
                        l.document().codeType(),
                        l.document().valide(),
                        l.document().delivreLe(),
                        l.document().demandeId(),
                        l.numeroDemande(),
                        proprietes.urlVerification(l.document().codeVerification())))
            .toList(),
        p.page(),
        p.taille(),
        p.total(),
        p.totalPages());
  }

  /**
   * Un document délivré.
   *
   * @param valide {@code false} après révocation
   * @param urlVerification page publique de vérification (QR)
   */
  record LigneDocumentDto(
      String numero,
      String codeType,
      boolean valide,
      Instant delivreLe,
      long demandeId,
      @Schema(nullable = true) String numeroDemande,
      String urlVerification) {}

  record PageDocumentsDto(
      List<LigneDocumentDto> elements, int page, int taille, long total, int totalPages) {}
}
