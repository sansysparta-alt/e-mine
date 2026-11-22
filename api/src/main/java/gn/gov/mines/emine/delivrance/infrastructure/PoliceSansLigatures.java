package gn.gov.mines.emine.delivrance.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Police TrueType sans table de substitution de glyphes (GSUB), préparée en mémoire au chargement.
 * PDFBox applique les ligatures contextuelles de Carlito (« ti », « tt »…) par des glyphes sans
 * correspondance Unicode : le texte s'affiche bien mais ne se copie, ne se cherche ni ne se lit
 * plus (lecteurs d'écran, PDF/A). Sans GSUB, chaque lettre reste un glyphe. Le fichier d'origine
 * n'est pas modifié ; seul le sous-ensemble embarqué dans le PDF en est dérivé (licence OFL).
 */
public final class PoliceSansLigatures {

  private static final int ENTETE = 12;
  private static final int ENREGISTREMENT = 16;

  private PoliceSansLigatures() {}

  /** Police lue depuis le flux, sans table GSUB (inchangée si elle n'en a pas). */
  public static byte[] lire(InputStream flux) {
    try (flux) {
      return sansTable(flux.readAllBytes(), "GSUB");
    } catch (IOException e) {
      throw new UncheckedIOException("Lecture de la police impossible", e);
    }
  }

  /** Copie de la police TrueType sans la table {@code etiquette}. */
  static byte[] sansTable(byte[] police, String etiquette) {
    ByteBuffer source = ByteBuffer.wrap(police);
    int version = source.getInt(0);
    int nombre = Short.toUnsignedInt(source.getShort(4));
    record Table(int etiquette, int somme, byte[] donnees) {}
    List<Table> gardees = new ArrayList<>();
    int retiree =
        ByteBuffer.wrap(etiquette.getBytes(java.nio.charset.StandardCharsets.US_ASCII)).getInt();
    for (int i = 0; i < nombre; i++) {
      int base = ENTETE + i * ENREGISTREMENT;
      int tag = source.getInt(base);
      if (tag == retiree) {
        continue;
      }
      int somme = source.getInt(base + 4);
      int debut = source.getInt(base + 8);
      int longueur = source.getInt(base + 12);
      byte[] donnees = new byte[longueur];
      System.arraycopy(police, debut, donnees, 0, longueur);
      gardees.add(new Table(tag, somme, donnees));
    }
    if (gardees.size() == nombre) {
      return police;
    }
    int n = gardees.size();
    int puissance = Integer.highestOneBit(n);
    ByteBuffer entete = ByteBuffer.allocate(ENTETE + n * ENREGISTREMENT);
    entete.putInt(version);
    entete.putShort((short) n);
    entete.putShort((short) (puissance * 16));
    entete.putShort((short) Integer.numberOfTrailingZeros(puissance));
    entete.putShort((short) (n * 16 - puissance * 16));
    ByteArrayOutputStream corps = new ByteArrayOutputStream();
    int position = ENTETE + n * ENREGISTREMENT;
    for (Table t : gardees) {
      entete.putInt(t.etiquette()).putInt(t.somme()).putInt(position).putInt(t.donnees().length);
      corps.writeBytes(t.donnees());
      int remplissage = (4 - t.donnees().length % 4) % 4;
      corps.writeBytes(new byte[remplissage]);
      position += t.donnees().length + remplissage;
    }
    ByteArrayOutputStream resultat = new ByteArrayOutputStream(position);
    resultat.writeBytes(entete.array());
    resultat.writeBytes(corps.toByteArray());
    return resultat.toByteArray();
  }
}
