package gn.gov.mines.emine.demande.application;

import gn.gov.mines.emine.auth.UtilisateurConnecte;
import gn.gov.mines.emine.auth.UtilisateurCourant;
import gn.gov.mines.emine.demande.domaine.DemandeDetail;
import gn.gov.mines.emine.demande.domaine.Passage;
import gn.gov.mines.emine.demande.domaine.VueDemandes;
import gn.gov.mines.emine.demande.infrastructure.DemandeRepository;
import gn.gov.mines.emine.demande.infrastructure.PassageRepository;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * « Passer ce dossier » (correctifs ergonomie) : l'agent qui traite une file en série peut passer
 * un dossier sans agir, en donnant un motif court. Le passage est tracé et visible de tous les
 * agents ; au 3e passage à la même étape, le dossier est signalé « Bloqué ».
 */
@Service
public class ServicePassages {

  private final DemandeRepository demandes;
  private final PassageRepository passages;
  private final UtilisateurCourant utilisateurCourant;

  public ServicePassages(
      DemandeRepository demandes,
      PassageRepository passages,
      UtilisateurCourant utilisateurCourant) {
    this.demandes = demandes;
    this.passages = passages;
    this.utilisateurCourant = utilisateurCourant;
  }

  /**
   * Passe le dossier : DEM-002 s'il n'est pas visible (ou brouillon), DEM-001 s'il n'est plus en
   * cours de traitement.
   */
  @Transactional
  public Passage.Historique passer(long id, String motif) {
    DemandeDetail d = lireEnInterne(id);
    if (!VueDemandes.STATUTS_ACTIFS.contains(d.statut())) {
      throw new ErreurMetier(CodeErreur.DEM_001, Map.of("id", id, "statut", d.statut()));
    }
    passages.ajouter(id, d.statut(), motif.strip());
    return historique(id);
  }

  /** Passages du dossier (profils internes seulement). */
  @Transactional(readOnly = true)
  public Passage.Historique lister(long id) {
    lireEnInterne(id);
    return historique(id);
  }

  private Passage.Historique historique(long id) {
    DemandeDetail d =
        demandes.lire(id).orElseThrow(() -> new ErreurMetier(CodeErreur.DEM_002, Map.of("id", id)));
    return new Passage.Historique(d.passages(), d.bloque(), passages.lister(id));
  }

  private DemandeDetail lireEnInterne(long id) {
    UtilisateurConnecte moi =
        utilisateurCourant
            .get()
            .orElseThrow(() -> new IllegalStateException("Requête authentifiée sans compte"));
    if (!moi.interne()) {
      throw new ErreurMetier(CodeErreur.PERM_001, Map.of());
    }
    DemandeDetail d =
        demandes
            .lire(id)
            .filter(x -> !x.estBrouillon())
            .orElseThrow(() -> new ErreurMetier(CodeErreur.DEM_002, Map.of("id", id)));
    return d;
  }
}
