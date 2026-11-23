package gn.gov.mines.emine.notification.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.NOTIFICATION_EVENEMENT;

import gn.gov.mines.emine.notification.domaine.CanauxEvenement;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/** Canaux de chaque événement de notification (table {@code notification_evenement}, V16, V21). */
@Repository
public class CanauxRepository {

  private final DSLContext dsl;

  public CanauxRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public List<CanauxEvenement> lister() {
    return dsl.selectFrom(NOTIFICATION_EVENEMENT)
        .orderBy(NOTIFICATION_EVENEMENT.ID)
        .fetch(
            r ->
                new CanauxEvenement(
                    r.getModele(),
                    r.getLibelleFr(),
                    r.getLibelleEn(),
                    r.getSms(),
                    r.getEmail(),
                    r.getInterne()));
  }

  /**
   * Écrit les canaux d'un événement s'ils changent (le journal d'audit ne garde que les vrais
   * changements) ; la base contrôle les droits (V21).
   */
  public void enregistrer(CanauxEvenement c) {
    dsl.update(NOTIFICATION_EVENEMENT)
        .set(NOTIFICATION_EVENEMENT.SMS, c.sms())
        .set(NOTIFICATION_EVENEMENT.EMAIL, c.email())
        .set(NOTIFICATION_EVENEMENT.INTERNE, c.interne())
        .where(NOTIFICATION_EVENEMENT.MODELE.eq(c.modele()))
        .and(
            NOTIFICATION_EVENEMENT
                .SMS
                .ne(c.sms())
                .or(NOTIFICATION_EVENEMENT.EMAIL.ne(c.email()))
                .or(NOTIFICATION_EVENEMENT.INTERNE.ne(c.interne())))
        .execute();
  }
}
