package gn.gov.mines.emine.delivrance.api;

import gn.gov.mines.emine.delivrance.application.ServiceDelivrance;
import gn.gov.mines.emine.delivrance.infrastructure.DelivranceRepository.Validite;
import gn.gov.mines.emine.delivrance.infrastructure.ProprietesDelivrance;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.pagination.Pagination;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
          "Documents délivrés des demandes visibles du profil connecté (les siens pour un"
              + " usager), du plus récent au plus ancien, paginés ; recherche tolérante (numéros,"
              + " libellé) et filtre de validité, avec le nombre de documents de chaque filtre")
  PageDocumentsDto lister(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer taille,
      @RequestParam(required = false) String recherche,
      @Parameter(description = "valides ou revoques ; absent : tous")
          @RequestParam(required = false)
          String validite) {
    var resultat =
        service.listerVisibles(Pagination.de(page, taille), recherche, validite(validite));
    var p = resultat.page();
    var c = resultat.compteurs();
    return new PageDocumentsDto(
        p.elements().stream()
            .map(
                l ->
                    new LigneDocumentDto(
                        l.document().numero(),
                        l.document().codeType(),
                        l.document().valide(),
                        l.document().delivreLe(),
                        l.document().revoqueLe(),
                        l.document().motifInvalidation(),
                        l.document().demandeId(),
                        l.numeroDemande(),
                        proprietes.urlVerification(l.document().codeVerification())))
            .toList(),
        p.page(),
        p.taille(),
        p.total(),
        p.totalPages(),
        new CompteursDto(c.tous(), c.valides(), c.revoques()));
  }

  private static Validite validite(String valeur) {
    if (valeur == null || valeur.isBlank()) {
      return null;
    }
    return switch (valeur) {
      case "valides" -> Validite.VALIDES;
      case "revoques" -> Validite.REVOQUES;
      default ->
          throw new ErreurMetier(
              CodeErreur.VAL_001,
              Map.of(
                  "champs",
                  List.of(
                      Map.of(
                          "champ",
                          "validite",
                          "message",
                          "Valeur attendue : valides ou revoques."))));
    };
  }

  /**
   * Un document délivré.
   *
   * @param valide {@code false} après révocation
   * @param revoqueLe date de la révocation, ou {@code null}
   * @param motifRevocation motif de la révocation, ou {@code null}
   * @param urlVerification page publique de vérification (QR)
   */
  record LigneDocumentDto(
      String numero,
      String codeType,
      boolean valide,
      Instant delivreLe,
      @Schema(nullable = true) Instant revoqueLe,
      @Schema(nullable = true) String motifRevocation,
      long demandeId,
      @Schema(nullable = true) String numeroDemande,
      String urlVerification) {}

  /** Nombre de documents de chaque filtre de validité, pour la recherche en cours. */
  record CompteursDto(long tous, long valides, long revoques) {}

  record PageDocumentsDto(
      List<LigneDocumentDto> elements,
      int page,
      int taille,
      long total,
      int totalPages,
      CompteursDto compteurs) {}
}
