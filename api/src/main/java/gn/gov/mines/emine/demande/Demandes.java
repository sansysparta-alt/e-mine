package gn.gov.mines.emine.demande;

import gn.gov.mines.emine.demande.infrastructure.DemandeRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API publique du module {@code demande} pour les autres modules (paiement, instruction) : lecture
 * d'une demande visible selon la RLS.
 */
@Service
public class Demandes {

  private final DemandeRepository demandes;

  public Demandes(DemandeRepository demandes) {
    this.demandes = demandes;
  }

  /** La demande si elle existe et est visible pour l'utilisateur courant (RLS). */
  @Transactional(readOnly = true)
  public Optional<DemandeResume> lire(long id) {
    return demandes
        .lire(id)
        .map(
            d ->
                new DemandeResume(
                    d.id(), d.numero(), d.statut(), d.typeDocumentCode(), d.demandeur().id()));
  }

  /**
   * Résumé d'une demande pour les autres modules.
   *
   * @param numero {@code DEM-AAAA-NNNNNN}, {@code null} pour un brouillon
   * @param statut statut de la base ({@code deposee}, {@code decision}…)
   */
  public record DemandeResume(
      long id, String numero, String statut, String typeDocumentCode, long demandeurId) {}
}
