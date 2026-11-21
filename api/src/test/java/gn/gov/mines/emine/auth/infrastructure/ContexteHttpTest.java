package gn.gov.mines.emine.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import gn.gov.mines.emine.auth.UtilisateurConnecte;
import gn.gov.mines.emine.auth.UtilisateurCourant;
import gn.gov.mines.emine.shared.session.ContexteSession;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Contexte RLS tiré de la requête HTTP, et conversion des rôles Keycloak (US-01.2, US-01.5). */
class ContexteHttpTest {

  private final FournisseurContexteSessionHttp fournisseur = new FournisseurContexteSessionHttp();

  @AfterEach
  void nettoyer() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void horsRequeteLeContexteEstAnonyme() {
    assertThat(fournisseur.contexteCourant()).isEqualTo(ContexteSession.ANONYME);
  }

  @Test
  void leCompteRattacheEtLIpDuClientAlimententLeContexte() {
    MockHttpServletRequest requete = new MockHttpServletRequest();
    requete.setRemoteAddr("127.0.0.1");
    requete.addHeader("X-Forwarded-For", "198.51.100.23, 10.0.0.2");
    requete.setAttribute(
        UtilisateurCourant.ATTRIBUT,
        new UtilisateurConnecte(
            42L,
            UUID.randomUUID(),
            "Camara",
            "Aïcha",
            "a@e-mine.test",
            null,
            "GUICHET",
            "Guichet",
            true,
            java.util.Set.of()));
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requete));

    assertThat(fournisseur.contexteCourant())
        .isEqualTo(new ContexteSession(42L, true, "198.51.100.23"));
  }

  @Test
  void sansEnTeteTransmisLAdresseDeConnexionEstUtilisee() {
    MockHttpServletRequest requete = new MockHttpServletRequest();
    requete.setRemoteAddr("203.0.113.9");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requete));

    assertThat(fournisseur.contexteCourant())
        .isEqualTo(new ContexteSession(null, false, "203.0.113.9"));
  }

  @Test
  void lesRolesKeycloakDeviennentDesAutorites() {
    Jwt jwt =
        Jwt.withTokenValue("t")
            .header("alg", "none")
            .subject("s")
            .issuedAt(Instant.now())
            .claim("realm_access", Map.of("roles", List.of("DIRECTEUR", "INVESTISSEUR")))
            .build();
    assertThat(ConfigurationSecurite.roles(jwt))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_DIRECTEUR", "ROLE_INVESTISSEUR");
  }

  @Test
  void unJetonSansRolesNeDonneAucuneAutorite() {
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("s").build();
    assertThat(ConfigurationSecurite.roles(jwt)).isEmpty();
  }
}
