package gn.gov.mines.emine.demande.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.COMPLEMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.PIECE_JOINTE;

import gn.gov.mines.emine.demande.domaine.Piece;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** Accès jOOQ aux pièces jointes, protégé par la RLS (V6). Pas de suppression physique. */
@Repository
public class PieceRepository {

  private final DSLContext dsl;

  public PieceRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public Piece ajouter(
      long demandeId,
      String typePiece,
      String nomFichier,
      String typeMime,
      long taille,
      String empreinte,
      String cle,
      long deposePar) {
    return dsl.insertInto(PIECE_JOINTE)
        .set(PIECE_JOINTE.DEMANDE_ID, demandeId)
        .set(PIECE_JOINTE.TYPE_PIECE, typePiece)
        .set(PIECE_JOINTE.NOM_FICHIER, nomFichier)
        .set(PIECE_JOINTE.MIME_TYPE, typeMime)
        .set(PIECE_JOINTE.TAILLE_OCTETS, taille)
        .set(PIECE_JOINTE.EMPREINTE_SHA256, empreinte)
        .set(PIECE_JOINTE.STOCKAGE_CLE, cle)
        .set(PIECE_JOINTE.DEPOSE_PAR, deposePar)
        .returning()
        .fetchOne(PieceRepository::piece);
  }

  /** Pièces non retirées de la demande, dans l'ordre de dépôt. */
  public List<Piece> lister(long demandeId) {
    return dsl.selectFrom(PIECE_JOINTE)
        .where(PIECE_JOINTE.DEMANDE_ID.eq(demandeId))
        .and(PIECE_JOINTE.DELETED_AT.isNull())
        .orderBy(PIECE_JOINTE.ID)
        .fetch(PieceRepository::piece);
  }

  public Optional<Piece> lire(long demandeId, long pieceId) {
    return dsl.selectFrom(PIECE_JOINTE)
        .where(PIECE_JOINTE.ID.eq(pieceId))
        .and(PIECE_JOINTE.DEMANDE_ID.eq(demandeId))
        .and(PIECE_JOINTE.DELETED_AT.isNull())
        .fetchOptional(PieceRepository::piece);
  }

  /** Retrait logique ({@code deleted_at}) ; l'objet stocké est conservé. */
  public int retirer(long demandeId, long pieceId) {
    return dsl.update(PIECE_JOINTE)
        .set(PIECE_JOINTE.DELETED_AT, Instant.now())
        .where(PIECE_JOINTE.ID.eq(pieceId))
        .and(PIECE_JOINTE.DEMANDE_ID.eq(demandeId))
        .and(PIECE_JOINTE.DELETED_AT.isNull())
        .execute();
  }

  /**
   * Retrait d'une pièce jointe <b>pendant le complément ouvert</b> (US-05.2) : seules les pièces
   * ajoutées depuis la demande de complément peuvent être retirées ; le dossier déposé reste
   * intact.
   */
  public int retirerPendantComplement(long demandeId, long pieceId) {
    return dsl.update(PIECE_JOINTE)
        .set(PIECE_JOINTE.DELETED_AT, Instant.now())
        .where(PIECE_JOINTE.ID.eq(pieceId))
        .and(PIECE_JOINTE.DEMANDE_ID.eq(demandeId))
        .and(PIECE_JOINTE.DELETED_AT.isNull())
        .and(
            PIECE_JOINTE.CREATED_AT.ge(
                dsl.select(COMPLEMENT.DEMANDE_LE)
                    .from(COMPLEMENT)
                    .where(COMPLEMENT.DEMANDE_ID.eq(demandeId))
                    .and(COMPLEMENT.REPONDU_LE.isNull())
                    .and(COMPLEMENT.CLOS_LE.isNull())))
        .execute();
  }

  /**
   * Réponse à un complément (V14) : pour chaque type demandé, la pièce jointe depuis la demande de
   * complément remplace les versions antérieures de ce type, conservées comme « remplacées ».
   * Renvoie le nombre de pièces remplacées.
   */
  public int remplacerVersionsAnterieures(long demandeId, Instant depuis, List<String> types) {
    if (types.isEmpty()) {
      return 0;
    }
    var nouvelle = PIECE_JOINTE.as("nouvelle");
    Field<Long> remplacante =
        DSL.field(
            DSL.select(DSL.max(nouvelle.ID))
                .from(nouvelle)
                .where(nouvelle.DEMANDE_ID.eq(PIECE_JOINTE.DEMANDE_ID))
                .and(nouvelle.TYPE_PIECE.eq(PIECE_JOINTE.TYPE_PIECE))
                .and(nouvelle.CREATED_AT.ge(depuis))
                .and(nouvelle.DELETED_AT.isNull()));
    return dsl.update(PIECE_JOINTE)
        .set(PIECE_JOINTE.REMPLACEE_LE, DSL.currentInstant())
        .set(PIECE_JOINTE.REMPLACEE_PAR, remplacante)
        .where(PIECE_JOINTE.DEMANDE_ID.eq(demandeId))
        .and(PIECE_JOINTE.TYPE_PIECE.in(types))
        .and(PIECE_JOINTE.CREATED_AT.lt(depuis))
        .and(PIECE_JOINTE.REMPLACEE_LE.isNull())
        .and(PIECE_JOINTE.DELETED_AT.isNull())
        .and(remplacante.isNotNull())
        .execute();
  }

  static Piece piece(Record r) {
    return new Piece(
        r.get(PIECE_JOINTE.ID),
        r.get(PIECE_JOINTE.DEMANDE_ID),
        r.get(PIECE_JOINTE.TYPE_PIECE),
        r.get(PIECE_JOINTE.NOM_FICHIER),
        r.get(PIECE_JOINTE.MIME_TYPE),
        r.get(PIECE_JOINTE.TAILLE_OCTETS),
        r.get(PIECE_JOINTE.EMPREINTE_SHA256),
        r.get(PIECE_JOINTE.STOCKAGE_CLE),
        r.get(PIECE_JOINTE.CREATED_AT),
        r.get(PIECE_JOINTE.REMPLACEE_LE),
        r.get(PIECE_JOINTE.REMPLACEE_PAR));
  }
}
