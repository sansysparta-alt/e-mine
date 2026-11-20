package gn.gov.mines.emine.auth;

import java.util.UUID;

/**
 * Utilisateur e-mine rattaché au jeton OIDC de la requête (claim {@code sub} = {@code
 * utilisateur.keycloak_id}). API publique du module {@code auth}.
 *
 * @param interne profil interne du Ministère ({@code profil.est_interne})
 */
public record UtilisateurConnecte(
    long id,
    UUID keycloakId,
    String nom,
    String prenom,
    String email,
    String profilCode,
    String profilLibelle,
    boolean interne) {}
