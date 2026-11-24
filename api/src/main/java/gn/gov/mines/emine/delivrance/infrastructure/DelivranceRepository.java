package gn.gov.mines.emine.delivrance.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.AVIS_DECISION;
import static gn.gov.mines.emine.shared.jooq.Tables.DEMANDE;
import static gn.gov.mines.emine.shared.jooq.Tables.DOCUMENT_DELIVRE;
import static gn.gov.mines.emine.shared.jooq.Tables.TYPE_DOCUMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.UTILISATEUR;

import gn.gov.mines.emine.delivrance.domaine.DocumentDelivre;
import gn.gov.mines.emine.shared.jooq.enums.NatureAvis;
import gn.gov.mines.emine.shared.jooq.enums.SensAvis;
import gn.gov.mines.emine.shared.jooq.enums.StatutDemande;
import gn.gov.mines.emine.shared.jooq.enums.StatutDocument;
import java.time.Instant;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * Registre des documents délivrés (jOOQ). Toujours appelé dans une transaction ; numéro et code de
 * vérification sont tirés par la base (V10, {@code gen_random_uuid}), jamais en Java.
 */
@Repository
public class DelivranceRepository {

  private final DSLContext dsl;

  public DelivranceRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * Numéro {@code <CODE>-AAAA-NNNN} réservé par le compteur de la base (annulé si la transaction
   * l'est).
   */
  public String prochainNumero(String codeType) {
    return dsl.select(DSL.field("fn_next_numero({0}, 4)", String.class, DSL.val(codeType)))
        .fetchOne(0, String.class);
  }

  /** Code de vérification aléatoire (128 bits) tiré par la base, comme la valeur par défaut. */
  public String nouveauCode() {
    return dsl.select(DSL.field("replace(gen_random_uuid()::text, '-', '')", String.class))
        .fetchOne(0, String.class);
  }

  /**
   * Droits payés → délivrée. Verrouille la ligne de la demande : une seconde délivrance simultanée
   * trouve 0 ligne et s'arrête. Les transitions et l'historique restent ceux des triggers.
   */
  public int passerADelivree(long demandeId) {
    return dsl.update(DEMANDE)
        .set(DEMANDE.STATUT, StatutDemande.delivree)
        .where(DEMANDE.ID.eq(demandeId))
        .and(DEMANDE.STATUT.eq(StatutDemande.droits_payes))
        .and(DEMANDE.DELETED_AT.isNull())
        .execute();
  }

  /** Inscrit le document signé au registre et renvoie son identifiant. */
  public long inscrire(
      String numero,
      short typeDocumentId,
      long demandeId,
      long signataireId,
      String signature,
      Instant horodatage,
      String empreinte,
      String code) {
    return dsl.insertInto(DOCUMENT_DELIVRE)
        .set(DOCUMENT_DELIVRE.NUMERO, numero)
        .set(DOCUMENT_DELIVRE.TYPE_DOCUMENT_ID, typeDocumentId)
        .set(DOCUMENT_DELIVRE.DEMANDE_ID, demandeId)
        .set(DOCUMENT_DELIVRE.SIGNATAIRE_ID, signataireId)
        .set(DOCUMENT_DELIVRE.SIGNATURE_HSM, signature)
        .set(DOCUMENT_DELIVRE.HORODATAGE, horodatage)
        .set(DOCUMENT_DELIVRE.EMPREINTE_SHA256, empreinte)
        .set(DOCUMENT_DELIVRE.CODE_QR, code)
        .set(DOCUMENT_DELIVRE.DELIVRE_LE, horodatage)
        .returning(DOCUMENT_DELIVRE.ID)
        .fetchOne(DOCUMENT_DELIVRE.ID);
  }

  /**
   * Révoque le document : statut « non valide » et motif (contrainte du schéma). Renvoie 0 s'il
   * était déjà révoqué. L'audit de la base trace l'auteur et l'heure.
   */
  public int revoquer(long id, String motif) {
    return dsl.update(DOCUMENT_DELIVRE)
        .set(DOCUMENT_DELIVRE.STATUT, StatutDocument.non_valide)
        .set(DOCUMENT_DELIVRE.MOTIF_INVALIDATION, motif)
        .where(DOCUMENT_DELIVRE.ID.eq(id))
        .and(DOCUMENT_DELIVRE.STATUT.eq(StatutDocument.valide))
        .execute();
  }

  /** Document délivré pour cette demande, s'il existe. */
  public Optional<DocumentDelivre> parDemande(long demandeId) {
    return selection()
        .where(DOCUMENT_DELIVRE.DEMANDE_ID.eq(demandeId))
        .fetchOptional(this::document);
  }

  /** Un document délivré et le numéro de sa demande (liste « Documents délivrés »). */
  public record LigneDocument(DocumentDelivre document, String numeroDemande) {}

  /**
   * Documents délivrés visibles du profil connecté, du plus récent au plus ancien. Le registre n'a
   * pas de RLS propre : la jointure sur {@code demande} (RLS) limite un usager à ses dossiers.
   */
  public gn.gov.mines.emine.shared.pagination.PageResultat<LigneDocument> listerVisibles(
      gn.gov.mines.emine.shared.pagination.Pagination pagination) {
    var demande = gn.gov.mines.emine.shared.jooq.Tables.DEMANDE;
    long total =
        dsl.selectCount()
            .from(DOCUMENT_DELIVRE)
            .join(demande)
            .on(demande.ID.eq(DOCUMENT_DELIVRE.DEMANDE_ID))
            .fetchOne(0, Long.class);
    var elements =
        selection(demande.NUMERO)
            .join(demande)
            .on(demande.ID.eq(DOCUMENT_DELIVRE.DEMANDE_ID))
            .orderBy(DOCUMENT_DELIVRE.DELIVRE_LE.desc(), DOCUMENT_DELIVRE.ID.desc())
            .limit(pagination.taille())
            .offset(pagination.decalage())
            .fetch(r -> new LigneDocument(document(r), r.get(demande.NUMERO)));
    return new gn.gov.mines.emine.shared.pagination.PageResultat<>(
        elements, pagination.page(), pagination.taille(), total);
  }

  /** Document du registre par son code de vérification, s'il existe. */
  public Optional<DocumentDelivre> parCode(String code) {
    return selection().where(DOCUMENT_DELIVRE.CODE_QR.eq(code)).fetchOptional(this::document);
  }

  /** Dernière décision favorable : date et observations éventuelles. */
  public Optional<DecisionFavorable> decisionFavorable(long demandeId) {
    return dsl.select(AVIS_DECISION.RENDU_LE, AVIS_DECISION.MOTIF)
        .from(AVIS_DECISION)
        .where(AVIS_DECISION.DEMANDE_ID.eq(demandeId))
        .and(AVIS_DECISION.NATURE.eq(NatureAvis.decision))
        .and(AVIS_DECISION.SENS.eq(SensAvis.favorable))
        .orderBy(AVIS_DECISION.ID.desc())
        .limit(1)
        .fetchOptional(r -> new DecisionFavorable(r.value1(), r.value2()));
  }

  /** Date et observations de la décision favorable. */
  public record DecisionFavorable(Instant le, String observations) {}

  private SelectOnConditionStep<? extends Record> selection(
      org.jooq.SelectField<?>... supplementaires) {
    return dsl.select(supplementaires)
        .select(
            DOCUMENT_DELIVRE.ID,
            DOCUMENT_DELIVRE.NUMERO,
            DOCUMENT_DELIVRE.DEMANDE_ID,
            DOCUMENT_DELIVRE.STATUT,
            DOCUMENT_DELIVRE.MOTIF_INVALIDATION,
            DOCUMENT_DELIVRE.DELIVRE_LE,
            DOCUMENT_DELIVRE.EMPREINTE_SHA256,
            DOCUMENT_DELIVRE.CODE_QR,
            DOCUMENT_DELIVRE.SIGNATURE_HSM,
            TYPE_DOCUMENT.CODE,
            UTILISATEUR.PRENOM,
            UTILISATEUR.NOM)
        .from(DOCUMENT_DELIVRE)
        .join(TYPE_DOCUMENT)
        .on(TYPE_DOCUMENT.ID.eq(DOCUMENT_DELIVRE.TYPE_DOCUMENT_ID))
        .join(UTILISATEUR)
        .on(UTILISATEUR.ID.eq(DOCUMENT_DELIVRE.SIGNATAIRE_ID));
  }

  private DocumentDelivre document(Record r) {
    return new DocumentDelivre(
        r.get(DOCUMENT_DELIVRE.ID),
        r.get(DOCUMENT_DELIVRE.NUMERO),
        r.get(TYPE_DOCUMENT.CODE),
        r.get(DOCUMENT_DELIVRE.DEMANDE_ID),
        r.get(DOCUMENT_DELIVRE.STATUT) == StatutDocument.valide,
        r.get(DOCUMENT_DELIVRE.MOTIF_INVALIDATION),
        r.get(DOCUMENT_DELIVRE.DELIVRE_LE),
        r.get(DOCUMENT_DELIVRE.EMPREINTE_SHA256),
        r.get(DOCUMENT_DELIVRE.CODE_QR),
        (r.get(UTILISATEUR.PRENOM) + " " + r.get(UTILISATEUR.NOM)).strip(),
        r.get(DOCUMENT_DELIVRE.SIGNATURE_HSM));
  }
}
