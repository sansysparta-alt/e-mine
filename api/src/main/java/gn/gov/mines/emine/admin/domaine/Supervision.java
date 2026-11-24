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
   */
  public record ProfilCompte(
      String code, String libelle, boolean interne, long utilisateurs, int droits) {}

  /** Un service dont dépend la plateforme, et sa disponibilité au moment de la lecture. */
  public record Service(String cle, boolean disponible) {}

  /** Tableau de bord technique. */
  public record TableauDeBord(
      Comptes comptes, List<ProfilCompte> profils, List<Service> services, Instant verifieLe) {}

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
      Instant derniereConnexion) {}
}
