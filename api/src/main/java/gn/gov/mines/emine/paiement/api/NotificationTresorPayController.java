package gn.gov.mines.emine.paiement.api;

import gn.gov.mines.emine.paiement.application.ServiceNotifications;
import gn.gov.mines.emine.paiement.application.ServiceNotifications.Issue;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notification de TrésorPay (US-04.4, contrat §4). Adresse sans connexion utilisateur : la
 * signature HMAC-SHA256 du corps brut, le marchand et l'horodatage en tiennent lieu. Le corps est
 * lu en octets bruts pour vérifier la signature avant tout décodage.
 */
@RestController
@Tag(name = "Paiements")
class NotificationTresorPayController {

  private final ServiceNotifications service;

  NotificationTresorPayController(ServiceNotifications service) {
    this.service = service;
  }

  /** Accusé de réception. */
  record AccuseDto(boolean recu, String issue) {}

  @PostMapping(path = "/api/v1/paiements/notifications/tresorpay", consumes = "application/json")
  @Operation(
      operationId = "recevoirNotificationTresorPay",
      summary =
          "Notification signée de TrésorPay (serveur à serveur) : 200 si traitée ou déjà reçue,"
              + " 401 PAY-004 si la signature est invalide, 404 PAY-005 si la référence est"
              + " inconnue")
  AccuseDto recevoir(
      @RequestBody byte[] corps,
      @RequestHeader(name = "X-TresorPay-Marchand", required = false) String marchand,
      @RequestHeader(name = "X-TresorPay-Horodatage", required = false) String horodatage,
      @RequestHeader(name = "X-TresorPay-Signature", required = false) String signature,
      HttpServletRequest requete) {
    // Appel direct de TrésorPay (pas du BFF) : l'adresse de la connexion fait foi.
    Issue issue = service.recevoir(corps, marchand, horodatage, signature, requete.getRemoteAddr());
    if (issue == Issue.REFERENCE_INCONNUE) {
      throw new ErreurMetier(CodeErreur.PAY_005);
    }
    return new AccuseDto(true, issue.name());
  }
}
