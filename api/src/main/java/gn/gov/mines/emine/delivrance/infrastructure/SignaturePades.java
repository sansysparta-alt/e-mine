package gn.gov.mines.emine.delivrance.infrastructure;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.Pkcs12SignatureToken;
import gn.gov.mines.emine.delivrance.domaine.SignatureService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Signature PAdES des documents délivrés avec EU DSS (US-06.2). V1 (décision du porteur du projet)
 * : niveau <b>PAdES-BASELINE-B</b>, sans service d'horodatage externe ; l'heure de signature est
 * celle du serveur, aussi enregistrée au registre. Mode {@code pkcs12} : certificat de test
 * auto-signé généré par {@code pnpm signature:certificat}, exclu de Git. Le certificat est relu à
 * chaque signature (rien n'est gardé en mémoire) ; son absence n'empêche pas l'API de démarrer.
 */
@Component
@EnableConfigurationProperties(ProprietesSignature.class)
public class SignaturePades implements SignatureService {

  static final SignatureLevel NIVEAU = SignatureLevel.PAdES_BASELINE_B;

  private final ProprietesSignature proprietes;
  private final Clock horloge;

  public SignaturePades(ProprietesSignature proprietes, Clock horloge) {
    this.proprietes = proprietes;
    this.horloge = horloge;
  }

  @Override
  public DocumentSigne signer(byte[] pdf, Signataire signataire) {
    if (!"pkcs12".equals(proprietes.mode())) {
      throw new SignatureIndisponible(
          "Mode de signature « " + proprietes.mode() + " » non disponible en V1 (pkcs12 seulement)",
          null);
    }
    Path chemin = certificat();
    String motDePasse = proprietes.pkcs12MotDePasse() == null ? "" : proprietes.pkcs12MotDePasse();
    try (Pkcs12SignatureToken jeton =
        new Pkcs12SignatureToken(
            Files.newInputStream(chemin),
            new KeyStore.PasswordProtection(motDePasse.toCharArray()))) {
      DSSPrivateKeyEntry cle = jeton.getKeys().getFirst();
      Instant maintenant = horloge.instant();
      PAdESSignatureParameters parametres = new PAdESSignatureParameters();
      parametres.setSignatureLevel(NIVEAU);
      parametres.setDigestAlgorithm(DigestAlgorithm.SHA256);
      parametres.setSigningCertificate(cle.getCertificate());
      parametres.setCertificateChain(cle.getCertificateChain());
      parametres.bLevel().setSigningDate(Date.from(maintenant));
      parametres.setSignerName(signataire.nom());
      parametres.setReason(signataire.raison());
      parametres.setLocation(signataire.lieu() == null ? proprietes.lieu() : signataire.lieu());

      PAdESService service = new PAdESService(new CommonCertificateVerifier());
      DSSDocument document = new InMemoryDocument(pdf);
      ToBeSigned aSigner = service.getDataToSign(document, parametres);
      SignatureValue valeur = jeton.sign(aSigner, parametres.getDigestAlgorithm(), cle);
      DSSDocument signe = service.signDocument(document, parametres, valeur);
      CertificateToken certificat = cle.getCertificate();
      return new DocumentSigne(
          DSSUtils.toByteArray(signe),
          "PAdES-BASELINE-B",
          certificat.getSubject().getRFC2253(),
          HexFormat.of().formatHex(certificat.getDigest(DigestAlgorithm.SHA256)),
          maintenant);
    } catch (SignatureIndisponible e) {
      throw e;
    } catch (Exception e) {
      throw new SignatureIndisponible("Certificat de signature illisible : " + chemin, e);
    }
  }

  /**
   * Fichier du certificat : chemin absolu, ou relatif au dossier courant, ou à son parent (l'API
   * est lancée depuis {@code api/} alors que {@code .env} donne un chemin depuis la racine).
   */
  Path certificat() {
    String valeur = proprietes.pkcs12Chemin();
    if (valeur == null || valeur.isBlank()) {
      throw new SignatureIndisponible("SIGNATURE_PKCS12_PATH n'est pas renseigné", null);
    }
    Path chemin = Path.of(valeur);
    if (!chemin.isAbsolute() && !Files.isRegularFile(chemin)) {
      Path parent = Path.of("..").resolve(valeur).normalize();
      if (Files.isRegularFile(parent)) {
        return parent;
      }
    }
    if (!Files.isRegularFile(chemin)) {
      throw new SignatureIndisponible(
          "Certificat de signature introuvable (" + valeur + ") : lancez pnpm signature:certificat",
          null);
    }
    return chemin;
  }
}
