package gn.gov.mines.emine.demande;

import gn.gov.mines.emine.demande.application.ServicePieces;
import gn.gov.mines.emine.demande.domaine.Piece;
import gn.gov.mines.emine.demande.infrastructure.DemandeRepository;
import gn.gov.mines.emine.demande.infrastructure.PieceRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API publique du module {@code demande} pour les autres modules (paiement, instruction) : lecture
 * d'une demande visible selon la RLS, et des types de pièces jointes (recevabilité).
 */
@Service
public class Demandes {

  private final DemandeRepository demandes;
  private final PieceRepository pieces;
  private final ServicePieces servicePieces;

  public Demandes(DemandeRepository demandes, PieceRepository pieces, ServicePieces servicePieces) {
    this.demandes = demandes;
    this.pieces = pieces;
    this.servicePieces = servicePieces;
  }

  /**
   * Justificatif d'un virement (US-04.7) joint à une demande déposée et visible : contrôle du
   * format (PDF, JPG, PNG), de la taille (5 Mo), empreinte et stockage. Renvoie l'identifiant de la
   * pièce, que le module paiement rattache au paiement.
   */
  public long deposerJustificatifVirement(long id, String nomOriginal, byte[] contenu) {
    return servicePieces.ajouterJustificatifVirement(id, nomOriginal, contenu).id();
  }

  /** Justificatif d'un virement : nom, format et flux du contenu (l'appelant ferme le flux). */
  public FichierJoint justificatifVirement(long id, long pieceId) {
    ServicePieces.Contenu c = servicePieces.contenuJustificatif(id, pieceId);
    return new FichierJoint(c.piece().nomFichier(), c.piece().typeMime(), c.flux());
  }

  /**
   * Nom et format du justificatif d'un virement, sans son contenu ; vide s'il n'est pas visible.
   */
  @Transactional(readOnly = true)
  public Optional<InfosFichier> infosJustificatifVirement(long id, long pieceId) {
    return pieces
        .lireJustificatif(id, pieceId)
        .map(p -> new InfosFichier(p.nomFichier(), p.typeMime()));
  }

  /** Nom et format d'un fichier joint. */
  public record InfosFichier(String nomFichier, String typeMime) {}

  /** Fichier joint lu dans le stockage. */
  public record FichierJoint(String nomFichier, String typeMime, java.io.InputStream flux) {}

  /** La demande si elle existe et est visible pour l'utilisateur courant (RLS). */
  @Transactional(readOnly = true)
  public Optional<DemandeResume> lire(long id) {
    return demandes
        .lire(id)
        .map(
            d ->
                new DemandeResume(
                    d.id(),
                    d.numero(),
                    d.statut(),
                    d.typeDocumentCode(),
                    d.demandeur().id(),
                    (d.demandeur().prenom() + " " + d.demandeur().nom()).strip(),
                    d.demandeur().organisation(),
                    d.fraisPayes(),
                    demandes.versionDocument(d.id()).orElse(null)));
  }

  /**
   * Types des pièces jointes (non retirées) d'une demande visible : base du contrôle des pièces
   * obligatoires à la recevabilité (US-05.1). Liste vide si la demande n'est pas visible.
   */
  @Transactional(readOnly = true)
  public List<String> typesPiecesFournies(long id) {
    return pieces.lister(id).stream().filter(Piece::enVigueur).map(Piece::typePiece).toList();
  }

  /**
   * Types des pièces jointes (non retirées) ajoutées depuis un instant : réponse à un complément
   * (US-05.2). Liste vide si la demande n'est pas visible.
   */
  @Transactional(readOnly = true)
  public List<String> typesPiecesAjouteesDepuis(long id, java.time.Instant depuis) {
    return pieces.lister(id).stream()
        .filter(p -> !p.deposeLe().isBefore(depuis))
        .map(Piece::typePiece)
        .toList();
  }

  /**
   * Réponse à un complément (US-05.2, V14) : les pièces de ces types jointes depuis la demande de
   * complément remplacent leurs versions antérieures (conservées, marquées « remplacées »).
   */
  @Transactional
  public int remplacerVersionsAnterieures(long id, java.time.Instant depuis, List<String> types) {
    return pieces.remplacerVersionsAnterieures(id, depuis, types);
  }

  /**
   * Résumé d'une demande pour les autres modules.
   *
   * @param numero {@code DEM-AAAA-NNNNNN}, {@code null} pour un brouillon
   * @param statut statut de la base ({@code deposee}, {@code decision}…)
   * @param demandeurNom prénom et nom du demandeur
   * @param organisation organisation du demandeur, {@code null} pour une personne physique
   * @param fraisPayes frais de dossier (ou attestation) payés : condition de la recevabilité (V8)
   * @param versionDocumentId version de la fiche en vigueur au dépôt (V18) : délai, avis requis et
   *     signataire de cette demande ; {@code null} pour un brouillon
   */
  public record DemandeResume(
      long id,
      String numero,
      String statut,
      String typeDocumentCode,
      long demandeurId,
      String demandeurNom,
      String organisation,
      boolean fraisPayes,
      Long versionDocumentId) {}
}
