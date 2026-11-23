package gn.gov.mines.emine.notification.api;

import gn.gov.mines.emine.notification.application.ServiceCanaux;
import gn.gov.mines.emine.notification.domaine.CanauxEvenement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Canaux des notifications (US-02.7) : pour chaque événement, SMS, e-mail et notification dans
 * l'application, choisis par l'administrateur (chaque SMS a un coût pour le Ministère). Immédiat et
 * tracé ; la base garde au moins un canal par événement et contrôle les droits (V21).
 */
@RestController
@RequestMapping("/api/v1/parametres/notifications")
@Tag(name = "Notifications")
@PreAuthorize("hasAnyAuthority('PERM_PARAMETRER', 'PERM_VALIDER_CATALOGUE')")
class CanauxNotificationController {

  private final ServiceCanaux service;

  CanauxNotificationController(ServiceCanaux service) {
    this.service = service;
  }

  @GetMapping
  @Operation(operationId = "listerCanauxNotification", summary = "Canaux de chaque événement")
  List<CanauxDto> lister() {
    return service.lister().stream().map(CanauxDto::depuis).toList();
  }

  @PutMapping
  @PreAuthorize("hasAuthority('PERM_PARAMETRER')")
  @Operation(
      operationId = "modifierCanauxNotification",
      summary = "Enregistre les canaux de chaque événement (au moins un canal par événement)")
  List<CanauxDto> modifier(
      @Valid @RequestBody @NotNull @Size(max = 50) List<@Valid ChoixDto> choix) {
    List<CanauxEvenement> saisis =
        choix.stream()
            .map(c -> new CanauxEvenement(c.modele(), null, null, c.sms(), c.email(), c.interne()))
            .toList();
    return service.enregistrer(saisis).stream().map(CanauxDto::depuis).toList();
  }

  /** Canaux d'un événement. */
  record CanauxDto(
      String modele,
      String libelleFr,
      String libelleEn,
      boolean sms,
      boolean email,
      boolean interne) {

    static CanauxDto depuis(CanauxEvenement c) {
      return new CanauxDto(
          c.modele(), c.libelleFr(), c.libelleEn(), c.sms(), c.email(), c.interne());
    }
  }

  /** Choix de l'administrateur pour un événement. */
  record ChoixDto(
      @Pattern(regexp = "^[A-Z_]{3,40}$") String modele,
      boolean sms,
      boolean email,
      boolean interne) {}
}
