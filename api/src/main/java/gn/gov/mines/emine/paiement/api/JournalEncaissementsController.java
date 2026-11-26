package gn.gov.mines.emine.paiement.api;

import gn.gov.mines.emine.paiement.application.ServiceJournalEncaissements;
import gn.gov.mines.emine.paiement.infrastructure.JournalEncaissementsRepository;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.jooq.enums.MoyenPaiement;
import gn.gov.mines.emine.shared.jooq.enums.ObjetPaiement;
import gn.gov.mines.emine.shared.pagination.Pagination;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Journal des encaissements et indicateurs du Responsable fiscal (US-04.11) : recettes et frais
 * remboursés d'une période, filtres par moyen et objet, export CSV identique à l'écran.
 */
@RestController
@RequestMapping("/api/v1/encaissements")
@PreAuthorize("hasAuthority('PERM_ENCAISSER')")
@Tag(name = "Encaissements")
class JournalEncaissementsController {

  private final ServiceJournalEncaissements service;

  JournalEncaissementsController(ServiceJournalEncaissements service) {
    this.service = service;
  }

  @GetMapping("/journal")
  @Operation(
      operationId = "listerJournalEncaissements",
      summary =
          "Journal des encaissements : recettes (paiements acceptés) et frais remboursés sur"
              + " rejet, du plus récent au plus ancien, avec les totaux des mêmes critères")
  PageJournalEncaissementsDto lister(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
      @Parameter(description = "TRESORPAY ou VIREMENT ; absent : tous")
          @RequestParam(required = false)
          String moyen,
      @Parameter(description = "FRAIS_DOSSIER, DROITS, ATTESTATION ou FORMATION ; absent : tous")
          @RequestParam(required = false)
          String objet,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer taille) {
    var r = service.lister(critere(du, au, moyen, objet), Pagination.de(page, taille));
    var p = r.page();
    return new PageJournalEncaissementsDto(
        p.elements().stream().map(LigneJournalEncaissementsDto::depuis).toList(),
        p.page(),
        p.taille(),
        p.total(),
        p.totalPages(),
        TotauxEncaissementsDto.depuis(r.totaux()));
  }

  @GetMapping(path = "/journal.csv", produces = "text/csv")
  @Operation(
      operationId = "exporterJournalEncaissements",
      summary = "Export CSV du journal (mêmes critères que l'écran), pour un tableur")
  ResponseEntity<byte[]> exporter(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
      @RequestParam(required = false) String moyen,
      @RequestParam(required = false) String objet) {
    StringWriter csv = new StringWriter();
    service.exporter(critere(du, au, moyen, objet), csv);
    String nom =
        "journal-encaissements"
            + (du != null ? "-du-" + du : "")
            + (au != null ? "-au-" + au : "")
            + ".csv";
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(nom).build().toString())
        .body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  @GetMapping("/indicateurs")
  @Operation(
      operationId = "lireIndicateursEncaissements",
      summary =
          "Tableau de bord du Responsable fiscal : à vérifier, remboursements à valider, ordres à"
              + " enregistrer (à ce jour) et recettes du mois en cours")
  IndicateursEncaissementsDto indicateurs() {
    var i = service.indicateurs();
    return new IndicateursEncaissementsDto(
        i.aVerifier(),
        i.remboursementsAValider(),
        i.ordresAEnregistrer(),
        TotauxEncaissementsDto.depuis(i.mois()),
        i.debutMois(),
        i.finMois());
  }

  private static JournalEncaissementsRepository.Critere critere(
      LocalDate du, LocalDate au, String moyen, String objet) {
    return new JournalEncaissementsRepository.Critere(du, au, moyen(moyen), objet(objet));
  }

  private static MoyenPaiement moyen(String v) {
    if (v == null || v.isBlank()) {
      return null;
    }
    for (MoyenPaiement m : MoyenPaiement.values()) {
      if (m.getLiteral().equals(v)) {
        return m;
      }
    }
    throw invalide("moyen", "TRESORPAY ou VIREMENT");
  }

  private static ObjetPaiement objet(String v) {
    if (v == null || v.isBlank()) {
      return null;
    }
    for (ObjetPaiement o : ObjetPaiement.values()) {
      if (o.getLiteral().equals(v)) {
        return o;
      }
    }
    throw invalide("objet", "FRAIS_DOSSIER, DROITS, ATTESTATION ou FORMATION");
  }

  private static ErreurMetier invalide(String champ, String attendu) {
    return new ErreurMetier(
        CodeErreur.VAL_001,
        Map.of(
            "champs", List.of(Map.of("champ", champ, "message", "Valeur attendue : " + attendu))));
  }

  /**
   * Ligne du journal.
   *
   * @param type {@code encaissement} (montant positif) ou {@code remboursement} (négatif)
   * @param reference {@code PAY-…} ou {@code RMB-…}
   * @param piece reçu {@code REC-…} d'un encaissement, ordre du Trésor d'un remboursement
   */
  record LigneJournalEncaissementsDto(
      String type,
      Instant date,
      String reference,
      @Schema(nullable = true) String piece,
      long montantGnf,
      String moyen,
      String objet,
      @Schema(nullable = true) Long demandeId,
      @Schema(nullable = true) String numeroDemande,
      String codeDocument,
      @Schema(nullable = true) String payeur) {

    static LigneJournalEncaissementsDto depuis(JournalEncaissementsRepository.Ligne l) {
      return new LigneJournalEncaissementsDto(
          l.type(),
          l.date(),
          l.reference(),
          l.piece(),
          l.montantGnf(),
          l.moyen(),
          l.objet(),
          l.demandeId(),
          l.numeroDemande(),
          l.codeDocument(),
          l.payeur());
    }
  }

  /**
   * Totaux (GNF entiers).
   *
   * @param encaisseGnf recettes : montant dû des paiements acceptés
   * @param rembourseGnf frais remboursés sur rejet d'une demande
   * @param soldeGnf recettes moins frais remboursés
   */
  record TotauxEncaissementsDto(
      long encaisseGnf,
      long rembourseGnf,
      long soldeGnf,
      long nombre,
      long tresorPayGnf,
      long virementGnf) {

    static TotauxEncaissementsDto depuis(JournalEncaissementsRepository.Totaux t) {
      return new TotauxEncaissementsDto(
          t.encaisseGnf(),
          t.rembourseGnf(),
          t.soldeGnf(),
          t.nombre(),
          t.tresorPayGnf(),
          t.virementGnf());
    }
  }

  record PageJournalEncaissementsDto(
      List<LigneJournalEncaissementsDto> elements,
      int page,
      int taille,
      long total,
      int totalPages,
      TotauxEncaissementsDto totaux) {}

  /**
   * Tableau de bord du Responsable fiscal.
   *
   * @param mois recettes du mois en cours (du {@code debutMois} au {@code finMois})
   */
  record IndicateursEncaissementsDto(
      long aVerifier,
      long remboursementsAValider,
      long ordresAEnregistrer,
      TotauxEncaissementsDto mois,
      LocalDate debutMois,
      LocalDate finMois) {}
}
