package gn.gov.mines.emine.catalogue.api;

import gn.gov.mines.emine.catalogue.TypeDocument;
import gn.gov.mines.emine.catalogue.application.ServiceFiches;
import gn.gov.mines.emine.catalogue.application.ServiceFiches.Filtre;
import gn.gov.mines.emine.catalogue.domaine.RegleFiche;
import gn.gov.mines.emine.catalogue.domaine.VersionFiche;
import gn.gov.mines.emine.catalogue.domaine.VersionFiche.Profil;
import gn.gov.mines.emine.shared.pagination.Pagination;
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
import java.util.List;
import java.util.Map;
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
 * Administration des fiches du catalogue (US-02.2, US-02.4) : l'administrateur technique propose
 * une version de la fiche d'un document, le Ministre l'approuve ou la refuse (cahier des charges :
 * « paramétrés par l'Administrateur et approuvés par le Ministère »).
 */
@RestController
@RequestMapping("/api/v1/fiches-documents")
@Tag(name = "Catalogue")
@PreAuthorize("hasAnyAuthority('PERM_PARAMETRER', 'PERM_VALIDER_CATALOGUE')")
class FicheDocumentController {

  private final ServiceFiches service;

  FicheDocumentController(ServiceFiches service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      operationId = "listerFichesDocuments",
      summary = "Fiches du catalogue (actives ou non), paginées, avec les propositions en attente")
  PageFichesDto lister(
      @RequestParam(required = false) @Pattern(regexp = "^(TOUS|A_VALIDER|INACTIFS)$")
          String filtre,
      @RequestParam(required = false) @Size(max = 100) String q,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer taille) {
    var l =
        service.lister(
            filtre == null ? Filtre.TOUS : Filtre.valueOf(filtre), q, Pagination.de(page, taille));
    return new PageFichesDto(
        l.page().elements().stream()
            .map(
                e ->
                    new LigneFicheDto(
                        e.document().code(),
                        e.document().libelleFr(),
                        e.document().famille().libelle(),
                        e.document().delaiJours(),
                        e.document().signataire(),
                        e.document().actif(),
                        e.numeroEnVigueur(),
                        e.numeroProposee(),
                        e.proposeeLe(),
                        e.action().name()))
            .toList(),
        l.page().page(),
        l.page().taille(),
        l.page().total(),
        l.page().totalPages(),
        l.compteurs());
  }

  @GetMapping("/references")
  @Operation(
      operationId = "referencesFichesDocuments",
      summary = "Signataires et profils d'avis possibles (formulaire de proposition)")
  ReferencesDto references() {
    var r = service.references();
    return new ReferencesDto(r.signataires(), r.profilsAvis());
  }

  @GetMapping("/{code}")
  @Operation(
      operationId = "lireFicheDocument",
      summary = "Fiche d'un document : version en vigueur, proposition en attente, historique")
  FicheDto lire(@PathVariable @Pattern(regexp = "^[A-Z]{3}$") String code) {
    var f = service.lire(code);
    TypeDocument d = f.document();
    return new FicheDto(
        d.code(),
        d.famille().libelle(),
        d.actif(),
        VersionDto.depuis(f.enVigueur(), List.of()),
        f.proposition() == null ? null : VersionDto.depuis(f.proposition(), f.changements()),
        f.historique().stream().map(h -> VersionDto.depuis(h.version(), h.changements())).toList(),
        f.action().name(),
        f.peutProposer());
  }

  @PostMapping("/{code}/versions")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PERM_PARAMETRER')")
  @Operation(
      operationId = "proposerVersionFiche",
      summary = "Propose une nouvelle version de la fiche (soumise à l'approbation du Ministre)")
  PropositionCreeeDto proposer(
      @PathVariable @Pattern(regexp = "^[A-Z]{3}$") String code,
      @Valid @RequestBody PropositionDto p) {
    return new PropositionCreeeDto(
        service.proposer(
            code,
            new ServiceFiches.Proposition(
                p.libelleFr(),
                p.libelleEn(),
                p.pieces(),
                p.delaiJours(),
                p.instruitPar(),
                p.signataire(),
                p.avis(),
                p.actif(),
                p.motif())));
  }

  @PostMapping("/{code}/versions/{numero}/approbation")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('PERM_VALIDER_CATALOGUE')")
  @Operation(
      operationId = "approuverVersionFiche",
      summary = "Le Ministre approuve la proposition : elle devient la fiche en vigueur")
  void approuver(
      @PathVariable @Pattern(regexp = "^[A-Z]{3}$") String code, @PathVariable int numero) {
    service.approuver(code, numero);
  }

  @PostMapping("/{code}/versions/{numero}/refus")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('PERM_VALIDER_CATALOGUE')")
  @Operation(operationId = "refuserVersionFiche", summary = "Le Ministre refuse la proposition")
  void refuser(
      @PathVariable @Pattern(regexp = "^[A-Z]{3}$") String code,
      @PathVariable int numero,
      @Valid @RequestBody MotifDto motif) {
    service.refuser(code, numero, motif.motif());
  }

  @PostMapping("/{code}/versions/{numero}/retrait")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('PERM_PARAMETRER')")
  @Operation(
      operationId = "retirerVersionFiche",
      summary = "L'administrateur retire sa proposition avant la décision du Ministre")
  void retirer(
      @PathVariable @Pattern(regexp = "^[A-Z]{3}$") String code, @PathVariable int numero) {
    service.retirer(code, numero);
  }

  /** Page de la liste du catalogue (modèle de pagination du socle). */
  record PageFichesDto(
      List<LigneFicheDto> elements,
      int page,
      int taille,
      long total,
      int totalPages,
      Map<Filtre, Long> compteurs) {}

  /**
   * Ligne de la liste du catalogue.
   *
   * @param numeroProposee proposition en attente du Ministre
   * @param action {@code DECIDER} (Ministre), {@code ATTENDRE} (administrateur) ou {@code AUCUNE}
   */
  record LigneFicheDto(
      String code,
      String libelleFr,
      String famille,
      int delaiJours,
      String signataire,
      boolean actif,
      int numeroEnVigueur,
      @Schema(nullable = true) Integer numeroProposee,
      @Schema(nullable = true) Instant proposeeLe,
      String action) {}

  /**
   * Fiche d'un document.
   *
   * @param proposition proposition en attente, avec ses changements par rapport à la fiche en
   *     vigueur
   * @param historique toutes les versions, avec leurs changements par rapport à la fiche en vigueur
   *     au moment de la proposition
   */
  record FicheDto(
      String code,
      String famille,
      boolean actif,
      VersionDto enVigueur,
      @Schema(nullable = true) VersionDto proposition,
      List<VersionDto> historique,
      String action,
      boolean peutProposer) {}

  /** Version d'une fiche. */
  record VersionDto(
      int numero,
      String statut,
      String libelleFr,
      @Schema(nullable = true) String libelleEn,
      List<String> pieces,
      int delaiJours,
      String instruitPar,
      String signataire,
      List<Profil> avis,
      boolean actif,
      String motif,
      @Schema(nullable = true) String proposeePar,
      Instant proposeeLe,
      @Schema(nullable = true) String decideePar,
      @Schema(nullable = true) Instant decideeLe,
      @Schema(nullable = true) String motifRefus,
      List<ChangementFicheDto> changements) {

    static VersionDto depuis(VersionFiche v, List<RegleFiche.Changement> changements) {
      var c = v.contenu();
      return new VersionDto(
          v.numero(),
          v.statut(),
          c.libelleFr(),
          c.libelleEn(),
          c.pieces(),
          c.delaiJours(),
          c.instruitPar(),
          c.signataire(),
          c.avis(),
          c.actif(),
          v.motif(),
          v.proposeePar(),
          v.proposeeLe(),
          v.decideePar(),
          v.decideeLe(),
          v.motifRefus(),
          changements.stream()
              .map(x -> new ChangementFicheDto(x.champ(), x.avant(), x.apres()))
              .toList());
    }
  }

  /**
   * Proposition de l'administrateur.
   *
   * @param pieces pièces requises (1 à 20, sans virgule)
   * @param avis codes des profils qui rendent un avis technique, dans l'ordre
   * @param motif objet de la modification ; motif de la désactivation si {@code actif = false}
   */
  record PropositionDto(
      @NotBlank @Size(min = 3, max = 150) String libelleFr,
      @Size(max = 150) String libelleEn,
      @NotNull @Size(min = 1, max = RegleFiche.PIECES_MAX)
          List<@Size(max = RegleFiche.PIECE_MAX) String> pieces,
      @Min(1) @Max(365) int delaiJours,
      @NotBlank @Size(min = 2, max = 150) String instruitPar,
      @NotBlank String signataire,
      @NotNull @Size(max = 10) List<@Pattern(regexp = "^[A-Z_]{2,30}$") String> avis,
      boolean actif,
      @NotBlank @Size(min = 10, max = 2000) String motif) {}

  /**
   * Champ modifié par une version, avec l'ancienne et la nouvelle valeur en texte.
   *
   * @param champ {@code libelleFr}, {@code libelleEn}, {@code pieces}, {@code delaiJours}, {@code
   *     instruitPar}, {@code signataire}, {@code avis} ou {@code actif}
   */
  record ChangementFicheDto(
      String champ, @Schema(nullable = true) String avant, @Schema(nullable = true) String apres) {}

  /** Numéro de la version proposée. */
  record PropositionCreeeDto(int numero) {}

  /** Motif du refus (10 à 2000 caractères). */
  record MotifDto(@NotBlank @Size(min = 10, max = 2000) String motif) {}

  /** Références du formulaire. */
  record ReferencesDto(List<String> signataires, List<Profil> profilsAvis) {}
}
