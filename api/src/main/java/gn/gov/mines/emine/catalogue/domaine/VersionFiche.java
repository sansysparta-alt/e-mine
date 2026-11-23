package gn.gov.mines.emine.catalogue.domaine;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Une version de la fiche d'un document du catalogue (US-02.2, V18) : proposée par
 * l'administrateur, approuvée ou refusée par le Ministre.
 *
 * @param numero numéro de version (v1, v2…), attribué par la base
 * @param statut {@code a_valider}, {@code publiee}, {@code remplacee}, {@code refusee} ou {@code
 *     retiree}
 * @param motif objet de la modification (motif de la désactivation le cas échéant)
 * @param proposeePar nom de l'administrateur, {@code null} pour la version initiale
 * @param decideePar nom de la personne qui a approuvé, refusé ou retiré
 * @param tarifsDateEffet entrée en vigueur des montants proposés (US-02.3), {@code null} si la
 *     version ne change pas le barème
 */
public record VersionFiche(
    int numero,
    String statut,
    ContenuFiche contenu,
    String motif,
    String proposeePar,
    Instant proposeeLe,
    String decideePar,
    Instant decideeLe,
    String motifRefus,
    LocalDate tarifsDateEffet) {

  public static final String A_VALIDER = "a_valider";
  public static final String PUBLIEE = "publiee";
  public static final String REMPLACEE = "remplacee";
  public static final String REFUSEE = "refusee";
  public static final String RETIREE = "retiree";

  /** Versions publiées une fois (en vigueur ou remplacées) : l'historique de la fiche. */
  public boolean aEteEnVigueur() {
    return PUBLIEE.equals(statut) || REMPLACEE.equals(statut);
  }

  /**
   * Contenu d'une fiche.
   *
   * @param pieces pièces requises, dans l'ordre
   * @param signataire titre du signataire (D4) : Ministre, Directeur général…
   * @param avis profils qui rendent un avis technique avant la décision, dans l'ordre
   * @param actif document proposé au dépôt
   * @param fraisDossierGnf barème en GNF entiers (US-02.3), {@code null} si le document n'a pas cet
   *     objet de paiement ; de même pour les droits et l'attestation
   */
  public record ContenuFiche(
      String libelleFr,
      String libelleEn,
      List<String> pieces,
      int delaiJours,
      String instruitPar,
      String signataire,
      List<Profil> avis,
      boolean actif,
      Long fraisDossierGnf,
      Long droitsGnf,
      Long attestationGnf) {

    public ContenuFiche {
      pieces = List.copyOf(pieces);
      avis = List.copyOf(avis);
    }
  }

  /** Profil désigné par son code et son libellé. */
  public record Profil(String code, String libelle) {}
}
