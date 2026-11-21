package gn.gov.mines.emine.audit.api;

import gn.gov.mines.emine.audit.application.ServiceJournal;
import gn.gov.mines.emine.audit.application.ServiceJournal.Consultation;
import gn.gov.mines.emine.audit.domaine.EntreeJournal;
import gn.gov.mines.emine.shared.pagination.Pagination;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Journal d'audit (US-11.7, partiel V1) : consultation par l'administrateur technique (droit «
 * paramétrer »). Le journal est écrit par les triggers de la base et n'est jamais modifiable.
 */
@RestController
@RequestMapping("/api/v1/journal-audit")
@Tag(name = "Audit")
@PreAuthorize("hasAuthority('PERM_PARAMETRER')")
class JournalAuditController {

  private final ServiceJournal service;

  JournalAuditController(ServiceJournal service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      operationId = "consulterJournal",
      summary =
          "Journal d'audit paginé, du plus récent au plus ancien, filtrable par catégorie,"
              + " recherche et période (dates de Conakry, bornes incluses)")
  PageJournalDto consulter(
      @RequestParam(required = false) @Pattern(regexp = "^[a-z]{1,20}$") String categorie,
      @RequestParam(required = false) @Size(max = 100) String q,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer taille) {
    Consultation c = service.consulter(categorie, q, du, au, Pagination.de(page, taille));
    return new PageJournalDto(
        c.page().elements().stream().map(EntreeJournalDto::depuis).toList(),
        c.page().page(),
        c.page().taille(),
        c.page().total(),
        c.page().totalPages(),
        c.compteurs());
  }

  @GetMapping("/{id}")
  @Operation(operationId = "lireEntreeJournal", summary = "Une ligne du journal d'audit")
  EntreeJournalDto lire(@PathVariable long id) {
    return EntreeJournalDto.depuis(service.lire(id));
  }

  /**
   * Une page du journal (modèle de pagination du socle).
   *
   * @param compteurs nombre de lignes par catégorie pour la recherche et la période
   */
  record PageJournalDto(
      List<EntreeJournalDto> elements,
      int page,
      int taille,
      long total,
      int totalPages,
      Map<String, Long> compteurs) {}

  /**
   * Ligne du journal.
   *
   * @param auteur {@code null} pour une action du système
   * @param operation {@code I} création, {@code U} modification, {@code D} suppression
   */
  record EntreeJournalDto(
      long id,
      Instant date,
      @Schema(nullable = true) String auteur,
      @Schema(nullable = true) String auteurProfil,
      String table,
      String categorie,
      String operation,
      @Schema(nullable = true) String objetId,
      @Schema(nullable = true) String objetLibelle,
      @Schema(nullable = true) Long demandeId,
      List<ChangementDto> changements,
      @Schema(nullable = true) String adresseIp) {

    static EntreeJournalDto depuis(EntreeJournal e) {
      return new EntreeJournalDto(
          e.id(),
          e.date(),
          e.auteur(),
          e.auteurProfil(),
          e.table(),
          e.categorie(),
          e.operation(),
          e.objetId(),
          e.objetLibelle(),
          e.demandeId(),
          e.changements().stream()
              .map(c -> new ChangementDto(c.champ(), c.avant(), c.apres()))
              .toList(),
          e.adresseIp());
    }
  }

  /** Champ modifié : valeur avant et après ({@code null} si absente). */
  record ChangementDto(
      String champ, @Schema(nullable = true) String avant, @Schema(nullable = true) String apres) {}
}
