package gn.gov.mines.emine.paiement.api;

import gn.gov.mines.emine.paiement.application.ServiceReleves;
import gn.gov.mines.emine.paiement.infrastructure.ReleveRepository;
import gn.gov.mines.emine.paiement.infrastructure.ReleveRepository.FiltreLignes;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.pagination.Pagination;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Relevés bancaires du compte des virements (US-04.8), réservés au Responsable fiscal (droit «
 * encaisser ») : import CSV, rapprochement automatique, consultation ligne par ligne.
 */
@RestController
@RequestMapping("/api/v1/releves")
@PreAuthorize("hasAuthority('PERM_ENCAISSER')")
@Tag(name = "Relevés bancaires", description = "Import et rapprochement des virements (US-04.8)")
class ReleveController {

  private final ServiceReleves service;

  ReleveController(ServiceReleves service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      operationId = "importerReleve",
      summary =
          "Importer un relevé CSV (contrat docs/contrats/releve-csv.md) et rapprocher ses lignes ;"
              + " PAY-011 si le fichier a déjà été importé, VAL-001 si son format est invalide")
  ReleveDto importer(
      @RequestPart("fichier") MultipartFile fichier,
      @RequestParam(required = false) String banque,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate dateReleve)
      throws IOException {
    return ReleveDto.depuis(
        service.importer(banque, dateReleve, fichier.getOriginalFilename(), fichier.getBytes()));
  }

  @GetMapping
  @Operation(
      operationId = "listerReleves",
      summary = "Relevés importés, du plus récent au plus ancien, avec leurs résultats")
  PageRelevesDto lister(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer taille) {
    var p = service.lister(Pagination.de(page, taille));
    return new PageRelevesDto(
        p.elements().stream().map(ReleveDto::depuis).toList(),
        p.page(),
        p.taille(),
        p.total(),
        p.totalPages(),
        service.exempleActif());
  }

  @GetMapping("/{id}")
  @Operation(
      operationId = "lireReleve",
      summary = "Un relevé et ses nombres de lignes par résultat")
  ReleveDto lire(@PathVariable long id) {
    return ReleveDto.depuis(service.lire(id));
  }

  @GetMapping("/{id}/lignes")
  @Operation(
      operationId = "listerLignesReleve",
      summary = "Lignes d'un relevé, dans l'ordre du fichier, avec le paiement rapproché")
  PageLignesDto lignes(
      @PathVariable long id,
      @Parameter(description = "rapprochees, a-verifier ou traitees ; absent : toutes")
          @RequestParam(required = false)
          String resultat,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer taille) {
    var p = service.lignes(id, filtre(resultat), Pagination.de(page, taille));
    return new PageLignesDto(
        p.elements().stream().map(LigneReleveDto::depuis).toList(),
        p.page(),
        p.taille(),
        p.total(),
        p.totalPages());
  }

  @GetMapping(path = "/exemple", produces = "text/csv")
  @Operation(
      operationId = "telechargerReleveExemple",
      summary =
          "Relevé d'exemple de démonstration : une ligne conforme par virement déclaré en attente"
              + " (désactivé en recette et production)")
  ResponseEntity<byte[]> exemple() {
    byte[] csv = service.exemple().getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("releve-exemple.csv").build().toString())
        .body(csv);
  }

  private static FiltreLignes filtre(String valeur) {
    if (valeur == null || valeur.isBlank()) {
      return null;
    }
    return switch (valeur) {
      case "rapprochees" -> FiltreLignes.RAPPROCHEES;
      case "a-verifier" -> FiltreLignes.A_VERIFIER;
      case "traitees" -> FiltreLignes.TRAITEES;
      default ->
          throw new ErreurMetier(
              CodeErreur.VAL_001,
              Map.of(
                  "champs",
                  List.of(
                      Map.of(
                          "champ",
                          "resultat",
                          "message",
                          "Valeur attendue : rapprochees, a-verifier ou traitees"))));
    };
  }

  /** Relevé importé et ses résultats. */
  record ReleveDto(
      long id,
      String banque,
      LocalDate dateReleve,
      String nomFichier,
      java.time.Instant importeLe,
      @Schema(nullable = true) String importePar,
      int lignes,
      int rapprochees,
      int ecarts,
      int inconnues,
      int traitees,
      long totalCreditsGnf) {

    static ReleveDto depuis(ReleveRepository.Releve r) {
      return new ReleveDto(
          r.id(),
          r.banque(),
          r.dateReleve(),
          r.nomFichier(),
          r.importeLe(),
          r.importePar(),
          r.lignes(),
          r.rapprochees(),
          r.ecarts(),
          r.inconnues(),
          r.traitees(),
          r.totalCreditsGnf());
    }
  }

  /**
   * Ligne d'un relevé.
   *
   * @param statut {@code rapprochee}, {@code ecart} ou {@code inconnue}
   * @param nature {@code conforme}, {@code inferieur}, {@code superieur}, {@code inconnue}, {@code
   *     doublon}, {@code tardif} ou {@code retour}
   * @param sens {@code C} (crédit) ou {@code D} (débit)
   * @param ecartGnf montant reçu moins montant attendu (débit : négatif)
   * @param decision décision du Responsable fiscal ({@code valide}, {@code rejete}, {@code
   *     associe}, {@code classe}), {@code null} si la ligne n'a pas été traitée (US-04.9)
   */
  record LigneReleveDto(
      long id,
      int numero,
      LocalDate dateOperation,
      String libelle,
      long montantGnf,
      String sens,
      String statut,
      @Schema(nullable = true) String nature,
      @Schema(nullable = true) String referenceLue,
      @Schema(nullable = true) Long ecartGnf,
      @Schema(nullable = true) String referencePaiement,
      @Schema(nullable = true) Long demandeId,
      @Schema(nullable = true) String numeroDemande,
      @Schema(nullable = true) String decision,
      @Schema(nullable = true) String motif) {

    static LigneReleveDto depuis(ReleveRepository.Ligne l) {
      return new LigneReleveDto(
          l.id(),
          l.numero(),
          l.dateOperation(),
          l.libelle(),
          l.montantGnf(),
          String.valueOf(l.sens()),
          l.statut(),
          l.nature(),
          l.referenceLue(),
          l.ecartGnf(),
          l.referencePaiement(),
          l.demandeId(),
          l.numeroDemande(),
          l.decision(),
          l.motif());
    }
  }

  /**
   * Page de relevés.
   *
   * @param exempleDisponible le relevé d'exemple de démonstration peut être téléchargé
   */
  record PageRelevesDto(
      List<ReleveDto> elements,
      int page,
      int taille,
      long total,
      int totalPages,
      boolean exempleDisponible) {}

  record PageLignesDto(
      List<LigneReleveDto> elements, int page, int taille, long total, int totalPages) {}
}
