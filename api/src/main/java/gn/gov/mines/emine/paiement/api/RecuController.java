package gn.gov.mines.emine.paiement.api;

import gn.gov.mines.emine.paiement.application.ServiceRecus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reçu PDF d'un paiement confirmé (US-04.6), visible selon la RLS. */
@RestController
@Validated
@Tag(name = "Paiements")
class RecuController {

  private final ServiceRecus service;

  RecuController(ServiceRecus service) {
    this.service = service;
  }

  @GetMapping(
      path = "/api/v1/paiements/{reference}/recu",
      produces = MediaType.APPLICATION_PDF_VALUE)
  @Operation(
      operationId = "telechargerRecu",
      summary =
          "Reçu PDF REC-AAAA-NNNNNN d'un paiement confirmé (PAY-009 s'il ne l'est pas), à"
              + " télécharger ou à afficher dans le visualiseur (inline=true)",
      responses =
          @ApiResponse(
              responseCode = "200",
              content =
                  @Content(
                      mediaType = MediaType.APPLICATION_PDF_VALUE,
                      schema = @Schema(type = "string", format = "binary"))))
  ResponseEntity<byte[]> recu(
      @PathVariable @Pattern(regexp = "^PAY-[0-9]{4}-[0-9]{6}$") String reference,
      @RequestParam(defaultValue = "false") boolean inline) {
    ServiceRecus.RecuPdf recu = service.recu(reference);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename("recu-" + recu.numero() + ".pdf")
                .build()
                .toString())
        .header("X-Content-Type-Options", "nosniff")
        .cacheControl(CacheControl.noStore())
        .body(recu.contenu());
  }
}
