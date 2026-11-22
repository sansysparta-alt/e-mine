package gn.gov.mines.emine.instruction.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.COMPLEMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.PROFIL;
import static gn.gov.mines.emine.shared.jooq.Tables.UTILISATEUR;

import gn.gov.mines.emine.instruction.domaine.Complement;
import gn.gov.mines.emine.shared.jooq.Routines;
import gn.gov.mines.emine.shared.jooq.enums.StatutDemande;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;
import org.springframework.stereotype.Repository;

/**
 * Compléments (US-05.2, table {@code complement}, V12). Toujours appelé dans une transaction : la
 * RLS réserve la création aux agents et la réponse au demandeur ; la base pose le délai de réponse,
 * fige la demande de complément, notifie le demandeur et journalise chaque écriture.
 */
@Repository
public class ComplementRepository {

  private final DSLContext dsl;

  public ComplementRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** Ouvre un complément ; renvoie son identifiant. */
  public long ouvrir(
      long demandeId, long auteurId, String statutOrigine, String motif, List<String> pieces) {
    return dsl.insertInto(COMPLEMENT)
        .set(COMPLEMENT.DEMANDE_ID, demandeId)
        .set(COMPLEMENT.DEMANDE_PAR, auteurId)
        .set(COMPLEMENT.STATUT_ORIGINE, StatutDemande.lookupLiteral(statutOrigine))
        .set(COMPLEMENT.MOTIF, motif)
        .set(COMPLEMENT.PIECES_DEMANDEES, pieces.toArray(String[]::new))
        // Remplacée par la base (demande_le + 5 jours), exigée par la contrainte NOT NULL.
        .set(COMPLEMENT.DATE_LIMITE, Instant.EPOCH)
        .returning(COMPLEMENT.ID)
        .fetchOne(COMPLEMENT.ID);
  }

  /** Complément en attente de réponse, s'il y en a un (au plus un par demande). */
  public Optional<Complement> ouvert(long demandeId) {
    return selection()
        .where(COMPLEMENT.DEMANDE_ID.eq(demandeId))
        .and(COMPLEMENT.REPONDU_LE.isNull())
        .and(COMPLEMENT.CLOS_LE.isNull())
        .fetchOptional(ComplementRepository::versComplement);
  }

  /** Tous les compléments de la demande, du plus récent au plus ancien. */
  public List<Complement> lister(long demandeId) {
    return selection()
        .where(COMPLEMENT.DEMANDE_ID.eq(demandeId))
        .orderBy(COMPLEMENT.DEMANDE_LE.desc(), COMPLEMENT.ID.desc())
        .fetch(ComplementRepository::versComplement);
  }

  /** Inscrit la réponse du demandeur (0 ligne si le complément n'est plus ouvert). */
  public int repondre(long id, long auteurId, String reponse, Instant le) {
    return dsl.update(COMPLEMENT)
        .set(COMPLEMENT.REPONDU_LE, le)
        .set(COMPLEMENT.REPONDU_PAR, auteurId)
        .set(COMPLEMENT.REPONSE, reponse)
        .where(COMPLEMENT.ID.eq(id))
        .and(COMPLEMENT.REPONDU_LE.isNull())
        .and(COMPLEMENT.CLOS_LE.isNull())
        .execute();
  }

  /** Relance par l'agent : nouveau délai de réponse à compter de maintenant. */
  public int relancer(long id, Instant le, Duration delai) {
    return dsl.update(COMPLEMENT)
        .set(COMPLEMENT.RELANCE_LE, le)
        .set(COMPLEMENT.DATE_LIMITE, le.plus(delai))
        .where(COMPLEMENT.ID.eq(id))
        .and(COMPLEMENT.REPONDU_LE.isNull())
        .and(COMPLEMENT.CLOS_LE.isNull())
        .execute();
  }

  /** Clôture sans réponse par un agent. */
  public int clore(long id, long auteurId, Instant le) {
    return dsl.update(COMPLEMENT)
        .set(COMPLEMENT.CLOS_LE, le)
        .set(COMPLEMENT.CLOS_PAR, auteurId)
        .where(COMPLEMENT.ID.eq(id))
        .and(COMPLEMENT.REPONDU_LE.isNull())
        .and(COMPLEMENT.CLOS_LE.isNull())
        .execute();
  }

  /**
   * Relance automatique à J+3 (fonction de la base, idempotente) ; renvoie le nombre de relances.
   */
  public int relancerAutomatiquement() {
    Integer n = dsl.select(Routines.fnRelancerComplements()).fetchOne(0, Integer.class);
    return n == null ? 0 : n;
  }

  private SelectOnConditionStep<Record> selection() {
    return dsl.select(COMPLEMENT.fields())
        .select(UTILISATEUR.PRENOM, UTILISATEUR.NOM, PROFIL.LIBELLE)
        .from(COMPLEMENT)
        .join(UTILISATEUR)
        .on(UTILISATEUR.ID.eq(COMPLEMENT.DEMANDE_PAR))
        .join(PROFIL)
        .on(PROFIL.ID.eq(UTILISATEUR.PROFIL_ID));
  }

  private static Complement versComplement(Record r) {
    String[] pieces = r.get(COMPLEMENT.PIECES_DEMANDEES);
    return new Complement(
        r.get(COMPLEMENT.ID),
        r.get(COMPLEMENT.STATUT_ORIGINE).getLiteral(),
        r.get(COMPLEMENT.MOTIF),
        pieces == null ? List.of() : Arrays.asList(pieces),
        r.get(COMPLEMENT.DEMANDE_LE),
        r.get(COMPLEMENT.DATE_LIMITE),
        r.get(COMPLEMENT.RELANCE_LE),
        r.get(COMPLEMENT.REPONDU_LE),
        r.get(COMPLEMENT.REPONSE),
        r.get(COMPLEMENT.CLOS_LE),
        (r.get(UTILISATEUR.PRENOM) + " " + r.get(UTILISATEUR.NOM)).strip(),
        r.get(PROFIL.LIBELLE));
  }
}
