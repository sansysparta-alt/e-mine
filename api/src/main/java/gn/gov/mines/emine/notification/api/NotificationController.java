package gn.gov.mines.emine.notification.api;

import gn.gov.mines.emine.notification.application.ServiceNotificationsInternes;
import gn.gov.mines.emine.notification.application.ServiceNotificationsInternes.NotificationRedigee;
import gn.gov.mines.emine.shared.erreur.FabriqueProblemes;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Centre de notifications de l'application (US-07.2) : chaque utilisateur connecté ne voit que ses
 * propres notifications (RLS), rédigées dans la langue de l'interface ({@code Accept-Language}).
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
class NotificationController {

  private final ServiceNotificationsInternes service;

  NotificationController(ServiceNotificationsInternes service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      operationId = "listerNotifications",
      summary =
          "Mes notifications, des plus récentes aux plus anciennes, paginées ; seulement les non"
              + " lues si demandé")
  PageNotificationsDto lister(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer taille,
      @RequestParam(defaultValue = "false") boolean nonLues,
      HttpServletRequest requete) {
    String langue = FabriqueProblemes.langue(requete) == Locale.ENGLISH ? "en" : "fr";
    PageResultat<NotificationDto> resultat =
        service.lister(Pagination.de(page, taille), nonLues, langue).map(NotificationDto::depuis);
    return new PageNotificationsDto(
        resultat.elements(),
        resultat.page(),
        resultat.taille(),
        resultat.total(),
        resultat.totalPages(),
        service.nonLues(),
        service.toutes());
  }

  @GetMapping("/non-lues")
  @Operation(
      operationId = "compterNotificationsNonLues",
      summary = "Nombre de notifications non lues")
  NonLuesDto nonLues() {
    return new NonLuesDto(service.nonLues());
  }

  @PostMapping("/{id}/lue")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "marquerNotificationLue", summary = "Marquer une notification comme lue")
  void marquerLue(@PathVariable long id) {
    service.marquerLue(id);
  }

  @PostMapping("/lues")
  @Operation(
      operationId = "marquerNotificationsLues",
      summary = "Marquer toutes mes notifications comme lues")
  NonLuesDto marquerToutesLues() {
    service.marquerToutesLues();
    return new NonLuesDto(service.nonLues());
  }

  /**
   * Une notification.
   *
   * @param modele code de l'événement ({@code DEMANDE_DEPOSEE}…)
   * @param titre objet du message, dans la langue de l'interface
   * @param texte texte du message (paragraphes séparés par une ligne vide)
   * @param demandeId demande concernée
   * @param numeroDemande numéro de la demande concernée
   * @param creeLe date de l'événement
   * @param lue la notification a été lue
   * @param montantGnf montant à payer annoncé par le message (droits…), en GNF entiers
   * @param action action encore attendue de l'usager sur ce dossier ({@code payer-droits}, {@code
   *     completer}) ; absente une fois faite
   */
  record NotificationDto(
      long id,
      String modele,
      String titre,
      String texte,
      @Schema(nullable = true) Long demandeId,
      @Schema(nullable = true) String numeroDemande,
      Instant creeLe,
      boolean lue,
      @Schema(nullable = true) Long montantGnf,
      @Schema(
              nullable = true,
              allowableValues = {"payer-droits", "completer"})
          String action) {

    static NotificationDto depuis(NotificationRedigee r) {
      var n = r.notification();
      return new NotificationDto(
          n.id(),
          n.modele(),
          r.titre(),
          r.texte(),
          n.demandeId(),
          n.numeroDemande(),
          n.creeLe(),
          n.lueLe() != null,
          n.contenu().get("montant_gnf") instanceof Number m ? m.longValue() : null,
          n.action());
    }
  }

  /**
   * Page de notifications, avec le nombre de non lues (celui de la pastille de la cloche) et de
   * toutes, pour les filtres « Toutes » et « Non lues ».
   */
  record PageNotificationsDto(
      List<NotificationDto> elements,
      int page,
      int taille,
      long total,
      int totalPages,
      long nonLues,
      long toutes) {}

  /** Nombre de notifications non lues. */
  record NonLuesDto(long nombre) {}
}
