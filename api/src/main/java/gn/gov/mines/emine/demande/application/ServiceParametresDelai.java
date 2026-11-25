package gn.gov.mines.emine.demande.application;

import gn.gov.mines.emine.auth.Permission;
import gn.gov.mines.emine.auth.UtilisateurConnecte;
import gn.gov.mines.emine.auth.UtilisateurCourant;
import gn.gov.mines.emine.demande.domaine.RegleDelais;
import gn.gov.mines.emine.demande.domaine.VersionRegleDelai;
import gn.gov.mines.emine.demande.infrastructure.ParametresDelaiRepository;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.gouvernance.EtatVersion;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Règle des délais (US-02.6, gouvernance US-02.8) : l'administrateur propose une nouvelle règle
 * avec sa date d'effet, le Ministre la valide ou la refuse ; les demandes déposées gardent la règle
 * de leur dépôt. Droits contrôlés ici et, indépendamment, par la base.
 */
@Service
public class ServiceParametresDelai {

  private final ParametresDelaiRepository parametres;
  private final UtilisateurCourant utilisateurCourant;

  public ServiceParametresDelai(
      ParametresDelaiRepository parametres, UtilisateurCourant utilisateurCourant) {
    this.parametres = parametres;
    this.utilisateurCourant = utilisateurCourant;
  }

  /**
   * La règle et son historique, vus par le profil connecté.
   *
   * @param versions toutes les versions, la plus récente d'abord
   * @param peutProposer administrateur, sans proposition en attente
   * @param peutDecider Ministre
   */
  public record Detail(
      RegleDelais.Parametres enVigueur,
      List<VersionRegleDelai> versions,
      boolean peutProposer,
      boolean peutDecider) {}

  @Transactional(readOnly = true)
  public Detail lire() {
    UtilisateurConnecte moi = gestionnaire();
    List<VersionRegleDelai> versions = parametres.versions();
    boolean enAttente = versions.stream().anyMatch(v -> EtatVersion.A_VALIDER.equals(v.etat()));
    return new Detail(
        parametres.lire(),
        versions,
        moi.peut(Permission.PARAMETRER) && !enAttente,
        moi.peut(Permission.VALIDER_CATALOGUE));
  }

  /**
   * Proposition de l'administrateur.
   *
   * @return numéro de la version proposée
   */
  @Transactional
  public int proposer(RegleDelais.Parametres p, String motif, LocalDate dateEffet) {
    if (!gestionnaire().peut(Permission.PARAMETRER)) {
      throw new ErreurMetier(CodeErreur.PERM_001);
    }
    return parametres.proposer(p, motif.strip(), dateEffet);
  }

  /**
   * Décision : le Ministre valide ou refuse, l'administrateur retire.
   *
   * @return date d'effet retenue pour une validation, {@code null} sinon
   */
  @Transactional
  public LocalDate decider(int version, EtatVersion.Decision decision, String motif) {
    UtilisateurConnecte moi = gestionnaire();
    Permission requise =
        decision == EtatVersion.Decision.RETIRER
            ? Permission.PARAMETRER
            : Permission.VALIDER_CATALOGUE;
    if (!moi.peut(requise)) {
      throw new ErreurMetier(CodeErreur.PERM_001);
    }
    return parametres.decider(version, decision, motif == null ? null : motif.strip());
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
