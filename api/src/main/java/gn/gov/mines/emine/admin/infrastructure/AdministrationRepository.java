package gn.gov.mines.emine.admin.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.ORGANISATION;
import static gn.gov.mines.emine.shared.jooq.Tables.PERMISSION;
import static gn.gov.mines.emine.shared.jooq.Tables.PROFIL;
import static gn.gov.mines.emine.shared.jooq.Tables.PROFIL_PERMISSION;
import static gn.gov.mines.emine.shared.jooq.Tables.UTILISATEUR;

import gn.gov.mines.emine.admin.domaine.Supervision;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** Lecture des comptes et des profils pour l'administrateur technique (aucune donnée modifiée). */
@Repository
public class AdministrationRepository {

  /** Fenêtre « connectés récemment ». */
  public static final Duration RECENTS = Duration.ofDays(30);

  private final DSLContext dsl;

  public AdministrationRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** Comptes non supprimés par statut ; connectés depuis {@code maintenant} moins 30 jours. */
  public Supervision.Comptes comptes(Instant maintenant) {
    var r =
        dsl.select(
                DSL.count().filterWhere(UTILISATEUR.STATUT.eq("actif")),
                DSL.count().filterWhere(UTILISATEUR.STATUT.eq("suspendu")),
                DSL.count().filterWhere(UTILISATEUR.STATUT.eq("invite")),
                DSL.count()
                    .filterWhere(UTILISATEUR.DERNIERE_CONNEXION.ge(maintenant.minus(RECENTS))))
            .from(UTILISATEUR)
            .where(UTILISATEUR.DELETED_AT.isNull())
            .fetchOne();
    return new Supervision.Comptes(r.value1(), r.value2(), r.value3(), r.value4());
  }

  /** Les profils dans leur ordre (numéro 1 à 16), avec leurs comptes (non supprimés) et droits. */
  public List<Supervision.ProfilCompte> profils() {
    var actifs =
        DSL.selectCount()
            .from(UTILISATEUR)
            .where(UTILISATEUR.PROFIL_ID.eq(PROFIL.ID))
            .and(UTILISATEUR.DELETED_AT.isNull())
            .asField("actifs");
    var droits =
        DSL.selectCount()
            .from(PROFIL_PERMISSION)
            .where(PROFIL_PERMISSION.PROFIL_ID.eq(PROFIL.ID))
            .asField("droits");
    var permissions =
        DSL.field(
                DSL.select(DSL.arrayAgg(PERMISSION.CODE).orderBy(PERMISSION.ID))
                    .from(PROFIL_PERMISSION)
                    .join(PERMISSION)
                    .on(PERMISSION.ID.eq(PROFIL_PERMISSION.PERMISSION_ID))
                    .where(PROFIL_PERMISSION.PROFIL_ID.eq(PROFIL.ID)))
            .as("permissions");
    return dsl.select(PROFIL.CODE, PROFIL.LIBELLE, PROFIL.EST_INTERNE, actifs, droits, permissions)
        .from(PROFIL)
        .orderBy(PROFIL.NUMERO)
        .fetch(
            r -> {
              String[] codes = r.get(permissions);
              return new Supervision.ProfilCompte(
                  r.get(PROFIL.CODE),
                  r.get(PROFIL.LIBELLE),
                  r.get(PROFIL.EST_INTERNE),
                  r.get(actifs, Long.class),
                  r.get(droits, Integer.class),
                  codes == null ? List.of() : List.of(codes));
            });
  }

  /**
   * Comptes (« Utilisateurs & rôles »), du plus récemment connecté au plus ancien, filtrables par
   * profil et par recherche (nom, prénom, e-mail, organisation ; accents ignorés).
   */
  public Supervision.PageUtilisateurs utilisateurs(
      String profilCode,
      String statut,
      Instant connectesDepuis,
      Instant recentsDepuis,
      String recherche,
      Pagination pagination) {
    // Base des compteurs : profil et recherche (les filtres de statut se choisissent avec eux).
    Condition c = UTILISATEUR.DELETED_AT.isNull();
    if (profilCode != null) {
      c = c.and(PROFIL.CODE.eq(profilCode));
    }
    if (recherche != null && !recherche.isBlank()) {
      var texte =
          normaliser(
              DSL.field(
                  "concat_ws(' ', {0}, {1}, {2}, {3})",
                  String.class,
                  UTILISATEUR.PRENOM,
                  UTILISATEUR.NOM,
                  UTILISATEUR.EMAIL,
                  ORGANISATION.RAISON_SOCIALE));
      for (String mot : recherche.strip().split("[\\s’']+")) {
        if (!mot.isBlank()) {
          c = c.and(texte.like(normaliser(DSL.val("%" + echapperLike(mot) + "%")), '\\'));
        }
      }
    }
    var n =
        dsl.select(
                DSL.count(),
                DSL.count().filterWhere(UTILISATEUR.STATUT.eq("actif")),
                DSL.count().filterWhere(UTILISATEUR.STATUT.eq("suspendu")),
                DSL.count().filterWhere(UTILISATEUR.STATUT.eq("invite")),
                DSL.count().filterWhere(UTILISATEUR.DERNIERE_CONNEXION.ge(recentsDepuis)))
            .from(UTILISATEUR)
            .join(PROFIL)
            .on(PROFIL.ID.eq(UTILISATEUR.PROFIL_ID))
            .leftJoin(ORGANISATION)
            .on(ORGANISATION.ID.eq(UTILISATEUR.ORGANISATION_ID))
            .where(c)
            .fetchOne();
    var compteurs =
        new Supervision.CompteursUtilisateurs(
            n.value1(), n.value2(), n.value3(), n.value4(), n.value5());
    if (statut != null) {
      c = c.and(UTILISATEUR.STATUT.eq(statut));
    }
    if (connectesDepuis != null) {
      c = c.and(UTILISATEUR.DERNIERE_CONNEXION.ge(connectesDepuis));
    }
    long total =
        statut == null && connectesDepuis == null
            ? compteurs.tous()
            : dsl.selectCount()
                .from(UTILISATEUR)
                .join(PROFIL)
                .on(PROFIL.ID.eq(UTILISATEUR.PROFIL_ID))
                .leftJoin(ORGANISATION)
                .on(ORGANISATION.ID.eq(UTILISATEUR.ORGANISATION_ID))
                .where(c)
                .fetchOne(0, Long.class);
    List<Supervision.Utilisateur> elements =
        dsl.select(
                UTILISATEUR.ID,
                UTILISATEUR.PRENOM,
                UTILISATEUR.NOM,
                UTILISATEUR.EMAIL,
                ORGANISATION.RAISON_SOCIALE,
                PROFIL.CODE,
                PROFIL.LIBELLE,
                UTILISATEUR.STATUT,
                UTILISATEUR.DERNIERE_CONNEXION,
                UTILISATEUR.KEYCLOAK_ID)
            .from(UTILISATEUR)
            .join(PROFIL)
            .on(PROFIL.ID.eq(UTILISATEUR.PROFIL_ID))
            .leftJoin(ORGANISATION)
            .on(ORGANISATION.ID.eq(UTILISATEUR.ORGANISATION_ID))
            .where(c)
            .orderBy(
                UTILISATEUR.DERNIERE_CONNEXION.desc().nullsLast(), UTILISATEUR.NOM, UTILISATEUR.ID)
            .limit(pagination.taille())
            .offset(pagination.decalage())
            .fetch(
                r ->
                    new Supervision.Utilisateur(
                        r.get(UTILISATEUR.ID),
                        r.get(UTILISATEUR.PRENOM),
                        r.get(UTILISATEUR.NOM),
                        r.get(UTILISATEUR.EMAIL),
                        r.get(ORGANISATION.RAISON_SOCIALE),
                        r.get(PROFIL.CODE),
                        r.get(PROFIL.LIBELLE),
                        r.get(UTILISATEUR.STATUT),
                        r.get(UTILISATEUR.DERNIERE_CONNEXION),
                        r.get(UTILISATEUR.KEYCLOAK_ID)));
    return new Supervision.PageUtilisateurs(
        new PageResultat<>(elements, pagination.page(), pagination.taille(), total), compteurs);
  }

  /** La base répond-elle ? */
  public boolean baseDisponible() {
    return dsl.selectOne().fetchOne() != null;
  }

  private static org.jooq.Field<String> normaliser(org.jooq.Field<String> texte) {
    return DSL.function("fn_normaliser", String.class, texte);
  }

  private static String echapperLike(String texte) {
    return texte.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
