package gn.gov.mines.emine.auth.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.PROFIL;
import static gn.gov.mines.emine.shared.jooq.Tables.UTILISATEUR;

import gn.gov.mines.emine.auth.UtilisateurConnecte;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/** Lecture des comptes e-mine à partir de l'identifiant Keycloak (jOOQ). */
@Repository
class UtilisateurRepository {

  private final DSLContext dsl;

  UtilisateurRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** Compte actif, non supprimé, rattaché à cet identifiant Keycloak. */
  Optional<UtilisateurConnecte> trouverActifParKeycloakId(UUID keycloakId) {
    return dsl.select(
            UTILISATEUR.ID,
            UTILISATEUR.KEYCLOAK_ID,
            UTILISATEUR.NOM,
            UTILISATEUR.PRENOM,
            UTILISATEUR.EMAIL,
            PROFIL.CODE,
            PROFIL.LIBELLE,
            PROFIL.EST_INTERNE)
        .from(UTILISATEUR)
        .join(PROFIL)
        .on(PROFIL.ID.eq(UTILISATEUR.PROFIL_ID))
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
                    r.get(PROFIL.CODE),
                    r.get(PROFIL.LIBELLE),
                    r.get(PROFIL.EST_INTERNE)));
  }
}
