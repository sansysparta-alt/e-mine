package gn.gov.mines.emine.demande.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.UTILISATEUR;
import static gn.gov.mines.emine.shared.jooq.Tables.VERSION_REGLE_DELAI;

import gn.gov.mines.emine.demande.domaine.RegleDelais;
import gn.gov.mines.emine.demande.domaine.VersionRegleDelai;
import gn.gov.mines.emine.shared.gouvernance.EtatVersion;
import gn.gov.mines.emine.shared.jooq.Routines;
import gn.gov.mines.emine.shared.jooq.tables.records.VersionRegleDelaiRecord;
import java.time.LocalDate;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * Règle des délais et ses versions (table {@code version_regle_delai}, V27) : lue ici, écrite
 * seulement par les fonctions de la base, qui contrôlent les droits et tracent tout.
 */
@Repository
public class ParametresDelaiRepository {

  /** Version en vigueur aujourd'hui (heure de Conakry), calculée par la base. */
  static final Field<Integer> EN_VIGUEUR =
      DSL.function("fn_regle_delai_version_en_vigueur", Integer.class);

  private final DSLContext dsl;

  public ParametresDelaiRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** Règle en vigueur aujourd'hui (celle des demandes déposées aujourd'hui). */
  public RegleDelais.Parametres lire() {
    return parametres(
        dsl.selectFrom(VERSION_REGLE_DELAI)
            .where(VERSION_REGLE_DELAI.VERSION.eq(EN_VIGUEUR))
            .fetchSingle());
  }

  /** Toutes les versions, la plus récente d'abord, avec leur état. */
  public List<VersionRegleDelai> versions() {
    var proposeePar = UTILISATEUR.as("proposee_par");
    var decideePar = UTILISATEUR.as("decidee_par");
    Integer enVigueur = dsl.select(EN_VIGUEUR).fetchOne(EN_VIGUEUR);
    return dsl.select(
            VERSION_REGLE_DELAI.asterisk(),
            nom(proposeePar.PRENOM, proposeePar.NOM).as("auteur"),
            nom(decideePar.PRENOM, decideePar.NOM).as("decideur"))
        .from(VERSION_REGLE_DELAI)
        .leftJoin(proposeePar)
        .on(proposeePar.ID.eq(VERSION_REGLE_DELAI.PROPOSEE_PAR))
        .leftJoin(decideePar)
        .on(decideePar.ID.eq(VERSION_REGLE_DELAI.DECIDEE_PAR))
        .orderBy(VERSION_REGLE_DELAI.VERSION.desc())
        .fetch(
            r -> {
              VersionRegleDelaiRecord v = r.into(VERSION_REGLE_DELAI);
              return new VersionRegleDelai(
                  v.getVersion(),
                  EtatVersion.etat(v.getStatut(), v.getVersion(), enVigueur),
                  parametres(v),
                  v.getDateEffet(),
                  v.getMotif(),
                  vide(r.get("auteur", String.class)),
                  v.getProposeeLe(),
                  vide(r.get("decideur", String.class)),
                  v.getDecideeLe(),
                  v.getMotifRefus());
            });
  }

  /**
   * Propose une nouvelle règle, à valider par le Ministre (fonction de la base : droits, une seule
   * proposition ouverte, date d'effet, DOC-010 si rien ne change).
   *
   * @return numéro de la version proposée
   */
  public int proposer(RegleDelais.Parametres p, String motif, LocalDate dateEffet) {
    return Routines.fnProposerRegleDelai(
        dsl.configuration(),
        p.depart(),
        p.pauseComplement(),
        p.pauseDroits(),
        (short) p.seuilProchePourcent(),
        (short) p.seuilProcheMinJours(),
        motif,
        dateEffet);
  }

  /**
   * Décision sur une proposition (fonction de la base : droits, statut, date d'effet).
   *
   * @return date d'effet retenue pour une approbation, {@code null} sinon
   */
  public LocalDate decider(int version, EtatVersion.Decision decision, String motif) {
    return Routines.fnDeciderRegleDelai(dsl.configuration(), version, decision.code(), motif);
  }

  private static RegleDelais.Parametres parametres(VersionRegleDelaiRecord v) {
    return new RegleDelais.Parametres(
        v.getDepart(),
        v.getPauseComplement(),
        v.getPauseDroits(),
        v.getSeuilProchePourcent(),
        v.getSeuilProcheMinJours());
  }

  private static Field<String> nom(Field<String> prenom, Field<String> nom) {
    return DSL.trim(DSL.concat(DSL.coalesce(prenom, ""), DSL.val(" "), DSL.coalesce(nom, "")));
  }

  private static String vide(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
