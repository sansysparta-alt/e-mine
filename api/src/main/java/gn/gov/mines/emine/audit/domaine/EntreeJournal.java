package gn.gov.mines.emine.audit.domaine;

import gn.gov.mines.emine.audit.domaine.RegleJournal.Changement;
import java.time.Instant;
import java.util.List;

/**
 * Ligne du journal d'audit rendue lisible (US-11.7).
 *
 * @param auteur prénom et nom de l'auteur, {@code null} pour une action du système (notification
 *     TrésorPay, tâche planifiée…)
 * @param auteurProfil libellé du profil de l'auteur, ou {@code null}
 * @param table table modifiée ({@code demande}, {@code paiement}…)
 * @param categorie catégorie d'objet proposée au filtre ({@code demandes}…)
 * @param operation {@code I} création, {@code U} modification, {@code D} suppression
 * @param objetLibelle numéro, référence ou nom lisible de l'objet, ou {@code null}
 * @param demandeId demande concernée, pour ouvrir sa fiche, ou {@code null}
 * @param changements champs modifiés (toutes les valeurs pour une création, dans le détail)
 */
public record EntreeJournal(
    long id,
    Instant date,
    String auteur,
    String auteurProfil,
    String auteurProfilCode,
    String table,
    String categorie,
    String operation,
    String objetId,
    String objetLibelle,
    Long demandeId,
    List<Changement> changements,
    String adresseIp) {

  public EntreeJournal {
    changements = List.copyOf(changements);
  }
}
