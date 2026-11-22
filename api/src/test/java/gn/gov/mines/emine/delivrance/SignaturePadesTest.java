package gn.gov.mines.emine.delivrance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.europa.esig.dss.diagnostic.DiagnosticData;
import eu.europa.esig.dss.diagnostic.SignatureWrapper;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import gn.gov.mines.emine.delivrance.domaine.ContenuDocument;
import gn.gov.mines.emine.delivrance.domaine.SignatureService.DocumentSigne;
import gn.gov.mines.emine.delivrance.domaine.SignatureService.Signataire;
import gn.gov.mines.emine.delivrance.domaine.SignatureService.SignatureIndisponible;
import gn.gov.mines.emine.delivrance.infrastructure.GenerateurDocumentPdf;
import gn.gov.mines.emine.delivrance.infrastructure.ProprietesSignature;
import gn.gov.mines.emine.delivrance.infrastructure.SignaturePades;
import gn.gov.mines.emine.support.CertificatDeTest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * US-06.2 : le document délivré est signé en PAdES-BASELINE-B (EU DSS) avec le certificat de test,
 * par le port de signature ; la signature est intègre et vérifiable par un validateur standard,
 * toute modification la casse, et le PDF reste déclaré PDF/A.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SignaturePadesTest {

  private static final Instant MAINTENANT = Instant.parse("2026-11-22T09:30:00Z");

  private DocumentSigne signe;

  @BeforeAll
  void signer() {
    byte[] pdf =
        new GenerateurDocumentPdf()
            .generer(
                new ContenuDocument(
                    "CAR-2026-0001",
                    "CAR",
                    "Autorisation de carrière",
                    "Fodé Bangoura",
                    null,
                    "DEM-2026-000002",
                    MAINTENANT,
                    null,
                    "Directeur national",
                    "Sékou Kouyaté",
                    MAINTENANT,
                    "http://localhost:3000/verifier/abc",
                    "abc"));
    signe =
        signataire(CertificatDeTest.chemin().toString(), "pkcs12")
            .signer(
                pdf, new Signataire("Sékou Kouyaté", "Délivrance du document CAR-2026-0001", null));
  }

  @Test
  void laSignatureEstPadesBaselineBIntegreEtCryptographiquementValide() {
    SignatureWrapper signature = valider(signe.pdf());
    assertThat(signature.getSignatureFormat()).isEqualTo(SignatureLevel.PAdES_BASELINE_B);
    assertThat(signature.isSignatureIntact()).as("intégrité").isTrue();
    assertThat(signature.isSignatureValid()).as("valeur cryptographique").isTrue();
    assertThat(signe.niveau()).isEqualTo("PAdES-BASELINE-B");
    assertThat(signe.signeLe()).isEqualTo(MAINTENANT);
    assertThat(signe.certificat()).contains("CN=e-mine TESTS - sans valeur");
    assertThat(signe.empreinteCertificat()).matches("[0-9a-f]{64}");
  }

  @Test
  void leDictionnaireDeSignatureNommeLeSignataireEtLeMotif() throws Exception {
    try (PDDocument doc = Loader.loadPDF(signe.pdf())) {
      PDSignature signature = doc.getLastSignatureDictionary();
      assertThat(signature.getName()).isEqualTo("Sékou Kouyaté");
      assertThat(signature.getReason()).isEqualTo("Délivrance du document CAR-2026-0001");
      assertThat(signature.getLocation()).isEqualTo("Conakry");
      String xmp =
          new String(doc.getDocumentCatalog().getMetadata().toByteArray(), StandardCharsets.UTF_8);
      assertThat(xmp).as("toujours déclaré PDF/A après signature").contains("pdfaid");
    }
  }

  @Test
  void toutOctetModifieCasseLaSignature() {
    byte[] modifie = signe.pdf().clone();
    // Octets du commentaire binaire d'en-tête : couverts par la signature, sans casser la lecture.
    modifie[11] = (byte) (modifie[11] ^ 0x01);
    SignatureWrapper signature = valider(modifie);
    assertThat(signature.isSignatureIntact()).isFalse();
  }

  @Test
  void sansCertificatOuEnModePkcs11LaSignatureEstIndisponible() {
    byte[] pdf = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
    Signataire qui = new Signataire("X", "Y", null);
    assertThatThrownBy(() -> signataire("introuvable.p12", "pkcs12").signer(pdf, qui))
        .isInstanceOf(SignatureIndisponible.class)
        .hasMessageContaining("pnpm signature:certificat");
    assertThatThrownBy(
            () -> signataire(CertificatDeTest.chemin().toString(), "pkcs11").signer(pdf, qui))
        .isInstanceOf(SignatureIndisponible.class)
        .hasMessageContaining("pkcs11");
  }

  private static SignaturePades signataire(String chemin, String mode) {
    return new SignaturePades(
        new ProprietesSignature(mode, chemin, CertificatDeTest.MOT_DE_PASSE, "Conakry"),
        Clock.fixed(MAINTENANT, ZoneOffset.UTC));
  }

  private static SignatureWrapper valider(byte[] pdf) {
    SignedDocumentValidator validateur =
        SignedDocumentValidator.fromDocument(new InMemoryDocument(pdf));
    validateur.setCertificateVerifier(new CommonCertificateVerifier());
    DiagnosticData diagnostic = validateur.validateDocument().getDiagnosticData();
    assertThat(diagnostic.getSignatures()).hasSize(1);
    return diagnostic.getSignatures().getFirst();
  }
}
