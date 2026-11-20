package gn.gov.mines.emine.shared.erreur;

import java.util.Map;

/**
 * Erreur prévue, levée par un cas d'usage ou un contrôleur : elle devient une réponse ProblemDetail
 * avec le code, le statut HTTP et le message traduit du catalogue (US-00.8).
 */
public class ErreurMetier extends RuntimeException {

  private final transient CodeErreur code;
  private final transient Map<String, Object> details;

  public ErreurMetier(CodeErreur code) {
    this(code, Map.of());
  }

  /**
   * @param details informations utiles au client (identifiants, champs, éléments manquants) ;
   *     jamais de donnée sensible
   */
  public ErreurMetier(CodeErreur code, Map<String, Object> details) {
    super(code.code());
    this.code = code;
    this.details = Map.copyOf(details);
  }

  public CodeErreur code() {
    return code;
  }

  public Map<String, Object> details() {
    return details;
  }
}
