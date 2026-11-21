package gn.gov.mines.emine.demande.domaine;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Règles des pièces jointes (US-03.2) : PDF, JPG ou PNG reconnus par leur signature (le type
 * annoncé par le navigateur n'est pas fiable), 10 Mo au plus, empreinte SHA-256.
 */
public final class ReglesPieces {

  /** Taille maximale d'une pièce : 10 Mo (contrainte de {@code piece_jointe.taille_octets}). */
  public static final long TAILLE_MAX_OCTETS = 10L * 1024 * 1024;

  /** Longueur maximale du nom de fichier conservé. */
  static final int NOM_MAX = 200;

  private ReglesPieces() {}

  /** Type MIME déduit des premiers octets, s'il fait partie des formats acceptés. */
  public static Optional<String> formatReconnu(byte[] contenu) {
    if (commencePar(contenu, 0x25, 0x50, 0x44, 0x46, 0x2D)) { // %PDF-
      return Optional.of("application/pdf");
    }
    if (commencePar(contenu, 0xFF, 0xD8, 0xFF)) {
      return Optional.of("image/jpeg");
    }
    if (commencePar(contenu, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
      return Optional.of("image/png");
    }
    return Optional.empty();
  }

  public static String empreinteSha256(byte[] contenu) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 indisponible", e);
    }
  }

  /**
   * Nom de fichier affichable : sans chemin ni caractère de contrôle, tronqué à 200 caractères ; «
   * piece » s'il ne reste rien.
   */
  public static String nomFichier(String original) {
    String nom = original == null ? "" : original;
    nom = nom.substring(Math.max(nom.lastIndexOf('/'), nom.lastIndexOf('\\')) + 1);
    nom = nom.replaceAll("\\p{Cntrl}", "").strip();
    if (nom.length() > NOM_MAX) {
      nom = nom.substring(nom.length() - NOM_MAX);
    }
    return nom.isEmpty() ? "piece" : nom;
  }

  private static boolean commencePar(byte[] contenu, int... signature) {
    if (contenu.length < signature.length) {
      return false;
    }
    for (int i = 0; i < signature.length; i++) {
      if ((contenu[i] & 0xFF) != signature[i]) {
        return false;
      }
    }
    return true;
  }
}
