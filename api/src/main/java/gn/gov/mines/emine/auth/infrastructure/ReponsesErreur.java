package gn.gov.mines.emine.auth.infrastructure;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Réponses d'erreur de sécurité au format commun ProblemDetail (RFC 9457) enrichi de {@code code},
 * {@code message} et {@code details}. Écrites avant Spring MVC (filtres de sécurité), donc
 * sérialisées ici. Le catalogue complet (FR/EN) arrive avec US-00.8.
 */
final class ReponsesErreur {

  static final String NON_AUTHENTIFIE = "AUTH-001";
  static final String COMPTE_NON_RATTACHE = "AUTH-003";
  static final String ACCES_REFUSE = "PERM-001";

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private ReponsesErreur() {}

  static void ecrire(HttpServletResponse reponse, HttpStatus statut, String code, String message)
      throws IOException {
    Map<String, Object> probleme = new LinkedHashMap<>();
    probleme.put("type", "about:blank");
    probleme.put("title", statut.getReasonPhrase());
    probleme.put("status", statut.value());
    probleme.put("detail", message);
    probleme.put("code", code);
    probleme.put("message", message);
    probleme.put("details", Map.of());
    reponse.setStatus(statut.value());
    reponse.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    reponse.setCharacterEncoding("UTF-8");
    reponse.getWriter().write(JSON.writeValueAsString(probleme));
  }
}
