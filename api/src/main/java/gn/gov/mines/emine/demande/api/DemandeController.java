package gn.gov.mines.emine.demande.api;

import gn.gov.mines.emine.demande.application.ServiceDemandes;
import gn.gov.mines.emine.demande.domaine.Brouillon;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
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
 * Demandes (E-DEMANDES) : brouillon (US-03.1). Créer et modifier exigent le droit « déposer »
 * (US-01.6) ; la lecture est filtrée par la RLS.
 */
@RestController
@RequestMapping("/api/v1/demandes")
@Tag(name = "Demandes")
class DemandeController {

  private final ServiceDemandes service;

  DemandeController(ServiceDemandes service) {
    this.service = service;
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
