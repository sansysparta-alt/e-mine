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
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workflows (US-02.5) : consultés par l'administrateur et le Ministre, modifiés par
 * l'administrateur (immédiat et tracé, choix du porteur du projet) ; affichage seulement, le
 * circuit réel restant conduit par les fiches des documents.
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
   * @param peutModifier le profil connecté peut le modifier (administrateur)
   */
  public record Detail(Workflow workflow, List<VersionWorkflow> versions, boolean peutModifier) {}

  @Transactional(readOnly = true)
  public Detail lire(String code) {
    UtilisateurConnecte moi = gestionnaire();
    Workflow w =
        workflows.lister().stream()
            .filter(x -> x.code().equals(code))
            .findFirst()
            .orElseThrow(() -> new ErreurMetier(CodeErreur.SYS_002, Map.of("code", code)));
    return new Detail(w, workflows.versions(code), moi.peut(Permission.PARAMETRER));
  }

  /**
   * Nouvelle version du workflow (administrateur) : DOC-010 si rien ne change.
   *
   * @return numéro de la nouvelle version
   */
  @Transactional
  public int modifier(
      String code, String libelle, String reglePaiement, List<Etape> etapes, String motif) {
    if (!gestionnaire().peut(Permission.PARAMETRER)) {
      throw new ErreurMetier(CodeErreur.PERM_001);
    }
    workflows
        .code(code)
        .orElseThrow(() -> new ErreurMetier(CodeErreur.SYS_002, Map.of("code", code)));
    return workflows.modifier(
        code,
        libelle.strip(),
        reglePaiement.strip(),
        etapes.stream().map(e -> new Etape(e.acteur().strip(), e.libelle().strip())).toList(),
        motif.strip());
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
