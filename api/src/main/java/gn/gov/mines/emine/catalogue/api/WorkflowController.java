package gn.gov.mines.emine.catalogue.api;

import gn.gov.mines.emine.catalogue.application.ServiceWorkflows;
import gn.gov.mines.emine.catalogue.domaine.Workflow;
import gn.gov.mines.emine.catalogue.domaine.Workflow.Etape;
import gn.gov.mines.emine.catalogue.domaine.Workflow.VersionWorkflow;
import gn.gov.mines.emine.shared.pagination.Pagination;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Les 5 workflows du catalogue (US-02.5) : étapes, intervenants, règle de paiement et fourchette de
 * délais. Modification par l'administrateur, immédiate et tracée (une version par modification).
 */
@RestController
@RequestMapping("/api/v1/workflows")
@Tag(name = "Catalogue")
@PreAuthorize("hasAnyAuthority('PERM_PARAMETRER', 'PERM_VALIDER_CATALOGUE')")
class WorkflowController {

  private static final String CODE = "^[A-Z_]{2,30}$";

  private final ServiceWorkflows service;

  WorkflowController(ServiceWorkflows service) {
    this.service = service;
  }

  @GetMapping
  @Operation(operationId = "listerWorkflows", summary = "Workflows du catalogue, paginés")
  PageWorkflowsDto lister(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer taille) {
    var p = service.lister(Pagination.de(page, taille));
    return new PageWorkflowsDto(
        p.elements().stream().map(WorkflowDto::depuis).toList(),
        p.page(),
        p.taille(),
        p.total(),
        p.totalPages());
  }

  @GetMapping("/{code}")
  @Operation(operationId = "lireWorkflow", summary = "Un workflow et toutes ses versions")
  DetailWorkflowDto lire(@PathVariable @Pattern(regexp = CODE) String code) {
    var d = service.lire(code);
    return new DetailWorkflowDto(
        WorkflowDto.depuis(d.workflow()),
        d.versions().stream().map(VersionWorkflowDto::depuis).toList(),
        d.peutModifier());
  }

  @PostMapping("/{code}/versions")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PERM_PARAMETRER')")
  @Operation(
      operationId = "modifierWorkflow",
      summary =
          "Nouvelle version du workflow (immédiate, tracée ; sans effet sur les demandes en cours)")
  VersionCreeeDto modifier(
      @PathVariable @Pattern(regexp = CODE) String code, @Valid @RequestBody ModificationDto m) {
    return new VersionCreeeDto(
        service.modifier(
            code,
            m.libelle(),
            m.reglePaiement(),
            m.etapes().stream().map(e -> new Etape(e.acteur(), e.libelle())).toList(),
            m.motif()));
  }

  /** Page de workflows (modèle de pagination du socle). */
  record PageWorkflowsDto(
      List<WorkflowDto> elements, int page, int taille, long total, int totalPages) {}

  /**
   * Workflow et sa version en vigueur.
   *
   * @param documents codes des documents de la famille
   * @param delaiMinJours délai le plus court des documents de la famille (fiches approuvées)
   */
  record WorkflowDto(
      String code,
      int delaiMinJours,
      int delaiMaxJours,
      List<String> documents,
      VersionWorkflowDto enVigueur) {

    static WorkflowDto depuis(Workflow w) {
      return new WorkflowDto(
          w.code(),
          w.delaiMinJours(),
          w.delaiMaxJours(),
          w.documents(),
          VersionWorkflowDto.depuis(w.enVigueur()));
    }
  }

  /** Une version d'un workflow. */
  record VersionWorkflowDto(
      int version,
      String libelle,
      String reglePaiement,
      String motif,
      @Schema(nullable = true) String modifieePar,
      Instant modifieeLe,
      List<EtapeDto> etapes) {

    static VersionWorkflowDto depuis(VersionWorkflow v) {
      return new VersionWorkflowDto(
          v.version(),
          v.libelle(),
          v.reglePaiement(),
          v.motif(),
          v.modifieePar(),
          v.modifieeLe(),
          v.etapes().stream().map(e -> new EtapeDto(e.acteur(), e.libelle())).toList());
    }
  }

  /**
   * Workflow et historique.
   *
   * @param versions toutes les versions, la plus récente d'abord
   */
  record DetailWorkflowDto(
      WorkflowDto workflow, List<VersionWorkflowDto> versions, boolean peutModifier) {}

  /** Étape : intervenant et action. */
  record EtapeDto(
      @NotBlank @Size(min = 2, max = 100) String acteur,
      @NotBlank @Size(min = 2, max = 150) String libelle) {}

  /**
   * Modification d'un workflow : version complète.
   *
   * @param etapes étapes dans l'ordre (1 à 12)
   */
  record ModificationDto(
      @NotBlank @Size(min = 3, max = 150) String libelle,
      @NotBlank @Size(min = 3, max = 300) String reglePaiement,
      @NotNull @Size(min = 1, max = Workflow.ETAPES_MAX) List<@Valid EtapeDto> etapes,
      @NotBlank @Size(min = 10, max = 2000) String motif) {}

  /** Numéro de la version créée. */
  record VersionCreeeDto(int version) {}
}
