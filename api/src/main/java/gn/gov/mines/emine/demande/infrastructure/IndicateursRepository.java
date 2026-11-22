package gn.gov.mines.emine.demande.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.DEMANDE;
import static gn.gov.mines.emine.shared.jooq.Tables.HISTORIQUE_STATUT_DEMANDE;
import static gn.gov.mines.emine.shared.jooq.Tables.PAIEMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.TYPE_DOCUMENT;

import gn.gov.mines.emine.demande.domaine.Indicateurs;
import gn.gov.mines.emine.demande.domaine.Periode;
import gn.gov.mines.emine.shared.jooq.Routines;
import gn.gov.mines.emine.shared.jooq.enums.StatutDemande;
import gn.gov.mines.emine.shared.jooq.enums.StatutPaiement;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * Indicateurs du tableau de bord (correctifs ergonomie), calculés par la base sur les demandes et
 * paiements visibles (RLS). Toujours appelé dans une transaction.
 */
@Repository
public class IndicateursRepository {

  /** Date de délivrance : passage au statut « délivrée » (historique tenu par la base). */
  private static final Field<Instant> DELIVREE_LE =
      DSL.field(
          DSL.select(DSL.max(HISTORIQUE_STATUT_DEMANDE.CREATED_AT))
              .from(HISTORIQUE_STATUT_DEMANDE)
              .where(HISTORIQUE_STATUT_DEMANDE.DEMANDE_ID.eq(DEMANDE.ID))
              .and(HISTORIQUE_STATUT_DEMANDE.NOUVEAU_STATUT.eq(StatutDemande.delivree)));

  private final DSLContext dsl;

  public IndicateursRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public Indicateurs calculer(Periode periode, Instant maintenant) {
    Instant debut = periode.debut(maintenant);
    Condition visible = DEMANDE.DELETED_AT.isNull();

    Map<String, Long> enCours =
        parDocument(visible.and(DEMANDE.STATUT.in(DemandeRepository.STATUTS_EN_COURS)));
    Map<String, Long> enRetard =
        parDocument(visible.and(DemandeRepository.conditionEnRetard(maintenant)));

    Condition delivreesPeriode =
        visible.and(DEMANDE.STATUT.eq(StatutDemande.delivree)).and(DELIVREE_LE.ge(debut));
    // Délai de traitement réel : hors jours passés en complément (délai suspendu, US-05.2).
    // Délai de délivrance compté jour du dépôt inclus (délivré le jour même = 1 jour, le lendemain
    // = 2 jours), hors jours passés en complément ; jamais moins d'un jour.
    Field<BigDecimal> delai =
        DSL.avg(
            DSL.greatest(
                DSL.inline(1),
                DemandeRepository.joursCalendaires(DEMANDE.DATE_DEPOT, DELIVREE_LE)
                    .minus(Routines.fnJoursSuspendus(DEMANDE.ID, DELIVREE_LE))
                    .plus(1)));
    Map<String, Long> delivrees = new HashMap<>();
    Map<String, Double> delais = new HashMap<>();
    dsl.select(TYPE_DOCUMENT.CODE, DSL.count(), delai)
        .from(DEMANDE)
        .join(TYPE_DOCUMENT)
        .on(TYPE_DOCUMENT.ID.eq(DEMANDE.TYPE_DOCUMENT_ID))
        .where(delivreesPeriode)
        .groupBy(TYPE_DOCUMENT.CODE)
        .fetch()
        .forEach(
            r -> {
              delivrees.put(r.value1(), r.value2().longValue());
              delais.put(r.value1(), arrondi(r.value3()));
            });
    Double delaiMoyen =
        arrondi(
            dsl.select(delai)
                .from(DEMANDE)
                .join(TYPE_DOCUMENT)
                .on(TYPE_DOCUMENT.ID.eq(DEMANDE.TYPE_DOCUMENT_ID))
                .where(delivreesPeriode)
                .fetchOne(0, BigDecimal.class));

    Map<String, Long> encaissements = new HashMap<>();
    dsl.select(TYPE_DOCUMENT.CODE, DSL.sum(PAIEMENT.MONTANT_RECU_GNF))
        .from(PAIEMENT)
        .join(DEMANDE)
        .on(DEMANDE.ID.eq(PAIEMENT.DEMANDE_ID))
        .join(TYPE_DOCUMENT)
        .on(TYPE_DOCUMENT.ID.eq(DEMANDE.TYPE_DOCUMENT_ID))
        .where(PAIEMENT.STATUT.eq(StatutPaiement.paye))
        .and(PAIEMENT.DATE_PAIEMENT.ge(debut))
        .groupBy(TYPE_DOCUMENT.CODE)
        .fetch()
        .forEach(
            r -> encaissements.put(r.value1(), r.value2() == null ? 0L : r.value2().longValue()));

    List<Indicateurs.ParDocument> lignes = new ArrayList<>();
    for (String code :
        dsl.select(TYPE_DOCUMENT.CODE)
            .from(TYPE_DOCUMENT)
            .orderBy(TYPE_DOCUMENT.ID)
            .fetch(TYPE_DOCUMENT.CODE)) {
      Indicateurs.ParDocument ligne =
          new Indicateurs.ParDocument(
              code,
              enCours.getOrDefault(code, 0L),
              enRetard.getOrDefault(code, 0L),
              delivrees.getOrDefault(code, 0L),
              delais.get(code),
              encaissements.getOrDefault(code, 0L));
      if (ligne.enCours() + ligne.enRetard() + ligne.delivrees() + ligne.encaissementsGnf() > 0) {
        lignes.add(ligne);
      }
    }

    return new Indicateurs(
        periode,
        debut,
        maintenant,
        somme(enRetard),
        compter(visible.and(DemandeRepository.conditionProcheDepassement(maintenant))),
        compter(visible.and(DEMANDE.STATUT.eq(StatutDemande.complement_requis))),
        compter(
            visible
                .and(DEMANDE.STATUT.eq(StatutDemande.deposee))
                .and(DSL.not(DemandeRepository.FRAIS_PAYES_COND))),
        compter(visible.and(DemandeRepository.CONDITION_BLOQUE)),
        compter(
            visible.and(DemandeRepository.conditionTraiteParMoi(Periode.debutDuJour(maintenant)))),
        somme(delivrees),
        delaiMoyen,
        somme(encaissements),
        lignes);
  }

  private Map<String, Long> parDocument(Condition condition) {
    Map<String, Long> nombres = new HashMap<>();
    dsl.select(TYPE_DOCUMENT.CODE, DSL.count())
        .from(DEMANDE)
        .join(TYPE_DOCUMENT)
        .on(TYPE_DOCUMENT.ID.eq(DEMANDE.TYPE_DOCUMENT_ID))
        .where(condition)
        .groupBy(TYPE_DOCUMENT.CODE)
        .fetch()
        .forEach(r -> nombres.put(r.value1(), r.value2().longValue()));
    return nombres;
  }

  private long compter(Condition condition) {
    return dsl.selectCount()
        .from(DEMANDE)
        .join(TYPE_DOCUMENT)
        .on(TYPE_DOCUMENT.ID.eq(DEMANDE.TYPE_DOCUMENT_ID))
        .where(condition)
        .fetchOne(0, Long.class);
  }

  private static long somme(Map<String, Long> nombres) {
    return nombres.values().stream().mapToLong(Long::longValue).sum();
  }

  /** Moyenne arrondie à un chiffre après la virgule, ou {@code null}. */
  private static Double arrondi(BigDecimal valeur) {
    return valeur == null ? null : valeur.setScale(1, RoundingMode.HALF_UP).doubleValue();
  }
}
