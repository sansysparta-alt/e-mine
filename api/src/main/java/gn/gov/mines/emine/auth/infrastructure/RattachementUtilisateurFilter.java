package gn.gov.mines.emine.auth.infrastructure;

import gn.gov.mines.emine.auth.UtilisateurConnecte;
import gn.gov.mines.emine.auth.UtilisateurCourant;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.FabriqueProblemes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Après validation du jeton, rattache la requête au compte e-mine (claim {@code sub} = {@code
 * utilisateur.keycloak_id}). Sans compte actif, la requête est refusée (AUTH-003). Le compte est
 * gardé dans un attribut de requête, lu ensuite pour le contexte RLS de chaque transaction.
 *
 * <p>Les autorités sont ensuite celles de la base (D2, US-01.6) : {@code ROLE_<profil>} et une
 * autorité {@code PERM_<CODE>} par droit de {@code profil_permission} ; les rôles portés par le
 * jeton Keycloak ne servent pas aux décisions d'accès.
 */
class RattachementUtilisateurFilter extends OncePerRequestFilter {

  static final String ATTRIBUT = UtilisateurCourant.ATTRIBUT;

  private final UtilisateurRepository utilisateurs;
  private final TransactionTemplate lecture;
  private final FabriqueProblemes problemes;

  RattachementUtilisateurFilter(
      UtilisateurRepository utilisateurs,
      TransactionTemplate lecture,
      FabriqueProblemes problemes) {
    this.utilisateurs = utilisateurs;
    this.lecture = lecture;
    this.problemes = problemes;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof JwtAuthenticationToken jeton) {
      Optional<UtilisateurConnecte> compte = rechercher(jeton.getToken().getSubject());
      if (compte.isEmpty()) {
        problemes.ecrire(requete, reponse, CodeErreur.AUTH_003);
        return;
      }
      requete.setAttribute(ATTRIBUT, compte.get());
      SecurityContext contexte = SecurityContextHolder.createEmptyContext();
      contexte.setAuthentication(
          new JwtAuthenticationToken(jeton.getToken(), autorites(compte.get()), jeton.getName()));
      SecurityContextHolder.setContext(contexte);
    }
    suite.doFilter(requete, reponse);
  }

  static List<GrantedAuthority> autorites(UtilisateurConnecte compte) {
    List<GrantedAuthority> autorites = new ArrayList<>();
    autorites.add(new SimpleGrantedAuthority("ROLE_" + compte.profilCode()));
    compte.permissions().stream()
        .sorted()
        .forEach(p -> autorites.add(new SimpleGrantedAuthority(p.autorite())));
    return autorites;
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
