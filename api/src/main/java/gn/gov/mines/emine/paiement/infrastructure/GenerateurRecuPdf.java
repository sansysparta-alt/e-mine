package gn.gov.mines.emine.paiement.infrastructure;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import gn.gov.mines.emine.paiement.domaine.Recu;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Reçu PDF (US-04.6) : gabarit Thymeleaf {@code gabarits/recu.html} rendu par OpenHTMLtoPDF (stack
 * {@code docs/02}). Formats français, heure de Conakry ; polices standard du PDF (aucun fichier de
 * police embarqué en V1), d'où des espaces insécables simples entre milliers. Le gabarit est en
 * XHTML bien formé et sans doctype (OpenHTMLtoPDF l'analyse en XML ; Prettier écrirait le doctype
 * en minuscules, refusé par l'analyseur XML).
 */
@Component
public class GenerateurRecuPdf {

  private static final ZoneId CONAKRY = ZoneId.of("Africa/Conakry");
  private static final DateTimeFormatter DATE_HEURE =
      DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRENCH).withZone(CONAKRY);

  private final TemplateEngine moteur;

  public GenerateurRecuPdf() {
    ClassLoaderTemplateResolver resolveur = new ClassLoaderTemplateResolver();
    resolveur.setPrefix("gabarits/");
    resolveur.setSuffix(".html");
    resolveur.setTemplateMode(TemplateMode.HTML);
    resolveur.setCharacterEncoding("UTF-8");
    resolveur.setCacheable(true);
    moteur = new TemplateEngine();
    moteur.setTemplateResolver(resolveur);
  }

  public byte[] generer(Recu recu) {
    Context contexte = new Context(Locale.FRENCH);
    contexte.setVariable("recu", recu);
    contexte.setVariable("montant", gnf(recu.montantGnf()));
    contexte.setVariable("datePaiement", DATE_HEURE.format(recu.datePaiement()));
    String html = moteur.process("recu", contexte);
    try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
      PdfRendererBuilder pdf = new PdfRendererBuilder();
      pdf.useFastMode();
      pdf.withHtmlContent(html, null);
      pdf.withProducer("e-mine");
      pdf.toStream(sortie);
      pdf.run();
      return sortie.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Génération du reçu impossible", e);
    }
  }

  /** {@code 5000000} → {@code 5 000 000 GNF} (espaces insécables). */
  static String gnf(long montant) {
    String chiffres = Long.toString(montant);
    StringBuilder sb = new StringBuilder();
    int premier = chiffres.length() % 3;
    for (int i = 0; i < chiffres.length(); i++) {
      if (i > 0 && (i - premier) % 3 == 0) {
        sb.append(' ');
      }
      sb.append(chiffres.charAt(i));
    }
    return sb + " GNF";
  }
}
