package gn.gov.mines.emine.paiement.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import gn.gov.mines.emine.paiement.domaine.Recu;
import java.time.Instant;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/**
 * US-04.14 : un virement n'a que la date du relevé bancaire, le reçu l'affiche seule (plus de « à
 * 00:00 ») ; un paiement TrésorPay garde son heure. Chaque reçu porte son QR de vérification.
 */
class GenerateurRecuPdfTest {

  private final GenerateurRecuPdf generateur = new GenerateurRecuPdf();

  @Test
  void unVirementAfficheLaDateSeule() throws Exception {
    String texte = texte(recu(false, Instant.parse("2026-11-05T00:00:00Z")));
    assertThat(texte).contains("Payé le 05/11/2026").doesNotContain("05/11/2026 à");
  }

  @Test
  void unPaiementTresorPayGardeSonHeureDeConakry() throws Exception {
    // 10 h 30 UTC = 10 h 30 à Conakry (UTC+0, sans heure d'été).
    assertThat(texte(recu(true, Instant.parse("2026-11-05T10:30:00Z"))))
        .contains("Payé le 05/11/2026 à 10:30");
  }

  @Test
  void leRecuPorteLeQrEtLAdresseDeVerification() throws Exception {
    byte[] pdf = generateur.generer(recu(true, Instant.parse("2026-11-05T10:30:00Z")));
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      assertThat(doc.getPage(0).getResources().getXObjectNames()).isNotEmpty();
      assertThat(new PDFTextStripper().getText(doc).replaceAll("\\s+", ""))
          .contains("Vérifiercereçu")
          .contains("/verifier/recu/0123456789abcdef0123456789abcdef");
    }
  }

  private String texte(Recu recu) throws Exception {
    try (PDDocument doc = Loader.loadPDF(generateur.generer(recu))) {
      return new PDFTextStripper().getText(doc).replace(' ', ' ');
    }
  }

  private static Recu recu(boolean tresorPay, Instant payeLe) {
    return new Recu(
        "REC-2026-000001",
        "PAY-2026-000001",
        payeLe,
        5_000_000,
        "Cinq millions de francs guinéens",
        "Frais de dossier",
        tresorPay ? "TrésorPay" : "Virement bancaire",
        tresorPay ? "TP-0001" : null,
        "DEM-2026-000001",
        "Permis d’exploitation (PEX)",
        "Awa Test",
        null,
        tresorPay,
        "http://localhost:3000/verifier/recu/0123456789abcdef0123456789abcdef");
  }
}
