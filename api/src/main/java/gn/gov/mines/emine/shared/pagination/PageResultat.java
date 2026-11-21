package gn.gov.mines.emine.shared.pagination;

import java.util.List;
import java.util.function.Function;

/**
 * Une page d'une liste (socle) : éléments, numéro de page, taille, nombre total d'éléments et de
 * pages.
 */
public record PageResultat<T>(List<T> elements, int page, int taille, long total) {

  public PageResultat {
    elements = List.copyOf(elements);
  }

  public int totalPages() {
    return total == 0 ? 0 : (int) ((total + taille - 1) / taille);
  }

  public <R> PageResultat<R> map(Function<T, R> conversion) {
    return new PageResultat<>(elements.stream().map(conversion).toList(), page, taille, total);
  }
}
