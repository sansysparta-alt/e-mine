package gn.gov.mines.emine.paiement.api;

import gn.gov.mines.emine.paiement.application.ServiceRemboursements;
import gn.gov.mines.emine.paiement.infrastructure.RegleRemboursementRepository;
import gn.gov.mines.emine.paiement.infrastructure.RemboursementRepository;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.gouvernance.EtatVersion;
import gn.gov.mines.emine.shared.pagination.Pagination;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Remboursements (US-04.10) et règle de remboursement des frais (décision V1-4, gouvernée). Le
 * Responsable fiscal valide ou refuse chaque demande puis enregistre l'ordre du Trésor ;
 * l'administrateur propose la règle, le Ministre la valide.
 */
@RestController
@Tag(name = "Remboursements", description = "Remboursements et règle V1-4 (US-04.10)")
class RemboursementController {

  private final ServiceRemboursements service;

  RemboursementController(ServiceRemboursements service) {
    this.service = service;
  }

  @GetMapping("/api/v1/remboursements")
  @PreAuthorize("hasAnyAuthority('PERM_ENCAISSER', 'PERM_VALIDER_REMBOURSEMENT')")
  @Operation(
      operationId = "listerRemboursements",
      summary =
          "Remboursements : à valider et à exécuter d'abord, puis l'historique ; nombre par statut")
  PageRemboursementsDto lister(
      @Parameter(description = "demande, valide, execute ou refuse ; absent : tous")
          @RequestParam(required = false)
          String statut,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer taille) {
    if (statut != null && !statut.isBlank() && !RemboursementRepository.STATUTS.contains(statut)) {
      throw new ErreurMetier(
          CodeErreur.VAL_001,
          Map.of(
              "champs",
              List.of(
                  Map.of(
                      "champ",
                      "statut",
                      "message",
                      "Valeur attendue : demande, valide, execute ou refuse"))));
    }
    var r =
        service.lister(
            statut == null || statut.isBlank() ? null : statut, Pagination.de(page, taille));
    var p = r.page();
    var c = r.compteurs();
    return new PageRemboursementsDto(
        p.elements().stream()
            .map(e -> RemboursementDto.depuis(e, service.aTraiterParMoi(e)))
            .toList(),
        p.page(),
        p.taille(),
        p.total(),
        p.totalPages(),
        new CompteursRemboursementsDto(
            c.values().stream().mapToLong(Long::longValue).sum(),
            service.aTraiter(),
            c.get("demande"),
            c.get("valide"),
            c.get("execute"),
            c.get("refuse")));
  }

  @GetMapping("/api/v1/remboursements/{id}")
  @PreAuthorize("hasAnyAuthority('PERM_ENCAISSER', 'PERM_VALIDER_REMBOURSEMENT')")
  @Operation(operationId = "lireRemboursement", summary = "Un remboursement")
  RemboursementDto lire(@PathVariable long id) {
    var r = service.lire(id);
    return RemboursementDto.depuis(r, service.aTraiterParMoi(r));
  }

  @PostMapping("/api/v1/remboursements/{id}/validation")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('PERM_VALIDER_REMBOURSEMENT')")
  @Operation(
      operationId = "validerRemboursement",
      summary =
          "Le Directeur valide la proposition : le Responsable fiscal peut demander l'ordre au"
              + " Trésor (PAY-018 pour son auteur)")
  void valider(@PathVariable long id) {
    service.valider(id);
  }

  @PostMapping("/api/v1/remboursements/{id}/refus")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('PERM_VALIDER_REMBOURSEMENT')")
  @Operation(
      operationId = "refuserRemboursement",
      summary = "Refus motivé d'un remboursement par le Directeur (PAY-018 pour son auteur)")
  void refuser(@PathVariable long id, @Valid @RequestBody MotifRemboursementDto corps) {
    service.refuser(id, corps.motif());
  }

  @PostMapping("/api/v1/remboursements/{id}/execution")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('PERM_ENCAISSER')")
  @Operation(
      operationId = "executerRemboursement",
      summary =
          "Ordre de remboursement émis par le Trésor (hors plateforme) : sa référence est"
              + " enregistrée et le demandeur est informé")
  void executer(@PathVariable long id, @Valid @RequestBody ExecutionRemboursementDto corps) {
    service.executer(id, corps.referenceOrdre());
  }

  // --- Règle de remboursement ---------------------------------------------------------------

  @GetMapping("/api/v1/parametres/remboursement")
  @PreAuthorize("hasAnyAuthority('PERM_PARAMETRER', 'PERM_VALIDER_CATALOGUE')")
  @Operation(
      operationId = "lireRegleRemboursement",
      summary = "Règle de remboursement des frais en vigueur, propositions et historique")
  DetailRegleRemboursementDto lireRegle() {
    var d = service.regle();
    return new DetailRegleRemboursementDto(
        d.rembourserSiRejet(),
        d.versions().stream().map(VersionRegleRemboursementDto::depuis).toList(),
        d.peutProposer(),
        d.peutDecider());
  }

  @PostMapping("/api/v1/parametres/remboursement/versions")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PERM_PARAMETRER')")
  @Operation(
      operationId = "proposerRegleRemboursement",
      summary = "L'administrateur propose une nouvelle règle, à valider par le Ministre")
  VersionRegleProposeeDto proposerRegle(@Valid @RequestBody PropositionRegleRemboursementDto p) {
    return new VersionRegleProposeeDto(
        service.proposerRegle(p.rembourserSiRejet(), p.motif(), p.dateEffet()));
  }

  @PostMapping("/api/v1/parametres/remboursement/versions/{version}/approbation")
  @PreAuthorize("hasAuthority('PERM_VALIDER_CATALOGUE')")
  @Operation(
      operationId = "approuverRegleRemboursement",
      summary = "Le Ministre valide : la règle s'applique à sa date d'effet")
  ApprobationRegleDto approuverRegle(@PathVariable int version) {
    return new ApprobationRegleDto(
        service.deciderRegle(version, EtatVersion.Decision.APPROUVER, null));
  }

  @PostMapping("/api/v1/parametres/remboursement/versions/{version}/refus")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('PERM_VALIDER_CATALOGUE')")
  @Operation(
      operationId = "refuserRegleRemboursement",
      summary = "Le Ministre refuse la proposition")
  void refuserRegle(@PathVariable int version, @Valid @RequestBody MotifRemboursementDto motif) {
    service.deciderRegle(version, EtatVersion.Decision.REFUSER, motif.motif());
  }

  @PostMapping("/api/v1/parametres/remboursement/versions/{version}/retrait")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('PERM_PARAMETRER')")
  @Operation(
      operationId = "retirerRegleRemboursement",
      summary = "L'administrateur retire sa proposition avant la décision du Ministre")
  void retirerRegle(@PathVariable int version) {
    service.deciderRegle(version, EtatVersion.Decision.RETIRER, null);
  }

  /**
   * Un remboursement.
   *
   * @param statut {@code demande}, {@code valide}, {@code execute} ou {@code refuse}
   * @param motif {@code double_paiement}, {@code excedent}, {@code rejet_demande} ou {@code autre}
   * @param referenceOrdre référence de l'ordre de remboursement du Trésor, une fois exécuté
   * @param aTraiterParMoi action attendue du profil connecté : décider (Directeur) ou enregistrer
   *     l'ordre du Trésor (Responsable fiscal)
   */
  record RemboursementDto(
      long id,
      String numero,
      String statut,
      String motif,
      long montantGnf,
      @Schema(nullable = true) String explication,
      @Schema(nullable = true) String motifRefus,
      @Schema(nullable = true) String referenceOrdre,
      Instant demandeLe,
      @Schema(nullable = true) String demandePar,
      @Schema(nullable = true) Instant decideLe,
      @Schema(nullable = true) String decidePar,
      @Schema(nullable = true) Instant executeLe,
      String referencePaiement,
      String objetPaiement,
      String moyenPaiement,
      @Schema(nullable = true) Long demandeId,
      @Schema(nullable = true) String numeroDemande,
      @Schema(nullable = true) String beneficiaire,
      boolean aTraiterParMoi) {

    static RemboursementDto depuis(RemboursementRepository.Remboursement r, boolean aTraiter) {
      return new RemboursementDto(
          r.id(),
          r.numero(),
          r.statut(),
          r.motif(),
          r.montantGnf(),
          r.explication(),
          r.motifRefus(),
          r.referenceOrdre(),
          r.demandeLe(),
          r.demandePar(),
          r.decideLe(),
          r.decidePar(),
          r.executeLe(),
          r.referencePaiement(),
          r.objetPaiement(),
          r.moyenPaiement(),
          r.demandeId(),
          r.numeroDemande(),
          r.beneficiaire(),
          aTraiter);
    }
  }

  /** Nombre de remboursements par statut. */
  /**
   * Nombre par statut.
   *
   * @param aTraiter ce que le profil connecté doit faire (règle « À traiter », US-04.18)
   */
  record CompteursRemboursementsDto(
      long tous, long aTraiter, long demande, long valide, long execute, long refuse) {}

  record PageRemboursementsDto(
      List<RemboursementDto> elements,
      int page,
      int taille,
      long total,
      int totalPages,
      CompteursRemboursementsDto compteurs) {}

  /** Motif d'un refus (10 à 2 000 caractères). */
  record MotifRemboursementDto(@NotBlank @Size(min = 10, max = 2000) String motif) {}

  /** Référence de l'ordre de remboursement émis par le Trésor. */
  record ExecutionRemboursementDto(@NotBlank @Size(min = 3, max = 60) String referenceOrdre) {}

  /**
   * La règle vue par le profil connecté.
   *
   * @param rembourserSiRejet règle des demandes déposées aujourd'hui
   * @param peutProposer administrateur, sans proposition en attente
   * @param peutDecider Ministre
   */
  record DetailRegleRemboursementDto(
      boolean rembourserSiRejet,
      List<VersionRegleRemboursementDto> versions,
      boolean peutProposer,
      boolean peutDecider) {}

  /**
   * Une version de la règle.
   *
   * @param etat {@code a_valider}, {@code en_vigueur}, {@code programmee}, {@code remplacee},
   *     {@code refusee} ou {@code retiree}
   */
  record VersionRegleRemboursementDto(
      int version,
      String etat,
      boolean rembourserSiRejet,
      LocalDate dateEffet,
      String motif,
      @Schema(nullable = true) String proposeePar,
      Instant proposeeLe,
      @Schema(nullable = true) String decideePar,
      @Schema(nullable = true) Instant decideeLe,
      @Schema(nullable = true) String motifRefus) {

    static VersionRegleRemboursementDto depuis(RegleRemboursementRepository.Version v) {
      return new VersionRegleRemboursementDto(
          v.version(),
          v.etat(),
          v.rembourserSiRejet(),
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
   * @param rembourserSiRejet rembourser les frais d'une demande rejetée
   * @param dateEffet aujourd'hui ou plus tard (heure de Conakry)
   * @param motif pourquoi ce changement (10 à 2 000 caractères)
   */
  record PropositionRegleRemboursementDto(
      boolean rembourserSiRejet,
      @NotNull LocalDate dateEffet,
      @NotBlank @Size(min = 10, max = 2000) String motif) {}

  record VersionRegleProposeeDto(int version) {}

  record ApprobationRegleDto(LocalDate dateEffet) {}
}
