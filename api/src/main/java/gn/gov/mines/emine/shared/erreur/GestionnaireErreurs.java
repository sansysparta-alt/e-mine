package gn.gov.mines.emine.shared.erreur;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Transforme toute exception remontée d'un contrôleur en réponse {@link Probleme} du catalogue
 * (US-00.8). Les erreurs inattendues donnent SYS-001 avec un identifiant d'incident, journalisé
 * avec la pile d'appels ; le client ne reçoit jamais de détail technique.
 */
@RestControllerAdvice
class GestionnaireErreurs extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GestionnaireErreurs.class);

  private final FabriqueProblemes fabrique;
  private final MessageSource messages;

  GestionnaireErreurs(FabriqueProblemes fabrique, MessageSource messages) {
    this.fabrique = fabrique;
    this.messages = messages;
  }

  @ExceptionHandler(ErreurMetier.class)
  ResponseEntity<Probleme> metier(ErreurMetier e, HttpServletRequest requete) {
    return fabrique.reponse(e.code(), FabriqueProblemes.langue(requete), e.details());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Probleme> accesRefuse(AccessDeniedException e, HttpServletRequest requete) {
    return fabrique.reponse(CodeErreur.PERM_001, FabriqueProblemes.langue(requete), Map.of());
  }

  @ExceptionHandler({DataAccessException.class, org.jooq.exception.DataAccessException.class})
  ResponseEntity<Probleme> base(RuntimeException e, HttpServletRequest requete) {
    Optional<CodeErreur> code = codeBase(e);
    if (code.isPresent()) {
      return fabrique.reponse(code.get(), FabriqueProblemes.langue(requete), Map.of());
    }
    return incident(e, requete);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Probleme> inattendue(Exception e, HttpServletRequest requete) {
    return incident(e, requete);
  }

  /** Exceptions standard de Spring MVC (validation, JSON illisible, 404, 405, taille…). */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode statut, WebRequest requete) {
    HttpServletRequest http = requete instanceof ServletWebRequest s ? s.getRequest() : null;
    Locale langue = FabriqueProblemes.langue(http);
    CodeErreur code = codeMvc(ex, statut);
    if (code == CodeErreur.SYS_001) {
      return ResponseEntity.status(code.statut()).body(incident(ex, http).getBody());
    }
    Map<String, Object> details = Map.of();
    if (code == CodeErreur.VAL_001) {
      details = Map.of("champs", champs(ex, langue));
    }
    ResponseEntity<Probleme> r = fabrique.reponse(code, langue, details);
    return ResponseEntity.status(r.getStatusCode()).headers(r.getHeaders()).body(r.getBody());
  }

  private ResponseEntity<Probleme> incident(Exception e, HttpServletRequest requete) {
    String incident = UUID.randomUUID().toString();
    LOG.error("Incident {} : erreur inattendue", incident, e);
    return fabrique.reponse(
        CodeErreur.SYS_001,
        FabriqueProblemes.langue(requete),
        Map.of("incident", incident),
        incident);
  }

  static CodeErreur codeMvc(Exception ex, HttpStatusCode statut) {
    return switch (ex) {
      case MethodArgumentNotValidException m -> CodeErreur.VAL_001;
      case HandlerMethodValidationException h -> CodeErreur.VAL_001;
      case MaxUploadSizeExceededException m -> CodeErreur.DOC_002;
      case NoResourceFoundException n -> CodeErreur.SYS_002;
      case HttpRequestMethodNotSupportedException h -> CodeErreur.SYS_005;
      case HttpMessageNotReadableException h -> CodeErreur.VAL_002;
      case HttpMediaTypeNotSupportedException h -> CodeErreur.VAL_002;
      case MissingServletRequestPartException m -> CodeErreur.VAL_002;
      case ServletRequestBindingException s -> CodeErreur.VAL_002;
      default -> statut.is4xxClientError() ? CodeErreur.VAL_002 : CodeErreur.SYS_001;
    };
  }

  private List<Map<String, String>> champs(Exception ex, Locale langue) {
    List<Map<String, String>> champs = new ArrayList<>();
    if (ex instanceof MethodArgumentNotValidException m) {
      for (FieldError fe : m.getBindingResult().getFieldErrors()) {
        champs.add(champ(fe.getField(), messages.getMessage(fe, langue)));
      }
      m.getBindingResult()
          .getGlobalErrors()
          .forEach(ge -> champs.add(champ(ge.getObjectName(), messages.getMessage(ge, langue))));
    } else if (ex instanceof HandlerMethodValidationException h) {
      h.getParameterValidationResults()
          .forEach(
              r ->
                  r.getResolvableErrors()
                      .forEach(
                          err ->
                              champs.add(
                                  champ(
                                      r.getMethodParameter().getParameterName(),
                                      messages.getMessage(err, langue)))));
    }
    return champs;
  }

  private static Map<String, String> champ(String nom, String message) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("champ", nom);
    m.put("message", message);
    return m;
  }

  /**
   * Erreurs de la base traduites en codes du catalogue : HINT posé par les triggers ({@code
   * DEM-001}, {@code PAY-006}), violation de RLS (PERM-001), conflit de version (SYS-004), objet
   * déjà payé (PAY-001).
   */
  static Optional<CodeErreur> codeBase(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof PSQLException p) {
        ServerErrorMessage m = p.getServerErrorMessage();
        String hint = m == null ? null : m.getHint();
        if (hint != null && CodeErreur.depuis(hint).isPresent()) {
          return CodeErreur.depuis(hint);
        }
        String etat = p.getSQLState();
        if ("42501".equals(etat)) {
          return Optional.of(CodeErreur.PERM_001);
        }
        if ("40001".equals(etat)) {
          return Optional.of(CodeErreur.SYS_004);
        }
        if ("23505".equals(etat) && m != null && "ux_paiement_paye".equals(m.getConstraint())) {
          return Optional.of(CodeErreur.PAY_001);
        }
        // Deux propositions simultanées (V18, V27) : une seule reste ouverte.
        if ("23505".equals(etat)
            && m != null
            && m.getConstraint() != null
            && m.getConstraint().endsWith("_une_ouverte")) {
          return Optional.of(CodeErreur.DOC_008);
        }
        return Optional.empty();
      }
    }
    return Optional.empty();
  }
}
