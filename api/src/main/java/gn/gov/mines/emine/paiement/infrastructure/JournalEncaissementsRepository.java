package gn.gov.mines.emine.paiement.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.DEMANDE;
import static gn.gov.mines.emine.shared.jooq.Tables.PAIEMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.REMBOURSEMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.TYPE_DOCUMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.UTILISATEUR;

import gn.gov.mines.emine.shared.jooq.enums.MoyenPaiement;
import gn.gov.mines.emine.shared.jooq.enums.ObjetPaiement;
import gn.gov.mines.emine.shared.jooq.enums.StatutPaiement;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Select;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

/**
 * Journal des encaissements (US-04.11) : les <b>recettes</b> du Ministère — montant dû de chaque
 * paiement accepté (frais, droits, attestations) à sa date de paiement — et, en négatif, les frais
 * remboursés sur rejet d'une demande (règle V1-4), à la date de l'ordre du Trésor. Les sommes
 * reçues sans contrepartie (excédent, doublon, paiement rejeté) ne sont pas des recettes : elles
 * sont suivies dans les remboursements. Dates à l'heure de Conakry (session de la base).
 */
@Repository
public class JournalEncaissementsRepository {

  private final DSLContext dsl;

  public JournalEncaissementsRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** Critères du journal (tous facultatifs) ; période en dates de Conakry, bornes incluses. */
  public record Critere(LocalDate du, LocalDate au, MoyenPaiement moyen, ObjetPaiement objet) {}

  /** Une ligne : un encaissement (montant positif) ou un remboursement de frais (négatif). */
  public record Ligne(
      String type,
      Instant date,
      String reference,
      String piece,
      long montantGnf,
      String moyen,
      String objet,
      Long demandeId,
      String numeroDemande,
      String codeDocument,
      String payeur) {}

  /** Totaux pour les critères : recettes, frais remboursés, solde, nombre de lignes. */
  public record Totaux(
      long encaisseGnf, long rembourseGnf, long nombre, long tresorPayGnf, long virementGnf) {
    public long soldeGnf() {
      return encaisseGnf - rembourseGnf;
    }
  }

  public PageResultat<Ligne> lister(Critere c, Pagination pagination) {
    Table<? extends Record> j = journal(c).asTable("j");
    long total = dsl.fetchCount(j);
    List<Ligne> lignes =
        dsl.selectFrom(j)
            .orderBy(
                DSL.field(DSL.name("j", "date_operation")).desc(),
                DSL.field(DSL.name("j", "reference")).desc())
            .limit(pagination.taille())
            .offset(pagination.decalage())
            .fetch(JournalEncaissementsRepository::ligne);
    return new PageResultat<>(lignes, pagination.page(), pagination.taille(), total);
  }

  /** Toutes les lignes, dans l'ordre chronologique (export), lues en flux. */
  public void parcourir(Critere c, Consumer<Ligne> traitement) {
    Table<? extends Record> j = journal(c).asTable("j");
    try (var curseur =
        dsl.selectFrom(j)
            .orderBy(
                DSL.field(DSL.name("j", "date_operation")).asc(),
                DSL.field(DSL.name("j", "reference")).asc())
            .fetchSize(500)
            .fetchLazy()) {
      curseur.forEach(r -> traitement.accept(ligne(r)));
    }
  }

  public Totaux totaux(Critere c) {
    Table<? extends Record> j = journal(c).asTable("j");
    Field<Long> montant = DSL.field(DSL.name("j", "montant_gnf"), Long.class);
    Field<String> type = DSL.field(DSL.name("j", "type"), String.class);
    Field<String> moyen = DSL.field(DSL.name("j", "moyen"), String.class);
    var r =
        dsl.select(
                DSL.sum(montant).filterWhere(type.eq("encaissement")),
                DSL.sum(DSL.abs(montant)).filterWhere(type.eq("remboursement")),
                DSL.count(),
                DSL.sum(montant).filterWhere(moyen.eq("TRESORPAY")),
                DSL.sum(montant).filterWhere(moyen.eq("VIREMENT")))
            .from(j)
            .fetchOne();
    return new Totaux(
        entier(r.value1()), entier(r.value2()), r.value3(), entier(r.value4()), entier(r.value5()));
  }

  private Select<? extends Record> journal(Critere c) {
    var payeur = UTILISATEUR.as("payeur_journal");
    Field<LocalDate> datePaiement =
        DSL.field("({0})::date", SQLDataType.LOCALDATE, PAIEMENT.DATE_PAIEMENT);
    Field<LocalDate> dateOrdre =
        DSL.field("({0})::date", SQLDataType.LOCALDATE, REMBOURSEMENT.EXECUTE_LE);
    Condition encaissements =
        PAIEMENT
            .STATUT
            .in(StatutPaiement.paye, StatutPaiement.remboursement_demande, StatutPaiement.rembourse)
            // US-04.12 : une seule recette, celle du paiement initial au montant dû, datée du
            // complément ; le complément n'est pas compté une seconde fois.
            .and(PAIEMENT.COMPLEMENT_DE.isNull())
            .and(periode(datePaiement, c))
            .and(filtres(c));
    Condition remboursements =
        REMBOURSEMENT
            .STATUT
            .eq("execute")
            .and(REMBOURSEMENT.MOTIF.eq("rejet_demande"))
            .and(periode(dateOrdre, c))
            .and(filtres(c));
    Field<String> nomPayeur = DSL.concat(payeur.PRENOM, DSL.inline(" "), payeur.NOM);
    var lignesEncaissement =
        dsl.select(
                DSL.inline("encaissement").as("type"),
                PAIEMENT.DATE_PAIEMENT.as("date_operation"),
                PAIEMENT.REFERENCE.as("reference"),
                PAIEMENT.RECU_NUMERO.as("piece"),
                PAIEMENT.MONTANT_GNF.as("montant_gnf"),
                PAIEMENT.MOYEN.cast(SQLDataType.VARCHAR).as("moyen"),
                PAIEMENT.OBJET.cast(SQLDataType.VARCHAR).as("objet"),
                DEMANDE.ID.as("demande_id"),
                DEMANDE.NUMERO.as("numero_demande"),
                TYPE_DOCUMENT.CODE.as("code_document"),
                nomPayeur.as("payeur"))
            .from(PAIEMENT)
            .join(DEMANDE)
            .on(DEMANDE.ID.eq(PAIEMENT.DEMANDE_ID))
            .join(TYPE_DOCUMENT)
            .on(TYPE_DOCUMENT.ID.eq(DEMANDE.TYPE_DOCUMENT_ID))
            .leftJoin(payeur)
            .on(payeur.ID.eq(PAIEMENT.PAYEUR_ID))
            .where(encaissements);
    var lignesRemboursement =
        dsl.select(
                DSL.inline("remboursement").as("type"),
                REMBOURSEMENT.EXECUTE_LE.as("date_operation"),
                REMBOURSEMENT.NUMERO.as("reference"),
                REMBOURSEMENT.REFERENCE_ORDRE.as("piece"),
                REMBOURSEMENT.MONTANT_GNF.neg().as("montant_gnf"),
                PAIEMENT.MOYEN.cast(SQLDataType.VARCHAR).as("moyen"),
                PAIEMENT.OBJET.cast(SQLDataType.VARCHAR).as("objet"),
                DEMANDE.ID.as("demande_id"),
                DEMANDE.NUMERO.as("numero_demande"),
                TYPE_DOCUMENT.CODE.as("code_document"),
                nomPayeur.as("payeur"))
            .from(REMBOURSEMENT)
            .join(PAIEMENT)
            .on(PAIEMENT.ID.eq(REMBOURSEMENT.PAIEMENT_ID))
            .join(DEMANDE)
            .on(DEMANDE.ID.eq(PAIEMENT.DEMANDE_ID))
            .join(TYPE_DOCUMENT)
            .on(TYPE_DOCUMENT.ID.eq(DEMANDE.TYPE_DOCUMENT_ID))
            .leftJoin(payeur)
            .on(payeur.ID.eq(PAIEMENT.PAYEUR_ID))
            .where(remboursements);
    return lignesEncaissement.unionAll(lignesRemboursement);
  }

  private static Condition periode(Field<LocalDate> date, Critere c) {
    Condition p = DSL.trueCondition();
    if (c.du() != null) {
      p = p.and(date.ge(c.du()));
    }
    if (c.au() != null) {
      p = p.and(date.le(c.au()));
    }
    return p;
  }

  private static Condition filtres(Critere c) {
    Condition f = DSL.trueCondition();
    if (c.moyen() != null) {
      f = f.and(PAIEMENT.MOYEN.eq(c.moyen()));
    }
    if (c.objet() != null) {
      f = f.and(PAIEMENT.OBJET.eq(c.objet()));
    }
    return f;
  }

  private static Ligne ligne(Record r) {
    return new Ligne(
        r.get("type", String.class),
        r.get("date_operation", Instant.class),
        r.get("reference", String.class),
        r.get("piece", String.class),
        r.get("montant_gnf", Long.class),
        r.get("moyen", String.class),
        r.get("objet", String.class),
        r.get("demande_id", Long.class),
        r.get("numero_demande", String.class),
        r.get("code_document", String.class),
        r.get("payeur", String.class));
  }

  private static long entier(Object valeur) {
    return valeur == null ? 0 : ((Number) valeur).longValue();
  }
}
