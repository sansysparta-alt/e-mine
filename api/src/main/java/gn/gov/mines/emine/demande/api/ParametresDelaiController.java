package gn.gov.mines.emine.demande.api;

import gn.gov.mines.emine.demande.application.ServiceParametresDelai;
import gn.gov.mines.emine.demande.domaine.RegleDelais;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Paramètres de la règle des délais (US-02.6, {@link RegleDelais}, V17) : départ, pauses et seuil «
 * proche du dépassement ». Choix provisoires du porteur du projet, à valider par le Ministère ;
 * modification par l'administrateur, immédiate et tracée (droits contrôlés en base, V21).
 */
@RestController
@RequestMapping("/api/v1/parametres/delais")
@Tag(name = "Demandes")
@PreAuthorize("hasAnyAuthority('PERM_PARAMETRER', 'PERM_VALIDER_CATALOGUE')")
class ParametresDelaiController {

  private final ServiceParametresDelai service;

  ParametresDelaiController(ServiceParametresDelai service) {
    this.service = service;
  }

  @GetMapping
  @Operation(operationId = "lireParametresDelai", summary = "Règle des délais en vigueur")
  ParametresDelaiDto lire() {
    return ParametresDelaiDto.depuis(service.lire());
  }

  @PutMapping
  @PreAuthorize("hasAuthority('PERM_PARAMETRER')")
  @Operation(
      operationId = "modifierParametresDelai",
      summary = "Modifie la règle des délais (s'applique aussitôt à tous les calculs)")
  ParametresDelaiDto modifier(@Valid @RequestBody ParametresDelaiDto p) {
    return ParametresDelaiDto.depuis(
        service.enregistrer(
            new RegleDelais.Parametres(
                p.depart(),
                p.pauseComplement(),
                p.pauseDroits(),
                p.seuilProchePourcent(),
                p.seuilProcheMinJours())));
  }

  /**
   * Règle des délais.
   *
   * @param depart {@code paiement_frais} (paiement des frais de dossier) ou {@code depot}
   * @param seuilProchePourcent « proche du dépassement » : il reste au plus ce pourcentage du délai
   * @param seuilProcheMinJours … et au moins ce nombre de jours
   */
  record ParametresDelaiDto(
      @Pattern(regexp = "^(paiement_frais|depot)$") String depart,
      boolean pauseComplement,
      boolean pauseDroits,
      @Min(1) @Max(100) int seuilProchePourcent,
      @Min(0) @Max(30) int seuilProcheMinJours) {

    static ParametresDelaiDto depuis(RegleDelais.Parametres p) {
      return new ParametresDelaiDto(
          p.depart(),
          p.pauseComplement(),
          p.pauseDroits(),
          p.seuilProchePourcent(),
          p.seuilProcheMinJours());
    }
  }
}
