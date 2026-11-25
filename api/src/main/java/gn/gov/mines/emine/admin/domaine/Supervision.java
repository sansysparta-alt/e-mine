package gn.gov.mines.emine.admin.domaine;

import java.time.Instant;
import java.util.List;

/**
 * Vue d'ensemble de l'administrateur technique (cahier des charges, espace « Administrateur
 * technique » : utilisateurs actifs, profils configurés, disponibilité, utilisateurs et rôles).
 */
public final class Supervision {

  private Supervision() {}

  /** Comptes par statut, et comptes connectés pendant les 30 derniers jours. */
  public record Comptes(long actifs, long suspendus, long invites, long connectes30Jours) {}

  /**
   * Un profil (D2 : 16 profils) et son nombre de comptes (tous statuts, hors comptes supprimés).
   *
   * @param droits nombre de droits de la matrice : un profil sans droit n'est pas configuré
   * @param permissions codes des droits du profil (matrice), consultables à l'écran
   */
  public record ProfilCompte(
      String code,
      String libelle,
      boolean interne,
      long utilisateurs,
      int droits,
      List<String> permissions) {

    public ProfilCompte {
      permissions = List.copyOf(permissions);
    }
  }

  /**
   * Nombre de comptes de chaque filtre de « Utilisateurs & rôles », pour le profil et la recherche
   * choisis : les compteurs suivent le filtre de profil.
   */
  public record CompteursUtilisateurs(
      long tous, long actifs, long suspendus, long invites, long connectes30Jours) {}

  /** Une page de comptes et les compteurs de ses filtres. */
  public record PageUtilisateurs(
      gn.gov.mines.emine.shared.pagination.PageResultat<Utilisateur> page,
      CompteursUtilisateurs compteurs) {}

  /** Un service dont dépend la plateforme, et sa disponibilité au moment de la lecture. */
  public record Service(String cle, boolean disponible) {}

  /** Tableau de bord technique. */
  public record TableauDeBord(
      Comptes comptes, List<ProfilCompte> profils, List<Service> services, Instant verifieLe) {}

  /**
   * Alerte de l'administrateur technique (cloche) : un service indisponible ({@code service}, avec
   * sa clé), des comptes suspendus ({@code suspendus}) ou des invitations jamais activées ({@code
   * invites}), avec leur nombre. Calculées à la lecture : une alerte disparaît dès que la situation
   * est rétablie.
   */
  public record Alerte(String cle, String service, long nombre) {}

  /** Alertes en cours, de la plus grave (service indisponible) à la moins grave. */
  public static List<Alerte> alertes(Comptes comptes, List<Service> services) {
    List<Alerte> alertes = new java.util.ArrayList<>();
    services.stream()
        .filter(s -> !s.disponible())
        .forEach(s -> alertes.add(new Alerte("service", s.cle(), 1)));
    if (comptes.suspendus() > 0) {
      alertes.add(new Alerte("suspendus", null, comptes.suspendus()));
    }
    if (comptes.invites() > 0) {
      alertes.add(new Alerte("invites", null, comptes.invites()));
    }
    return List.copyOf(alertes);
  }

  /** Ligne de « Utilisateurs & rôles ». */
  public record Utilisateur(
      long id,
      String prenom,
      String nom,
      String email,
      String organisation,
      String profilCode,
      String profilLibelle,
      String statut,
      Instant derniereConnexion,
      java.util.UUID keycloakId) {}
}
