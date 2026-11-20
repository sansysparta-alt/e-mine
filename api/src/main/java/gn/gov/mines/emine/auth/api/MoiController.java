package gn.gov.mines.emine.auth.api;

import gn.gov.mines.emine.auth.UtilisateurConnecte;
import gn.gov.mines.emine.auth.UtilisateurCourant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Utilisateur connecté (US-01.2) : sert au tableau de bord et à l'en-tête de l'application. */
@RestController
@RequestMapping("/api/v1/moi")
@Tag(name = "Authentification")
class MoiController {

  private final UtilisateurCourant utilisateurCourant;

  MoiController(UtilisateurCourant utilisateurCourant) {
    this.utilisateurCourant = utilisateurCourant;
  }

  @GetMapping
  @Operation(operationId = "lireMoi", summary = "Utilisateur connecté et son profil")
  MoiDto lire() {
    UtilisateurConnecte u =
        utilisateurCourant
            .get()
            .orElseThrow(() -> new IllegalStateException("Requête authentifiée sans compte"));
    return new MoiDto(
        u.id(),
        u.prenom(),
        u.nom(),
        u.email(),
        new MoiDto.Profil(u.profilCode(), u.profilLibelle()),
        u.interne());
  }

  /** Réponse de {@code GET /api/v1/moi}. */
  record MoiDto(long id, String prenom, String nom, String email, Profil profil, boolean interne) {

    /** Profil e-mine (l'un des 16 codes). */
    record Profil(String code, String libelle) {}
  }
}
