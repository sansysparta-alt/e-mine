package gn.gov.mines.emine.shared.erreur;

import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpStatus;

/**
 * Catalogue des codes d'erreur de l'API (US-00.8). Familles du cahier des charges : AUTH, PERM,
 * VAL, DEM, PAY, GEO, DOC, SYS. Chaque code a un statut HTTP et un message FR/EN dans {@code
 * messages/erreurs*.properties} (clé {@code erreur.<CODE>}). Les codes GEO arrivent avec le
 * cadastre (lot 2).
 */
public enum CodeErreur {
  /** Non authentifié (jeton absent, expiré ou invalide). */
  AUTH_001("AUTH-001", HttpStatus.UNAUTHORIZED),
  /** Compte verrouillé après 5 échecs (géré par Keycloak, code réservé). */
  AUTH_002("AUTH-002", HttpStatus.LOCKED),
  /** Compte Keycloak non rattaché à un utilisateur e-mine actif. */
  AUTH_003("AUTH-003", HttpStatus.FORBIDDEN),
  /** Le profil ne permet pas l'action. */
  PERM_001("PERM-001", HttpStatus.FORBIDDEN),
  /** Données invalides (détail par champ dans {@code details.champs}). */
  VAL_001("VAL-001", HttpStatus.BAD_REQUEST),
  /** Requête illisible (JSON mal formé, paramètre de type incorrect, format non pris en charge). */
  VAL_002("VAL-002", HttpStatus.BAD_REQUEST),
  /** Transition de statut de demande interdite (trigger {@code fn_verifier_transition}). */
  DEM_001("DEM-001", HttpStatus.CONFLICT),
  /** Demande introuvable (ou non visible pour cet utilisateur). */
  DEM_002("DEM-002", HttpStatus.NOT_FOUND),
  /** Demande non modifiable : elle n'est plus au statut brouillon. */
  DEM_003("DEM-003", HttpStatus.CONFLICT),
  /** Dépôt impossible : demande incomplète (détail dans {@code details.manques}). */
  DEM_004("DEM-004", HttpStatus.UNPROCESSABLE_CONTENT),
  /** Type de document inconnu ou inactif. */
  DEM_005("DEM-005", HttpStatus.UNPROCESSABLE_CONTENT),
  /** Recevabilité impossible : frais de dossier non payés (trigger, V8). */
  DEM_006("DEM-006", HttpStatus.CONFLICT),
  /**
   * Recevabilité impossible : pièce obligatoire sans fichier ({@code details.piecesManquantes}).
   */
  DEM_007("DEM-007", HttpStatus.UNPROCESSABLE_CONTENT),
  /** Objet déjà payé pour cette demande (index {@code ux_paiement_paye}). */
  PAY_001("PAY-001", HttpStatus.CONFLICT),
  /** Trois tentatives de paiement en ligne atteintes aujourd'hui pour cet objet (US-04.3). */
  PAY_002("PAY-002", HttpStatus.TOO_MANY_REQUESTS),
  /** TrésorPay indisponible (US-04.3) : réessayer plus tard ou choisir le virement. */
  PAY_003("PAY-003", HttpStatus.SERVICE_UNAVAILABLE),
  /** Notification TrésorPay refusée : signature, marchand ou horodatage invalide (US-04.4). */
  PAY_004("PAY-004", HttpStatus.UNAUTHORIZED),
  /** Paiement introuvable (ou non visible pour cet utilisateur). */
  PAY_005("PAY-005", HttpStatus.NOT_FOUND),
  /** Transition de statut de paiement interdite. */
  PAY_006("PAY-006", HttpStatus.CONFLICT),
  /** Aucun paiement n'est attendu pour cette demande à ce stade. */
  PAY_007("PAY-007", HttpStatus.CONFLICT),
  /** Moyen de paiement pas encore proposé (le virement arrive après la V1). */
  PAY_008("PAY-008", HttpStatus.UNPROCESSABLE_CONTENT),
  /** Reçu pas encore disponible : le paiement n'est pas confirmé (US-04.6). */
  PAY_009("PAY-009", HttpStatus.CONFLICT),
  /** Format de fichier non accepté (PDF, JPG ou PNG). */
  DOC_001("DOC-001", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
  /** Fichier trop volumineux (10 Mo au plus). */
  DOC_002("DOC-002", HttpStatus.CONTENT_TOO_LARGE),
  /** Pièce jointe introuvable. */
  DOC_003("DOC-003", HttpStatus.NOT_FOUND),
  /** Erreur interne : un identifiant d'incident est donné dans le message et les détails. */
  SYS_001("SYS-001", HttpStatus.INTERNAL_SERVER_ERROR),
  /** Ressource ou adresse introuvable. */
  SYS_002("SYS-002", HttpStatus.NOT_FOUND),
  /** Service dépendant indisponible (stockage, etc.). */
  SYS_003("SYS-003", HttpStatus.SERVICE_UNAVAILABLE),
  /** Modification concurrente : recharger et réessayer. */
  SYS_004("SYS-004", HttpStatus.CONFLICT),
  /** Méthode HTTP non prise en charge sur cette adresse. */
  SYS_005("SYS-005", HttpStatus.METHOD_NOT_ALLOWED);

  private final String code;
  private final HttpStatus statut;

  CodeErreur(String code, HttpStatus statut) {
    this.code = code;
    this.statut = statut;
  }

  /** Code public, par exemple {@code DEM-001}. */
  public String code() {
    return code;
  }

  public HttpStatus statut() {
    return statut;
  }

  /** Clé du message dans {@code messages/erreurs*.properties}. */
  public String cleMessage() {
    return "erreur." + code;
  }

  /** Code du catalogue correspondant à un code public ({@code DEM-001}), s'il existe. */
  public static Optional<CodeErreur> depuis(String code) {
    return Arrays.stream(values()).filter(c -> c.code.equals(code)).findFirst();
  }
}
