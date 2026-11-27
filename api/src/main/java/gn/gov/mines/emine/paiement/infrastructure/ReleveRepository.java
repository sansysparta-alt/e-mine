package gn.gov.mines.emine.paiement.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.DEMANDE;
import static gn.gov.mines.emine.shared.jooq.Tables.LIGNE_RELEVE;
import static gn.gov.mines.emine.shared.jooq.Tables.PAIEMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.RELEVE_BANCAIRE;
import static gn.gov.mines.emine.shared.jooq.Tables.UTILISATEUR;

import gn.gov.mines.emine.paiement.domaine.LectureReleveCsv.LigneLue;
import gn.gov.mines.emine.shared.jooq.Routines;
import gn.gov.mines.emine.shared.jooq.enums.MoyenPaiement;
import gn.gov.mines.emine.shared.jooq.enums.StatutLigneReleve;
import gn.gov.mines.emine.shared.jooq.enums.StatutPaiement;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * Relevés bancaires et leurs lignes (US-04.8). Écriture : import puis rapprochement par la fonction
 * de la base {@code fn_rapprocher_releve} (V30), qui seule confirme un paiement ; tout est tracé au
 * journal d'audit par les triggers.
 */
@Repository
public class ReleveRepository {

  private final DSLContext dsl;

  public ReleveRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** Un relevé avec les nombres de lignes par résultat. */
  public record Releve(
      long id,
      String banque,
      LocalDate dateReleve,
      String nomFichier,
      Instant importeLe,
      String importePar,
      int lignes,
      int rapprochees,
      int ecarts,
      int inconnues,
      int traitees,
      long totalCreditsGnf) {}

  /** Une ligne du relevé, avec le paiement et la demande rapprochés s'il y en a. */
  public record Ligne(
      long id,
      int numero,
      LocalDate dateOperation,
      String libelle,
      long montantGnf,
      char sens,
      String statut,
      String nature,
      String referenceLue,
      Long ecartGnf,
      String referencePaiement,
      Long demandeId,
      String numeroDemande,
      String decision,
      String motif) {}

  /**
   * Résultats d'une ligne proposés en filtre : rapprochées, à vérifier (écart ou inconnue pas
   * encore traitée), traitées par le Responsable fiscal (US-04.9).
   */
  public enum FiltreLignes {
    RAPPROCHEES,
    A_VERIFIER,
    TRAITEES
  }

  public boolean empreinteConnue(String empreinte) {
    return dsl.fetchExists(RELEVE_BANCAIRE, RELEVE_BANCAIRE.EMPREINTE_SHA256.eq(empreinte));
  }

  /** Enregistre le relevé et ses lignes (statut « non traitée ») ; renvoie l'identifiant. */
  public long enregistrer(
      String banque,
      LocalDate dateReleve,
      String nomFichier,
      String empreinte,
      long importePar,
      List<LigneLue> lignes) {
    long id =
        dsl.insertInto(RELEVE_BANCAIRE)
            .set(RELEVE_BANCAIRE.BANQUE, banque)
            .set(RELEVE_BANCAIRE.DATE_RELEVE, dateReleve)
            .set(RELEVE_BANCAIRE.FORMAT_FICHIER, "CSV")
            .set(RELEVE_BANCAIRE.NOM_FICHIER, nomFichier)
            .set(RELEVE_BANCAIRE.EMPREINTE_SHA256, empreinte)
            .set(RELEVE_BANCAIRE.IMPORTE_PAR, importePar)
            .returning(RELEVE_BANCAIRE.ID)
            .fetchOne(RELEVE_BANCAIRE.ID);
    var insertion =
        dsl.insertInto(
            LIGNE_RELEVE,
            LIGNE_RELEVE.RELEVE_ID,
            LIGNE_RELEVE.NUMERO_LIGNE,
            LIGNE_RELEVE.DATE_OPERATION,
            LIGNE_RELEVE.LIBELLE,
            LIGNE_RELEVE.MONTANT_GNF,
            LIGNE_RELEVE.SENS);
    for (LigneLue l : lignes) {
      insertion =
          insertion.values(
              id, l.numero(), l.date(), l.libelle(), l.montantGnf(), String.valueOf(l.sens()));
    }
    insertion.execute();
    return id;
  }

  /** Rapprochement automatique par la base (7 cas du contrat). */
  public void rapprocher(long releveId) {
    Routines.fnRapprocherReleve(dsl.configuration(), releveId);
  }

  public PageResultat<Releve> lister(Pagination pagination) {
    long total = dsl.fetchCount(RELEVE_BANCAIRE);
    List<Releve> elements =
        selectReleves(DSL.trueCondition())
            .orderBy(RELEVE_BANCAIRE.CREATED_AT.desc(), RELEVE_BANCAIRE.ID.desc())
            .limit(pagination.taille())
            .offset(pagination.decalage())
            .fetch(ReleveRepository::releve);
    return new PageResultat<>(elements, pagination.page(), pagination.taille(), total);
  }

  public Optional<Releve> lire(long id) {
    return selectReleves(RELEVE_BANCAIRE.ID.eq(id)).fetchOptional(ReleveRepository::releve);
  }

  /** Lignes d'un relevé, dans l'ordre du fichier. */
  public PageResultat<Ligne> lignes(long releveId, FiltreLignes filtre, Pagination pagination) {
    Condition c = LIGNE_RELEVE.RELEVE_ID.eq(releveId);
    if (filtre == FiltreLignes.RAPPROCHEES) {
      c = c.and(LIGNE_RELEVE.STATUT.eq(StatutLigneReleve.rapprochee));
    } else if (filtre == FiltreLignes.A_VERIFIER) {
      c =
          c.and(LIGNE_RELEVE.STATUT.in(StatutLigneReleve.ecart, StatutLigneReleve.inconnue))
              .and(LIGNE_RELEVE.DECISION.isNull());
    } else if (filtre == FiltreLignes.TRAITEES) {
      c = c.and(LIGNE_RELEVE.DECISION.isNotNull());
    }
    long total = dsl.fetchCount(LIGNE_RELEVE, c);
    List<Ligne> elements =
        dsl.select(LIGNE_RELEVE.fields())
            .select(PAIEMENT.REFERENCE, DEMANDE.ID, DEMANDE.NUMERO, RANG)
            .from(LIGNE_RELEVE)
            .leftJoin(PAIEMENT)
            .on(PAIEMENT.ID.eq(LIGNE_RELEVE.PAIEMENT_ID))
            .leftJoin(DEMANDE)
            .on(DEMANDE.ID.eq(PAIEMENT.DEMANDE_ID))
            .where(c)
            .orderBy(LIGNE_RELEVE.NUMERO_LIGNE, LIGNE_RELEVE.ID)
            .limit(pagination.taille())
            .offset(pagination.decalage())
            .fetch(ReleveRepository::ligne);
    return new PageResultat<>(elements, pagination.page(), pagination.taille(), total);
  }

  /**
   * Virements déclarés, en attente du relevé : base du relevé d'exemple de démonstration (une ligne
   * conforme par virement).
   */
  public List<VirementAttendu> virementsAttendus() {
    return dsl.select(PAIEMENT.REFERENCE, PAIEMENT.MONTANT_GNF, UTILISATEUR.NOM)
        .from(PAIEMENT)
        .leftJoin(UTILISATEUR)
        .on(UTILISATEUR.ID.eq(PAIEMENT.PAYEUR_ID))
        .where(PAIEMENT.MOYEN.eq(MoyenPaiement.VIREMENT))
        .and(PAIEMENT.STATUT.eq(StatutPaiement.en_attente))
        .orderBy(PAIEMENT.VIREMENT_DECLARE_LE, PAIEMENT.REFERENCE)
        .limit(LIGNES_EXEMPLE_MAX)
        .fetch(r -> new VirementAttendu(r.value1(), r.value2(), r.value3()));
  }

  /** Virement déclaré attendu sur le relevé. */
  public record VirementAttendu(String reference, long montantGnf, String nomPayeur) {}

  static final int LIGNES_EXEMPLE_MAX = 500;

  /** Relevés filtrés, avec leurs nombres de lignes par résultat et le nom de qui les a importés. */
  private org.jooq.SelectHavingStep<Record> selectReleves(Condition filtre) {
    Field<Integer> lignes = DSL.count(LIGNE_RELEVE.ID).as("lignes");
    Field<Integer> rapprochees =
        DSL.count(LIGNE_RELEVE.ID)
            .filterWhere(LIGNE_RELEVE.STATUT.eq(StatutLigneReleve.rapprochee))
            .as("rapprochees");
    Field<Integer> ecarts =
        DSL.count(LIGNE_RELEVE.ID)
            .filterWhere(
                LIGNE_RELEVE.STATUT.eq(StatutLigneReleve.ecart).and(LIGNE_RELEVE.DECISION.isNull()))
            .as("ecarts");
    Field<Integer> inconnues =
        DSL.count(LIGNE_RELEVE.ID)
            .filterWhere(
                LIGNE_RELEVE
                    .STATUT
                    .eq(StatutLigneReleve.inconnue)
                    .and(LIGNE_RELEVE.DECISION.isNull()))
            .as("inconnues");
    Field<Integer> traitees =
        DSL.count(LIGNE_RELEVE.ID).filterWhere(LIGNE_RELEVE.DECISION.isNotNull()).as("traitees");
    Field<java.math.BigDecimal> credits =
        DSL.sum(LIGNE_RELEVE.MONTANT_GNF).filterWhere(LIGNE_RELEVE.SENS.eq("C")).as("credits");
    Field<String> importePar =
        DSL.concat(UTILISATEUR.PRENOM, DSL.inline(" "), UTILISATEUR.NOM).as("importe_par_nom");
    return dsl.select(RELEVE_BANCAIRE.fields())
        .select(lignes, rapprochees, ecarts, inconnues, traitees, credits, importePar)
        .from(RELEVE_BANCAIRE)
        .leftJoin(LIGNE_RELEVE)
        .on(LIGNE_RELEVE.RELEVE_ID.eq(RELEVE_BANCAIRE.ID))
        .leftJoin(UTILISATEUR)
        .on(UTILISATEUR.ID.eq(RELEVE_BANCAIRE.IMPORTE_PAR))
        .where(filtre)
        .groupBy(RELEVE_BANCAIRE.ID, UTILISATEUR.PRENOM, UTILISATEUR.NOM);
  }

  private static Releve releve(Record r) {
    java.math.BigDecimal credits = r.get("credits", java.math.BigDecimal.class);
    return new Releve(
        r.get(RELEVE_BANCAIRE.ID),
        r.get(RELEVE_BANCAIRE.BANQUE),
        r.get(RELEVE_BANCAIRE.DATE_RELEVE),
        r.get(RELEVE_BANCAIRE.NOM_FICHIER),
        r.get(RELEVE_BANCAIRE.CREATED_AT),
        r.get("importe_par_nom", String.class),
        r.get("lignes", Integer.class),
        r.get("rapprochees", Integer.class),
        r.get("ecarts", Integer.class),
        r.get("inconnues", Integer.class),
        r.get("traitees", Integer.class),
        credits == null ? 0 : credits.longValueExact());
  }

  /** Ligne de relevé, alias pour le rang. */
  private static final gn.gov.mines.emine.shared.jooq.tables.LigneReleve AUTRE =
      LIGNE_RELEVE.as("autre");

  /**
   * Rang de la ligne dans son relevé, de 1 à n dans l'ordre du fichier (R5) : le même quel que soit
   * le filtre, sans les lignes vides ni l'en-tête du fichier.
   */
  private static final Field<Integer> RANG =
      DSL.field(
              DSL.selectCount()
                  .from(AUTRE)
                  .where(AUTRE.RELEVE_ID.eq(LIGNE_RELEVE.RELEVE_ID))
                  .and(
                      AUTRE
                          .NUMERO_LIGNE
                          .lt(LIGNE_RELEVE.NUMERO_LIGNE)
                          .or(
                              AUTRE
                                  .NUMERO_LIGNE
                                  .eq(LIGNE_RELEVE.NUMERO_LIGNE)
                                  .and(AUTRE.ID.le(LIGNE_RELEVE.ID)))))
          .as("rang");

  private static Ligne ligne(Record r) {
    String sens = r.get(LIGNE_RELEVE.SENS);
    return new Ligne(
        r.get(LIGNE_RELEVE.ID),
        r.indexOf(RANG.getName()) >= 0
            ? r.get(RANG)
            : r.get(LIGNE_RELEVE.NUMERO_LIGNE) == null ? 0 : r.get(LIGNE_RELEVE.NUMERO_LIGNE),
        r.get(LIGNE_RELEVE.DATE_OPERATION),
        r.get(LIGNE_RELEVE.LIBELLE),
        r.get(LIGNE_RELEVE.MONTANT_GNF),
        sens == null || sens.isEmpty() ? 'C' : sens.charAt(0),
        r.get(LIGNE_RELEVE.STATUT).getLiteral(),
        r.get(LIGNE_RELEVE.NATURE),
        r.get(LIGNE_RELEVE.REFERENCE_LUE),
        r.get(LIGNE_RELEVE.ECART_GNF),
        r.get(PAIEMENT.REFERENCE),
        r.get(DEMANDE.ID),
        r.get(DEMANDE.NUMERO),
        r.get(LIGNE_RELEVE.DECISION),
        r.get(LIGNE_RELEVE.MOTIF));
  }
}
