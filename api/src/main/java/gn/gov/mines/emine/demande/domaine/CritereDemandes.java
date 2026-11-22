package gn.gov.mines.emine.demande.domaine;

import java.util.List;

/**
 * Critères de la liste des demandes (US-03.5, correctif J2 D10).
 *
 * @param statuts statuts de la base retenus (vide = tous)
 * @param typeDocumentCode code du document, ou {@code null}
 * @param recherche texte cherché dans le numéro et le document (sans casse), ou {@code null}
 * @param fraisPayes seulement les demandes dont les frais sont payés ({@code true}) ou non payés
 *     ({@code false}) ; {@code null} = toutes (file du guichet, US-05.1)
 * @param aTraiter seulement ce que le profil connecté doit faire maintenant, ou {@code null}
 * @param pilotage filtres ouverts depuis un indicateur du tableau de bord (en retard, traités
 *     aujourd'hui, entrés dans leur statut pendant la période), ou {@code null}
 * @param exclureBrouillons profil interne : un agent ne voit jamais les brouillons des usagers
 * @param vue vue exclusive du profil (« En cours »…), avec son filtre « à traiter », ou {@code
 *     null}
 * @param signataireProfil seulement les documents dont ce profil est le signataire au catalogue
 *     (file des décisions, US-05.4), ou {@code null}
 * @param tri colonne de tri
 * @param croissant sens du tri
 */
public record CritereDemandes(
    List<String> statuts,
    String typeDocumentCode,
    String recherche,
    Boolean fraisPayes,
    String signataireProfil,
    RegleATraiter.Filtre aTraiter,
    Vue vue,
    Pilotage pilotage,
    boolean exclureBrouillons,
    Tri tri,
    boolean croissant) {

  public CritereDemandes {
    statuts = statuts == null ? List.of() : List.copyOf(statuts);
  }

  /**
   * Vue demandée et contexte du profil nécessaire pour la calculer.
   *
   * @param aTraiter filtre « à traiter » du profil (vide pour un profil sans action)
   * @param interne profil interne : l'attente du demandeur est une vue à part
   */
  public record Vue(VueDemandes vue, RegleATraiter.Filtre aTraiter, boolean interne) {}

  /**
   * Filtres d'un indicateur du tableau de bord (correctifs ergonomie).
   *
   * @param enRetard seulement les dossiers en retard sur le délai du catalogue (jours calendaires
   *     depuis le dépôt)
   * @param traiteParMoiDepuis seulement les dossiers dont le profil connecté a changé le statut
   *     depuis cet instant, ou {@code null}
   * @param etapeDepuis seulement les dossiers entrés dans leur statut actuel depuis cet instant
   *     (délivrés pendant la période…), ou {@code null}
   * @param maintenant instant de référence du retard
   */
  public record Pilotage(
      boolean enRetard,
      java.time.Instant traiteParMoiDepuis,
      java.time.Instant etapeDepuis,
      java.time.Instant maintenant) {

    /** Aucun filtre demandé. */
    public boolean vide() {
      return !enRetard && traiteParMoiDepuis == null && etapeDepuis == null;
    }
  }

  /** Les mêmes critères, sans les brouillons (profil interne). */
  public CritereDemandes sansBrouillons() {
    return new CritereDemandes(
        statuts,
        typeDocumentCode,
        recherche,
        fraisPayes,
        signataireProfil,
        aTraiter,
        vue,
        pilotage,
        true,
        tri,
        croissant);
  }

  /** Colonnes triables de la liste. */
  public enum Tri {
    NUMERO,
    DATE,
    STATUT
  }
}
