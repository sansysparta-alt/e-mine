package gn.gov.mines.emine.paiement.domaine;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Lecture d'un relevé bancaire CSV (US-04.8), selon le contrat {@code docs/contrats/releve-csv.md}
 * : UTF-8, séparateur « ; », en-tête {@code date_operation;libelle;montant_gnf;sens}, montant
 * entier en GNF (espaces de milliers admis), sens C ou D. Tout ou rien : la moindre ligne fautive
 * fait refuser le fichier, en citant les lignes concernées (10 au plus).
 */
public final class LectureReleveCsv {

  /** Taille maximale d'un relevé (2 Mo). */
  public static final int TAILLE_MAX_OCTETS = 2 * 1024 * 1024;

  /** Nombre maximal de lignes d'opération. */
  public static final int LIGNES_MAX = 5_000;

  /** Erreurs citées au plus dans un refus. */
  static final int ERREURS_CITEES = 10;

  private static final List<String> EN_TETE =
      List.of("date_operation", "libelle", "montant_gnf", "sens");
  private static final DateTimeFormatter JJ_MM_AAAA =
      DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
  private static final DateTimeFormatter AAAA_MM_JJ =
      DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
  private static final Pattern MONTANT = Pattern.compile("[0-9]{1,15}");

  private LectureReleveCsv() {}

  /** Ligne d'opération lue (numéro = rang dans le fichier, en-tête compris). */
  public record LigneLue(int numero, LocalDate date, String libelle, long montantGnf, char sens) {}

  /** Problème de format, sur une ligne du fichier ({@code 0} = le fichier entier). */
  public record Probleme(int ligne, String message) {}

  /** Résultat : les lignes lues, ou les problèmes (au plus {@value #ERREURS_CITEES}). */
  public record Resultat(List<LigneLue> lignes, List<Probleme> problemes) {
    public boolean valide() {
      return problemes.isEmpty();
    }
  }

  public static Resultat lire(byte[] contenu) {
    String texte;
    try {
      texte =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(java.nio.ByteBuffer.wrap(contenu))
              .toString();
    } catch (CharacterCodingException e) {
      return refus(0, "Le fichier n'est pas encodé en UTF-8.");
    }
    if (texte.startsWith("﻿")) {
      texte = texte.substring(1);
    }
    String[] lignes = texte.split("\\r?\\n", -1);
    if (lignes.length == 0 || lignes[0].isBlank()) {
      return refus(0, "Le fichier est vide.");
    }
    List<String> entete = colonnes(lignes[0]).stream().map(LectureReleveCsv::cle).toList();
    if (!entete.equals(EN_TETE)) {
      return refus(1, "En-tête attendu : date_operation;libelle;montant_gnf;sens.");
    }
    List<LigneLue> lues = new ArrayList<>();
    List<Probleme> problemes = new ArrayList<>();
    for (int i = 1; i < lignes.length; i++) {
      String brute = lignes[i];
      if (brute.isBlank()) {
        continue;
      }
      int numero = i + 1;
      if (lues.size() + problemes.size() >= LIGNES_MAX) {
        return refus(0, "Le relevé dépasse " + LIGNES_MAX + " lignes.");
      }
      List<String> c = colonnes(brute);
      if (c.size() != 4) {
        problemes.add(new Probleme(numero, "4 colonnes attendues, " + c.size() + " trouvées."));
        continue;
      }
      LocalDate date = date(c.get(0));
      String libelle = c.get(1).strip();
      String montantBrut = c.get(2).replaceAll("[\\s\\u00A0\\u202F]", "");
      String sens = c.get(3).strip().toUpperCase(Locale.ROOT);
      if (date == null) {
        problemes.add(new Probleme(numero, "Date invalide (JJ/MM/AAAA ou AAAA-MM-JJ)."));
      } else if (libelle.isEmpty()) {
        problemes.add(new Probleme(numero, "Libellé vide."));
      } else if (!MONTANT.matcher(montantBrut).matches() || Long.parseLong(montantBrut) <= 0) {
        problemes.add(new Probleme(numero, "Montant invalide : entier positif en GNF attendu."));
      } else if (!sens.equals("C") && !sens.equals("D")) {
        problemes.add(new Probleme(numero, "Sens invalide : C (crédit) ou D (débit) attendu."));
      } else {
        lues.add(
            new LigneLue(
                numero,
                date,
                libelle.length() > 500 ? libelle.substring(0, 500) : libelle,
                Long.parseLong(montantBrut),
                sens.charAt(0)));
      }
      if (problemes.size() >= ERREURS_CITEES) {
        break;
      }
    }
    if (!problemes.isEmpty()) {
      return new Resultat(List.of(), List.copyOf(problemes));
    }
    if (lues.isEmpty()) {
      return refus(0, "Le relevé ne contient aucune opération.");
    }
    return new Resultat(List.copyOf(lues), List.of());
  }

  private static Resultat refus(int ligne, String message) {
    return new Resultat(List.of(), List.of(new Probleme(ligne, message)));
  }

  /** Colonnes d'une ligne : séparateur « ; », guillemets doubles facultatifs (« "" » échappé). */
  static List<String> colonnes(String ligne) {
    List<String> c = new ArrayList<>();
    StringBuilder courante = new StringBuilder();
    boolean entreGuillemets = false;
    for (int i = 0; i < ligne.length(); i++) {
      char ch = ligne.charAt(i);
      if (ch == '"') {
        if (entreGuillemets && i + 1 < ligne.length() && ligne.charAt(i + 1) == '"') {
          courante.append('"');
          i++;
        } else {
          entreGuillemets = !entreGuillemets;
        }
      } else if (ch == ';' && !entreGuillemets) {
        c.add(courante.toString());
        courante.setLength(0);
      } else {
        courante.append(ch);
      }
    }
    c.add(courante.toString());
    return c;
  }

  /** Nom de colonne comparable : minuscules, sans accents ni espaces autour. */
  private static String cle(String nom) {
    return Normalizer.normalize(nom.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "");
  }

  private static LocalDate date(String valeur) {
    String v = valeur.strip();
    try {
      return v.contains("/") ? LocalDate.parse(v, JJ_MM_AAAA) : LocalDate.parse(v, AAAA_MM_JJ);
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
