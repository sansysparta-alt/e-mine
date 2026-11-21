package gn.gov.mines.emine.shared.erreur;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Construit les réponses d'erreur du catalogue (US-00.8), dans la langue demandée par {@code
 * Accept-Language} (français par défaut, anglais si demandé). Sert au gestionnaire d'exceptions de
 * Spring MVC et aux filtres de sécurité, qui écrivent avant Spring MVC.
 */
@Component
public class FabriqueProblemes {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final MessageSource messages;

  public FabriqueProblemes(MessageSource messages) {
    this.messages = messages;
  }

  /** Langue de la réponse : anglais si le client le préfère, français sinon. */
  public static Locale langue(HttpServletRequest requete) {
    String accept = requete == null ? null : requete.getHeader("Accept-Language");
    if (accept != null && accept.strip().toLowerCase(Locale.ROOT).startsWith("en")) {
      return Locale.ENGLISH;
    }
    return Locale.FRENCH;
  }

  /** Message traduit d'un code du catalogue. */
  public String message(CodeErreur code, Locale langue, Object... arguments) {
    return messages.getMessage(code.cleMessage(), arguments, code.code(), langue);
  }

  public Probleme probleme(
      CodeErreur code, Locale langue, Map<String, Object> details, Object... arguments) {
    String message = message(code, langue, arguments);
    return new Probleme(
        "about:blank",
        code.statut().getReasonPhrase(),
        code.statut().value(),
        message,
        code.code(),
        message,
        details == null ? Map.of() : details);
  }

  public ResponseEntity<Probleme> reponse(
      CodeErreur code, Locale langue, Map<String, Object> details, Object... arguments) {
    return ResponseEntity.status(code.statut())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(probleme(code, langue, details, arguments));
  }

  /** Écrit directement la réponse (filtres de sécurité, avant Spring MVC). */
  public void ecrire(HttpServletRequest requete, HttpServletResponse reponse, CodeErreur code)
      throws IOException {
    Probleme probleme = probleme(code, langue(requete), Map.of());
    reponse.setStatus(probleme.status());
    reponse.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    reponse.setCharacterEncoding("UTF-8");
    reponse.getWriter().write(JSON.writeValueAsString(probleme));
  }
}
