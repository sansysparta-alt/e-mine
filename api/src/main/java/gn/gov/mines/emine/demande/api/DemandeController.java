package gn.gov.mines.emine.demande.api;

import gn.gov.mines.emine.demande.application.ServiceDemandes;
import gn.gov.mines.emine.demande.application.ServiceDepot;
import gn.gov.mines.emine.demande.domaine.Brouillon;
import gn.gov.mines.emine.demande.domaine.CritereDemandes;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demandes (E-DEMANDES) : brouillon (US-03.1), dépôt (US-03.4), liste « Mes demandes » (US-03.5).
 * Créer et modifier exigent le droit « déposer » (US-01.6) ; la lecture est filtrée par la RLS.
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

  @GetMapping
  @Operation(
      operationId = "listerDemandes",
      summary =
          "Mes demandes (toutes pour un agent), paginées, filtrables par statut, document,"
              + " frais payés, signataire et recherche, triables")
  PageDemandesDto lister(
      @RequestParam(required = false) List<@Pattern(regexp = "^[a-z_]+$") String> statut,
      @RequestParam(required = false) @Pattern(regexp = "^[A-Z]{3}$") String typeDocumentCode,
      @RequestParam(required = false) @Size(max = 100) String q,
      @RequestParam(required = false) Boolean fraisPayes,
      @RequestParam(required = false) @Pattern(regexp = "^[A-Z_]{1,30}$") String signataireProfil,
      @RequestParam(required = false) Boolean aTraiter,
      @RequestParam(required = false) @Pattern(regexp = "^[a-z-]{1,20}$") String vue,
      @RequestParam(required = false) @Pattern(regexp = "^[a-z-]{1,20}$") String groupe,
      @RequestParam(required = false) @Pattern(regexp = "^(numero|date|statut)$") String tri,
      @RequestParam(required = false) @Pattern(regexp = "^(asc|desc)$") String sens,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer taille) {
    CritereDemandes.Tri colonne =
        tri == null
            ? CritereDemandes.Tri.DATE
            : CritereDemandes.Tri.valueOf(tri.toUpperCase(java.util.Locale.ROOT));
    boolean croissant = sens == null ? colonne != CritereDemandes.Tri.DATE : "asc".equals(sens);
    PageResultat<DemandeDto> resultat =
        service
            .lister(
                new CritereDemandes(
                    statut,
                    typeDocumentCode,
                    q,
                    fraisPayes,
                    signataireProfil,
                    groupe != null || Boolean.TRUE.equals(aTraiter)
                        ? service.filtreATraiter(groupe)
                        : null,
                    vue == null ? null : service.vue(vue),
                    false,
                    colonne,
                    croissant),
                Pagination.de(page, taille))
            .map(DemandeDto::depuis);
    return new PageDemandesDto(
        resultat.elements(),
        resultat.page(),
        resultat.taille(),
        resultat.total(),
        resultat.totalPages());
  }

  @GetMapping("/compteurs")
  @Operation(
      operationId = "compterDemandes",
      summary = "Nombre de demandes visibles par statut (tableau de bord)")
  Map<String, Long> compteurs() {
    return service.compteurs();
  }

  @GetMapping("/a-traiter")
  @Operation(
      operationId = "compterATraiter",
      summary =
          "Ce que le profil connecté doit faire maintenant : nombre de demandes par groupe"
              + " (même règle que le filtre aTraiter de la liste)")
  ATraiterDto aTraiter() {
    List<GroupeDto> groupes =
        service.aTraiter().entrySet().stream()
            .map(e -> new GroupeDto(e.getKey().cle(), e.getValue()))
            .toList();
    return new ATraiterDto(groupes.stream().mapToLong(GroupeDto::nombre).sum(), groupes);
  }

  @GetMapping("/vues")
  @Operation(
      operationId = "compterVues",
      summary =
          "Vues de la liste selon le profil (À traiter, En attente du demandeur, En cours…) :"
              + " exclusives, leur somme est le total")
  VuesDto vues() {
    List<GroupeDto> vues =
        service.vues().entrySet().stream()
            .map(e -> new GroupeDto(e.getKey().cle(), e.getValue()))
            .toList();
    return new VuesDto(vues.stream().mapToLong(GroupeDto::nombre).sum(), vues);
  }

  /**
   * Vues du profil connecté.
   *
   * @param total nombre de demandes visibles (somme des vues, exclusives)
   * @param vues vues du profil dans l'ordre d'affichage ({@code a-traiter}, {@code en-cours}…)
   */
  record VuesDto(long total, List<GroupeDto> vues) {}

  /**
   * « À traiter » du profil connecté.
   *
   * @param total nombre de demandes à traiter, tous groupes confondus
   * @param groupes groupes du profil, dans l'ordre d'affichage ({@code a-examiner}, {@code
   *     a-decider}, {@code payer-frais}…), vide s'il n'a rien à faire
   */
  record ATraiterDto(long total, List<GroupeDto> groupes) {}

  /** Un groupe « à traiter » et son nombre de demandes. */
  record GroupeDto(String cle, long nombre) {}

  /** Une page de demandes (modèle de pagination du socle). */
  record PageDemandesDto(
      List<DemandeDto> elements, int page, int taille, long total, int totalPages) {}

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
