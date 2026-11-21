package gn.gov.mines.emine.instruction.api;

import gn.gov.mines.emine.instruction.application.ServiceDecision;
import gn.gov.mines.emine.instruction.application.ServiceDecision.EtatDecision;
import gn.gov.mines.emine.instruction.domaine.DecisionRendue;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Décision du signataire (US-05.4). La lecture est ouverte à qui voit la demande (RLS) : le
 * demandeur y lit la décision et son motif ; décider exige le droit « signer » et d'être le
 * signataire que le catalogue désigne pour ce document (D4).
 */
@RestController
@RequestMapping("/api/v1/demandes/{id}/decision")
@Tag(name = "Instruction")
class DecisionController {

  private final ServiceDecision service;

  DecisionController(ServiceDecision service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      operationId = "lireDecision",
      summary = "Signataire attendu, décision possible ou non, décision rendue")
  EtatDecisionDto lire(@PathVariable long id) {
    return EtatDecisionDto.depuis(service.etat(id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_SIGNER')")
  @Operation(
      operationId = "decider",
      summary =
          "Décision favorable (observations facultatives) ou rejet (motif de 10 à 2000"
              + " caractères), par le signataire du catalogue")
  ResultatDecisionDto decider(@PathVariable long id, @Valid @RequestBody DecisionDto corps) {
    return new ResultatDecisionDto(
        id, service.decider(id, Boolean.TRUE.equals(corps.favorable()), corps.motif()));
  }

  /**
   * Décision du signataire.
   *
   * @param motif obligatoire pour un rejet ; observations facultatives pour une décision favorable
   */
  record DecisionDto(@NotNull Boolean favorable, @Schema(nullable = true) String motif) {}

  /**
   * Nouveau statut : {@code decision} (droits à payer), {@code droits_payes} ou {@code rejetee}.
   */
  record ResultatDecisionDto(long demandeId, String statut) {}

  /**
   * État de la décision d'une demande. La décision rendue est à plat (champs {@code decision…},
   * tous {@code null} tant qu'il n'y en a pas) : springdoc n'applique pas {@code nullable} à un
   * objet imbriqué (journal {@code docs/02} §6, J2).
   *
   * @param signataire libellé du signataire du catalogue (D4)
   * @param signataireProfil code du profil signataire
   * @param droitsGnf droits à payer après une décision favorable, {@code null} s'il n'y en a pas
   * @param decisionFavorable sens de la décision rendue
   * @param decisionMotif motif du rejet ou observations
   * @param decisionAuteur prénom et nom du signataire qui a décidé
   * @param decisionAuteurProfil libellé de son profil
   */
  record EtatDecisionDto(
      boolean aDecider,
      String signataire,
      String signataireProfil,
      boolean peutDecider,
      @Schema(nullable = true) Long droitsGnf,
      @Schema(nullable = true) Boolean decisionFavorable,
      @Schema(nullable = true) String decisionMotif,
      @Schema(nullable = true) Instant decisionRenduLe,
      @Schema(nullable = true) String decisionAuteur,
      @Schema(nullable = true) String decisionAuteurProfil) {

    static EtatDecisionDto depuis(EtatDecision e) {
      DecisionRendue d = e.decision().orElse(null);
      return new EtatDecisionDto(
          e.aDecider(),
          e.signataire(),
          e.signataireProfil(),
          e.peutDecider(),
          e.droitsGnf().orElse(null),
          d == null ? null : d.favorable(),
          d == null ? null : d.motif(),
          d == null ? null : d.renduLe(),
          d == null ? null : d.auteur(),
          d == null ? null : d.auteurProfil());
    }
  }
}
