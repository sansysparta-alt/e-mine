package gn.gov.mines.emine.demande.api;

import gn.gov.mines.emine.demande.application.ServiceDemandes;
import gn.gov.mines.emine.demande.application.ServiceDepot;
import gn.gov.mines.emine.demande.domaine.Brouillon;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demandes (E-DEMANDES) : brouillon (US-03.1), dépôt (US-03.4). Créer et modifier exigent le droit
 * « déposer » (US-01.6) ; la lecture est filtrée par la RLS.
 */
@RestController
@RequestMapping("/api/v1/demandes")
@Tag(name = "Demandes")
class DemandeController {

  private final ServiceDemandes service;
  private final ServiceDepot depot;

  DemandeController(ServiceDemandes service, ServiceDepot depot) {
    this.service = service;
    this.depot = depot;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PERM_DEPOSER')")
  @Operation(operationId = "creerBrouillon", summary = "Créer une demande au statut brouillon")
  DemandeDto creer(@Valid @RequestBody BrouillonDto corps) {
    return DemandeDto.depuis(service.creer(corps.versDomaine()));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_DEPOSER')")
  @Operation(operationId = "modifierBrouillon", summary = "Enregistrer le brouillon")
  DemandeDto modifier(@PathVariable long id, @Valid @RequestBody BrouillonDto corps) {
    return DemandeDto.depuis(service.modifier(id, corps.versDomaine()));
  }

  @GetMapping("/{id}")
  @Operation(
      operationId = "lireDemande",
      summary = "Une demande (les siennes, ou toutes pour un agent)")
  DemandeDto lire(@PathVariable long id) {
    return DemandeDto.depuis(service.lire(id));
  }

  @PostMapping("/{id}/depot")
  @PreAuthorize("hasAuthority('PERM_DEPOSER')")
  @Operation(
      operationId = "deposerDemande",
      summary = "Déposer la demande (déclaration sur l'honneur, au moins une pièce)")
  DemandeDto deposer(@PathVariable long id, @Valid @RequestBody DepotDto corps) {
    return DemandeDto.depuis(depot.deposer(id, Boolean.TRUE.equals(corps.declarationHonneur())));
  }

  /** Dépôt : acceptation de la déclaration sur l'honneur. */
  record DepotDto(@NotNull Boolean declarationHonneur) {}

  /** Saisie du brouillon. */
  record BrouillonDto(
      @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String typeDocumentCode,
      @Schema(nullable = true) Short prefectureId,
      @Schema(nullable = true) @Pattern(regexp = "^[A-Z_]+$") String substanceCode,
      @Schema(nullable = true) @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2)
          BigDecimal superficieHa) {

    Brouillon versDomaine() {
      return new Brouillon(typeDocumentCode, prefectureId, substanceCode, superficieHa);
    }
  }
}
