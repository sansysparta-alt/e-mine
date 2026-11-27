package gn.gov.mines.emine.paiement.api;

import gn.gov.mines.emine.paiement.application.ServiceEcarts;
import gn.gov.mines.emine.paiement.domaine.RegleEcarts.Decision;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.pagination.Pagination;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * File « À vérifier » du Responsable fiscal (US-04.9) : écarts de rapprochement, lignes sans
 * référence, paiements en ligne au montant divergent ; une décision motivée et tracée par élément.
 */
@RestController
@Validated
@RequestMapping("/api/v1/encaissements")
@PreAuthorize("hasAuthority('PERM_ENCAISSER')")
@Tag(name = "Encaissements", description = "Écarts à vérifier (US-04.9)")
class EcartController {

  private final ServiceEcarts service;

  EcartController(ServiceEcarts service) {
    this.service = service;
  }

  @GetMapping("/a-verifier")
  @Operation(
      operationId = "listerAVerifier",
      summary =
          "Éléments à vérifier, les plus anciens d'abord, avec les décisions possibles pour"
              + " chacun (règle unique de l'API)")
  PageAVerifierDto lister(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer taille) {
    var p = service.lister(Pagination.de(page, taille));
    return new PageAVerifierDto(
        p.elements().stream().map(ElementAVerifierDto::depuis).toList(),
        p.page(),
        p.taille(),
        p.total(),
        p.totalPages());
  }

  @PostMapping("/lignes/{id}/decision")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      operationId = "deciderLigneReleve",
      summary =
          "Décision sur une ligne de relevé : valider, rejeter, associer (référence du paiement)"
              + " ou classer ; motif obligatoire pour rejeter ou classer ; PAY-013 si la décision"
              + " n'est pas possible, PAY-014 si le paiement à associer ne convient pas")
  void deciderLigne(@PathVariable long id, @Valid @RequestBody DecisionEcartDto corps) {
    service.deciderLigne(id, decision(corps.decision()), corps.motif(), corps.reference());
  }

  @PostMapping("/paiements/{reference}/decision")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      operationId = "deciderPaiementAVerifier",
      summary =
          "Décision sur un paiement en ligne au montant divergent : valider (montant reçu au"
              + " moins égal) ou rejeter avec motif")
  void deciderPaiement(
      @PathVariable @Pattern(regexp = "^PAY-[0-9]{4}-[0-9]{6}$") String reference,
      @Valid @RequestBody DecisionEcartDto corps) {
    service.deciderPaiement(reference, decision(corps.decision()), corps.motif());
  }

  private static Decision decision(String valeur) {
    return Decision.depuis(valeur)
        .orElseThrow(
            () ->
                new ErreurMetier(
                    CodeErreur.VAL_001,
                    Map.of(
                        "champs",
                        List.of(
                            Map.of(
                                "champ",
                                "decision",
                                "message",
                                "Valeur attendue : VALIDER, REJETER, COMPLETER, ASSOCIER, REMBOURSER ou CLASSER")))));
  }

  /**
   * Décision du Responsable fiscal.
   *
   * @param decision {@code VALIDER}, {@code REJETER}, {@code COMPLETER} (US-04.12), {@code
   *     ASSOCIER}, {@code REMBOURSER} ou {@code CLASSER}
   * @param motif obligatoire pour rejeter ou demander le complément (communiqué au demandeur), ou
   *     classer
   * @param reference paiement à associer ({@code PAY-AAAA-NNNNNN}), pour {@code ASSOCIER}
   */
  record DecisionEcartDto(
      @NotBlank String decision,
      @Schema(nullable = true) String motif,
      @Schema(nullable = true) String reference) {}

  /**
   * Élément à vérifier.
   *
   * @param type {@code ligne} (ligne de relevé) ou {@code paiement} (paiement en ligne)
   * @param nature {@code inferieur}, {@code superieur}, {@code inconnue}, {@code doublon}, {@code
   *     tardif}, {@code retour}, {@code montant_divergent} ou {@code complement_echu} (complément
   *     attendu dont l'échéance est dépassée, US-04.12)
   * @param decisions décisions possibles, dans l'ordre d'affichage
   */
  record ElementAVerifierDto(
      String type,
      @Schema(nullable = true) Long ligneId,
      @Schema(nullable = true) Long releveId,
      LocalDate dateOperation,
      String libelle,
      long montantGnf,
      String sens,
      String nature,
      @Schema(nullable = true) Long ecartGnf,
      @Schema(nullable = true) String referencePaiement,
      @Schema(nullable = true) Long montantAttenduGnf,
      @Schema(nullable = true) String moyen,
      @Schema(nullable = true) Long demandeId,
      @Schema(nullable = true) String numeroDemande,
      Instant signaleLe,
      List<String> decisions) {

    static ElementAVerifierDto depuis(ServiceEcarts.ElementAVerifier a) {
      var e = a.element();
      return new ElementAVerifierDto(
          e.type(),
          e.ligneId(),
          e.releveId(),
          e.dateOperation(),
          e.libelle(),
          e.montantGnf(),
          e.sens(),
          e.nature(),
          e.ecartGnf(),
          e.referencePaiement(),
          e.montantAttenduGnf(),
          e.moyen(),
          e.demandeId(),
          e.numeroDemande(),
          e.signaleLe(),
          a.decisions().stream().map(Decision::name).toList());
    }
  }

  record PageAVerifierDto(
      List<ElementAVerifierDto> elements, int page, int taille, long total, int totalPages) {}
}
