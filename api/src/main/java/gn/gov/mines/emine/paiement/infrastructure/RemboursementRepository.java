package gn.gov.mines.emine.paiement.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.DEMANDE;
import static gn.gov.mines.emine.shared.jooq.Tables.PAIEMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.REMBOURSEMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.UTILISATEUR;

import gn.gov.mines.emine.shared.jooq.enums.StatutPaiement;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * Remboursements (US-04.10) : demande, validation ou refus par le Responsable fiscal, exécution
 * hors plateforme par le Trésor (référence de l'ordre). Numéro {@code RMB-AAAA-NNNNNN} et cycle de
 * vie contrôlés par la base (V32) ; RLS : un usager ne voit que ceux de ses paiements.
 */
@Repository
public class RemboursementRepository {

  /** Statuts d'un remboursement, dans l'ordre du circuit. */
  public static final List<String> STATUTS = List.of("demande", "valide", "execute", "refuse");

  private final DSLContext dsl;

  public RemboursementRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** Un remboursement, avec le paiement et la demande concernés. */
  public record Remboursement(
      long id,
      String numero,
      String statut,
      String motif,
      long montantGnf,
      String explication,
      String motifRefus,
      String referenceOrdre,
      Instant demandeLe,
      String demandePar,
      Instant decideLe,
      String decidePar,
      Instant executeLe,
      String referencePaiement,
      String objetPaiement,
      String moyenPaiement,
      Long demandeId,
      String numeroDemande,
      String beneficiaire,
      UUID paiementId) {}

  /** Crée une demande de remboursement ; renvoie son identifiant. */
  public long creer(
      UUID paiementId,
      String motif,
      long montantGnf,
      long demandePar,
      Long ligneReleveId,
      String explication) {
    return dsl.insertInto(REMBOURSEMENT)
        .set(REMBOURSEMENT.PAIEMENT_ID, paiementId)
        .set(REMBOURSEMENT.MOTIF, motif)
        .set(REMBOURSEMENT.MONTANT_GNF, montantGnf)
        .set(REMBOURSEMENT.DEMANDE_PAR, demandePar)
        .set(REMBOURSEMENT.LIGNE_RELEVE_ID, ligneReleveId)
        .set(REMBOURSEMENT.EXPLICATION, explication)
        .returning(REMBOURSEMENT.ID)
        .fetchOne(REMBOURSEMENT.ID);
  }

  /** Nombre de remboursements par statut (filtres de la liste). */
  public Map<String, Long> compter() {
    Map<String, Long> n = new LinkedHashMap<>();
    STATUTS.forEach(s -> n.put(s, 0L));
    dsl.select(REMBOURSEMENT.STATUT, DSL.count())
        .from(REMBOURSEMENT)
        .groupBy(REMBOURSEMENT.STATUT)
        .forEach(r -> n.put(r.value1(), r.value2().longValue()));
    return n;
  }

  public PageResultat<Remboursement> lister(String statut, Pagination pagination) {
    Condition c = statut == null ? DSL.trueCondition() : REMBOURSEMENT.STATUT.eq(statut);
    long total = dsl.fetchCount(REMBOURSEMENT, c);
    List<Remboursement> elements =
        select()
            .where(c)
            // À faire d'abord : les plus anciennes demandes, puis les validations à exécuter.
            .orderBy(
                DSL.when(REMBOURSEMENT.STATUT.eq("demande"), 0)
                    .when(REMBOURSEMENT.STATUT.eq("valide"), 1)
                    .otherwise(2),
                DSL.when(REMBOURSEMENT.STATUT.in("demande", "valide"), REMBOURSEMENT.CREATED_AT)
                    .asc(),
                REMBOURSEMENT.UPDATED_AT.desc())
            .limit(pagination.taille())
            .offset(pagination.decalage())
            .fetch(RemboursementRepository::remboursement);
    return new PageResultat<>(elements, pagination.page(), pagination.taille(), total);
  }

  public Optional<Remboursement> lire(long id) {
    return select()
        .where(REMBOURSEMENT.ID.eq(id))
        .fetchOptional(RemboursementRepository::remboursement);
  }

  /** Remboursements d'un paiement visible (RLS), du plus récent au plus ancien. */
  public List<Remboursement> duPaiement(UUID paiementId) {
    return select()
        .where(REMBOURSEMENT.PAIEMENT_ID.eq(paiementId))
        .orderBy(REMBOURSEMENT.CREATED_AT.desc())
        .fetch(RemboursementRepository::remboursement);
  }

  /** Verrouille un remboursement avant décision ; renvoie son statut et son motif. */
  public Optional<Record> verrouiller(long id) {
    return dsl.select(REMBOURSEMENT.STATUT, REMBOURSEMENT.MOTIF, REMBOURSEMENT.PAIEMENT_ID)
        .from(REMBOURSEMENT)
        .where(REMBOURSEMENT.ID.eq(id))
        .forUpdate()
        .fetchOptional()
        .map(r -> (Record) r);
  }

  public void valider(long id, long par) {
    dsl.update(REMBOURSEMENT)
        .set(REMBOURSEMENT.STATUT, "valide")
        .set(REMBOURSEMENT.VALIDE_PAR, par)
        .set(REMBOURSEMENT.DATE_VALIDATION, DSL.currentInstant())
        .where(REMBOURSEMENT.ID.eq(id))
        .execute();
  }

  public void refuser(long id, long par, String motif) {
    dsl.update(REMBOURSEMENT)
        .set(REMBOURSEMENT.STATUT, "refuse")
        .set(REMBOURSEMENT.VALIDE_PAR, par)
        .set(REMBOURSEMENT.DATE_VALIDATION, DSL.currentInstant())
        .set(REMBOURSEMENT.MOTIF_REFUS, motif)
        .where(REMBOURSEMENT.ID.eq(id))
        .execute();
  }

  /** Ordre du Trésor enregistré : le remboursement est exécuté (notification par la base). */
  public void executer(long id, long par, String referenceOrdre) {
    dsl.update(REMBOURSEMENT)
        .set(REMBOURSEMENT.STATUT, "execute")
        .set(REMBOURSEMENT.REFERENCE_ORDRE, referenceOrdre)
        .set(REMBOURSEMENT.EXECUTE_PAR, par)
        .set(REMBOURSEMENT.EXECUTE_LE, DSL.currentInstant())
        .where(REMBOURSEMENT.ID.eq(id))
        .execute();
  }

  /** Refus du remboursement d'un paiement entier : le paiement redevient « payé ». */
  public void paiementRedevientPaye(UUID paiementId) {
    dsl.update(PAIEMENT)
        .set(PAIEMENT.STATUT, StatutPaiement.paye)
        .where(PAIEMENT.ID.eq(paiementId))
        .and(PAIEMENT.STATUT.eq(StatutPaiement.remboursement_demande))
        .execute();
  }

  private org.jooq.SelectOnConditionStep<Record> select() {
    var demandeur = UTILISATEUR.as("demandeur_remb");
    var decideur = UTILISATEUR.as("decideur_remb");
    var payeur = UTILISATEUR.as("payeur_remb");
    return dsl.select(REMBOURSEMENT.fields())
        .select(
            PAIEMENT.REFERENCE,
            PAIEMENT.OBJET,
            PAIEMENT.MOYEN,
            PAIEMENT.DEMANDE_ID,
            DEMANDE.NUMERO,
            DSL.concat(demandeur.PRENOM, DSL.inline(" "), demandeur.NOM).as("demande_par_nom"),
            DSL.concat(decideur.PRENOM, DSL.inline(" "), decideur.NOM).as("decide_par_nom"),
            DSL.concat(payeur.PRENOM, DSL.inline(" "), payeur.NOM).as("beneficiaire_nom"))
        .from(REMBOURSEMENT)
        .join(PAIEMENT)
        .on(PAIEMENT.ID.eq(REMBOURSEMENT.PAIEMENT_ID))
        .join(DEMANDE)
        .on(DEMANDE.ID.eq(PAIEMENT.DEMANDE_ID))
        .leftJoin(demandeur)
        .on(demandeur.ID.eq(REMBOURSEMENT.DEMANDE_PAR))
        .leftJoin(decideur)
        .on(decideur.ID.eq(REMBOURSEMENT.VALIDE_PAR))
        .leftJoin(payeur)
        .on(payeur.ID.eq(PAIEMENT.PAYEUR_ID));
  }

  private static Remboursement remboursement(Record r) {
    return new Remboursement(
        r.get(REMBOURSEMENT.ID),
        r.get(REMBOURSEMENT.NUMERO),
        r.get(REMBOURSEMENT.STATUT),
        r.get(REMBOURSEMENT.MOTIF),
        r.get(REMBOURSEMENT.MONTANT_GNF),
        r.get(REMBOURSEMENT.EXPLICATION),
        r.get(REMBOURSEMENT.MOTIF_REFUS),
        r.get(REMBOURSEMENT.REFERENCE_ORDRE),
        r.get(REMBOURSEMENT.CREATED_AT),
        r.get("demande_par_nom", String.class),
        r.get(REMBOURSEMENT.DATE_VALIDATION),
        r.get("decide_par_nom", String.class),
        r.get(REMBOURSEMENT.EXECUTE_LE),
        r.get(PAIEMENT.REFERENCE),
        r.get(PAIEMENT.OBJET).getLiteral(),
        r.get(PAIEMENT.MOYEN).getLiteral(),
        r.get(PAIEMENT.DEMANDE_ID),
        r.get(DEMANDE.NUMERO),
        r.get("beneficiaire_nom", String.class),
        r.get(REMBOURSEMENT.PAIEMENT_ID));
  }
}
