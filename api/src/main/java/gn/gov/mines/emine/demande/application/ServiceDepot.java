package gn.gov.mines.emine.demande.application;

import gn.gov.mines.emine.catalogue.Catalogue;
import gn.gov.mines.emine.demande.domaine.DemandeDetail;
import gn.gov.mines.emine.demande.domaine.RegleDepot;
import gn.gov.mines.emine.demande.infrastructure.DemandeRepository;
import gn.gov.mines.emine.demande.infrastructure.PieceRepository;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dépôt d'une demande (US-03.4) : contrôle des conditions (RegleDepot), passage au statut {@code
 * deposee} ; la base attribue le numéro {@code DEM-AAAA-NNNNNN} (D3, V5) et historise le statut. Le
 * paiement des frais est créé quand le demandeur choisit son moyen de paiement (sprint J3).
 */
@Service
public class ServiceDepot {

  private final DemandeRepository demandes;
  private final PieceRepository pieces;
  private final Catalogue catalogue;

  public ServiceDepot(DemandeRepository demandes, PieceRepository pieces, Catalogue catalogue) {
    this.demandes = demandes;
    this.pieces = pieces;
    this.catalogue = catalogue;
  }

  @Transactional
  public DemandeDetail deposer(long id, boolean declarationAcceptee) {
    DemandeDetail demande =
        demandes.lire(id).orElseThrow(() -> new ErreurMetier(CodeErreur.DEM_002, Map.of("id", id)));
    if (!demande.estBrouillon()) {
      throw new ErreurMetier(CodeErreur.DEM_003, Map.of("id", id));
    }
    if (catalogue.trouverActif(demande.typeDocumentCode()).isEmpty()) {
      throw new ErreurMetier(CodeErreur.DEM_005, Map.of("code", demande.typeDocumentCode()));
    }
    List<String> manques = RegleDepot.manques(declarationAcceptee, pieces.lister(id).size());
    if (!manques.isEmpty()) {
      throw new ErreurMetier(CodeErreur.DEM_004, Map.of("manques", manques));
    }
    if (demandes.deposer(id) == 0) {
      throw new ErreurMetier(CodeErreur.DEM_003, Map.of("id", id));
    }
    return demandes.lire(id).orElseThrow();
  }
}
