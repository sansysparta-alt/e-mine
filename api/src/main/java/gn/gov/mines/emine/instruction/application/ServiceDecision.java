package gn.gov.mines.emine.instruction.application;

import gn.gov.mines.emine.auth.UtilisateurConnecte;
import gn.gov.mines.emine.auth.UtilisateurCourant;
import gn.gov.mines.emine.catalogue.Catalogue;
import gn.gov.mines.emine.catalogue.TypeDocument;
import gn.gov.mines.emine.demande.Demandes;
import gn.gov.mines.emine.demande.Demandes.DemandeResume;
import gn.gov.mines.emine.instruction.domaine.DecisionRendue;
import gn.gov.mines.emine.instruction.domaine.RegleDecision;
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
 * Décision du signataire (US-05.4) : favorable (la demande attend les droits, ou passe directement
 * à « droits payés » si le document n'en a pas) ou rejet motivé. Seul le profil désigné par le
 * catalogue décide (D4, DEM-008 sinon) ; la décision est inscrite dans {@code avis_decision}, le
 * changement de statut est historisé et audité par la base.
 */
@Service
public class ServiceDecision {

  private final Demandes demandes;
  private final Catalogue catalogue;
  private final InstructionRepository instruction;
  private final UtilisateurCourant utilisateurCourant;

  public ServiceDecision(
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
   * Où en est la décision d'une demande visible.
   *
   * @param aDecider la demande est recevable et attend la décision
   * @param signataire libellé du signataire du catalogue (« Ministre »…)
   * @param peutDecider l'utilisateur courant est ce signataire et la demande est à décider
   * @param droitsGnf droits à payer après une décision favorable, vide s'il n'y en a pas
   * @param decision dernière décision rendue, s'il y en a une
   */
  public record EtatDecision(
      boolean aDecider,
      String signataire,
      String signataireProfil,
      boolean peutDecider,
      Optional<Long> droitsGnf,
      Optional<DecisionRendue> decision) {}

  @Transactional(readOnly = true)
  public EtatDecision etat(long id) {
    DemandeResume demande = demande(id);
    TypeDocument type = type(demande);
    boolean aDecider = RegleDecision.aDecider(demande.statut(), !type.avisRequis().isEmpty());
    boolean signataire =
        utilisateurCourant
            .get()
            .map(u -> RegleDecision.estSignataire(u.profilCode(), type.signataireProfil()))
            .orElse(false);
    return new EtatDecision(
        aDecider,
        type.signataire(),
        type.signataireProfil(),
        aDecider && signataire,
        Optional.ofNullable(type.tarifs().droitsGnf()),
        instruction.derniereDecision(id));
  }

  /**
   * Rend la décision : DEM-008 si l'utilisateur n'est pas le signataire du catalogue, DEM-001 si la
   * demande n'attend pas de décision, VAL-001 si le motif d'un rejet manque ou si un texte est trop
   * long. Renvoie le nouveau statut.
   */
  @Transactional
  public String decider(long id, boolean favorable, String motif) {
    Optional<String> probleme =
        favorable ? RegleMotif.problemeFacultatif(motif) : RegleMotif.probleme(motif);
    probleme.ifPresent(
        message -> {
          throw new ErreurMetier(
              CodeErreur.VAL_001,
              Map.of("champs", List.of(Map.of("champ", "motif", "message", message))));
        });
    UtilisateurConnecte moi = moi();
    DemandeResume demande = demande(id);
    TypeDocument type = type(demande);
    if (!RegleDecision.estSignataire(moi.profilCode(), type.signataireProfil())) {
      throw new ErreurMetier(CodeErreur.DEM_008, Map.of("signataire", type.signataire()));
    }
    if (!RegleDecision.aDecider(demande.statut(), !type.avisRequis().isEmpty())) {
      throw new ErreurMetier(
          "recevable".equals(demande.statut()) ? CodeErreur.DEM_011 : CodeErreur.DEM_001,
          Map.of("id", id, "statut", demande.statut()));
    }
    StatutDemande actuel = StatutDemande.lookupLiteral(demande.statut());
    String texte = RegleMotif.nettoyer(motif);
    if (favorable) {
      exiger(instruction.changerStatut(id, actuel, StatutDemande.decision), id);
      instruction.enregistrerDecision(id, moi.id(), true, texte);
      String statut = RegleDecision.statutApresFavorable(type.tarifs().droitsGnf());
      if ("droits_payes".equals(statut)) {
        exiger(
            instruction.changerStatut(id, StatutDemande.decision, StatutDemande.droits_payes), id);
      }
      return statut;
    }
    exiger(instruction.rejeterDepuis(id, actuel, texte), id);
    instruction.enregistrerDecision(id, moi.id(), false, texte);
    return "rejetee";
  }

  private static void exiger(int lignes, long id) {
    if (lignes == 0) {
      throw new ErreurMetier(CodeErreur.DEM_001, Map.of("id", id));
    }
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

  private UtilisateurConnecte moi() {
    return utilisateurCourant
        .get()
        .orElseThrow(() -> new IllegalStateException("Requête authentifiée sans compte"));
  }
}
