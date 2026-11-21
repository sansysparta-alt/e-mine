package gn.gov.mines.emine.instruction.api;

import gn.gov.mines.emine.instruction.application.ServiceRecevabilite;
import gn.gov.mines.emine.instruction.domaine.RegleRecevabilite.Examen;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recevabilité d'une demande par l'agent du guichet (US-05.1) : examen des conditions, puis
 * déclaration recevable ou non recevable. Réservé au droit « instruire » (US-01.6).
 */
@RestController
@RequestMapping("/api/v1/demandes/{id}/recevabilite")
@Tag(name = "Instruction")
@PreAuthorize("hasAuthority('PERM_INSTRUIRE')")
class RecevabiliteController {

  private final ServiceRecevabilite service;

  RecevabiliteController(ServiceRecevabilite service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      operationId = "examinerRecevabilite",
      summary = "Conditions de la recevabilité : frais payés, pièces obligatoires fournies")
  ExamenRecevabiliteDto examiner(@PathVariable long id) {
    return ExamenRecevabiliteDto.depuis(service.examiner(id));
  }

  @PostMapping
  @Operation(
      operationId = "declarerRecevabilite",
      summary =
          "Déclarer la demande recevable, ou non recevable avec un motif (10 à 2000 caractères)")
  ResultatRecevabiliteDto declarer(
      @PathVariable long id, @Valid @RequestBody DeclarationRecevabiliteDto corps) {
    String statut =
        Boolean.TRUE.equals(corps.recevable())
            ? service.declarerRecevable(id)
            : service.declarerNonRecevable(id, corps.motif());
    return new ResultatRecevabiliteDto(id, statut);
  }

  /**
   * Déclaration de l'agent.
   *
   * @param motif obligatoire si la demande n'est pas recevable, ignoré sinon
   */
  record DeclarationRecevabiliteDto(
      @NotNull Boolean recevable, @Schema(nullable = true) String motif) {}

  /** Nouveau statut de la demande ({@code recevable} ou {@code rejetee}). */
  record ResultatRecevabiliteDto(long demandeId, String statut) {}

  /**
   * Conditions de la recevabilité.
   *
   * @param aExaminer la demande est au statut « déposée »
   * @param piecesManquantes pièces obligatoires du catalogue sans fichier
   * @param peutEtreRecevable toutes les conditions sont réunies
   */
  record ExamenRecevabiliteDto(
      boolean aExaminer,
      boolean fraisPayes,
      List<String> piecesManquantes,
      boolean peutEtreRecevable) {

    static ExamenRecevabiliteDto depuis(Examen e) {
      return new ExamenRecevabiliteDto(
          e.aExaminer(), e.fraisPayes(), e.piecesManquantes(), e.peutEtreRecevable());
    }
  }
}
