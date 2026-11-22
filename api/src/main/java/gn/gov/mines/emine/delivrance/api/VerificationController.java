package gn.gov.mines.emine.delivrance.api;

import gn.gov.mines.emine.delivrance.application.ServiceVerification;
import gn.gov.mines.emine.delivrance.application.ServiceVerification.Verification;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vérification publique d'un document délivré (US-06.5) : sans connexion, aucune donnée
 * personnelle, jamais mise en cache (une révocation doit se voir aussitôt).
 */
@RestController
@RequestMapping("/api/v1/verification")
@Tag(name = "Vérification")
class VerificationController {

  private final ServiceVerification service;

  VerificationController(ServiceVerification service) {
    this.service = service;
  }

  @GetMapping("/{code}")
  @Operation(
      operationId = "verifierDocument",
      summary = "Document authentique ou NON VALIDE (révoqué), par le code du QR")
  ResponseEntity<VerificationDto> verifier(@PathVariable String code) {
    Verification v = service.verifier(code);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new VerificationDto(
                v.authentique() ? "AUTHENTIQUE" : "NON_VALIDE",
                v.numero(),
                v.codeType(),
                v.document(),
                v.delivreLe(),
                v.signataire(),
                v.empreinteSha256()));
  }

  /**
   * Résultat public.
   *
   * @param statut {@code AUTHENTIQUE} ou {@code NON_VALIDE} (révoqué)
   * @param signataire fonction du signataire au catalogue (aucun nom de personne)
   */
  record VerificationDto(
      String statut,
      String numero,
      String codeType,
      String document,
      Instant delivreLe,
      @Schema(nullable = true) String signataire,
      String empreinteSha256) {}
}
