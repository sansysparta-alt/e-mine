package gn.gov.mines.emine.auth;

import java.util.Set;
import java.util.UUID;

/**
 * Utilisateur e-mine rattaché au jeton OIDC de la requête (claim {@code sub} = {@code
 * utilisateur.keycloak_id}). API publique du module {@code auth}.
 *
 * @param organisationId organisation de rattachement ({@code null} pour un agent du Ministère)
 * @param interne profil interne du Ministère ({@code profil.est_interne})
 * @param permissions droits du profil lus dans {@code profil_permission} (US-01.6)
 */
public record UtilisateurConnecte(
    long id,
    UUID keycloakId,
    String nom,
    String prenom,
    String email,
    Long organisationId,
    String profilCode,
    String profilLibelle,
    boolean interne,
    Set<Permission> permissions) {

  public UtilisateurConnecte {
    permissions = Set.copyOf(permissions);
  }

  public boolean peut(Permission permission) {
    return permissions.contains(permission);
  }
}
