package gn.gov.mines.emine.audit.domaine;

import java.time.Instant;
import java.util.List;

/**
 * Critères de consultation du journal d'audit (US-11.7).
 *
 * @param tables tables retenues (vide = toutes)
 * @param recherche un identifiant (l'objet, ou les lignes rattachées à cette demande), sinon un
 *     texte cherché dans les valeurs (numéro, référence, e-mail…) sans casse ; ou {@code null}
 * @param debut début de la période (inclus), ou {@code null}
 * @param fin fin de la période (exclue), ou {@code null}
 * @param connexions {@code true} : seulement les connexions ; {@code false} : sans elles ; {@code
 *     null} : toutes les lignes
 * @param auteurId auteur des lignes (compte), ou {@code null}
 */
public record CritereJournal(
    List<String> tables,
    String recherche,
    Instant debut,
    Instant fin,
    Boolean connexions,
    Long auteurId) {

  public CritereJournal {
    tables = tables == null ? List.of() : List.copyOf(tables);
  }
}
