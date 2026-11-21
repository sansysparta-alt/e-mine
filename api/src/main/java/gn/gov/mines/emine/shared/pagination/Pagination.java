package gn.gov.mines.emine.shared.pagination;

import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import java.util.List;
import java.util.Map;

/**
 * Demande de page d'une liste (socle, correctif J2 D10) : numéro de page à partir de 1 et taille
 * parmi {@link #TAILLES}. Toute liste de l'API se pagine ainsi (paramètres {@code page} et {@code
 * taille}), et répond par une {@link PageResultat}.
 */
public record Pagination(int page, int taille) {

  /** Tailles de page proposées à l'écran (20 par défaut). */
  public static final List<Integer> TAILLES = List.of(10, 20, 50, 100);

  public static final int TAILLE_PAR_DEFAUT = 20;

  /** Pagination validée ; valeurs absentes = page 1, taille par défaut ; sinon VAL-001. */
  public static Pagination de(Integer page, Integer taille) {
    int p = page == null ? 1 : page;
    int t = taille == null ? TAILLE_PAR_DEFAUT : taille;
    if (p < 1) {
      throw invalide("page", "La page commence à 1.");
    }
    if (!TAILLES.contains(t)) {
      throw invalide("taille", "Taille de page autorisée : 10, 20, 50 ou 100.");
    }
    return new Pagination(p, t);
  }

  public int decalage() {
    return (page - 1) * taille;
  }

  private static ErreurMetier invalide(String champ, String message) {
    return new ErreurMetier(
        CodeErreur.VAL_001, Map.of("champs", List.of(Map.of("champ", champ, "message", message))));
  }
}
