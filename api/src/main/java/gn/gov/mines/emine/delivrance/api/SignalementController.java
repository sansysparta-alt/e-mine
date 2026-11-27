package gn.gov.mines.emine.delivrance.api;

import gn.gov.mines.emine.delivrance.application.ServiceSignalements;
import gn.gov.mines.emine.delivrance.infrastructure.SignalementRepository.Signalement;
import gn.gov.mines.emine.shared.pagination.Pagination;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signalements d'un document délivré (US-06.8, décision V1-11) : la Brigade anti-fraude signale ;
 * le signataire annule le document ou classe le signalement. Réservé aux profils internes (RLS).
 */
@RestController
@Tag(name = "Délivrance")
class SignalementController {

  private final ServiceSignalements service;

  SignalementController(ServiceSignalements service) {
    this.service = service;
  }

  @GetMapping("/api/v1/demandes/{id}/document/signalements")
  @Operation(
      operationId = "lireSignalementsDocument",
      summary =
          "Ce que le profil connecté peut faire sur le document (annuler, signaler) et ses"
              + " signalements")
  EtatSignalementsDto etat(@PathVariable long id) {
    var e = service.etat(id);
    return new EtatSignalementsDto(
        e.peutAnnuler(),
        e.peutSignaler(),
        e.signalements().stream().map(s -> SignalementDto.depuis(s, e.peutAnnuler())).toList());
  }

  @PostMapping("/api/v1/demandes/{id}/document/signalements")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      operationId = "signalerDocument",
      summary = "La Brigade signale le document à son signataire (sans effet sur le document)")
  void signaler(@PathVariable long id, @Valid @RequestBody MotifSignalementDto corps) {
    service.signaler(id, corps.motif());
  }

  @PostMapping("/api/v1/signalements/{id}/decision")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      operationId = "deciderSignalement",
      summary = "Le signataire annule le document ou classe le signalement, avec un motif")
  void decider(@PathVariable long id, @Valid @RequestBody DecisionSignalementDto corps) {
    service.decider(id, ServiceSignalements.Decision.valueOf(corps.decision()), corps.motif());
  }

  @GetMapping("/api/v1/signalements/a-decider")
  @Operation(
      operationId = "listerSignalementsADecider",
      summary = "Signalements ouverts des documents dont le profil connecté est le signataire")
  PageSignalementsDto aDecider(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer taille) {
    var p = service.aDecider(Pagination.de(page, taille));
    return new PageSignalementsDto(
        p.elements().stream().map(s -> SignalementDto.depuis(s, true)).toList(),
        p.page(),
        p.taille(),
        p.total(),
        p.totalPages());
  }

  /**
   * Droits du profil connecté sur le document, et ses signalements.
   *
   * @param peutAnnuler le profil connecté en est le signataire (document valide)
   * @param peutSignaler la Brigade anti-fraude (document valide)
   */
  record EtatSignalementsDto(
      boolean peutAnnuler, boolean peutSignaler, List<SignalementDto> signalements) {}

  /**
   * Un signalement.
   *
   * @param statut {@code ouvert}, {@code annule} (document annulé) ou {@code classe}
   * @param aTraiterParMoi signalement ouvert dont le profil connecté est le signataire
   */
  record SignalementDto(
      long id,
      long demandeId,
      @Schema(nullable = true) String numeroDemande,
      String numeroDocument,
      @Schema(allowableValues = {"ouvert", "annule", "classe"}) String statut,
      String motif,
      @Schema(nullable = true) String auteur,
      Instant signaleLe,
      @Schema(nullable = true) String decidePar,
      @Schema(nullable = true) Instant decideLe,
      @Schema(nullable = true) String motifDecision,
      boolean aTraiterParMoi) {

    static SignalementDto depuis(Signalement s, boolean signataire) {
      return new SignalementDto(
          s.id(),
          s.demandeId(),
          s.numeroDemande(),
          s.numeroDocument(),
          s.statut(),
          s.motif(),
          s.auteur(),
          s.signaleLe(),
          s.decidePar(),
          s.decideLe(),
          s.motifDecision(),
          signataire && "ouvert".equals(s.statut()));
    }
  }

  record MotifSignalementDto(@Schema(nullable = true) String motif) {}

  /**
   * @param decision {@code ANNULER} ou {@code CLASSER}
   */
  record DecisionSignalementDto(
      @Schema(allowableValues = {"ANNULER", "CLASSER"})
          @jakarta.validation.constraints.Pattern(regexp = "ANNULER|CLASSER")
          String decision,
      @Schema(nullable = true) String motif) {}

  record PageSignalementsDto(
      List<SignalementDto> elements, int page, int taille, long total, int totalPages) {}
}
