package gn.gov.mines.emine.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Certificat de signature auto-signé des tests (US-06.2), généré une fois par exécution avec le
 * {@code keytool} du JDK dans un dossier temporaire : rien n'est versionné. Mot de passe fictif.
 */
public final class CertificatDeTest {

  /** Mot de passe fictif du certificat des tests. */
  public static final String MOT_DE_PASSE = "certificat-tests-fictif";

  private static Path fichier;

  private CertificatDeTest() {}

  /** Chemin du certificat PKCS#12 de test (créé au premier appel). */
  public static synchronized Path chemin() {
    if (fichier == null) {
      try {
        Path dossier = Files.createTempDirectory("emine-signature-");
        Path cible = dossier.resolve("certificat-test.p12");
        Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
        Process p =
            new ProcessBuilder(
                    List.of(
                        keytool.toString(),
                        "-genkeypair",
                        "-alias",
                        "emine-demo",
                        "-keyalg",
                        "RSA",
                        "-keysize",
                        "2048",
                        "-sigalg",
                        "SHA256withRSA",
                        // Valide depuis la veille : une signature datée de « maintenant » ne
                        // précède
                        // jamais le début de validité, quelle que soit l'heure d'exécution.
                        "-startdate",
                        "-1d",
                        "-validity",
                        "30",
                        "-dname",
                        "CN=e-mine TESTS - sans valeur, O=Plateforme e-mine (tests), C=GN",
                        "-ext",
                        "KU=digitalSignature,nonRepudiation",
                        "-storetype",
                        "PKCS12",
                        "-keystore",
                        cible.toString(),
                        "-storepass",
                        MOT_DE_PASSE,
                        "-keypass",
                        MOT_DE_PASSE))
                .redirectErrorStream(true)
                .start();
        if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0) {
          throw new IllegalStateException(
              "keytool a échoué : " + new String(p.getInputStream().readAllBytes()));
        }
        fichier = cible;
      } catch (IOException e) {
        throw new IllegalStateException("Certificat de test impossible", e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
    return fichier;
  }
}
