package gn.gov.mines.emine.admin.application;

import gn.gov.mines.emine.admin.domaine.Supervision;
import gn.gov.mines.emine.admin.infrastructure.AdministrationRepository;
import gn.gov.mines.emine.admin.infrastructure.SondesServices;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.pagination.Pagination;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Espace de l'administrateur technique (cahier des charges : « Utilisateurs actifs, Profils
 * configurés, Disponibilité », « Utilisateurs & rôles ») : lecture seule ; les comptes restent
 * gérés dans Keycloak (D8).
 */
@Service
public class ServiceAdministration {

  private final AdministrationRepository depot;
  private final SondesServices sondes;
  private final Clock horloge = Clock.systemUTC();
  private final TransactionTemplate lecture;

  public ServiceAdministration(
      AdministrationRepository depot,
      SondesServices sondes,
      PlatformTransactionManager transactions) {
    this.depot = depot;
    this.sondes = sondes;
    this.lecture = new TransactionTemplate(transactions);
    this.lecture.setReadOnly(true);
  }

  /**
   * Comptes, profils et disponibilité des services. Les sondes (2 s au plus chacune) passent hors
   * de la transaction : elles ne gardent pas de connexion à la base ouverte.
   */
  public Supervision.TableauDeBord tableauDeBord() {
    Instant maintenant = horloge.instant();
    List<Supervision.Service> services = sondes.verifier();
    return lecture.execute(
        statut ->
            new Supervision.TableauDeBord(
                depot.comptes(maintenant), depot.profils(), services, maintenant));
  }

  /** Alertes de la cloche de l'administrateur (mêmes sources que la vue d'ensemble). */
  public List<Supervision.Alerte> alertes() {
    Instant maintenant = horloge.instant();
    List<Supervision.Service> services = sondes.verifier();
    Supervision.Comptes comptes = lecture.execute(statut -> depot.comptes(maintenant));
    return Supervision.alertes(comptes, services);
  }

  /** Durée de la fenêtre « connectés récemment » (même valeur que la carte du tableau de bord). */
  public static final java.time.Duration RECENTS = AdministrationRepository.RECENTS;

  /**
   * « Utilisateurs & rôles » : VAL-001 si le profil demandé n'existe pas.
   *
   * @param recents seulement les comptes connectés pendant les 30 derniers jours
   */
  @Transactional(readOnly = true)
  public Supervision.PageUtilisateurs utilisateurs(
      String profilCode, String statut, boolean recents, String recherche, Pagination pagination) {
    if (profilCode != null
        && depot.profils().stream().noneMatch(p -> p.code().equals(profilCode))) {
      throw new ErreurMetier(
          CodeErreur.VAL_001,
          Map.of("champs", List.of(Map.of("champ", "profil", "message", "Profil inconnu."))));
    }
    Instant recentsDepuis = horloge.instant().minus(RECENTS);
    return depot.utilisateurs(
        profilCode, statut, recents ? recentsDepuis : null, recentsDepuis, recherche, pagination);
  }
}
