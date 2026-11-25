package gn.gov.mines.emine.admin.api;

import gn.gov.mines.emine.admin.application.ServiceAdministration;
import gn.gov.mines.emine.admin.domaine.Supervision;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Espace de l'administrateur technique (droit « paramétrer ») : vue d'ensemble technique et «
 * Utilisateurs & rôles », en lecture seule (les comptes sont gérés dans Keycloak, D8).
 */
@RestController
@RequestMapping("/api/v1/administration")
@Tag(name = "Administration")
@Validated
@PreAuthorize("hasAuthority('PERM_PARAMETRER')")
class AdministrationController {

  private final ServiceAdministration service;

  /** Adresse de la console Keycloak du realm (fiche d'un compte : « …/users/{id}/settings »). */
  private final String consoleKeycloak;

  AdministrationController(
      ServiceAdministration service,
      @org.springframework.beans.factory.annotation.Value(
              "${emine.administration.console-keycloak:}")
          String consoleKeycloak) {
    this.service = service;
    this.consoleKeycloak = consoleKeycloak;
  }

  @GetMapping("/tableau-de-bord")
  @Operation(
      operationId = "lireTableauAdministration",
      summary =
          "Vue d'ensemble technique : comptes par statut et connectés sur 30 jours, comptes par"
              + " profil et droits configurés, disponibilité des services (base, connexion,"
              + " stockage, messagerie)")
  TableauDto tableauDeBord() {
    Supervision.TableauDeBord t = service.tableauDeBord();
    return new TableauDto(
        new ComptesDto(
            t.comptes().actifs(),
            t.comptes().suspendus(),
            t.comptes().invites(),
            t.comptes().connectes30Jours()),
        t.profils().stream()
            .map(
                p ->
                    new ProfilDto(
                        p.code(),
                        p.libelle(),
                        p.interne(),
                        p.utilisateurs(),
                        p.droits(),
                        p.permissions()))
            .toList(),
        t.services().stream().map(s -> new ServiceDto(s.cle(), s.disponible())).toList(),
        t.verifieLe());
  }

  @GetMapping("/alertes")
  @Operation(
      operationId = "lireAlertesAdministration",
      summary =
          "Alertes de l'administrateur technique (cloche) : services indisponibles, comptes"
              + " suspendus, invitations jamais activées ; calculées à la lecture")
  AlertesDto alertes() {
    List<AlerteDto> alertes =
        service.alertes().stream()
            .map(a -> new AlerteDto(a.cle(), a.service(), a.nombre()))
            .toList();
    return new AlertesDto(alertes, alertes.size());
  }

  /**
   * Une alerte.
   *
   * @param cle {@code service}, {@code suspendus} ou {@code invites}
   * @param service clé du service indisponible ({@code base}, {@code stockage}…), sinon absente
   * @param nombre comptes concernés (1 pour un service)
   */
  record AlerteDto(
      @Schema(allowableValues = {"service", "suspendus", "invites"}) String cle,
      @Schema(nullable = true) String service,
      long nombre) {}

  /** Alertes en cours et leur nombre (pastille de la cloche). */
  record AlertesDto(List<AlerteDto> elements, int nombre) {}

  @GetMapping("/utilisateurs")
  @Operation(
      operationId = "listerUtilisateurs",
      summary =
          "Utilisateurs et rôles, du plus récemment connecté au plus ancien, paginés, filtrables"
              + " par profil, statut, connexion pendant les 30 derniers jours et recherche (nom,"
              + " e-mail, organisation)")
  PageUtilisateursDto utilisateurs(
      @RequestParam(required = false) @Pattern(regexp = "^[A-Z_]{2,30}$") String profil,
      @RequestParam(required = false) @Pattern(regexp = "^(actif|suspendu|invite)$") String statut,
      @RequestParam(required = false) Boolean recents,
      @RequestParam(required = false) @Size(max = 100) String q,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer taille) {
    Supervision.PageUtilisateurs resultat =
        service.utilisateurs(
            profil, statut, Boolean.TRUE.equals(recents), q, Pagination.de(page, taille));
    PageResultat<Supervision.Utilisateur> p = resultat.page();
    var c = resultat.compteurs();
    return new PageUtilisateursDto(
        p.elements().stream().map(u -> UtilisateurDto.depuis(u, consoleKeycloak)).toList(),
        p.page(),
        p.taille(),
        p.total(),
        p.totalPages(),
        new CompteursUtilisateursDto(
            c.tous(), c.actifs(), c.suspendus(), c.invites(), c.connectes30Jours()));
  }

  record ComptesDto(long actifs, long suspendus, long invites, long connectes30Jours) {}

  /**
   * Un profil, ses comptes et ses droits.
   *
   * @param permissions codes des droits de la matrice ({@code DEPOSER}, {@code PARAMETRER}…)
   */
  record ProfilDto(
      String code,
      String libelle,
      boolean interne,
      long utilisateurs,
      int droits,
      List<String> permissions) {}

  /** Nombre de comptes de chaque filtre, pour le profil et la recherche choisis. */
  record CompteursUtilisateursDto(
      long tous, long actifs, long suspendus, long invites, long connectes30Jours) {}

  /**
   * @param cle {@code base}, {@code authentification}, {@code stockage} ou {@code messagerie}
   */
  record ServiceDto(String cle, boolean disponible) {}

  record TableauDto(
      ComptesDto comptes, List<ProfilDto> profils, List<ServiceDto> services, Instant verifieLe) {}

  record UtilisateurDto(
      long id,
      String prenom,
      String nom,
      String email,
      @Schema(nullable = true) String organisation,
      String profilCode,
      String profilLibelle,
      String statut,
      @Schema(nullable = true) Instant derniereConnexion,
      @Schema(
              nullable = true,
              description = "Fiche du compte dans la console d'administration de Keycloak (D8)")
          String lienKeycloak) {

    static UtilisateurDto depuis(Supervision.Utilisateur u, String consoleKeycloak) {
      return new UtilisateurDto(
          u.id(),
          u.prenom(),
          u.nom(),
          u.email(),
          u.organisation(),
          u.profilCode(),
          u.profilLibelle(),
          u.statut(),
          u.derniereConnexion(),
          u.keycloakId() == null || consoleKeycloak == null || consoleKeycloak.isBlank()
              ? null
              : consoleKeycloak + "/users/" + u.keycloakId() + "/settings");
    }
  }

  record PageUtilisateursDto(
      List<UtilisateurDto> elements,
      int page,
      int taille,
      long total,
      int totalPages,
      CompteursUtilisateursDto compteurs) {}
}
