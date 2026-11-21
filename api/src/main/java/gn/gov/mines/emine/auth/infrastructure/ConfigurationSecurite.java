package gn.gov.mines.emine.auth.infrastructure;

import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.FabriqueProblemes;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sécurité de l'API (US-01.2) : serveur de ressources OAuth2, jetons JWT émis par Keycloak
 * (émetteur et audience {@code emine-api} vérifiés), sans session ni cookie. Les rôles Keycloak
 * (codes des 16 profils) deviennent des autorités {@code ROLE_<CODE>}, remplacées après
 * rattachement par celles de la base (profil et permissions, US-01.6). {@code @PreAuthorize} est
 * activé.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class ConfigurationSecurite {

  @Bean
  SecurityFilterChain securite(
      HttpSecurity http,
      UtilisateurRepository utilisateurs,
      PlatformTransactionManager transactions,
      FabriqueProblemes problemes)
      throws Exception {
    TransactionTemplate lecture = new TransactionTemplate(transactions);
    lecture.setReadOnly(true);

    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/actuator/health/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    // Catalogue public des documents (US-02.1)
                    .requestMatchers(
                        HttpMethod.GET, "/api/v1/types-documents", "/api/v1/types-documents/*")
                    .permitAll()
                    .requestMatchers("/api/v1/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(
            o ->
                o.jwt(j -> j.jwtAuthenticationConverter(convertisseurRoles()))
                    .authenticationEntryPoint(
                        (req, rep, ex) -> problemes.ecrire(req, rep, CodeErreur.AUTH_001)))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, rep, ex) -> problemes.ecrire(req, rep, CodeErreur.AUTH_001))
                    .accessDeniedHandler(
                        (req, rep, ex) -> problemes.ecrire(req, rep, CodeErreur.PERM_001)))
        .addFilterAfter(
            new RattachementUtilisateurFilter(utilisateurs, lecture, problemes),
            BearerTokenAuthenticationFilter.class);
    return http.build();
  }

  static JwtAuthenticationConverter convertisseurRoles() {
    JwtAuthenticationConverter convertisseur = new JwtAuthenticationConverter();
    convertisseur.setJwtGrantedAuthoritiesConverter(ConfigurationSecurite::roles);
    return convertisseur;
  }

  static Collection<GrantedAuthority> roles(Jwt jwt) {
    Object realm = jwt.getClaims().get("realm_access");
    if (!(realm instanceof Map<?, ?> acces) || !(acces.get("roles") instanceof List<?> roles)) {
      return List.of();
    }
    return roles.stream()
        .filter(String.class::isInstance)
        .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
        .toList();
  }
}
