package gn.gov.mines.emine.instruction.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.DEMANDE;

import gn.gov.mines.emine.shared.jooq.enums.StatutDemande;
import java.time.Instant;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/**
 * Changements de statut de l'instruction (jOOQ). Toujours appelé dans une transaction : la RLS
 * réserve ces écritures aux profils internes ; les transitions, l'historique, l'auteur et le
 * journal d'audit sont gérés par les triggers de la base. Chaque mise à jour n'agit que si la
 * demande est encore au statut attendu (0 ligne sinon : elle a été traitée entre-temps).
 */
@Repository
public class InstructionRepository {

  private final DSLContext dsl;

  public InstructionRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** Déposée → recevable, avec la date de recevabilité. */
  public int declarerRecevable(long id, Instant le) {
    return dsl.update(DEMANDE)
        .set(DEMANDE.STATUT, StatutDemande.recevable)
        .set(DEMANDE.DATE_RECEVABILITE, le)
        .where(DEMANDE.ID.eq(id))
        .and(DEMANDE.STATUT.eq(StatutDemande.deposee))
        .and(DEMANDE.DELETED_AT.isNull())
        .execute();
  }

  /** Déposée → rejetée (non recevable), avec le motif communiqué au demandeur. */
  public int declarerNonRecevable(long id, String motif) {
    return dsl.update(DEMANDE)
        .set(DEMANDE.STATUT, StatutDemande.rejetee)
        .set(DEMANDE.MOTIF_REJET, motif)
        .where(DEMANDE.ID.eq(id))
        .and(DEMANDE.STATUT.eq(StatutDemande.deposee))
        .and(DEMANDE.DELETED_AT.isNull())
        .execute();
  }
}
