package gn.gov.mines.emine.notification.infrastructure;

import gn.gov.mines.emine.notification.domaine.NotificationAEnvoyer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
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
