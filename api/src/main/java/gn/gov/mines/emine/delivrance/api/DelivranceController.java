package gn.gov.mines.emine.delivrance.api;

import gn.gov.mines.emine.delivrance.application.ServiceDelivrance;
import gn.gov.mines.emine.delivrance.application.ServiceDelivrance.Contenu;
import gn.gov.mines.emine.delivrance.application.ServiceDelivrance.EtatDelivrance;
import gn.gov.mines.emine.delivrance.domaine.DocumentDelivre;
import gn.gov.mines.emine.delivrance.infrastructure.ProprietesDelivrance;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Délivrance (US-06.3, US-06.4) : état, « Signer et délivrer » par le signataire du catalogue
 * (droit « signer » et D4), puis consultation du document délivré par qui voit la demande (RLS).
 */
@RestController
@RequestMapping("/api/v1/demandes/{id}")
@Tag(name = "Délivrance")
class DelivranceController {

  private final ServiceDelivrance service;
  private final ProprietesDelivrance proprietes;

  DelivranceController(ServiceDelivrance service, ProprietesDelivrance proprietes) {
    this.service = service;
    this.proprietes = proprietes;
  }

  @GetMapping("/delivrance")
  @Operation(
      operationId = "lireEtatDelivrance",
      summary = "La demande est-elle prête, et l'utilisateur peut-il signer et délivrer ?")
  EtatDelivranceDto etat(@PathVariable long id) {
    EtatDelivrance e = service.etat(id);
    return new EtatDelivranceDto(e.aDelivrer(), e.peutDelivrer(), e.signataire());
  }

  @PostMapping("/delivrance")
  @PreAuthorize("hasAuthority('PERM_SIGNER')")
  @Operation(
      operationId = "delivrer",
      summary =
          "Signer et délivrer : PDF/A avec QR, signature PAdES, archivage, registre, demande"
              + " délivrée")
  DocumentDelivreDto delivrer(@PathVariable long id) {
    return dto(service.delivrer(id));
  }

  @GetMapping("/document")
  @Operation(operationId = "lireDocumentDelivre", summary = "Document délivré de la demande")
  DocumentDelivreDto document(@PathVariable long id) {
    return dto(service.lire(id));
  }

  @GetMapping(value = "/document/contenu", produces = MediaType.APPLICATION_PDF_VALUE)
  @Operation(
      operationId = "telechargerDocumentDelivre",
      summary = "PDF signé du document délivré (à afficher dans le visualiseur : inline=true)",
      responses =
          @ApiResponse(
              responseCode = "200",
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_PDF_VALUE,
                      schema = @Schema(type = "string", format = "binary"))))
  ResponseEntity<InputStreamResource> contenu(
      @PathVariable long id, @RequestParam(defaultValue = "false") boolean inline) {
    Contenu c = service.contenu(id);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(c.document().numero() + ".pdf")
                .build()
                .toString())
        .header("X-Content-Type-Options", "nosniff")
        .cacheControl(CacheControl.noStore())
        .body(new InputStreamResource(c.flux()));
  }

  private DocumentDelivreDto dto(DocumentDelivre d) {
    return new DocumentDelivreDto(
        d.numero(),
        d.codeType(),
        d.valide(),
        d.motifInvalidation(),
        d.delivreLe(),
        d.empreinteSha256(),
        proprietes.urlVerification(d.codeVerification()),
        d.signataire());
  }

  /**
   * État de la délivrance.
   *
   * @param signataire libellé du signataire du catalogue (D4)
   */
  record EtatDelivranceDto(boolean aDelivrer, boolean peutDelivrer, String signataire) {}

  /**
   * Document délivré.
   *
   * @param valide {@code false} après révocation
   * @param motifInvalidation motif de la révocation, {@code null} sinon
   * @param urlVerification page publique de vérification (QR)
   * @param signataire prénom et nom de la personne qui a signé
   */
  record DocumentDelivreDto(
      String numero,
      String codeType,
      boolean valide,
      @Schema(nullable = true) String motifInvalidation,
      Instant delivreLe,
      String empreinteSha256,
      String urlVerification,
      String signataire) {}
}
