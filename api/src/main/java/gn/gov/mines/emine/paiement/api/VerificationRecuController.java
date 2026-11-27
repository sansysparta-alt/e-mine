package gn.gov.mines.emine.paiement.api;

import gn.gov.mines.emine.paiement.application.ServiceVerificationRecus;
import gn.gov.mines.emine.paiement.application.ServiceVerificationRecus.VerificationRecu;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vérification publique d'un reçu (US-04.14) : ouverte par le QR, sans connexion, aucune donnée
 * personnelle, jamais mise en cache (un remboursement doit se voir aussitôt).
 */
@RestController
@Tag(name = "Vérification")
class VerificationRecuController {

  private final ServiceVerificationRecus service;

  VerificationRecuController(ServiceVerificationRecus service) {
    this.service = service;
  }

  @GetMapping("/api/v1/verification/recus/{code}")
  @Operation(operationId = "verifierRecu", summary = "Reçu authentique, par le code de son QR")
  ResponseEntity<VerificationRecuDto> verifier(@PathVariable String code) {
    VerificationRecu v = service.verifier(code);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new VerificationRecuDto(
                v.numero(),
                v.objet(),
                v.moyen(),
                v.montantGnf(),
                v.payeLe(),
                v.moyen().equals("TRESORPAY"),
                v.demandeNumero(),
                v.rembourse()));
  }

  /**
   * Résultat public : un reçu authentique (sinon PAY-017).
   *
   * @param heureConnue l'heure du paiement est connue (TrésorPay) ; un virement n'a que la date du
   *     relevé bancaire
   * @param rembourse la somme a été rendue par le Trésor depuis : le reçu ne prouve plus un
   *     paiement acquis
   */
  record VerificationRecuDto(
      String numero,
      @Schema(allowableValues = {"FRAIS_DOSSIER", "DROITS", "ATTESTATION", "FORMATION"})
          String objet,
      @Schema(allowableValues = {"TRESORPAY", "VIREMENT"}) String moyen,
      long montantGnf,
      Instant payeLe,
      boolean heureConnue,
      String demandeNumero,
      boolean rembourse) {}
}
