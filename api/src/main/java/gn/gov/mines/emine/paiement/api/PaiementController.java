package gn.gov.mines.emine.paiement.api;

import gn.gov.mines.emine.paiement.application.ServicePaiements;
import gn.gov.mines.emine.paiement.domaine.Paiement;
import gn.gov.mines.emine.shared.jooq.enums.MoyenPaiement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Paiements d'une demande (US-04.2) : ce qui est à payer, historique, choix du moyen. Payer exige
 * le droit « payer » (US-01.6) ; la lecture est filtrée par la RLS.
 */
@RestController
@Validated
@Tag(name = "Paiements")
class PaiementController {

  private final ServicePaiements service;

  PaiementController(ServicePaiements service) {
    this.service = service;
  }

  @GetMapping("/api/v1/demandes/{id}/paiements/a-payer")
  @Operation(
      operationId = "lirePaiementAttendu",
      summary = "Ce qui est à payer pour la demande (montant du barème, moyens proposés)")
  APayerDto aPayer(@PathVariable long id) {
    ServicePaiements.APayer a = service.aPayer(id);
    return new APayerDto(
        a.objet().map(o -> o.objet().getLiteral()).orElse(null),
        a.objet().map(o -> o.montantGnf()).orElse(null),
        a.moyens().stream().map(m -> new MoyenDto(m.moyen().getLiteral(), m.disponible())).toList(),
        a.paiementOuvert().map(Paiement::reference).orElse(null),
        a.paiementOuvert().map(p -> p.moyen().getLiteral()).orElse(null));
  }

  @GetMapping("/api/v1/demandes/{id}/paiements")
  @Operation(
      operationId = "listerPaiementsDemande",
      summary = "Paiements de la demande, du plus récent au plus ancien")
  List<PaiementDto> lister(@PathVariable long id) {
    return service.lister(id).stream().map(PaiementDto::depuis).toList();
  }

  @PostMapping("/api/v1/demandes/{id}/paiements")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PERM_PAYER')")
  @Operation(
      operationId = "choisirMoyenPaiement",
      summary =
          "Choisir le moyen de paiement : crée le paiement au montant du barème (référence"
              + " PAY-AAAA-NNNNNN) et annule un paiement encore ouvert pour le même objet")
  PaiementDto choisir(@PathVariable long id, @Valid @RequestBody ChoixMoyenDto corps) {
    return PaiementDto.depuis(service.choisirMoyen(id, MoyenPaiement.valueOf(corps.moyen())));
  }

  @GetMapping("/api/v1/paiements/{reference}")
  @Operation(operationId = "lirePaiement", summary = "Un paiement, par sa référence")
  PaiementDto lire(@PathVariable @Pattern(regexp = "^PAY-[0-9]{4}-[0-9]{6}$") String reference) {
    return PaiementDto.depuis(service.lireParReference(reference));
  }

  /** Choix du moyen : aucun n'est présélectionné, le montant n'est jamais transmis. */
  record ChoixMoyenDto(@NotNull @Pattern(regexp = "^(TRESORPAY|VIREMENT)$") String moyen) {}

  /** Moyen proposé à l'écran. */
  record MoyenDto(String moyen, boolean disponible) {}

  /**
   * Paiement attendu.
   *
   * @param objet objet attendu, {@code null} si rien n'est à payer à ce stade (ou déjà payé)
   * @param montantGnf montant du barème, en GNF entiers
   * @param referenceOuverte paiement en cours pour cet objet (initié ou en attente), s'il existe
   */
  record APayerDto(
      @Schema(nullable = true) String objet,
      @Schema(nullable = true) Long montantGnf,
      List<MoyenDto> moyens,
      @Schema(nullable = true) String referenceOuverte,
      @Schema(nullable = true) String moyenOuvert) {}
}
