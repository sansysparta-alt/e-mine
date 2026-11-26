package gn.gov.mines.emine.catalogue.application;

import gn.gov.mines.emine.auth.Permission;
import gn.gov.mines.emine.auth.UtilisateurConnecte;
import gn.gov.mines.emine.auth.UtilisateurCourant;
import gn.gov.mines.emine.catalogue.domaine.Workflow;
import gn.gov.mines.emine.catalogue.domaine.Workflow.Etape;
import gn.gov.mines.emine.catalogue.domaine.Workflow.VersionWorkflow;
import gn.gov.mines.emine.catalogue.infrastructure.WorkflowRepository;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.gouvernance.EtatVersion;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workflows (US-02.5) : consultés par l'administrateur et le Ministre. Gouvernance (US-02.8) :
 * l'administrateur propose une nouvelle version avec sa date d'effet, le Ministre la valide ou la
 * refuse ; les demandes déposées gardent la version de leur dépôt. Affichage seulement : le circuit
 * réel reste conduit par les fiches des documents.
 */
@Service
public class ServiceWorkflows {

  private final WorkflowRepository workflows;
  private final UtilisateurCourant utilisateurCourant;

  public ServiceWorkflows(WorkflowRepository workflows, UtilisateurCourant utilisateurCourant) {
    this.workflows = workflows;
    this.utilisateurCourant = utilisateurCourant;
  }

  @Transactional(readOnly = true)
  public PageResultat<Workflow> lister(Pagination pagination) {
    gestionnaire();
    List<Workflow> tous = workflows.lister();
    int debut = Math.min(pagination.decalage(), tous.size());
    int fin = Math.min(debut + pagination.taille(), tous.size());
    return new PageResultat<>(
        tous.subList(debut, fin), pagination.page(), pagination.taille(), tous.size());
  }

  /**
   * Un workflow, avec toutes ses versions (la plus récente d'abord).
   *
   * @param peutProposer administrateur, sans proposition en attente pour ce workflow
   * @param peutDecider Ministre
   */
  public record Detail(
      Workflow workflow,
      List<VersionWorkflow> versions,
      boolean peutProposer,
      boolean peutDecider) {}

  /** Propositions de workflows en attente du Ministre (compteur du menu et du tableau de bord). */
  @Transactional(readOnly = true)
  public long nombreAValider() {
    gestionnaire();
    return workflows.lister().stream().filter(w -> w.aValider() != null).count();
  }

  @Transactional(readOnly = true)
  public Detail lire(String code) {
    UtilisateurConnecte moi = gestionnaire();
    Workflow w =
        workflows.lister().stream()
            .filter(x -> x.code().equals(code))
            .findFirst()
            .orElseThrow(() -> new ErreurMetier(CodeErreur.SYS_002, Map.of("code", code)));
    return new Detail(
        w,
        workflows.versions(code),
        moi.peut(Permission.PARAMETRER) && w.aValider() == null,
        moi.peut(Permission.VALIDER_CATALOGUE));
  }

  /**
   * Proposition d'une nouvelle version (administrateur) : DOC-008 si une proposition attend déjà,
   * DOC-010 si rien ne change.
   *
   * @return numéro de la version proposée
   */
  @Transactional
  public int proposer(
      String code,
      String libelle,
      String reglePaiement,
      List<Etape> etapes,
      String motif,
      LocalDate dateEffet) {
    if (!gestionnaire().peut(Permission.PARAMETRER)) {
      throw new ErreurMetier(CodeErreur.PERM_001);
    }
    workflows
        .code(code)
        .orElseThrow(() -> new ErreurMetier(CodeErreur.SYS_002, Map.of("code", code)));
    return workflows.proposer(
        code,
        libelle.strip(),
        reglePaiement.strip(),
        etapes.stream().map(e -> new Etape(e.acteur().strip(), e.libelle().strip())).toList(),
        motif.strip(),
        dateEffet);
  }

  /**
   * Décision : le Ministre valide ou refuse, l'administrateur retire sa proposition.
   *
   * @return date d'effet retenue pour une validation, {@code null} sinon
   */
  @Transactional
  public LocalDate decider(String code, int version, EtatVersion.Decision decision, String motif) {
    UtilisateurConnecte moi = gestionnaire();
    Permission requise =
        decision == EtatVersion.Decision.RETIRER
            ? Permission.PARAMETRER
            : Permission.VALIDER_CATALOGUE;
    if (!moi.peut(requise)) {
      throw new ErreurMetier(CodeErreur.PERM_001);
    }
    return workflows.decider(code, version, decision, motif == null ? null : motif.strip());
  }

  private UtilisateurConnecte gestionnaire() {
    UtilisateurConnecte moi =
        utilisateurCourant.get().orElseThrow(() -> new ErreurMetier(CodeErreur.AUTH_001));
    if (!moi.peut(Permission.PARAMETRER) && !moi.peut(Permission.VALIDER_CATALOGUE)) {
      throw new ErreurMetier(CodeErreur.PERM_001);
    }
    return moi;
  }
}
