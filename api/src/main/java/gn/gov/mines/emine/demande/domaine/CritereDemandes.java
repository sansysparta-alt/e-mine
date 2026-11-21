package gn.gov.mines.emine.demande.domaine;

/**
 * Critères de la liste des demandes (US-03.5, correctif J2 D10).
 *
 * @param statut statut de la base, ou {@code null} pour tous
 * @param typeDocumentCode code du document, ou {@code null}
 * @param recherche texte cherché dans le numéro et le document (sans casse), ou {@code null}
 * @param tri colonne de tri
 * @param croissant sens du tri
 */
public record CritereDemandes(
    String statut, String typeDocumentCode, String recherche, Tri tri, boolean croissant) {

  /** Colonnes triables de la liste. */
  public enum Tri {
    NUMERO,
    DATE,
    STATUT
  }
}
