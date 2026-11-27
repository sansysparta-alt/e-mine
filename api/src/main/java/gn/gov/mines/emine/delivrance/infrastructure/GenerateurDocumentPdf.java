package gn.gov.mines.emine.delivrance.infrastructure;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder.PdfAConformance;
import gn.gov.mines.emine.delivrance.domaine.ContenuDocument;
import gn.gov.mines.emine.shared.qr.GenerateurQr;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Document officiel délivré (US-06.1) : gabarit générique Thymeleaf {@code gabarits/document.html}
 * paramétré par le catalogue, rendu en <b>PDF/A-2b</b> par OpenHTMLtoPDF : police Carlito (licence
 * OFL) embarquée sans ligatures contextuelles (texte copiable et lisible par les lecteurs d'écran),
 * profil de couleur sRVB, métadonnées XMP. Le QR mène à la page publique de vérification. Le PDF
 * est ensuite signé (US-06.2) sans perdre sa conformité.
 */
@Component
public class GenerateurDocumentPdf {

  private static final ZoneId CONAKRY = ZoneId.of("Africa/Conakry");
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH).withZone(CONAKRY);
  private static final DateTimeFormatter DATE_HEURE =
      DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRENCH).withZone(CONAKRY);

  /** Famille de police du gabarit, embarquée dans le PDF (exigence PDF/A). */
  static final String POLICE = "Carlito";

  private static final int TAILLE_QR = 300;

  private final TemplateEngine moteur;
  private final byte[] carlito = PoliceSansLigatures.lire(police("Carlito-Regular.ttf"));
  private final byte[] carlitoGras = PoliceSansLigatures.lire(police("Carlito-Bold.ttf"));
  private final byte[] profilSrvb = ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData();

  public GenerateurDocumentPdf() {
    ClassLoaderTemplateResolver resolveur = new ClassLoaderTemplateResolver();
    resolveur.setPrefix("gabarits/");
    resolveur.setSuffix(".html");
    resolveur.setTemplateMode(TemplateMode.HTML);
    resolveur.setCharacterEncoding("UTF-8");
    resolveur.setCacheable(true);
    moteur = new TemplateEngine();
    moteur.setTemplateResolver(resolveur);
  }

  public byte[] generer(ContenuDocument document) {
    Context contexte = new Context(Locale.FRENCH);
    contexte.setVariable("d", document);
    contexte.setVariable("decisionLe", DATE.format(document.decisionLe()));
    contexte.setVariable("delivreLe", DATE_HEURE.format(document.delivreLe()));
    contexte.setVariable(
        "qr",
        "data:image/png;base64,"
            + Base64.getEncoder()
                .encodeToString(GenerateurQr.png(document.urlVerification(), TAILLE_QR)));
    String html = moteur.process("document", contexte);
    try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
      PdfRendererBuilder pdf = new PdfRendererBuilder();
      pdf.useFastMode();
      pdf.usePdfAConformance(PdfAConformance.PDFA_2_B);
      pdf.useColorProfile(profilSrvb);
      pdf.useFont(() -> new ByteArrayInputStream(carlito), POLICE, 400, FontStyle.NORMAL, true);
      pdf.useFont(() -> new ByteArrayInputStream(carlitoGras), POLICE, 700, FontStyle.NORMAL, true);
      pdf.withHtmlContent(html, null);
      pdf.withProducer("e-mine");
      pdf.toStream(sortie);
      pdf.run();
      return sortie.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Génération du document impossible", e);
    }
  }

  private static InputStream police(String fichier) {
    InputStream flux = GenerateurDocumentPdf.class.getResourceAsStream("/polices/" + fichier);
    if (flux == null) {
      throw new IllegalStateException("Police absente des ressources : " + fichier);
    }
    return flux;
  }
}
