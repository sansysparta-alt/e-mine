package gn.gov.mines.emine.catalogue;

import java.util.List;

/**
 * Document du catalogue (l'un des 15), avec ses tarifs en vigueur. API publique du module {@code
 * catalogue} : le module {@code demande} s'en sert pour le formulaire et le dépôt.
 *
 * @param id identifiant {@code type_document.id}
 * @param code code à 3 lettres (PEX, COR…)
 * @param libelleFr libellé de l'application (D2)
 * @param libelleEn libellé anglais, {@code null} s'il n'est pas encore renseigné
 * @param famille workflow du document (Titres miniers, Attestations…)
 * @param delaiJours délai réglementaire de traitement
 * @param piecesRequises pièces clés, découpées depuis {@code type_document.pieces_cles}
 * @param signataire signataire paramétré dans le catalogue (D4), libellé affiché
 * @param signataireProfil code du profil qui décide et signe ce document (D4, V9), par exemple
 *     {@code MINISTRE}
 * @param tarifs montants en vigueur, en GNF entiers
 * @param actif document proposé au dépôt ; un document retiré du catalogue (US-02.2) ne se dépose
 *     plus, mais ses demandes en cours vont à leur terme
 */
public record TypeDocument(
    short id,
    String code,
    String libelleFr,
    String libelleEn,
    Famille famille,
    int delaiJours,
    boolean renouvelable,
    List<String> piecesRequises,
    String instruitPar,
    String signataire,
    String signataireProfil,
    Tarifs tarifs,
    List<AvisRequis> avisRequis,
    boolean actif) {

  public TypeDocument {
    piecesRequises = List.copyOf(piecesRequises);
    avisRequis = List.copyOf(avisRequis);
  }

  /**
   * Avis technique motivé requis avant la décision (US-05.3, table {@code type_document_avis}).
   *
   * @param profilCode profil qui rend l'avis ({@code DNM}, {@code GEOLOGUE}, {@code INSPECTEUR})
   * @param libelle libellé du profil
   */
  public record AvisRequis(String profilCode, String libelle) {}

  /** Codes des profils dont l'avis est requis, dans l'ordre du catalogue. */
  public List<String> profilsAvis() {
    return avisRequis.stream().map(AvisRequis::profilCode).toList();
  }

  /** Workflow (famille) du document. */
  public record Famille(String code, String libelle) {}

  /**
   * Tarifs en vigueur (version active la plus récente, date d'effet passée), en GNF entiers ;
   * {@code null} si le document n'a pas ce tarif.
   */
  public record Tarifs(Long fraisDossierGnf, Long droitsGnf, Long attestationGnf) {

    /** Montant à payer au dépôt : frais de dossier, ou paiement unique d'une attestation. */
    public Long aPayerAuDepotGnf() {
      return fraisDossierGnf != null ? fraisDossierGnf : attestationGnf;
    }
  }
}
