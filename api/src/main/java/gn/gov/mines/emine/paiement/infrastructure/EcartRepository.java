package gn.gov.mines.emine.paiement.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.DEMANDE;
import static gn.gov.mines.emine.shared.jooq.Tables.LIGNE_RELEVE;
import static gn.gov.mines.emine.shared.jooq.Tables.PAIEMENT;

import gn.gov.mines.emine.shared.jooq.enums.MoyenPaiement;
import gn.gov.mines.emine.shared.jooq.enums.StatutLigneReleve;
import gn.gov.mines.emine.shared.jooq.enums.StatutPaiement;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
 * File « À vérifier » du Responsable fiscal (US-04.9) : lignes de relevé en écart ou inconnues pas
 * encore traitées, et paiements en ligne « à vérifier » sans ligne de relevé (montant divergent
 * notifié par TrésorPay). Les plus anciennes d'abord.
 */
@Repository
public class EcartRepository {

  private final DSLContext dsl;

  public EcartRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** Élément de la file : une ligne de relevé ou un paiement en ligne. */
  public record Element(
      String type,
      Long ligneId,
      Long releveId,
      LocalDate dateOperation,
      String libelle,
      long montantGnf,
      String sens,
      String nature,
      Long ecartGnf,
      String referencePaiement,
      Long montantAttenduGnf,
      Long montantRecuGnf,
      String statutPaiement,
      String moyen,
      Long demandeId,
      String numeroDemande,
      Instant signaleLe,
      boolean complement) {

    public boolean paiementAVerifier() {
      return "a_verifier".equals(statutPaiement);
    }

    /** Complément attendu dont l'échéance est dépassée (US-04.12). */
    public boolean complementEchu() {
      return "complement_attendu".equals(statutPaiement);
    }
  }

  /** Ligne de relevé verrouillée pour une décision, avec le paiement rapproché. */
  public record LigneADecider(
      long id,
      LocalDate dateOperation,
      long montantGnf,
      String statut,
      String nature,
      String decision,
      UUID paiementId,
      String statutPaiement,
      boolean complement) {}

  /** Ligne en écart ou inconnue, pas encore traitée. */
  static final Condition LIGNE_A_VERIFIER =
      LIGNE_RELEVE
          .STATUT
          .in(StatutLigneReleve.ecart, StatutLigneReleve.inconnue)
          .and(LIGNE_RELEVE.DECISION.isNull());

  /** Paiement complémentaire, alias pour les sous-requêtes. */
  private static final gn.gov.mines.emine.shared.jooq.tables.Paiement CPL = PAIEMENT.as("cpl");

  /** Complément attendu, échéance dépassée, sans paiement complémentaire en cours (US-04.12). */
  static final Condition COMPLEMENT_ECHU =
      PAIEMENT
          .STATUT
          .eq(StatutPaiement.complement_attendu)
          .and(PAIEMENT.COMPLEMENT_ECHEANCE.lt(DSL.currentInstant()))
          .andNotExists(
              DSL.selectOne()
                  .from(CPL)
                  .where(CPL.COMPLEMENT_DE.eq(PAIEMENT.ID))
                  .and(
                      CPL.STATUT.in(
                          StatutPaiement.initie,
                          StatutPaiement.en_attente,
                          StatutPaiement.a_verifier)));

  /**
   * Paiement « à vérifier » qu'aucune ligne non traitée ne porte (paiement en ligne), ou complément
   * attendu dont l'échéance est dépassée.
   */
  static final Condition PAIEMENT_SEUL_A_VERIFIER =
      PAIEMENT
          .STATUT
          .eq(StatutPaiement.a_verifier)
          .andNotExists(
              DSL.selectOne()
                  .from(LIGNE_RELEVE)
                  .where(LIGNE_RELEVE.PAIEMENT_ID.eq(PAIEMENT.ID))
                  .and(LIGNE_A_VERIFIER))
          .or(COMPLEMENT_ECHU);

  /**
   * Le paiement se décide seul : en ligne « à vérifier » sans ligne de relevé, ou complément
   * attendu dont l'échéance est dépassée (US-04.12).
   */
  public boolean seulADecider(UUID paiementId) {
    return dsl.fetchExists(PAIEMENT, PAIEMENT.ID.eq(paiementId).and(PAIEMENT_SEUL_A_VERIFIER));
  }

  /** Le paiement porte une ligne de relevé non traitée (sa décision passe par elle). */
  public boolean aUneLigneATraiter(UUID paiementId) {
    return dsl.fetchExists(
        LIGNE_RELEVE, LIGNE_RELEVE.PAIEMENT_ID.eq(paiementId).and(LIGNE_A_VERIFIER));
  }

  public long compter() {
    return dsl.fetchCount(LIGNE_RELEVE, LIGNE_A_VERIFIER)
        + dsl.fetchCount(PAIEMENT, PAIEMENT_SEUL_A_VERIFIER);
  }

  public PageResultat<Element> lister(Pagination pagination) {
    Table<? extends Record> file = file().asTable("file");
    long total = compter();
    List<Element> elements =
        dsl.selectFrom(file)
            .orderBy(
                DSL.field(DSL.name("file", "signale_le")).asc(),
                DSL.field(DSL.name("file", "cle")).asc())
            .limit(pagination.taille())
            .offset(pagination.decalage())
            .fetch(EcartRepository::element);
    return new PageResultat<>(elements, pagination.page(), pagination.taille(), total);
  }

  private Select<? extends Record> file() {
    Field<String> statutPaiement = PAIEMENT.STATUT.cast(SQLDataType.VARCHAR).as("statut_paiement");
    Field<String> moyen = PAIEMENT.MOYEN.cast(SQLDataType.VARCHAR).as("moyen");
    var lignes =
        dsl.select(
                DSL.inline("ligne").as("type"),
                LIGNE_RELEVE.ID.cast(SQLDataType.VARCHAR).as("cle"),
                LIGNE_RELEVE.ID.as("ligne_id"),
                LIGNE_RELEVE.RELEVE_ID.as("releve_id"),
                LIGNE_RELEVE.DATE_OPERATION.as("date_operation"),
                LIGNE_RELEVE.LIBELLE.as("libelle"),
                LIGNE_RELEVE.MONTANT_GNF.as("montant_gnf"),
                LIGNE_RELEVE.SENS.cast(SQLDataType.VARCHAR).as("sens"),
                LIGNE_RELEVE.NATURE.as("nature"),
                LIGNE_RELEVE.ECART_GNF.as("ecart_gnf"),
                PAIEMENT.REFERENCE.as("reference"),
                PAIEMENT.MONTANT_GNF.as("montant_attendu"),
                PAIEMENT.MONTANT_RECU_GNF.as("montant_recu"),
                statutPaiement,
                moyen,
                DEMANDE.ID.as("demande_id"),
                DEMANDE.NUMERO.as("numero_demande"),
                LIGNE_RELEVE.CREATED_AT.as("signale_le"),
                DSL.coalesce(DSL.field(PAIEMENT.COMPLEMENT_DE.isNotNull()), DSL.inline(false))
                    .as("complement"))
            .from(LIGNE_RELEVE)
            .leftJoin(PAIEMENT)
            .on(PAIEMENT.ID.eq(LIGNE_RELEVE.PAIEMENT_ID))
            .leftJoin(DEMANDE)
            .on(DEMANDE.ID.eq(PAIEMENT.DEMANDE_ID))
            .where(LIGNE_A_VERIFIER);
    var paiements =
        dsl.select(
                DSL.inline("paiement").as("type"),
                PAIEMENT.REFERENCE.as("cle"),
                DSL.castNull(SQLDataType.BIGINT).as("ligne_id"),
                DSL.castNull(SQLDataType.BIGINT).as("releve_id"),
                PAIEMENT.UPDATED_AT.cast(SQLDataType.LOCALDATE).as("date_operation"),
                DSL.inline("").as("libelle"),
                DSL.coalesce(PAIEMENT.MONTANT_RECU_GNF, PAIEMENT.MONTANT_GNF).as("montant_gnf"),
                DSL.inline("C").as("sens"),
                DSL.when(
                        PAIEMENT.STATUT.eq(StatutPaiement.complement_attendu),
                        DSL.inline("complement_echu"))
                    .otherwise(DSL.inline("montant_divergent"))
                    .as("nature"),
                DSL.coalesce(PAIEMENT.MONTANT_RECU_GNF, PAIEMENT.MONTANT_GNF)
                    .minus(PAIEMENT.MONTANT_GNF)
                    .as("ecart_gnf"),
                PAIEMENT.REFERENCE.as("reference"),
                PAIEMENT.MONTANT_GNF.as("montant_attendu"),
                PAIEMENT.MONTANT_RECU_GNF.as("montant_recu"),
                statutPaiement,
                moyen,
                DEMANDE.ID.as("demande_id"),
                DEMANDE.NUMERO.as("numero_demande"),
                PAIEMENT.UPDATED_AT.as("signale_le"),
                DSL.field(PAIEMENT.COMPLEMENT_DE.isNotNull()).as("complement"))
            .from(PAIEMENT)
            .join(DEMANDE)
            .on(DEMANDE.ID.eq(PAIEMENT.DEMANDE_ID))
            .where(PAIEMENT_SEUL_A_VERIFIER);
    return lignes.unionAll(paiements);
  }

  private static Element element(Record r) {
    return new Element(
        r.get("type", String.class),
        r.get("ligne_id", Long.class),
        r.get("releve_id", Long.class),
        r.get("date_operation", LocalDate.class),
        r.get("libelle", String.class),
        r.get("montant_gnf", Long.class),
        r.get("sens", String.class),
        r.get("nature", String.class),
        r.get("ecart_gnf", Long.class),
        r.get("reference", String.class),
        r.get("montant_attendu", Long.class),
        r.get("montant_recu", Long.class),
        r.get("statut_paiement", String.class),
        r.get("moyen", String.class),
        r.get("demande_id", Long.class),
        r.get("numero_demande", String.class),
        r.get("signale_le", Instant.class),
        Boolean.TRUE.equals(r.get("complement", Boolean.class)));
  }

  /**
   * Virements déclarés par les demandeurs qui attendent un relevé bancaire (R3) : rien à décider,
   * mais le Responsable fiscal sait qu'ils arriveront avec le prochain relevé.
   */
  public long virementsAttendus() {
    return dsl.fetchCount(
        PAIEMENT,
        PAIEMENT
            .MOYEN
            .eq(MoyenPaiement.VIREMENT)
            .and(PAIEMENT.STATUT.eq(StatutPaiement.en_attente)));
  }

  /** Virement en cours auquel une ligne peut être associée (R4). */
  public record PaiementAssociable(
      String reference, long montantGnf, String numeroDemande, Instant declareLe) {}

  /**
   * Virements en cours (initiés ou déclarés) du même montant que la ligne : ceux auxquels elle peut
   * être associée (R4), les déclarés d'abord, puis les plus anciens.
   */
  public List<PaiementAssociable> associables(long montantGnf) {
    return dsl.select(
            PAIEMENT.REFERENCE, PAIEMENT.MONTANT_GNF, DEMANDE.NUMERO, PAIEMENT.VIREMENT_DECLARE_LE)
        .from(PAIEMENT)
        .join(DEMANDE)
        .on(DEMANDE.ID.eq(PAIEMENT.DEMANDE_ID))
        .where(PAIEMENT.MOYEN.eq(MoyenPaiement.VIREMENT))
        .and(PAIEMENT.STATUT.in(StatutPaiement.initie, StatutPaiement.en_attente))
        .and(PAIEMENT.MONTANT_GNF.eq(montantGnf))
        .orderBy(PAIEMENT.VIREMENT_DECLARE_LE.asc().nullsLast(), PAIEMENT.CREATED_AT.asc())
        .limit(50)
        .fetch(r -> new PaiementAssociable(r.value1(), r.value2(), r.value3(), r.value4()));
  }

  /** Statut d'un paiement par sa référence (message clair d'une association refusée, R4). */
  public Optional<StatutPaiement> statut(String reference) {
    return dsl.select(PAIEMENT.STATUT)
        .from(PAIEMENT)
        .where(PAIEMENT.REFERENCE.eq(reference))
        .fetchOptional(PAIEMENT.STATUT);
  }

  /** Ligne de relevé verrouillée (décision en cours), avec le statut du paiement rapproché. */
  public Optional<LigneADecider> verrouillerLigne(long id) {
    return ligne(id, true);
  }

  /** Ligne de relevé lue sans verrou, avec le statut du paiement rapproché. */
  public Optional<LigneADecider> lireLigne(long id) {
    return ligne(id, false);
  }

  private Optional<LigneADecider> ligne(long id, boolean verrou) {
    var requete =
        dsl.select(
                LIGNE_RELEVE.ID,
                LIGNE_RELEVE.DATE_OPERATION,
                LIGNE_RELEVE.MONTANT_GNF,
                LIGNE_RELEVE.STATUT,
                LIGNE_RELEVE.NATURE,
                LIGNE_RELEVE.DECISION,
                LIGNE_RELEVE.PAIEMENT_ID,
                PAIEMENT.STATUT,
                PAIEMENT.COMPLEMENT_DE)
            .from(LIGNE_RELEVE)
            .leftJoin(PAIEMENT)
            .on(PAIEMENT.ID.eq(LIGNE_RELEVE.PAIEMENT_ID))
            .where(LIGNE_RELEVE.ID.eq(id));
    return (verrou ? requete.forUpdate().of(LIGNE_RELEVE) : requete)
        .fetchOptional(
            r ->
                new LigneADecider(
                    r.value1(),
                    r.value2(),
                    r.value3(),
                    r.value4().getLiteral(),
                    r.value5(),
                    r.value6(),
                    r.value7(),
                    r.value8() == null ? null : r.value8().getLiteral(),
                    r.value9() != null));
  }

  /** Enregistre la décision sur une ligne (et le paiement associé, le cas échéant). */
  public void deciderLigne(
      long id, String decision, String motif, long auteur, UUID paiementAssocie) {
    var maj =
        dsl.update(LIGNE_RELEVE)
            .set(LIGNE_RELEVE.DECISION, decision)
            .set(LIGNE_RELEVE.MOTIF, motif)
            .set(LIGNE_RELEVE.TRAITEE_PAR, auteur)
            .set(LIGNE_RELEVE.TRAITEE_LE, DSL.currentInstant());
    if (paiementAssocie != null) {
      maj = maj.set(LIGNE_RELEVE.PAIEMENT_ID, paiementAssocie);
    }
    maj.where(LIGNE_RELEVE.ID.eq(id)).and(LIGNE_RELEVE.DECISION.isNull()).execute();
  }

  /**
   * Paiement auquel associer une ligne : virement en cours (initié ou déclaré) de ce montant exact,
   * verrouillé ; vide sinon.
   */
  public Optional<UUID> virementAssociable(String reference, long montantGnf) {
    return dsl.select(PAIEMENT.ID)
        .from(PAIEMENT)
        .where(PAIEMENT.REFERENCE.eq(reference))
        .and(PAIEMENT.MOYEN.eq(MoyenPaiement.VIREMENT))
        .and(PAIEMENT.STATUT.in(StatutPaiement.initie, StatutPaiement.en_attente))
        .and(PAIEMENT.MONTANT_GNF.eq(montantGnf))
        .forUpdate()
        .fetchOptional(PAIEMENT.ID);
  }

  /**
   * Confirme un paiement : passage à {@code paye} (transitions contrôlées par la base ; reçu
   * attribué par le trigger), montant reçu et date de l'opération bancaire.
   */
  public void confirmer(UUID paiementId, long montantRecuGnf, LocalDate dateOperation) {
    dsl.update(PAIEMENT)
        .set(PAIEMENT.STATUT, StatutPaiement.en_attente)
        .where(PAIEMENT.ID.eq(paiementId))
        .and(PAIEMENT.STATUT.eq(StatutPaiement.initie))
        .execute();
    var maj =
        dsl.update(PAIEMENT)
            .set(PAIEMENT.STATUT, StatutPaiement.paye)
            .set(PAIEMENT.MONTANT_RECU_GNF, montantRecuGnf);
    if (dateOperation != null) {
      maj =
          maj.set(
              PAIEMENT.DATE_PAIEMENT,
              DSL.field("{0}::timestamptz", PAIEMENT.DATE_PAIEMENT.getDataType(), dateOperation));
    }
    maj.where(PAIEMENT.ID.eq(paiementId)).execute();
  }

  /**
   * Rejette un paiement « à vérifier », ou en attente d'un complément (US-04.12) : motif communiqué
   * au demandeur (notification).
   */
  public int rejeter(UUID paiementId, String motif) {
    return dsl.update(PAIEMENT)
        .set(PAIEMENT.STATUT, StatutPaiement.rejete)
        .set(PAIEMENT.MOTIF_REJET, motif)
        .where(PAIEMENT.ID.eq(paiementId))
        .and(PAIEMENT.STATUT.in(StatutPaiement.a_verifier, StatutPaiement.complement_attendu))
        .execute();
  }

  /**
   * Demande le complément d'un paiement « à vérifier » au montant inférieur (US-04.12) : il n'est
   * pas rejeté ; la somme reçue est enregistrée ; motif et échéance sont communiqués au demandeur.
   */
  public int demanderComplement(
      UUID paiementId, long montantRecuGnf, String motif, Instant echeance) {
    return dsl.update(PAIEMENT)
        .set(PAIEMENT.STATUT, StatutPaiement.complement_attendu)
        .set(PAIEMENT.MONTANT_RECU_GNF, montantRecuGnf)
        .set(PAIEMENT.MOTIF_COMPLEMENT, motif)
        .set(PAIEMENT.COMPLEMENT_ECHEANCE, echeance)
        .where(PAIEMENT.ID.eq(paiementId))
        .and(PAIEMENT.STATUT.eq(StatutPaiement.a_verifier))
        .execute();
  }
}
