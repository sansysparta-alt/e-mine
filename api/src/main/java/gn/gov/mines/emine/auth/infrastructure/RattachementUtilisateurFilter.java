package gn.gov.mines.emine.auth.infrastructure;

import gn.gov.mines.emine.auth.UtilisateurConnecte;
import gn.gov.mines.emine.auth.UtilisateurCourant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Après validation du jeton, rattache la requête au compte e-mine (claim {@code sub} = {@code
 * utilisateur.keycloak_id}). Sans compte actif, la requête est refusée (AUTH-003). Le compte est
 * gardé dans un attribut de requête, lu ensuite pour le contexte RLS de chaque transaction.
 */
class RattachementUtilisateurFilter extends OncePerRequestFilter {

  static final String ATTRIBUT = UtilisateurCourant.ATTRIBUT;

  private final UtilisateurRepository utilisateurs;
  private final TransactionTemplate lecture;

  RattachementUtilisateurFilter(UtilisateurRepository utilisateurs, TransactionTemplate lecture) {
    this.utilisateurs = utilisateurs;
    this.lecture = lecture;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof JwtAuthenticationToken jeton) {
      Optional<UtilisateurConnecte> compte = rechercher(jeton.getToken().getSubject());
      if (compte.isEmpty()) {
        ReponsesErreur.ecrire(
            reponse,
            HttpStatus.FORBIDDEN,
            ReponsesErreur.COMPTE_NON_RATTACHE,
            "Votre compte n'est pas encore activé sur e-mine. Contactez l'administrateur.");
        return;
      }
      requete.setAttribute(ATTRIBUT, compte.get());
    }
    suite.doFilter(requete, reponse);
  }

  private Optional<UtilisateurConnecte> rechercher(String sub) {
    UUID keycloakId;
    try {
      keycloakId = UUID.fromString(sub);
    } catch (IllegalArgumentException | NullPointerException e) {
      return Optional.empty();
    }
    return lecture.execute(statut -> utilisateurs.trouverActifParKeycloakId(keycloakId));
  }
}
