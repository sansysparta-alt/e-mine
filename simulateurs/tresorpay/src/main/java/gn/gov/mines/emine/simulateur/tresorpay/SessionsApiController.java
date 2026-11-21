package gn.gov.mines.emine.simulateur.tresorpay;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Initiation d'une session de paiement par e-mine (contrat §3). La signature est vérifiée sur le
 * corps brut, avant tout décodage JSON.
 */
@RestController
class SessionsApiController {

  private static final Logger LOG = LoggerFactory.getLogger(SessionsApiController.class);
  private static final Pattern REFERENCE = Pattern.compile("^PAY-[0-9]{4}-[0-9]{6}$");

  /** Corps attendu de {@code POST /api/v1/sessions}. */
  record DemandeSession(String reference, Long montantGnf, String libelle, String urlRetour) {}

  /** Réponse {@code 201}. */
  record SessionOuverte(String sessionId, String urlPaiement, Instant expireLe) {}

  private final ProprietesSimulateur proprietes;
  private final RegistreSessions registre;
  private final JsonMapper json;
  private final Clock horloge;

  SessionsApiController(
      ProprietesSimulateur proprietes, RegistreSessions registre, JsonMapper json, Clock horloge) {
    this.proprietes = proprietes;
    this.registre = registre;
    this.json = json;
    this.horloge = horloge;
  }

  @PostMapping(path = "/api/v1/sessions", consumes = "application/json")
  ResponseEntity<?> ouvrir(
      @RequestBody byte[] corps,
      @RequestHeader(name = "X-TresorPay-Marchand", required = false) String marchand,
      @RequestHeader(name = "X-TresorPay-Horodatage", required = false) String horodatage,
      @RequestHeader(name = "X-TresorPay-Signature", required = false) String signature) {
    if (!registre.disponible()) {
      return erreur(HttpStatus.SERVICE_UNAVAILABLE, "INDISPONIBLE", "TrésorPay est indisponible.");
    }
    if (!proprietes.marchandId().equals(marchand)
        || !SignatureHmac.verifier(
            proprietes.secretHmac(),
            horodatage,
            corps,
            signature,
            horloge,
            proprietes.toleranceHorodatage())) {
      LOG.warn("Initiation refusée : marchand, horodatage ou signature invalide");
      return erreur(HttpStatus.UNAUTHORIZED, "SIGNATURE_INVALIDE", "Signature refusée.");
    }
    DemandeSession demande;
    try {
      demande = json.readValue(corps, DemandeSession.class);
    } catch (JacksonException e) {
      return erreur(HttpStatus.BAD_REQUEST, "REQUETE_INVALIDE", "Corps JSON illisible.");
    }
    String probleme = valider(demande);
    if (probleme != null) {
      return erreur(HttpStatus.BAD_REQUEST, "REQUETE_INVALIDE", probleme);
    }
    Instant maintenant = horloge.instant();
    SessionPaiement session =
        new SessionPaiement(
            UUID.randomUUID().toString(),
            demande.reference(),
            demande.montantGnf(),
            demande.libelle().strip(),
            demande.urlRetour(),
            maintenant,
            maintenant.plus(proprietes.dureeSession()),
            null);
    registre.enregistrer(session);
    URI urlPaiement =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/payer/{id}")
            .buildAndExpand(session.id())
            .toUri();
    LOG.info("Session ouverte pour {} ({} GNF)", session.reference(), session.montantGnf());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new SessionOuverte(session.id(), urlPaiement.toString(), session.expireLe()));
  }

  private String valider(DemandeSession d) {
    if (d.reference() == null || !REFERENCE.matcher(d.reference()).matches()) {
      return "reference doit suivre le format PAY-AAAA-NNNNNN.";
    }
    if (d.montantGnf() == null || d.montantGnf() <= 0) {
      return "montantGnf doit être un entier positif.";
    }
    if (d.libelle() == null || d.libelle().isBlank() || d.libelle().length() > 200) {
      return "libelle est obligatoire (200 caractères au plus).";
    }
    if (d.urlRetour() == null || !d.urlRetour().startsWith(proprietes.urlRetourAutorisee())) {
      return "urlRetour n’est pas une adresse de retour autorisée pour ce marchand.";
    }
    return null;
  }

  private static ResponseEntity<Map<String, String>> erreur(
      HttpStatus statut, String code, String message) {
    return ResponseEntity.status(statut).body(Map.of("code", code, "message", message));
  }
}
