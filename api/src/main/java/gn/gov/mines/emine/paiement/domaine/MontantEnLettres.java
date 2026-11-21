package gn.gov.mines.emine.paiement.domaine;

/**
 * Montant en toutes lettres pour le reçu (US-04.6) : « Cinq millions de francs guinéens ». Règles
 * de l'orthographe traditionnelle : « vingt et un », « soixante et onze », « quatre-vingts » mais «
 * quatre-vingt-un », « deux cents » mais « deux cent un » et « deux cent mille », « mille »
 * invariable, « millions de francs » quand rien ne suit.
 */
public final class MontantEnLettres {

  private static final String[] UNITES = {
    "zéro",
    "un",
    "deux",
    "trois",
    "quatre",
    "cinq",
    "six",
    "sept",
    "huit",
    "neuf",
    "dix",
    "onze",
    "douze",
    "treize",
    "quatorze",
    "quinze",
    "seize"
  };
  private static final String[] DIZAINES = {
    "", "dix", "vingt", "trente", "quarante", "cinquante", "soixante"
  };

  private MontantEnLettres() {}

  /** Montant en GNF entiers (1 à 999 999 999 999) suivi de « franc(s) guinéen(s) ». */
  public static String gnf(long montant) {
    if (montant <= 0 || montant > 999_999_999_999L) {
      throw new IllegalArgumentException("Montant hors limites : " + montant);
    }
    String lettres = nombre(montant);
    String devise = montant == 1 ? "franc guinéen" : "francs guinéens";
    boolean millionsRonds = montant >= 1_000_000 && montant % 1_000_000 == 0;
    String texte = lettres + (millionsRonds ? " de " : " ") + devise;
    return Character.toUpperCase(texte.charAt(0)) + texte.substring(1);
  }

  /** Nombre entier positif en lettres, sans majuscule. */
  static String nombre(long n) {
    if (n == 0) {
      return UNITES[0];
    }
    long milliards = n / 1_000_000_000L;
    long millions = (n / 1_000_000L) % 1_000;
    long milliers = (n / 1_000L) % 1_000;
    int reste = (int) (n % 1_000);
    StringBuilder sb = new StringBuilder();
    if (milliards > 0) {
      ajouter(sb, centaines((int) milliards, true) + (milliards > 1 ? " milliards" : " milliard"));
    }
    if (millions > 0) {
      ajouter(sb, centaines((int) millions, true) + (millions > 1 ? " millions" : " million"));
    }
    if (milliers > 0) {
      // « mille » invariable, jamais « un mille » ; « cent » et « vingt » sans s devant « mille ».
      ajouter(sb, milliers == 1 ? "mille" : centaines((int) milliers, false) + " mille");
    }
    if (reste > 0) {
      ajouter(sb, centaines(reste, true));
    }
    return sb.toString();
  }

  private static void ajouter(StringBuilder sb, String partie) {
    if (!sb.isEmpty()) {
      sb.append(' ');
    }
    sb.append(partie);
  }

  /**
   * Nombre de 1 à 999.
   *
   * @param pluriel accord de « cents » et « quatre-vingts » quand ils terminent le nombre (ou
   *     précèdent « millions », « milliards »), pas devant « mille »
   */
  private static String centaines(int n, boolean pluriel) {
    int c = n / 100;
    int r = n % 100;
    StringBuilder sb = new StringBuilder();
    if (c > 0) {
      sb.append(c == 1 ? "cent" : UNITES[c] + " cent");
      if (c > 1 && r == 0 && pluriel) {
        sb.append('s');
      }
    }
    if (r > 0) {
      if (!sb.isEmpty()) {
        sb.append(' ');
      }
      sb.append(dizaines(r, pluriel));
    }
    return sb.toString();
  }

  /** Nombre de 1 à 99. */
  private static String dizaines(int n, boolean pluriel) {
    if (n <= 16) {
      return UNITES[n];
    }
    if (n < 20) {
      return "dix-" + UNITES[n - 10];
    }
    int d = n / 10;
    int u = n % 10;
    if (d == 7 || d == 9) {
      // 70 à 79 : soixante-dix… ; 90 à 99 : quatre-vingt-dix…
      String base = d == 7 ? "soixante" : "quatre-vingt";
      int complement = 10 + u;
      if (d == 7 && u == 1) {
        return "soixante et onze";
      }
      return base + "-" + dizaines(complement, false);
    }
    if (d == 8) {
      return u == 0 ? (pluriel ? "quatre-vingts" : "quatre-vingt") : "quatre-vingt-" + UNITES[u];
    }
    if (u == 0) {
      return DIZAINES[d];
    }
    if (u == 1) {
      return DIZAINES[d] + " et un";
    }
    return DIZAINES[d] + "-" + UNITES[u];
  }
}
