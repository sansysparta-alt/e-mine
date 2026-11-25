package gn.gov.mines.emine.delivrance.domaine;

import java.time.Instant;

/**
 * Document inscrit au registre {@code document_delivre} (US-06.3).
 *
 * @param numero {@code <CODE>-AAAA-NNNN}
 * @param valide statut du registre ({@code valide} / {@code non_valide})
 * @param motifInvalidation motif de la révocation, ou {@code null}
 * @param revoqueLe date de la révocation (posée par la base), ou {@code null}
 * @param revoqueAutorite autorité qui a révoqué (« Directeur national »…), ou {@code null}
 * @param revoqueAutoriteEn la même en anglais
 * @param empreinteSha256 empreinte du PDF signé, en hexadécimal
 * @param codeVerification code du QR (page publique {@code /verifier/{code}})
 * @param signataire prénom et nom de la personne qui a signé
 * @param signature niveau et certificat de signature
 */
public record DocumentDelivre(
    long id,
    String numero,
    String codeType,
    Long demandeId,
    boolean valide,
    String motifInvalidation,
    Instant revoqueLe,
    String revoqueAutorite,
    String revoqueAutoriteEn,
    Instant delivreLe,
    String empreinteSha256,
    String codeVerification,
    String signataire,
    String signature) {

  /** Clé de l'objet PDF dans le compartiment des documents délivrés. */
  public String cleStockage() {
    return cleStockage(numero);
  }

  public static String cleStockage(String numero) {
    return "documents/" + numero + ".pdf";
  }
}
