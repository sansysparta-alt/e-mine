package gn.gov.mines.emine.delivrance.domaine;

import java.time.Instant;

/**
 * Port de signature électronique des documents délivrés (US-06.2, D5) : PAdES, remplaçable (pkcs12
 * sur le poste de développement, PKCS#11 vers SoftHSM2 puis HSM au plan complet). L'application ne
 * dépend que de ce contrat.
 */
public interface SignatureService {

  /**
   * Signe le PDF au nom du signataire.
   *
   * @throws SignatureIndisponible si le certificat ou le module de signature n'est pas utilisable
   */
  DocumentSigne signer(byte[] pdf, Signataire signataire);

  /**
   * Personne qui signe et contexte de la signature (dictionnaire de signature du PDF).
   *
   * @param nom prénom et nom du signataire
   * @param raison motif affiché par les lecteurs PDF
   * @param lieu lieu de signature
   */
  record Signataire(String nom, String raison, String lieu) {}

  /**
   * Résultat de la signature.
   *
   * @param pdf document signé
   * @param niveau niveau de signature, par exemple {@code PAdES-BASELINE-B}
   * @param certificat sujet du certificat de signature
   * @param empreinteCertificat empreinte SHA-256 du certificat, en hexadécimal
   * @param signeLe date de signature inscrite dans la signature
   */
  record DocumentSigne(
      byte[] pdf, String niveau, String certificat, String empreinteCertificat, Instant signeLe) {}

  /** Signature impossible : certificat absent, illisible ou mode non disponible. */
  final class SignatureIndisponible extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SignatureIndisponible(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
