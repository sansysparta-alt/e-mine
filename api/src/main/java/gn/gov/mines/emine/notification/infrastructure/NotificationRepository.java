package gn.gov.mines.emine.notification.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.DEMANDE;
import static gn.gov.mines.emine.shared.jooq.Tables.NOTIFICATION;

import gn.gov.mines.emine.notification.domaine.NotificationAEnvoyer;
import gn.gov.mines.emine.notification.domaine.NotificationInterne;
import gn.gov.mines.emine.shared.jooq.enums.CanalNotification;
import gn.gov.mines.emine.shared.jooq.enums.StatutDemande;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Boîte d'envoi des notifications (US-07.1, V15). La tâche d'envoi n'a pas d'utilisateur connecté :
 * elle passe par deux fonctions {@code SECURITY DEFINER} limitées à la prise d'un lot et à
 * l'enregistrement du résultat ; la RLS réserve tout le reste au destinataire.
 */
@Repository
public class NotificationRepository {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final TypeReference<Map<String, Object>> CARTE = new TypeReference<>() {};

  private final DSLContext dsl;

  public NotificationRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * Prend au plus {@code taille} notifications dues (SMS et e-mail), sans attendre celles qu'une
   * autre instance traite ; l'essai est compté et le prochain reporté avant l'envoi.
   */
  public List<NotificationAEnvoyer> prendreLot(int taille) {
    return dsl.resultQuery("SELECT * FROM fn_notifications_a_envoyer(?)", taille)
        .fetch(NotificationRepository::versNotification);
  }

  /**
   * Enregistre le résultat d'un essai ({@code erreur} nulle = envoyée) ; renvoie le nouveau statut
   * ({@code envoyee}, {@code en_attente} ou {@code echec}).
   */
  public String enregistrerResultat(long id, String erreur) {
    return dsl.resultQuery("SELECT fn_notification_resultat(?, ?)", id, erreur)
        .fetchOne(0, String.class);
  }

  /**
   * Action encore attendue de l'usager sur le dossier, quand la notification la demandait : payer
   * les droits (« Décision favorable, droits à payer ») ou compléter le dossier. Une fois l'action
   * faite, la notification redevient un simple historique.
   */
  static final Field<String> ACTION =
      DSL.when(
              NOTIFICATION
                  .MODELE
                  .eq("DECISION_DROITS")
                  .and(DEMANDE.STATUT.eq(StatutDemande.decision)),
              DSL.inline("payer-droits"))
          .when(
              NOTIFICATION
                  .MODELE
                  .in("COMPLEMENT_DEMANDE", "COMPLEMENT_RELANCE")
                  .and(DEMANDE.STATUT.eq(StatutDemande.complement_requis)),
              DSL.inline("completer"))
          .as("action");

  /**
   * Notifications de l'application du destinataire connecté (la RLS ne montre que les siennes), des
   * plus récentes aux plus anciennes ; seulement les non lues si demandé.
   */
  public PageResultat<NotificationInterne> lister(Pagination pagination, boolean seulementNonLues) {
    Condition condition = NOTIFICATION.CANAL.eq(CanalNotification.interne);
    if (seulementNonLues) {
      condition = condition.and(NOTIFICATION.LUE_LE.isNull());
    }
    long total = dsl.fetchCount(NOTIFICATION, condition);
    List<NotificationInterne> elements =
        dsl.select(
                NOTIFICATION.ID,
                NOTIFICATION.MODELE,
                NOTIFICATION.CONTENU,
                NOTIFICATION.DEMANDE_ID,
                DEMANDE.NUMERO,
                NOTIFICATION.CREATED_AT,
                NOTIFICATION.LUE_LE,
                ACTION)
            .from(NOTIFICATION)
            .leftJoin(DEMANDE)
            .on(DEMANDE.ID.eq(NOTIFICATION.DEMANDE_ID))
            .where(condition)
            .orderBy(NOTIFICATION.CREATED_AT.desc(), NOTIFICATION.ID.desc())
            .limit(pagination.taille())
            .offset(pagination.decalage())
            .fetch(
                r ->
                    new NotificationInterne(
                        r.get(NOTIFICATION.ID),
                        r.get(NOTIFICATION.MODELE),
                        contenu(r.get(NOTIFICATION.CONTENU)),
                        r.get(NOTIFICATION.DEMANDE_ID),
                        r.get(DEMANDE.NUMERO),
                        r.get(NOTIFICATION.CREATED_AT),
                        r.get(NOTIFICATION.LUE_LE),
                        r.get(ACTION)));
    return new PageResultat<>(elements, pagination.page(), pagination.taille(), total);
  }

  /** Nombre de notifications du destinataire connecté (toutes, lues ou non). */
  public long toutes() {
    return dsl.fetchCount(NOTIFICATION, NOTIFICATION.CANAL.eq(CanalNotification.interne));
  }

  /** Nombre de notifications non lues du destinataire connecté. */
  public long nonLues() {
    return dsl.fetchCount(
        NOTIFICATION,
        NOTIFICATION.CANAL.eq(CanalNotification.interne).and(NOTIFICATION.LUE_LE.isNull()));
  }

  /** Marque comme lue une notification du destinataire connecté ; faux si elle n'existe pas. */
  public boolean marquerLue(long id, Instant maintenant) {
    int lignes =
        dsl.update(NOTIFICATION)
            .set(
                NOTIFICATION.LUE_LE,
                DSL.coalesce(NOTIFICATION.LUE_LE, DSL.val(maintenant, NOTIFICATION.LUE_LE)))
            .where(NOTIFICATION.ID.eq(id).and(NOTIFICATION.CANAL.eq(CanalNotification.interne)))
            .execute();
    return lignes == 1;
  }

  /** Marque comme lues toutes les notifications du destinataire connecté ; renvoie leur nombre. */
  public int marquerToutesLues(Instant maintenant) {
    return dsl.update(NOTIFICATION)
        .set(NOTIFICATION.LUE_LE, maintenant)
        .where(NOTIFICATION.CANAL.eq(CanalNotification.interne).and(NOTIFICATION.LUE_LE.isNull()))
        .execute();
  }

  /**
   * Alerte J-2 des références de paiement proches de l'expiration (une seule par paiement) ;
   * renvoie le nombre d'alertes écrites.
   */
  public int alerterReferencesExpirantes() {
    return dsl.resultQuery("SELECT fn_alerter_references_expirantes()").fetchOne(0, Integer.class);
  }

  private static NotificationAEnvoyer versNotification(Record r) {
    return new NotificationAEnvoyer(
        r.get("id", Long.class),
        r.get("canal", String.class),
        r.get("modele", String.class),
        contenu(r.get("contenu", JSONB.class)),
        r.get("tentative", Integer.class),
        r.get("email", String.class),
        r.get("telephone", String.class),
        r.get("langue", String.class),
        r.get("prenom", String.class),
        r.get("demande_id", Long.class),
        r.get("numero_demande", String.class));
  }

  static Map<String, Object> contenu(JSONB json) {
    if (json == null || json.data() == null) {
      return Map.of();
    }
    Map<String, Object> valeurs = JSON.readValue(json.data(), CARTE);
    Map<String, Object> sansNull = new LinkedHashMap<>();
    valeurs.forEach(
        (cle, valeur) -> {
          if (valeur != null) {
            sansNull.put(cle, valeur);
          }
        });
    return sansNull;
  }
}
