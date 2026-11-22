package gn.gov.mines.emine.demande.api;

import gn.gov.mines.emine.demande.application.ServicePassages;
import gn.gov.mines.emine.demande.domaine.Passage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
 * « Passer ce dossier » dans le traitement en série (correctifs ergonomie) : passage tracé avec un
 * motif court, visible de tous les agents ; « Bloqué » au 3e passage à la même étape.
 */
@RestController
@RequestMapping("/api/v1/demandes/{id}/passages")
@Tag(name = "Demandes")
class PassageController {

  private final ServicePassages service;

  PassageController(ServicePassages service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      operationId = "listerPassages",
      summary = "Passages du dossier dans le traitement en série (profils internes)")
  PassagesDto lister(@PathVariable long id) {
    return PassagesDto.depuis(service.lister(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyAuthority('PERM_INSTRUIRE', 'PERM_SIGNER')")
  @Operation(
      operationId = "passerDossier",
      summary = "Passer le dossier sans agir, avec un motif court (3 à 300 caractères)")
  PassagesDto passer(@PathVariable long id, @Valid @RequestBody PassageDto corps) {
    return PassagesDto.depuis(service.passer(id, corps.motif()));
  }

  /** Motif du passage. */
  record PassageDto(@NotBlank @Size(min = 3, max = 300) String motif) {}

  /**
   * Passages du dossier.
   *
   * @param aLEtape nombre de passages depuis l'entrée dans le statut actuel
   * @param bloque passé au moins 3 fois à cette étape
   * @param passages du plus récent au plus ancien
   */
  record PassagesDto(int aLEtape, boolean bloque, List<LignePassageDto> passages) {

    static PassagesDto depuis(Passage.Historique h) {
      return new PassagesDto(
          h.aLEtape(),
          h.bloque(),
          h.passages().stream()
              .map(p -> new LignePassageDto(p.auteur(), p.statut(), p.motif(), p.le()))
              .toList());
    }
  }

  /** Un passage : agent, statut du moment, motif, date. */
  record LignePassageDto(String auteur, String statut, String motif, Instant le) {}
}
