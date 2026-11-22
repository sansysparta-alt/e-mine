package gn.gov.mines.emine.instruction.api;

import gn.gov.mines.emine.instruction.application.ServiceComplements;
import gn.gov.mines.emine.instruction.application.ServiceComplements.EtatComplements;
import gn.gov.mines.emine.instruction.domaine.Complement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Compléments (US-05.2). La lecture est ouverte à qui voit la demande (RLS) ; demander, relancer et
 * clore exigent le droit « instruire » ; répondre exige le droit « déposer » et d'être le
 * demandeur.
 */
@RestController
@RequestMapping("/api/v1/demandes/{id}/complements")
@Tag(name = "Instruction")
class ComplementController {

  private final ServiceComplements service;

  ComplementController(ServiceComplements service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      operationId = "lireComplements",
      summary = "Compléments de la demande et actions possibles pour l'utilisateur connecté")
  EtatComplementsDto lire(@PathVariable long id, Authentication authentification) {
    boolean instruit =
        authentification.getAuthorities().stream()
            .anyMatch(a -> "PERM_INSTRUIRE".equals(a.getAuthority()));
    return EtatComplementsDto.depuis(service.etat(id, instruit));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_INSTRUIRE')")
  @Operation(
      operationId = "demanderComplement",
      summary =
          "Demander un complément : motif (10 à 2000 caractères) et pièces attendues ; délai de"
              + " réponse de 5 jours")
  ComplementDto demander(@PathVariable long id, @Valid @RequestBody DemandeComplementDto corps) {
    return ComplementDto.depuis(service.demander(id, corps.motif(), corps.pieces()));
  }

  @PostMapping("/reponse")
  @PreAuthorize("hasAuthority('PERM_DEPOSER')")
  @Operation(
      operationId = "repondreComplement",
      summary =
          "Répondre au complément : pièces demandées jointes, ou réponse écrite si aucune pièce"
              + " n'est demandée")
  ResultatComplementDto repondre(
      @PathVariable long id, @Valid @RequestBody ReponseComplementDto corps) {
    return new ResultatComplementDto(id, service.repondre(id, corps.message()));
  }

  @PostMapping("/relance")
  @PreAuthorize("hasAuthority('PERM_INSTRUIRE')")
  @Operation(
      operationId = "relancerComplement",
      summary = "Relancer le demandeur après le délai de réponse : nouveau délai de 5 jours")
  ComplementDto relancer(@PathVariable long id) {
    return ComplementDto.depuis(service.relancer(id));
  }

  @PostMapping("/cloture")
  @PreAuthorize("hasAuthority('PERM_INSTRUIRE')")
  @Operation(
      operationId = "cloreComplement",
      summary =
          "Clore le dossier sans réponse après le délai : la demande est rejetée avec le motif")
  ResultatComplementDto clore(
      @PathVariable long id, @Valid @RequestBody ClotureComplementDto corps) {
    return new ResultatComplementDto(id, service.clore(id, corps.motif()));
  }

  /** Demande de complément ({@code pieces} : types de pièces attendues, facultatif). */
  record DemandeComplementDto(
      @Schema(nullable = true) String motif, @Schema(nullable = true) List<String> pieces) {}

  /** Réponse du demandeur ({@code message} obligatoire si aucune pièce n'est demandée). */
  record ReponseComplementDto(@Schema(nullable = true) String message) {}

  /** Clôture sans réponse (motif communiqué au demandeur). */
  record ClotureComplementDto(@Schema(nullable = true) String motif) {}

  /** Nouveau statut de la demande. */
  record ResultatComplementDto(long demandeId, String statut) {}

  /**
   * Compléments et actions possibles.
   *
   * @param peutDemander l'utilisateur peut demander un complément à cette étape
   * @param peutRepondre l'utilisateur est le demandeur et un complément attend sa réponse
   * @param delaiDepasse le complément ouvert a dépassé son délai : l'agent relance ou clôt
   * @param piecesJointesDepuis pièces demandées déjà jointes depuis la demande de complément
   */
  record EtatComplementsDto(
      List<ComplementDto> complements,
      boolean peutDemander,
      boolean peutRepondre,
      boolean delaiDepasse,
      List<String> piecesJointesDepuis) {

    static EtatComplementsDto depuis(EtatComplements e) {
      return new EtatComplementsDto(
          e.complements().stream().map(ComplementDto::depuis).toList(),
          e.peutDemander(),
          e.peutRepondre(),
          e.delaiDepasse(),
          e.piecesJointesDepuis());
    }
  }

  /**
   * Un complément.
   *
   * @param statutOrigine étape où la demande retourne après la réponse
   * @param auteur agent qui a demandé le complément
   */
  record ComplementDto(
      long id,
      boolean ouvert,
      String statutOrigine,
      String motif,
      List<String> piecesDemandees,
      Instant demandeLe,
      Instant dateLimite,
      @Schema(nullable = true) Instant relanceLe,
      @Schema(nullable = true) Instant reponduLe,
      @Schema(nullable = true) String reponse,
      @Schema(nullable = true) Instant closLe,
      String auteur,
      String auteurProfil) {

    static ComplementDto depuis(Complement c) {
      return new ComplementDto(
          c.id(),
          c.ouvert(),
          c.statutOrigine(),
          c.motif(),
          c.piecesDemandees(),
          c.demandeLe(),
          c.dateLimite(),
          c.relanceLe(),
          c.reponduLe(),
          c.reponse(),
          c.closLe(),
          c.auteur(),
          c.auteurProfil());
    }
  }
}
