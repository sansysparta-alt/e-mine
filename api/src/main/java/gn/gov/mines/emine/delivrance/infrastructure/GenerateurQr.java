package gn.gov.mines.emine.delivrance.infrastructure;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * QR du document délivré (US-06.1/06.3, ZXing) : image PNG noir et blanc, sans transparence
 * (compatible PDF/A), correction d'erreur moyenne pour rester lisible après impression.
 */
public final class GenerateurQr {

  private GenerateurQr() {}

  /** PNG carré de {@code taille} pixels encodant {@code texte}. */
  public static byte[] png(String texte, int taille) {
    try {
      BitMatrix matrice =
          new QRCodeWriter()
              .encode(
                  texte,
                  BarcodeFormat.QR_CODE,
                  taille,
                  taille,
                  Map.of(
                      EncodeHintType.ERROR_CORRECTION,
                      ErrorCorrectionLevel.M,
                      EncodeHintType.MARGIN,
                      1,
                      EncodeHintType.CHARACTER_SET,
                      "UTF-8"));
      BufferedImage image =
          new BufferedImage(
              matrice.getWidth(), matrice.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
      for (int x = 0; x < matrice.getWidth(); x++) {
        for (int y = 0; y < matrice.getHeight(); y++) {
          image.setRGB(x, y, matrice.get(x, y) ? 0x000000 : 0xFFFFFF);
        }
      }
      try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
        ImageIO.write(image, "png", sortie);
        return sortie.toByteArray();
      }
    } catch (WriterException e) {
      throw new IllegalArgumentException("QR impossible pour ce texte", e);
    } catch (IOException e) {
      throw new UncheckedIOException("Écriture du QR impossible", e);
    }
  }
}
