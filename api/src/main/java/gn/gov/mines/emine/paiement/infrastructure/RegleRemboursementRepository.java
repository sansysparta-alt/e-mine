package gn.gov.mines.emine.paiement.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.UTILISATEUR;
import static gn.gov.mines.emine.shared.jooq.Tables.VERSION_REGLE_REMBOURSEMENT;

import gn.gov.mines.emine.shared.gouvernance.EtatVersion;
import gn.gov.mines.emine.shared.jooq.Routines;
import gn.gov.mines.emine.shared.jooq.tables.records.VersionRegleRemboursementRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * Règle de remboursement des frais (décision V1-4, V32) : versions proposées par l'administrateur
 * et validées par le Ministre, comme la règle des délais. Écriture par les fonctions de la base
 * seulement (droits, une proposition ouverte, date d'effet).
 */
@Repository
public class RegleRemboursementRepository {

  private static final Field<Integer> EN_VIGUEUR =
      DSL.function("fn_regle_remboursement_version_en_vigueur", Integer.class);

  private final DSLContext dsl;

  public RegleRemboursementRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** Une version de la règle et son état affiché ({@link EtatVersion}). */
  public record Version(
      int version,
      String etat,
      boolean rembourserSiRejet,
      LocalDate dateEffet,
      String motif,
      String proposeePar,
      Instant proposeeLe,
      String decideePar,
      Instant decideeLe,
      String motifRefus) {}

  /** Règle en vigueur aujourd'hui : rembourser ou non les frais d'une demande rejetée. */
  public boolean rembourserSiRejet() {
    return dsl.select(VERSION_REGLE_REMBOURSEMENT.REMBOURSER_SI_REJET)
        .from(VERSION_REGLE_REMBOURSEMENT)
        .where(VERSION_REGLE_REMBOURSEMENT.VERSION.eq(EN_VIGUEUR))
        .fetchSingle(VERSION_REGLE_REMBOURSEMENT.REMBOURSER_SI_REJET);
  }

  /** Toutes les versions, la plus récente d'abord. */
  public List<Version> versions() {
    var proposeePar = UTILISATEUR.as("proposee_par");
    var decideePar = UTILISATEUR.as("decidee_par");
    Integer enVigueur = dsl.select(EN_VIGUEUR).fetchOne(EN_VIGUEUR);
    return dsl.select(
            VERSION_REGLE_REMBOURSEMENT.asterisk(),
            nom(proposeePar.PRENOM, proposeePar.NOM).as("auteur"),
            nom(decideePar.PRENOM, decideePar.NOM).as("decideur"))
        .from(VERSION_REGLE_REMBOURSEMENT)
        .leftJoin(proposeePar)
        .on(proposeePar.ID.eq(VERSION_REGLE_REMBOURSEMENT.PROPOSEE_PAR))
        .leftJoin(decideePar)
        .on(decideePar.ID.eq(VERSION_REGLE_REMBOURSEMENT.DECIDEE_PAR))
        .orderBy(VERSION_REGLE_REMBOURSEMENT.VERSION.desc())
        .fetch(
            r -> {
              VersionRegleRemboursementRecord v = r.into(VERSION_REGLE_REMBOURSEMENT);
              return new Version(
                  v.getVersion(),
                  EtatVersion.etat(v.getStatut(), v.getVersion(), enVigueur),
                  v.getRembourserSiRejet(),
                  v.getDateEffet(),
                  v.getMotif(),
                  vide(r.get("auteur", String.class)),
                  v.getProposeeLe(),
                  vide(r.get("decideur", String.class)),
                  v.getDecideeLe(),
                  v.getMotifRefus());
            });
  }

  public int proposer(boolean rembourserSiRejet, String motif, LocalDate dateEffet) {
    return Routines.fnProposerRegleRemboursement(
        dsl.configuration(), rembourserSiRejet, motif, dateEffet);
  }

  public LocalDate decider(int version, EtatVersion.Decision decision, String motif) {
    return Routines.fnDeciderRegleRemboursement(
        dsl.configuration(), version, decision.code(), motif);
  }

  private static Field<String> nom(Field<String> prenom, Field<String> nom) {
    return DSL.trim(DSL.concat(DSL.coalesce(prenom, ""), DSL.val(" "), DSL.coalesce(nom, "")));
  }

  private static String vide(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
