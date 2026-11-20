package gn.gov.mines.emine.auth;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/** Donne le compte e-mine de la requête en cours (API publique du module {@code auth}). */
@Component
public class UtilisateurCourant {

  /** Attribut de requête où le filtre de rattachement range le compte. */
  public static final String ATTRIBUT = UtilisateurConnecte.class.getName();

  public Optional<UtilisateurConnecte> get() {
    RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
    if (attrs == null) {
      return Optional.empty();
    }
    return attrs.getAttribute(ATTRIBUT, RequestAttributes.SCOPE_REQUEST)
            instanceof UtilisateurConnecte u
        ? Optional.of(u)
        : Optional.empty();
  }
}
