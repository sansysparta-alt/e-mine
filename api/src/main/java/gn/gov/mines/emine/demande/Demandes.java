package gn.gov.mines.emine.demande;

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

  public Demandes(DemandeRepository demandes, PieceRepository pieces) {
    this.demandes = demandes;
    this.pieces = pieces;
  }

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
                    d.fraisPayes()));
  }

  /**
   * Types des pièces jointes (non retirées) d'une demande visible : base du contrôle des pièces
   * obligatoires à la recevabilité (US-05.1). Liste vide si la demande n'est pas visible.
   */
  @Transactional(readOnly = true)
  public List<String> typesPiecesFournies(long id) {
    return pieces.lister(id).stream().map(Piece::typePiece).toList();
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
   * Résumé d'une demande pour les autres modules.
   *
   * @param numero {@code DEM-AAAA-NNNNNN}, {@code null} pour un brouillon
   * @param statut statut de la base ({@code deposee}, {@code decision}…)
   * @param demandeurNom prénom et nom du demandeur
   * @param organisation organisation du demandeur, {@code null} pour une personne physique
   * @param fraisPayes frais de dossier (ou attestation) payés : condition de la recevabilité (V8)
   */
  public record DemandeResume(
      long id,
      String numero,
      String statut,
      String typeDocumentCode,
      long demandeurId,
      String demandeurNom,
      String organisation,
      boolean fraisPayes) {}
}
