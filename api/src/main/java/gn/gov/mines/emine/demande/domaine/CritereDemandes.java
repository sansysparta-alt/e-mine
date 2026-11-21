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
    Tri tri,
    boolean croissant) {

  public CritereDemandes {
    statuts = statuts == null ? List.of() : List.copyOf(statuts);
  }

  /** Colonnes triables de la liste. */
  public enum Tri {
    NUMERO,
    DATE,
    STATUT
  }
}
