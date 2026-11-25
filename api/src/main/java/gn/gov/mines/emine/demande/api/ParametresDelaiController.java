package gn.gov.mines.emine.demande.api;

import gn.gov.mines.emine.demande.application.ServiceParametresDelai;
import gn.gov.mines.emine.demande.domaine.RegleDelais;
import gn.gov.mines.emine.demande.domaine.VersionRegleDelai;
import gn.gov.mines.emine.shared.gouvernance.EtatVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Règle des délais (US-02.6, {@link RegleDelais}) et sa gouvernance (US-02.8, V27) : départ, pauses
 * et seuil « proche du dépassement ». L'administrateur propose avec une date d'effet, le Ministre
 * valide ou refuse ; les demandes déposées gardent la règle de leur dépôt ; tout est tracé.
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
  @Operation(
      operationId = "lireParametresDelai",
      summary = "Règle des délais en vigueur, propositions et historique")
  DetailRegleDelaiDto lire() {
    var d = service.lire();
    return new DetailRegleDelaiDto(
        ParametresDelaiDto.depuis(d.enVigueur()),
        d.versions().stream().map(VersionRegleDelaiDto::depuis).toList(),
        d.peutProposer(),
        d.peutDecider());
  }

  @PostMapping("/versions")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PERM_PARAMETRER')")
  @Operation(
      operationId = "proposerRegleDelai",
      summary = "L'administrateur propose une nouvelle règle, à valider par le Ministre")
  VersionProposeeDto proposer(@Valid @RequestBody PropositionRegleDelaiDto p) {
    return new VersionProposeeDto(
        service.proposer(
            new RegleDelais.Parametres(
                p.depart(),
                p.pauseComplement(),
                p.pauseDroits(),
                p.seuilProchePourcent(),
                p.seuilProcheMinJours()),
            p.motif(),
            p.dateEffet()));
  }

  @PostMapping("/versions/{version}/approbation")
  @PreAuthorize("hasAuthority('PERM_VALIDER_CATALOGUE')")
  @Operation(
      operationId = "approuverRegleDelai",
      summary = "Le Ministre valide : la règle s'applique à sa date d'effet")
  ApprobationDto approuver(@PathVariable int version) {
    return new ApprobationDto(service.decider(version, EtatVersion.Decision.APPROUVER, null));
  }

  @PostMapping("/versions/{version}/refus")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('PERM_VALIDER_CATALOGUE')")
  @Operation(operationId = "refuserRegleDelai", summary = "Le Ministre refuse la proposition")
  void refuser(@PathVariable int version, @Valid @RequestBody MotifRefusDto motif) {
    service.decider(version, EtatVersion.Decision.REFUSER, motif.motif());
  }

  @PostMapping("/versions/{version}/retrait")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('PERM_PARAMETRER')")
  @Operation(
      operationId = "retirerRegleDelai",
      summary = "L'administrateur retire sa proposition avant la décision du Ministre")
  void retirer(@PathVariable int version) {
    service.decider(version, EtatVersion.Decision.RETIRER, null);
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

  /**
   * La règle vue par le profil connecté.
   *
   * @param enVigueur règle des demandes déposées aujourd'hui
   * @param versions toutes les versions, la plus récente d'abord
   * @param peutProposer administrateur, sans proposition en attente
   * @param peutDecider Ministre
   */
  record DetailRegleDelaiDto(
      ParametresDelaiDto enVigueur,
      List<VersionRegleDelaiDto> versions,
      boolean peutProposer,
      boolean peutDecider) {}

  /**
   * Une version de la règle.
   *
   * @param etat {@code a_valider}, {@code en_vigueur}, {@code programmee}, {@code remplacee},
   *     {@code refusee} ou {@code retiree}
   * @param dateEffet date d'application aux demandes déposées (heure de Conakry)
   */
  record VersionRegleDelaiDto(
      int version,
      String etat,
      ParametresDelaiDto regle,
      LocalDate dateEffet,
      String motif,
      @Schema(nullable = true) String proposeePar,
      Instant proposeeLe,
      @Schema(nullable = true) String decideePar,
      @Schema(nullable = true) Instant decideeLe,
      @Schema(nullable = true) String motifRefus) {

    static VersionRegleDelaiDto depuis(VersionRegleDelai v) {
      return new VersionRegleDelaiDto(
          v.version(),
          v.etat(),
          ParametresDelaiDto.depuis(v.parametres()),
          v.dateEffet(),
          v.motif(),
          v.proposeePar(),
          v.proposeeLe(),
          v.decideePar(),
          v.decideeLe(),
          v.motifRefus());
    }
  }

  /**
   * Proposition d'une nouvelle règle.
   *
   * @param dateEffet aujourd'hui ou plus tard (heure de Conakry)
   * @param motif pourquoi ce changement (10 à 2 000 caractères)
   */
  record PropositionRegleDelaiDto(
      @NotNull @Pattern(regexp = "^(paiement_frais|depot)$") String depart,
      boolean pauseComplement,
      boolean pauseDroits,
      @Min(1) @Max(100) int seuilProchePourcent,
      @Min(0) @Max(30) int seuilProcheMinJours,
      @NotNull LocalDate dateEffet,
      @NotBlank @Size(min = 10, max = 2000) String motif) {}

  /** Numéro de la version proposée. */
  record VersionProposeeDto(int version) {}

  /** Date d'effet retenue (aujourd'hui au plus tôt). */
  record ApprobationDto(LocalDate dateEffet) {}

  /** Motif d'un refus (10 à 2 000 caractères). */
  record MotifRefusDto(@NotBlank @Size(min = 10, max = 2000) String motif) {}
}
