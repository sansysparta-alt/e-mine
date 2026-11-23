package gn.gov.mines.emine.demande.domaine;

import java.time.Instant;
import java.util.List;

/**
 * Indicateurs du tableau de bord (correctifs ergonomie), calculés sur les demandes visibles (RLS).
 *
 * @param periode période des indicateurs « sur la période »
 * @param debut début de la période
 * @param fin fin de la période (maintenant)
 * @param enRetard à ce jour : dossiers en cours dont le délai du catalogue est dépassé ({@link
 *     RegleDelais})
 * @param procheDepassement à ce jour : dossiers en cours proches du dépassement du délai (US-05.6)
 * @param complementsAttendus à ce jour : dossiers en attente d'un complément du demandeur
 * @param fraisAttendus à ce jour : dossiers déposés dont les frais ne sont pas payés
 * @param bloques à ce jour : dossiers en cours passés au moins 3 fois à leur étape actuelle
 * @param traitesAujourdhui aujourd'hui : dossiers dont le profil connecté a changé le statut
 * @param delivrees sur la période : documents délivrés
 * @param delivreesDansLeDelai sur la période : documents délivrés dans le délai du catalogue
 * @param rejetees sur la période : demandes rejetées (hors du pourcentage « dans le délai »)
 * @param delaiMoyenJours sur la période : délai moyen de traitement des documents délivrés ({@link
 *     RegleDelais}), en jours ({@code null} sans délivrance)
 * @param encaissementsGnf sur la période : paiements reçus, en GNF
 * @param parDocument répartition par document du catalogue (documents ayant au moins un chiffre)
 * @param parametresDelai règle des délais en vigueur (affichée avec les chiffres)
 */
public record Indicateurs(
    Periode periode,
    Instant debut,
    Instant fin,
    long enRetard,
    long procheDepassement,
    long complementsAttendus,
    long fraisAttendus,
    long bloques,
    long traitesAujourdhui,
    long delivrees,
    long delivreesDansLeDelai,
    long rejetees,
    Double delaiMoyenJours,
    long encaissementsGnf,
    List<ParDocument> parDocument,
    RegleDelais.Parametres parametresDelai) {

  public Indicateurs {
    parDocument = List.copyOf(parDocument);
  }

  /**
   * Chiffres d'un document du catalogue.
   *
   * @param code code du document
   * @param enCours à ce jour : dossiers en cours
   * @param enRetard à ce jour : dossiers en retard
   * @param delaiJours délai du catalogue, en jours
   * @param delivrees sur la période : documents délivrés
   * @param delivreesDansLeDelai sur la période : documents délivrés dans le délai du catalogue
   * @param rejetees sur la période : demandes rejetées
   * @param delaiMoyenJours sur la période : délai moyen de traitement, ou {@code null}
   * @param encaissementsGnf sur la période : paiements reçus, en GNF
   */
  public record ParDocument(
      String code,
      long enCours,
      long enRetard,
      int delaiJours,
      long delivrees,
      long delivreesDansLeDelai,
      long rejetees,
      Double delaiMoyenJours,
      long encaissementsGnf) {}
}
