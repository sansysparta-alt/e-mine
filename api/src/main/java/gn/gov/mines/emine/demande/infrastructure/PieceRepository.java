package gn.gov.mines.emine.demande.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.PIECE_JOINTE;

import gn.gov.mines.emine.demande.domaine.Piece;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
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
        r.get(PIECE_JOINTE.CREATED_AT));
  }
}
