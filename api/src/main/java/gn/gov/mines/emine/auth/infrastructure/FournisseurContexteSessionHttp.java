package gn.gov.mines.emine.auth.infrastructure;

import gn.gov.mines.emine.auth.UtilisateurConnecte;
import gn.gov.mines.emine.shared.session.ContexteSession;
import gn.gov.mines.emine.shared.session.FournisseurContexteSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Contexte RLS de la requête HTTP courante (US-01.5) : compte rattaché par {@link
 * RattachementUtilisateurFilter} et adresse IP du client. L'IP vient de {@code X-Forwarded-For}
 * (posé par le BFF, seul client de l'API) ou, à défaut, de l'adresse de la connexion.
 */
@Component
class FournisseurContexteSessionHttp implements FournisseurContexteSession {

  @Override
  public ContexteSession contexteCourant() {
    if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
      return ContexteSession.ANONYME;
    }
    HttpServletRequest requete = attrs.getRequest();
    String ip = ip(requete);
    if (requete.getAttribute(RattachementUtilisateurFilter.ATTRIBUT)
        instanceof UtilisateurConnecte u) {
      return new ContexteSession(u.id(), u.interne(), ip);
    }
    return new ContexteSession(null, false, ip);
  }

  static String ip(HttpServletRequest requete) {
    String transmise = requete.getHeader("X-Forwarded-For");
    if (transmise != null && !transmise.isBlank()) {
      return transmise.split(",")[0].strip();
    }
    return requete.getRemoteAddr();
  }
}
