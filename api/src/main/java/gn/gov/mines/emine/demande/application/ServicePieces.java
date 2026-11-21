package gn.gov.mines.emine.demande.application;

import gn.gov.mines.emine.auth.UtilisateurCourant;
import gn.gov.mines.emine.demande.domaine.DemandeDetail;
import gn.gov.mines.emine.demande.domaine.Piece;
import gn.gov.mines.emine.demande.domaine.ReglesPieces;
import gn.gov.mines.emine.demande.infrastructure.DemandeRepository;
import gn.gov.mines.emine.demande.infrastructure.PieceRepository;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.stockage.ProprietesStockage;
import gn.gov.mines.emine.shared.stockage.StockageObjets;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pièces jointes d'une demande (US-03.2) : ajout tant que la demande est un brouillon, contrôle du
 * format (signature du fichier) et de la taille, empreinte SHA-256, stockage MinIO. Pas d'analyse
 * antivirus en V1 ({@code antivirus_ok} reste vide, ClamAV au plan complet).
 */
@Service
public class ServicePieces {

  private final DemandeRepository demandes;
  private final PieceRepository pieces;
  private final StockageObjets stockage;
  private final String compartiment;
  private final UtilisateurCourant utilisateurCourant;

  public ServicePieces(
      DemandeRepository demandes,
      PieceRepository pieces,
      StockageObjets stockage,
      ProprietesStockage proprietes,
      UtilisateurCourant utilisateurCourant) {
    this.demandes = demandes;
    this.pieces = pieces;
    this.stockage = stockage;
    this.compartiment = proprietes.compartimentPieces();
    this.utilisateurCourant = utilisateurCourant;
  }

  @Transactional
  public Piece ajouter(long demandeId, String typePiece, String nomOriginal, byte[] contenu) {
    brouillonVisible(demandeId);
    if (contenu.length == 0) {
      throw new ErreurMetier(CodeErreur.DOC_001, Map.of("raison", "vide"));
    }
    if (contenu.length > ReglesPieces.TAILLE_MAX_OCTETS) {
      throw new ErreurMetier(
          CodeErreur.DOC_002, Map.of("tailleMax", ReglesPieces.TAILLE_MAX_OCTETS));
    }
    String typeMime =
        ReglesPieces.formatReconnu(contenu)
            .orElseThrow(() -> new ErreurMetier(CodeErreur.DOC_001, Map.of()));
    String cle = "demandes/" + demandeId + "/" + UUID.randomUUID();
    stockage.deposer(compartiment, cle, contenu, typeMime);
    long moi =
        utilisateurCourant
            .get()
            .orElseThrow(() -> new IllegalStateException("Requête authentifiée sans compte"))
            .id();
    return pieces.ajouter(
        demandeId,
        typePiece.strip(),
        ReglesPieces.nomFichier(nomOriginal),
        typeMime,
        contenu.length,
        ReglesPieces.empreinteSha256(contenu),
        cle,
        moi);
  }

  @Transactional(readOnly = true)
  public List<Piece> lister(long demandeId) {
    visible(demandeId);
    return pieces.lister(demandeId);
  }

  /** Pièce et flux de son contenu ; l'appelant ferme le flux. */
  @Transactional(readOnly = true)
  public Contenu contenu(long demandeId, long pieceId) {
    Piece piece = piece(demandeId, pieceId);
    return new Contenu(piece, stockage.lire(compartiment, piece.stockageCle()));
  }

  @Transactional
  public void retirer(long demandeId, long pieceId) {
    brouillonVisible(demandeId);
    if (pieces.retirer(demandeId, pieceId) == 0) {
      throw new ErreurMetier(CodeErreur.DOC_003, Map.of("id", pieceId));
    }
  }

  /** Pièce et son contenu stocké. */
  public record Contenu(Piece piece, InputStream flux) {}

  private Piece piece(long demandeId, long pieceId) {
    return pieces
        .lire(demandeId, pieceId)
        .orElseThrow(() -> new ErreurMetier(CodeErreur.DOC_003, Map.of("id", pieceId)));
  }

  private DemandeDetail visible(long demandeId) {
    return demandes
        .lire(demandeId)
        .orElseThrow(() -> new ErreurMetier(CodeErreur.DEM_002, Map.of("id", demandeId)));
  }

  private void brouillonVisible(long demandeId) {
    if (!visible(demandeId).estBrouillon()) {
      throw new ErreurMetier(CodeErreur.DEM_003, Map.of("id", demandeId));
    }
  }
}
