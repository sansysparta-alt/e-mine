package gn.gov.mines.emine.delivrance;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import gn.gov.mines.emine.delivrance.domaine.ContenuDocument;
import gn.gov.mines.emine.delivrance.infrastructure.GenerateurDocumentPdf;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * US-06.1 : le document officiel est un PDF/A-2b (police embarquée, profil de couleur, métadonnées
 * XMP) produit par le gabarit générique paramétré par le catalogue ; son QR mène à la page publique
 * de vérification.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GenerateurDocumentPdfTest {

  private static final String URL =
      "http://localhost:3000/verifier/0123456789abcdef0123456789abcdef";

  private byte[] pdf;

  @BeforeAll
  void generer() {
    pdf =
        new GenerateurDocumentPdf()
            .generer(
                new ContenuDocument(
                    "PEX-2026-0001",
                    "PEX",
                    "Permis d'exploitation",
                    "Fodé Bangoura",
                    "Horizon Invest SARL (fictive)",
                    "DEM-2026-000001",
                    Instant.parse("2026-11-21T10:00:00Z"),
                    "Respecter le plan de restauration.",
                    "Ministre",
                    "Ibrahima Soumah",
                    Instant.parse("2026-11-22T09:30:00Z"),
                    URL,
                    "0123456789abcdef0123456789abcdef"));
  }

  @Test
  void leDocumentEstDeclarePdfA2bAvecUnProfilDeCouleur() throws Exception {
    assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      byte[] xmp = doc.getDocumentCatalog().getMetadata().toByteArray();
      String metadonnees = new String(xmp, StandardCharsets.UTF_8);
      assertThat(metadonnees).containsPattern("pdfaid:part(=\"|>)2");
      assertThat(metadonnees).containsPattern("pdfaid:conformance(=\"|>)B");
      assertThat(doc.getDocumentCatalog().getOutputIntents()).isNotEmpty();
    }
  }

  @Test
  void toutesLesPolicesSontEmbarquees() throws Exception {
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      List<String> polices = new ArrayList<>();
      for (PDPage page : doc.getPages()) {
        PDResources ressources = page.getResources();
        for (COSName nom : ressources.getFontNames()) {
          PDFont police = ressources.getFont(nom);
          assertThat(police.isEmbedded()).as("police %s embarquée", police.getName()).isTrue();
          polices.add(police.getName());
        }
      }
      assertThat(polices).isNotEmpty().allMatch(n -> n.contains("Carlito"));
    }
  }

  @Test
  void leGabaritReprendLeCatalogueLeTitulaireEtLeSignataire() throws Exception {
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      String texte = new PDFTextStripper().getText(doc).replace(' ', ' ');
      assertThat(texte)
          .contains("RÉPUBLIQUE DE GUINÉE")
          .contains("Permis d'exploitation")
          .contains("PEX-2026-0001")
          .contains("Fodé Bangoura")
          .contains("Horizon Invest SARL (fictive)")
          .contains("DEM-2026-000001")
          .contains("Le Ministre")
          .contains("Ibrahima Soumah")
          .contains("21/11/2026")
          .contains("Respecter le plan de restauration.")
          .contains("sans valeur légale")
          // Ligatures contextuelles de Carlito (« ti », « tt ») neutralisées : texte copiable.
          .contains("démonstration")
          .contains("http://localhost:3000/verifier");
      assertThat(doc.getNumberOfPages()).isEqualTo(1);
    }
  }

  @Test
  void leQrMeneALaPagePubliqueDeVerification() throws Exception {
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      PDResources ressources = doc.getPage(0).getResources();
      String decode = null;
      for (COSName nom : ressources.getXObjectNames()) {
        if (ressources.getXObject(nom) instanceof PDImageXObject image) {
          decode = decoder(image.getImage());
        }
      }
      assertThat(decode).isEqualTo(URL);
    }
  }

  private static String decoder(BufferedImage image) throws Exception {
    int[] pixels =
        image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    RGBLuminanceSource source = new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels);
    return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText();
  }
}
