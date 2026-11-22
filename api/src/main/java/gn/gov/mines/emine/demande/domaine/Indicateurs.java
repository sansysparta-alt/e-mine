package gn.gov.mines.emine.demande.domaine;

import java.time.Instant;
import java.util.List;

/**
 * Indicateurs du tableau de bord (correctifs ergonomie), calculés sur les demandes visibles (RLS).
 *
 * @param periode période des indicateurs « sur la période »
 * @param debut début de la période
 * @param fin fin de la période (maintenant)
 * @param enRetard à ce jour : dossiers en cours dont le délai du catalogue, compté en jours
 *     calendaires depuis le dépôt, est dépassé
 * @param complementsAttendus à ce jour : dossiers en attente d'un complément du demandeur
 * @param fraisAttendus à ce jour : dossiers déposés dont les frais ne sont pas payés
 * @param bloques à ce jour : dossiers en cours passés au moins 3 fois à leur étape actuelle
 * @param traitesAujourdhui aujourd'hui : dossiers dont le profil connecté a changé le statut
 * @param delivrees sur la période : documents délivrés
 * @param delaiMoyenJours sur la période : délai moyen du dépôt à la délivrance, en jours
 *     calendaires ({@code null} sans délivrance)
 * @param encaissementsGnf sur la période : paiements reçus, en GNF
 * @param parDocument répartition par document du catalogue (documents ayant au moins un chiffre)
 */
public record Indicateurs(
    Periode periode,
    Instant debut,
    Instant fin,
    long enRetard,
    long complementsAttendus,
    long fraisAttendus,
    long bloques,
    long traitesAujourdhui,
    long delivrees,
    Double delaiMoyenJours,
    long encaissementsGnf,
    List<ParDocument> parDocument) {

  public Indicateurs {
    parDocument = List.copyOf(parDocument);
  }

  /**
   * Chiffres d'un document du catalogue.
   *
   * @param code code du document
   * @param enCours à ce jour : dossiers en cours
   * @param enRetard à ce jour : dossiers en retard
   * @param delivrees sur la période : documents délivrés
   * @param delaiMoyenJours sur la période : délai moyen de délivrance, ou {@code null}
   * @param encaissementsGnf sur la période : paiements reçus, en GNF
   */
  public record ParDocument(
      String code,
      long enCours,
      long enRetard,
      long delivrees,
      Double delaiMoyenJours,
      long encaissementsGnf) {}
}
