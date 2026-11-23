package gn.gov.mines.emine.catalogue.domaine;

import gn.gov.mines.emine.catalogue.domaine.VersionFiche.ContenuFiche;
import gn.gov.mines.emine.catalogue.domaine.VersionFiche.Profil;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Règles de la fiche d'un document (US-02.2) : forme des pièces, changements d'une version par
 * rapport à la fiche en vigueur (ancienne et nouvelle valeur, affichées au Ministre), et action
 * attendue de chaque profil sur une proposition.
 */
public final class RegleFiche {

  /** Nombre de pièces requises d'une fiche. */
  public static final int PIECES_MAX = 20;

  /** Longueur d'une pièce (la virgule sépare les pièces dans {@code pieces_cles}). */
  public static final int PIECE_MAX = 200;

  private RegleFiche() {}

  /**
   * Pièces saisies, nettoyées (espaces, lignes vides, doublons sans tenir compte de la casse) ;
   * vide si une pièce contient une virgule ou dépasse la longueur, ou si la liste est vide ou trop
   * longue.
   */
  public static Optional<List<String>> pieces(List<String> saisies) {
    LinkedHashSet<String> vues = new LinkedHashSet<>();
    List<String> pieces = new ArrayList<>();
    for (String s : saisies == null ? List.<String>of() : saisies) {
      String p = s == null ? "" : s.strip().replaceAll("\\s+", " ");
      if (p.isEmpty()) {
        continue;
      }
      if (p.contains(",") || p.length() < 2 || p.length() > PIECE_MAX) {
        return Optional.empty();
      }
      if (vues.add(p.toLowerCase(Locale.FRENCH))) {
        pieces.add(p);
      }
    }
    return pieces.isEmpty() || pieces.size() > PIECES_MAX
        ? Optional.empty()
        : Optional.of(List.copyOf(pieces));
  }

  /** Pièces au format de la base ({@code pieces_cles}) : séparées par une virgule. */
  public static String piecesCles(List<String> pieces) {
    return String.join(", ", pieces);
  }

  /**
   * Un champ modifié par une version.
   *
   * @param champ {@code libelleFr}, {@code libelleEn}, {@code pieces}, {@code delaiJours}, {@code
   *     instruitPar}, {@code signataire}, {@code avis}, {@code actif}, {@code fraisDossierGnf},
   *     {@code droitsGnf} ou {@code attestationGnf} (montants en GNF entiers)
   * @param avant valeur de la fiche de référence, en texte ({@code null} si vide)
   * @param apres valeur proposée, en texte ({@code null} si vide)
   */
  public record Changement(String champ, String avant, String apres) {}

  /** Changements de {@code apres} par rapport à {@code avant}, dans l'ordre de la fiche. */
  public static List<Changement> changements(ContenuFiche avant, ContenuFiche apres) {
    List<Changement> c = new ArrayList<>();
    ajouter(c, "libelleFr", avant.libelleFr(), apres.libelleFr());
    ajouter(c, "libelleEn", avant.libelleEn(), apres.libelleEn());
    ajouter(c, "pieces", String.join(" · ", avant.pieces()), String.join(" · ", apres.pieces()));
    ajouter(
        c, "delaiJours", String.valueOf(avant.delaiJours()), String.valueOf(apres.delaiJours()));
    ajouter(c, "instruitPar", avant.instruitPar(), apres.instruitPar());
    ajouter(c, "signataire", avant.signataire(), apres.signataire());
    ajouter(c, "avis", avis(avant.avis()), avis(apres.avis()));
    ajouter(c, "actif", String.valueOf(avant.actif()), String.valueOf(apres.actif()));
    ajouter(c, "fraisDossierGnf", texte(avant.fraisDossierGnf()), texte(apres.fraisDossierGnf()));
    ajouter(c, "droitsGnf", texte(avant.droitsGnf()), texte(apres.droitsGnf()));
    ajouter(c, "attestationGnf", texte(avant.attestationGnf()), texte(apres.attestationGnf()));
    return List.copyOf(c);
  }

  /** La version change-t-elle un montant du barème (US-02.3) ? */
  public static boolean changeTarifs(ContenuFiche avant, ContenuFiche apres) {
    return !Objects.equals(avant.fraisDossierGnf(), apres.fraisDossierGnf())
        || !Objects.equals(avant.droitsGnf(), apres.droitsGnf())
        || !Objects.equals(avant.attestationGnf(), apres.attestationGnf());
  }

  /**
   * Les objets payés pour un document (frais, droits, attestation) ne changent pas par une version
   * de fiche : ils décident du circuit de paiement.
   */
  public static boolean memesObjets(ContenuFiche avant, ContenuFiche apres) {
    return (avant.fraisDossierGnf() == null) == (apres.fraisDossierGnf() == null)
        && (avant.droitsGnf() == null) == (apres.droitsGnf() == null)
        && (avant.attestationGnf() == null) == (apres.attestationGnf() == null);
  }

  private static String texte(Long montant) {
    return montant == null ? null : String.valueOf(montant);
  }

  /** La version change-t-elle le signataire (valeur juridique des documents) ? */
  public static boolean changeSignataire(ContenuFiche avant, ContenuFiche apres) {
    return !Objects.equals(avant.signataire(), apres.signataire());
  }

  private static String avis(List<Profil> avis) {
    return avis.stream().map(Profil::libelle).collect(Collectors.joining(" · "));
  }

  private static void ajouter(List<Changement> c, String champ, String avant, String apres) {
    String a = vide(avant);
    String b = vide(apres);
    if (!Objects.equals(a, b)) {
      c.add(new Changement(champ, a, b));
    }
  }

  private static String vide(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }

  /** Action attendue du profil connecté sur une proposition ouverte. */
  public enum Action {
    /** Le Ministre doit approuver ou refuser (orange, « À approuver »). */
    DECIDER,
    /** L'administrateur attend la décision du Ministre ; il peut retirer sa proposition. */
    ATTENDRE,
    /** Rien à faire (lecture seule). */
    AUCUNE
  }

  /**
   * Action du profil connecté sur une proposition ouverte.
   *
   * @param peutValider droit « approuver le catalogue » (Ministre)
   * @param peutParametrer droit « paramétrer » (administrateur technique)
   */
  public static Action action(boolean peutValider, boolean peutParametrer) {
    if (peutValider) {
      return Action.DECIDER;
    }
    return peutParametrer ? Action.ATTENDRE : Action.AUCUNE;
  }
}
