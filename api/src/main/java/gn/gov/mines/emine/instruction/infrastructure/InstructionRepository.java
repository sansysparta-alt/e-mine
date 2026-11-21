package gn.gov.mines.emine.instruction.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.AVIS_DECISION;
import static gn.gov.mines.emine.shared.jooq.Tables.DEMANDE;
import static gn.gov.mines.emine.shared.jooq.Tables.PROFIL;
import static gn.gov.mines.emine.shared.jooq.Tables.UTILISATEUR;

import gn.gov.mines.emine.instruction.domaine.DecisionRendue;
import gn.gov.mines.emine.shared.jooq.enums.NatureAvis;
import gn.gov.mines.emine.shared.jooq.enums.SensAvis;
import gn.gov.mines.emine.shared.jooq.enums.StatutDemande;
import java.time.Instant;
import java.util.Optional;
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

  /** Passe la demande d'un statut à un autre (0 ligne si elle n'est plus au statut attendu). */
  public int changerStatut(long id, StatutDemande de, StatutDemande vers) {
    return dsl.update(DEMANDE)
        .set(DEMANDE.STATUT, vers)
        .where(DEMANDE.ID.eq(id))
        .and(DEMANDE.STATUT.eq(de))
        .and(DEMANDE.DELETED_AT.isNull())
        .execute();
  }

  /** Recevable → rejetée à la décision, avec le motif communiqué au demandeur. */
  public int rejeter(long id, String motif) {
    return dsl.update(DEMANDE)
        .set(DEMANDE.STATUT, StatutDemande.rejetee)
        .set(DEMANDE.MOTIF_REJET, motif)
        .where(DEMANDE.ID.eq(id))
        .and(DEMANDE.STATUT.eq(StatutDemande.recevable))
        .and(DEMANDE.DELETED_AT.isNull())
        .execute();
  }

  /** Inscrit la décision (non modifiable, auditée par la base). */
  public void enregistrerDecision(long demandeId, long auteurId, boolean favorable, String motif) {
    dsl.insertInto(AVIS_DECISION)
        .set(AVIS_DECISION.DEMANDE_ID, demandeId)
        .set(AVIS_DECISION.AUTEUR_ID, auteurId)
        .set(AVIS_DECISION.NATURE, NatureAvis.decision)
        .set(AVIS_DECISION.SENS, favorable ? SensAvis.favorable : SensAvis.defavorable)
        .set(AVIS_DECISION.MOTIF, motif)
        .execute();
  }

  /** Dernière décision rendue sur la demande, s'il y en a une. */
  public Optional<DecisionRendue> derniereDecision(long demandeId) {
    return dsl.select(
            AVIS_DECISION.SENS,
            AVIS_DECISION.MOTIF,
            AVIS_DECISION.RENDU_LE,
            UTILISATEUR.PRENOM,
            UTILISATEUR.NOM,
            PROFIL.LIBELLE)
        .from(AVIS_DECISION)
        .join(UTILISATEUR)
        .on(UTILISATEUR.ID.eq(AVIS_DECISION.AUTEUR_ID))
        .join(PROFIL)
        .on(PROFIL.ID.eq(UTILISATEUR.PROFIL_ID))
        .where(AVIS_DECISION.DEMANDE_ID.eq(demandeId))
        .and(AVIS_DECISION.NATURE.eq(NatureAvis.decision))
        .orderBy(AVIS_DECISION.ID.desc())
        .limit(1)
        .fetchOptional(
            r ->
                new DecisionRendue(
                    r.get(AVIS_DECISION.SENS) == SensAvis.favorable,
                    r.get(AVIS_DECISION.MOTIF),
                    r.get(AVIS_DECISION.RENDU_LE),
                    (r.get(UTILISATEUR.PRENOM) + " " + r.get(UTILISATEUR.NOM)).strip(),
                    r.get(PROFIL.LIBELLE)));
  }
}
