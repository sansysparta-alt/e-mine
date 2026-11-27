package gn.gov.mines.emine.simulateur.tresorpay;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Interrogation d'une transaction par e-mine (contrat §5, US-04.5) : quand la notification n'est
 * pas arrivée, e-mine demande l'issue de la dernière session d'une référence. Requête signée sur un
 * corps vide.
 */
@RestController
class TransactionsApiController {

  private static final Logger LOG = LoggerFactory.getLogger(TransactionsApiController.class);

  private final ProprietesSimulateur proprietes;
  private final RegistreSessions registre;
  private final Clock horloge;

  TransactionsApiController(
      ProprietesSimulateur proprietes, RegistreSessions registre, Clock horloge) {
    this.proprietes = proprietes;
    this.registre = registre;
    this.horloge = horloge;
  }

  @GetMapping("/api/v1/transactions/{reference}")
  ResponseEntity<Map<String, Object>> interroger(
      @PathVariable String reference,
      @RequestHeader(name = "X-TresorPay-Marchand", required = false) String marchand,
      @RequestHeader(name = "X-TresorPay-Horodatage", required = false) String horodatage,
      @RequestHeader(name = "X-TresorPay-Signature", required = false) String signature) {
    if (!registre.disponible()) {
      return reponse(HttpStatus.SERVICE_UNAVAILABLE, Map.of("code", "INDISPONIBLE"));
    }
    if (!proprietes.marchandId().equals(marchand)
        || !SignatureHmac.verifier(
            proprietes.secretHmac(),
            horodatage,
            new byte[0],
            signature,
            horloge,
            proprietes.toleranceHorodatage())) {
      LOG.warn("Interrogation refusée : marchand, horodatage ou signature invalide");
      return reponse(HttpStatus.UNAUTHORIZED, Map.of("code", "SIGNATURE_INVALIDE"));
    }
    Optional<SessionPaiement> trouvee = registre.derniereDe(reference);
    if (trouvee.isEmpty()) {
      return reponse(HttpStatus.NOT_FOUND, Map.of("code", "INCONNUE", "reference", reference));
    }
    SessionPaiement s = trouvee.get();
    Map<String, Object> corps = new LinkedHashMap<>();
    corps.put("reference", s.reference());
    SessionPaiement.Resultat r = s.resultat();
    Instant maintenant = horloge.instant();
    if (r != null && r.statut() != null) {
      corps.put("statut", r.statut());
      corps.put("transactionId", r.transactionId());
      if ("PAYE".equals(r.statut())) {
        corps.put("montantGnf", r.montantGnf());
      }
      corps.put("date", r.date() == null ? null : r.date().toString());
    } else {
      corps.put("statut", s.expiree(maintenant) ? "EXPIREE" : "EN_COURS");
    }
    LOG.info("Interrogation de {} : {}", reference, corps.get("statut"));
    return reponse(HttpStatus.OK, corps);
  }

  private static ResponseEntity<Map<String, Object>> reponse(
      HttpStatus statut, Map<String, Object> corps) {
    return ResponseEntity.status(statut).body(corps);
  }
}
