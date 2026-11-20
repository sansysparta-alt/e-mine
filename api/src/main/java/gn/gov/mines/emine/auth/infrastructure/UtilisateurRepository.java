package gn.gov.mines.emine.auth.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.ORGANISATION;
import static gn.gov.mines.emine.shared.jooq.Tables.PERMISSION;
import static gn.gov.mines.emine.shared.jooq.Tables.PROFIL;
import static gn.gov.mines.emine.shared.jooq.Tables.PROFIL_PERMISSION;
import static gn.gov.mines.emine.shared.jooq.Tables.UTILISATEUR;

import gn.gov.mines.emine.auth.Permission;
import gn.gov.mines.emine.auth.UtilisateurConnecte;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/** Lecture des comptes e-mine et de leurs droits à partir de l'identifiant Keycloak (jOOQ). */
@Repository
class UtilisateurRepository {

  private final DSLContext dsl;

  UtilisateurRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** Compte actif, non supprimé, rattaché à cet identifiant Keycloak, avec ses permissions. */
  Optional<UtilisateurConnecte> trouverActifParKeycloakId(UUID keycloakId) {
    return dsl.select(
            UTILISATEUR.ID,
            UTILISATEUR.KEYCLOAK_ID,
            UTILISATEUR.NOM,
            UTILISATEUR.PRENOM,
            UTILISATEUR.EMAIL,
            UTILISATEUR.ORGANISATION_ID,
            ORGANISATION.RAISON_SOCIALE,
            UTILISATEUR.PROFIL_ID,
            PROFIL.CODE,
            PROFIL.LIBELLE,
            PROFIL.EST_INTERNE)
        .from(UTILISATEUR)
        .join(PROFIL)
        .on(PROFIL.ID.eq(UTILISATEUR.PROFIL_ID))
        .leftJoin(ORGANISATION)
        .on(ORGANISATION.ID.eq(UTILISATEUR.ORGANISATION_ID))
        .where(UTILISATEUR.KEYCLOAK_ID.eq(keycloakId))
        .and(UTILISATEUR.DELETED_AT.isNull())
        .and(UTILISATEUR.STATUT.eq("actif"))
        .fetchOptional(
            r ->
                new UtilisateurConnecte(
                    r.get(UTILISATEUR.ID),
                    r.get(UTILISATEUR.KEYCLOAK_ID),
                    r.get(UTILISATEUR.NOM),
                    r.get(UTILISATEUR.PRENOM),
                    r.get(UTILISATEUR.EMAIL),
                    r.get(UTILISATEUR.ORGANISATION_ID),
                    r.get(ORGANISATION.RAISON_SOCIALE),
                    r.get(PROFIL.CODE),
                    r.get(PROFIL.LIBELLE),
                    r.get(PROFIL.EST_INTERNE),
                    permissions(r.get(UTILISATEUR.PROFIL_ID))));
  }

  /** Droits du profil (matrice {@code profil_permission}) ; un code inconnu de l'API est ignoré. */
  private Set<Permission> permissions(short profilId) {
    Set<Permission> permissions = EnumSet.noneOf(Permission.class);
    dsl.select(PERMISSION.CODE)
        .from(PROFIL_PERMISSION)
        .join(PERMISSION)
        .on(PERMISSION.ID.eq(PROFIL_PERMISSION.PERMISSION_ID))
        .where(PROFIL_PERMISSION.PROFIL_ID.eq(profilId))
        .fetch(PERMISSION.CODE)
        .forEach(
            code ->
                Arrays.stream(Permission.values())
                    .filter(p -> p.name().equals(code))
                    .findFirst()
                    .ifPresent(permissions::add));
    return permissions;
  }
}
