package gn.gov.mines.emine.delivrance.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.DEMANDE;
import static gn.gov.mines.emine.shared.jooq.Tables.DOCUMENT_DELIVRE;
import static gn.gov.mines.emine.shared.jooq.Tables.SIGNALEMENT_DOCUMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.UTILISATEUR;

import gn.gov.mines.emine.shared.jooq.Routines;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * Signalements d'un document délivré par la Brigade anti-fraude (US-06.8, V35), sous la RLS
 * (profils internes) ; la base contrôle qui signale et qui décide.
 */
@Repository
public class SignalementRepository {

  private final DSLContext dsl;

  public SignalementRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * Un signalement.
   *
   * @param statut {@code ouvert}, {@code annule} (document annulé) ou {@code classe}
   */
  public record Signalement(
      long id,
      long demandeId,
      String numeroDemande,
      String numeroDocument,
      String statut,
      String motif,
      String auteur,
      Instant signaleLe,
      String decidePar,
      Instant decideLe,
      String motifDecision) {}

  /** Le profil connecté est-il le signataire du document de cette demande ? (V35) */
  public boolean estSignataire(long demandeId) {
    return Boolean.TRUE.equals(
        dsl.select(Routines.fnEstSignataireDocument(Routines.fnCurrentUserId(), DSL.val(demandeId)))
            .fetchOne(0, Boolean.class));
  }

  public long creer(long documentId, String motif) {
    return dsl.insertInto(SIGNALEMENT_DOCUMENT)
        .set(SIGNALEMENT_DOCUMENT.DOCUMENT_ID, documentId)
        .set(SIGNALEMENT_DOCUMENT.MOTIF, motif)
        // Auteur posé par la base (session de l'appelant) ; valeur provisoire exigée par NOT NULL.
        .set(SIGNALEMENT_DOCUMENT.AUTEUR_ID, Routines.fnCurrentUserId())
        .returning(SIGNALEMENT_DOCUMENT.ID)
        .fetchOne(SIGNALEMENT_DOCUMENT.ID);
  }

  public int decider(long id, String statut, String motif) {
    return dsl.update(SIGNALEMENT_DOCUMENT)
        .set(SIGNALEMENT_DOCUMENT.STATUT, statut)
        .set(SIGNALEMENT_DOCUMENT.MOTIF_DECISION, motif)
        .where(SIGNALEMENT_DOCUMENT.ID.eq(id))
        .and(SIGNALEMENT_DOCUMENT.STATUT.eq("ouvert"))
        .execute();
  }

  public Optional<Signalement> lire(long id) {
    return select().where(SIGNALEMENT_DOCUMENT.ID.eq(id)).fetchOptional(this::signalement);
  }

  public List<Signalement> duDocument(long documentId) {
    return select()
        .where(SIGNALEMENT_DOCUMENT.DOCUMENT_ID.eq(documentId))
        .orderBy(SIGNALEMENT_DOCUMENT.CREATED_AT.desc())
        .fetch(this::signalement);
  }

  /** Signalements ouverts des documents dont le profil connecté est le signataire. */
  private static Condition aDeciderParMoi() {
    return SIGNALEMENT_DOCUMENT
        .STATUT
        .eq("ouvert")
        .and(
            DSL.field(Routines.fnEstSignataireDocument(Routines.fnCurrentUserId(), DEMANDE.ID))
                .isTrue());
  }

  public long compterADecider() {
    return dsl.fetchCount(
        DSL.selectOne()
            .from(SIGNALEMENT_DOCUMENT)
            .join(DOCUMENT_DELIVRE)
            .on(DOCUMENT_DELIVRE.ID.eq(SIGNALEMENT_DOCUMENT.DOCUMENT_ID))
            .join(DEMANDE)
            .on(DEMANDE.ID.eq(DOCUMENT_DELIVRE.DEMANDE_ID))
            .where(aDeciderParMoi()));
  }

  public PageResultat<Signalement> listerADecider(Pagination pagination) {
    long total = compterADecider();
    List<Signalement> elements =
        select()
            .where(aDeciderParMoi())
            .orderBy(SIGNALEMENT_DOCUMENT.CREATED_AT.asc())
            .limit(pagination.taille())
            .offset(pagination.decalage())
            .fetch(this::signalement);
    return new PageResultat<>(elements, pagination.page(), pagination.taille(), total);
  }

  private org.jooq.SelectOnConditionStep<Record> select() {
    var auteur = UTILISATEUR.as("auteur_sig");
    var decideur = UTILISATEUR.as("decideur_sig");
    return dsl.select(SIGNALEMENT_DOCUMENT.fields())
        .select(
            DEMANDE.ID,
            DEMANDE.NUMERO,
            DOCUMENT_DELIVRE.NUMERO,
            DSL.concat(auteur.PRENOM, DSL.inline(" "), auteur.NOM).as("auteur_nom"),
            DSL.concat(decideur.PRENOM, DSL.inline(" "), decideur.NOM).as("decideur_nom"))
        .from(SIGNALEMENT_DOCUMENT)
        .join(DOCUMENT_DELIVRE)
        .on(DOCUMENT_DELIVRE.ID.eq(SIGNALEMENT_DOCUMENT.DOCUMENT_ID))
        .join(DEMANDE)
        .on(DEMANDE.ID.eq(DOCUMENT_DELIVRE.DEMANDE_ID))
        .join(auteur)
        .on(auteur.ID.eq(SIGNALEMENT_DOCUMENT.AUTEUR_ID))
        .leftJoin(decideur)
        .on(decideur.ID.eq(SIGNALEMENT_DOCUMENT.DECIDE_PAR));
  }

  private Signalement signalement(Record r) {
    return new Signalement(
        r.get(SIGNALEMENT_DOCUMENT.ID),
        r.get(DEMANDE.ID),
        r.get(DEMANDE.NUMERO),
        r.get(DOCUMENT_DELIVRE.NUMERO),
        r.get(SIGNALEMENT_DOCUMENT.STATUT),
        r.get(SIGNALEMENT_DOCUMENT.MOTIF),
        r.get("auteur_nom", String.class),
        r.get(SIGNALEMENT_DOCUMENT.CREATED_AT),
        r.get("decideur_nom", String.class),
        r.get(SIGNALEMENT_DOCUMENT.DECIDE_LE),
        r.get(SIGNALEMENT_DOCUMENT.MOTIF_DECISION));
  }
}
