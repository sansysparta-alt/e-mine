package gn.gov.mines.emine.shared.erreur;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * Corps de toute réponse d'erreur de l'API (US-00.8) : ProblemDetail (RFC 9457) enrichi de {@code
 * code}, {@code message} (traduit) et {@code details}.
 *
 * @param type toujours {@code about:blank}
 * @param title libellé HTTP du statut
 * @param status statut HTTP
 * @param detail message traduit (identique à {@code message}, pour les clients RFC 9457)
 * @param code code du catalogue, par exemple {@code DEM-001}
 * @param message message traduit, affichable tel quel
 * @param details informations complémentaires (champs invalides, identifiant d'incident…)
 */
@Schema(name = "Probleme", description = "Erreur de l'API (ProblemDetail + code du catalogue)")
public record Probleme(
    String type,
    String title,
    int status,
    String detail,
    String code,
    String message,
    Map<String, Object> details) {}
