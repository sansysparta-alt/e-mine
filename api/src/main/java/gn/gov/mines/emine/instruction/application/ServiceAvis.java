package gn.gov.mines.emine.instruction.application;

import gn.gov.mines.emine.auth.UtilisateurConnecte;
import gn.gov.mines.emine.auth.UtilisateurCourant;
import gn.gov.mines.emine.catalogue.Catalogue;
import gn.gov.mines.emine.catalogue.TypeDocument;
import gn.gov.mines.emine.demande.Demandes;
import gn.gov.mines.emine.demande.Demandes.DemandeResume;
import gn.gov.mines.emine.instruction.domaine.AvisRendu;
import gn.gov.mines.emine.instruction.domaine.RegleAvis;
import gn.gov.mines.emine.instruction.domaine.RegleMotif;
import gn.gov.mines.emine.instruction.infrastructure.InstructionRepository;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.jooq.enums.StatutDemande;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Avis techniques motivés (US-05.3, règles dans {@link RegleAvis}). Les avis sont des pièces de
 * travail internes : seuls les profils internes les lisent ; le demandeur ne voit que la décision.
 */
@Service
public class ServiceAvis {

  private final Demandes demandes;
  private final Catalogue catalogue;
  private final InstructionRepository instruction;
  private final UtilisateurCourant utilisateurCourant;

  public ServiceAvis(
      Demandes demandes,
      Catalogue catalogue,
      InstructionRepository instruction,
      UtilisateurCourant utilisateurCourant) {
    this.demandes = demandes;
    this.catalogue = catalogue;
    this.instruction = instruction;
    this.utilisateurCourant = utilisateurCourant;
  }

  /**
   * Un avis requis par le catalogue, rendu ou attendu.
   *
   * @param avis l'avis rendu, vide s'il est attendu
   */
  public record LigneAvis(String profilCode, String libelle, Optional<AvisRendu> avis) {}

  /**
   * Avis d'une demande.
   *
   * @param requis avis requis par le catalogue, dans l'ordre
   * @param peutRendre l'utilisateur courant doit rendre son avis maintenant
   */
  public record EtatAvis(List<LigneAvis> requis, boolean peutRendre) {}

  @Transactional(readOnly = true)
  public EtatAvis etat(long id) {
    UtilisateurConnecte moi = interne();
    DemandeResume demande = demande(id);
    TypeDocument type = type(demande);
    List<AvisRendu> rendus = instruction.listerAvis(id);
    List<LigneAvis> lignes =
        type.avisRequis().stream()
            .map(
                a ->
                    new LigneAvis(
                        a.profilCode(),
                        a.libelle(),
                        rendus.stream()
                            .filter(r -> r.profilCode().equals(a.profilCode()))
                            .findFirst()))
            .toList();
    boolean peutRendre =
        RegleAvis.aRendre(
            demande.statut(),
            type.profilsAvis(),
            moi.profilCode(),
            rendus.stream().map(AvisRendu::profilCode).toList());
    return new EtatAvis(lignes, peutRendre);
  }

  /**
   * Rend l'avis du profil connecté : DEM-012 si son avis n'est pas requis ou déjà rendu, DEM-001 si
   * la demande n'est pas à l'instruction, VAL-001 si le motif manque. Quand c'est le dernier avis
   * attendu, la demande passe à « avis rendu ». Renvoie le statut de la demande.
   */
  @Transactional
  public String rendre(long id, boolean favorable, String motif) {
    RegleMotif.probleme(motif)
        .ifPresent(
            m -> {
              throw new ErreurMetier(
                  CodeErreur.VAL_001,
                  Map.of("champs", List.of(Map.of("champ", "motif", "message", m))));
            });
    UtilisateurConnecte moi = interne();
    DemandeResume demande = demande(id);
    TypeDocument type = type(demande);
    List<String> rendus = instruction.listerAvis(id).stream().map(AvisRendu::profilCode).toList();
    if (!type.profilsAvis().contains(moi.profilCode()) || rendus.contains(moi.profilCode())) {
      throw new ErreurMetier(CodeErreur.DEM_012, Map.of("id", id));
    }
    if (!RegleAvis.STATUT_INSTRUCTION.equals(demande.statut())) {
      throw new ErreurMetier(CodeErreur.DEM_001, Map.of("id", id, "statut", demande.statut()));
    }
    instruction.enregistrerAvis(id, moi.id(), favorable, motif.strip());
    List<String> apres = new java.util.ArrayList<>(rendus);
    apres.add(moi.profilCode());
    if (!RegleAvis.manquants(type.profilsAvis(), apres).isEmpty()) {
      return demande.statut();
    }
    if (instruction.changerStatut(id, StatutDemande.en_instruction, StatutDemande.avis_rendu)
        == 0) {
      throw new ErreurMetier(CodeErreur.DEM_001, Map.of("id", id));
    }
    return "avis_rendu";
  }

  private TypeDocument type(DemandeResume demande) {
    return catalogue
        .pourDemande(demande.typeDocumentCode(), demande.versionDocumentId())
        .orElseThrow(
            () -> new ErreurMetier(CodeErreur.DEM_005, Map.of("code", demande.typeDocumentCode())));
  }

  private DemandeResume demande(long id) {
    return demandes
        .lire(id)
        .orElseThrow(() -> new ErreurMetier(CodeErreur.DEM_002, Map.of("id", id)));
  }

  /** Avis : pièces internes, réservées aux profils du Ministère (PERM-001 sinon). */
  private UtilisateurConnecte interne() {
    UtilisateurConnecte moi =
        utilisateurCourant
            .get()
            .orElseThrow(() -> new IllegalStateException("Requête authentifiée sans compte"));
    if (!moi.interne()) {
      throw new ErreurMetier(CodeErreur.PERM_001, Map.of());
    }
    return moi;
  }
}
