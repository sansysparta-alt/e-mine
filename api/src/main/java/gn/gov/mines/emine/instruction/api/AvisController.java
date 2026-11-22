package gn.gov.mines.emine.instruction.api;

import gn.gov.mines.emine.instruction.application.ServiceAvis;
import gn.gov.mines.emine.instruction.application.ServiceAvis.EtatAvis;
import gn.gov.mines.emine.instruction.domaine.AvisRendu;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Avis techniques motivés (US-05.3) : lecture réservée aux profils internes, avis rendu par un
 * profil que le catalogue désigne pour ce document (droit « instruire »).
 */
@RestController
@RequestMapping("/api/v1/demandes/{id}/avis")
@Tag(name = "Instruction")
class AvisController {

  private final ServiceAvis service;

  AvisController(ServiceAvis service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      operationId = "lireAvis",
      summary = "Avis requis par le catalogue, rendus ou attendus, et avis à rendre par moi")
  EtatAvisDto lire(@PathVariable long id) {
    return EtatAvisDto.depuis(service.etat(id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_INSTRUIRE')")
  @Operation(
      operationId = "rendreAvis",
      summary = "Rendre mon avis motivé (favorable ou défavorable, motif de 10 à 2000 caractères)")
  ResultatAvisDto rendre(@PathVariable long id, @Valid @RequestBody AvisDto corps) {
    return new ResultatAvisDto(
        id, service.rendre(id, Boolean.TRUE.equals(corps.favorable()), corps.motif()));
  }

  /** Avis du profil connecté (motif toujours obligatoire). */
  record AvisDto(@NotNull Boolean favorable, @Schema(nullable = true) String motif) {}

  /** Statut de la demande après l'avis ({@code avis_rendu} quand tous les avis sont rendus). */
  record ResultatAvisDto(long demandeId, String statut) {}

  /**
   * Avis de la demande.
   *
   * @param peutRendre l'utilisateur connecté doit rendre son avis maintenant
   */
  record EtatAvisDto(List<LigneAvisDto> requis, boolean peutRendre) {

    static EtatAvisDto depuis(EtatAvis e) {
      return new EtatAvisDto(
          e.requis().stream()
              .map(
                  l -> {
                    AvisRendu a = l.avis().orElse(null);
                    return new LigneAvisDto(
                        l.profilCode(),
                        l.libelle(),
                        a != null,
                        a == null ? null : a.favorable(),
                        a == null ? null : a.motif(),
                        a == null ? null : a.auteur(),
                        a == null ? null : a.renduLe());
                  })
              .toList(),
          e.peutRendre());
    }
  }

  /**
   * Un avis requis : à plat, champs {@code null} tant qu'il n'est pas rendu (journal {@code
   * docs/02} §6, J2 : springdoc et objets imbriqués facultatifs).
   */
  record LigneAvisDto(
      String profil,
      String libelle,
      boolean rendu,
      @Schema(nullable = true) Boolean favorable,
      @Schema(nullable = true) String motif,
      @Schema(nullable = true) String auteur,
      @Schema(nullable = true) Instant renduLe) {}
}
