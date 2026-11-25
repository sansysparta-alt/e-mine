package gn.gov.mines.emine.catalogue.api;

import gn.gov.mines.emine.catalogue.application.ServiceWorkflows;
import gn.gov.mines.emine.catalogue.domaine.Workflow;
import gn.gov.mines.emine.catalogue.domaine.Workflow.Etape;
import gn.gov.mines.emine.catalogue.domaine.Workflow.VersionWorkflow;
import gn.gov.mines.emine.shared.gouvernance.EtatVersion;
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
import java.time.LocalDate;
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
 * délais. Gouvernance (US-02.8, V27) : l'administrateur propose une version avec sa date d'effet,
 * le Ministre la valide ou la refuse ; tout est tracé ; les demandes en cours gardent leur version.
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
        p.totalPages(),
        service.nombreAValider());
  }

  @GetMapping("/{code}")
  @Operation(operationId = "lireWorkflow", summary = "Un workflow et toutes ses versions")
  DetailWorkflowDto lire(@PathVariable @Pattern(regexp = CODE) String code) {
    var d = service.lire(code);
    return new DetailWorkflowDto(
        WorkflowDto.depuis(d.workflow()),
        d.versions().stream().map(VersionWorkflowDto::depuis).toList(),
        d.peutProposer(),
        d.peutDecider());
  }

  @PostMapping("/{code}/versions")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PERM_PARAMETRER')")
  @Operation(
      operationId = "proposerWorkflow",
      summary = "L'administrateur propose une nouvelle version, à valider par le Ministre")
  VersionCreeeDto proposer(
      @PathVariable @Pattern(regexp = CODE) String code, @Valid @RequestBody ModificationDto m) {
    return new VersionCreeeDto(
        service.proposer(
            code,
            m.libelle(),
            m.reglePaiement(),
            m.etapes().stream()
                .map(e -> new Etape(e.acteur(), e.libelle(), e.acteurEn(), e.libelleEn()))
                .toList(),
            m.motif(),
            m.dateEffet(),
            m.libelleEn(),
            m.reglePaiementEn()));
  }

  @PostMapping("/{code}/versions/{version}/approbation")
  @PreAuthorize("hasAuthority('PERM_VALIDER_CATALOGUE')")
  @Operation(
      operationId = "approuverWorkflow",
      summary = "Le Ministre valide : la version s'applique à sa date d'effet")
  ApprobationDto approuver(
      @PathVariable @Pattern(regexp = CODE) String code, @PathVariable int version) {
    return new ApprobationDto(service.decider(code, version, EtatVersion.Decision.APPROUVER, null));
  }

  @PostMapping("/{code}/versions/{version}/refus")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('PERM_VALIDER_CATALOGUE')")
  @Operation(operationId = "refuserWorkflow", summary = "Le Ministre refuse la proposition")
  void refuser(
      @PathVariable @Pattern(regexp = CODE) String code,
      @PathVariable int version,
      @Valid @RequestBody MotifRefusDto motif) {
    service.decider(code, version, EtatVersion.Decision.REFUSER, motif.motif());
  }

  @PostMapping("/{code}/versions/{version}/retrait")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('PERM_PARAMETRER')")
  @Operation(
      operationId = "retirerWorkflow",
      summary = "L'administrateur retire sa proposition avant la décision du Ministre")
  void retirer(@PathVariable @Pattern(regexp = CODE) String code, @PathVariable int version) {
    service.decider(code, version, EtatVersion.Decision.RETIRER, null);
  }

  /**
   * Page de workflows (modèle de pagination du socle).
   *
   * @param aValider propositions en attente du Ministre, tous workflows confondus
   */
  record PageWorkflowsDto(
      List<WorkflowDto> elements,
      int page,
      int taille,
      long total,
      int totalPages,
      long aValider) {}

  /**
   * Workflow et sa version en vigueur.
   *
   * @param documents documents de la famille (code, nom, disponible ou non)
   * @param delaiMinJours délai le plus court des documents de la famille (fiches approuvées)
   * @param aValider proposition en attente du Ministre
   * @param programmee version validée, applicable à une date à venir
   */
  record WorkflowDto(
      String code,
      int delaiMinJours,
      int delaiMaxJours,
      List<DocumentFamilleDto> documents,
      VersionWorkflowDto enVigueur,
      @Schema(nullable = true) VersionWorkflowDto aValider,
      @Schema(nullable = true) VersionWorkflowDto programmee) {

    static WorkflowDto depuis(Workflow w) {
      return new WorkflowDto(
          w.code(),
          w.delaiMinJours(),
          w.delaiMaxJours(),
          w.documents().stream()
              .map(d -> new DocumentFamilleDto(d.code(), d.libelleFr(), d.libelleEn(), d.actif()))
              .toList(),
          VersionWorkflowDto.depuis(w.enVigueur()),
          w.aValider() == null ? null : VersionWorkflowDto.depuis(w.aValider()),
          w.programmee() == null ? null : VersionWorkflowDto.depuis(w.programmee()));
    }
  }

  /**
   * Document d'une famille.
   *
   * @param actif disponible à la demande ({@code false} : désactivé au catalogue)
   */
  record DocumentFamilleDto(String code, String libelleFr, String libelleEn, boolean actif) {}

  /**
   * Une version d'un workflow.
   *
   * @param etat {@code a_valider}, {@code en_vigueur}, {@code programmee}, {@code remplacee},
   *     {@code refusee} ou {@code retiree}
   * @param modifieePar administrateur qui l'a proposée ({@code null} : version initiale)
   * @param modifieeLe date de la proposition
   * @param dateEffet date d'application aux demandes déposées (heure de Conakry)
   */
  record VersionWorkflowDto(
      int version,
      String etat,
      String libelle,
      String reglePaiement,
      String motif,
      @Schema(nullable = true) String modifieePar,
      Instant modifieeLe,
      LocalDate dateEffet,
      @Schema(nullable = true) String decideePar,
      @Schema(nullable = true) Instant decideeLe,
      @Schema(nullable = true) String motifRefus,
      List<EtapeDto> etapes,
      @Schema(nullable = true) String libelleEn,
      @Schema(nullable = true) String reglePaiementEn) {

    static VersionWorkflowDto depuis(VersionWorkflow v) {
      return new VersionWorkflowDto(
          v.version(),
          v.etat(),
          v.libelle(),
          v.reglePaiement(),
          v.motif(),
          v.modifieePar(),
          v.modifieeLe(),
          v.dateEffet(),
          v.decideePar(),
          v.decideeLe(),
          v.motifRefus(),
          v.etapes().stream()
              .map(e -> new EtapeDto(e.acteur(), e.libelle(), e.acteurEn(), e.libelleEn()))
              .toList(),
          v.libelleEn(),
          v.reglePaiementEn());
    }
  }

  /**
   * Workflow et historique.
   *
   * @param versions toutes les versions, la plus récente d'abord
   * @param peutProposer administrateur, sans proposition en attente pour ce workflow
   * @param peutDecider Ministre
   */
  record DetailWorkflowDto(
      WorkflowDto workflow,
      List<VersionWorkflowDto> versions,
      boolean peutProposer,
      boolean peutDecider) {}

  /**
   * Étape : intervenant et action, en français et (facultatif) en anglais.
   *
   * @param acteurEn intervenant en anglais ; absent : repli sur le français
   */
  record EtapeDto(
      @NotBlank @Size(min = 2, max = 100) String acteur,
      @NotBlank @Size(min = 2, max = 150) String libelle,
      @Schema(nullable = true) @Size(max = 100) String acteurEn,
      @Schema(nullable = true) @Size(max = 150) String libelleEn) {}

  /**
   * Modification d'un workflow : version complète.
   *
   * @param etapes étapes dans l'ordre (1 à 12)
   */
  record ModificationDto(
      @NotBlank @Size(min = 3, max = 150) String libelle,
      @NotBlank @Size(min = 3, max = 300) String reglePaiement,
      @NotNull @Size(min = 1, max = Workflow.ETAPES_MAX) List<@Valid EtapeDto> etapes,
      @NotBlank @Size(min = 10, max = 2000) String motif,
      @NotNull LocalDate dateEffet,
      @Schema(nullable = true) @Size(max = 150) String libelleEn,
      @Schema(nullable = true) @Size(max = 300) String reglePaiementEn) {}

  /** Numéro de la version proposée. */
  record VersionCreeeDto(int version) {}

  /** Date d'effet retenue (aujourd'hui au plus tôt). */
  record ApprobationDto(LocalDate dateEffet) {}

  /** Motif d'un refus (10 à 2 000 caractères). */
  record MotifRefusDto(@NotBlank @Size(min = 10, max = 2000) String motif) {}
}
