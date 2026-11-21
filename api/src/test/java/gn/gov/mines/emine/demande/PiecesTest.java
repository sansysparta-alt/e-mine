package gn.gov.mines.emine.demande;

import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR_2;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.demande.domaine.ReglesPieces;
import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-03.2 : l'investisseur joint des pièces PDF, JPG ou PNG de 10 Mo au plus à son brouillon ; le
 * format est vérifié par la signature du fichier ; l'empreinte SHA-256 est enregistrée ; le fichier
 * est stocké dans MinIO (vrai conteneur) et relu à l'identique ; un autre investisseur n'y accède
 * pas.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PiecesTest extends TestIntegration {

  /** Même image que compose.yaml (MinIO reconstruit depuis les sources, épinglé par empreinte). */
  @SuppressWarnings("resource")
  static final GenericContainer<?> MINIO =
      new GenericContainer<>(
              "cgr.dev/chainguard/minio@sha256:e9cf6b99c226e9539ed0f14b6f00e37b07da153c7ba1f4f8709c807574b471ec")
          .withCommand("server", "/data")
          .withEnv("MINIO_ROOT_USER", "minio-test")
          .withEnv("MINIO_ROOT_PASSWORD", "minio-test-" + System.nanoTime())
          .withExposedPorts(9000)
          .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

  @DynamicPropertySource
  static void stockage(DynamicPropertyRegistry registry) {
    MINIO.start();
    registry.add(
        "emine.stockage.endpoint",
        () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
    registry.add("emine.stockage.cle-acces", () -> "minio-test");
    registry.add("emine.stockage.cle-secrete", () -> MINIO.getEnvMap().get("MINIO_ROOT_PASSWORD"));
    registry.add("emine.stockage.compartiment-pieces", () -> "pieces-test");
  }

  static final byte[] PDF =
      "%PDF-1.7\n1 0 obj << >> endobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
  static final byte[] PNG = {
    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 0x49, 0x48, 0x44, 0x52
  };

  @Autowired MockMvc mvc;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @Test
  void unePiecePdfEstStockeeAvecSonEmpreinteEtRelueALIdentique() throws Exception {
    long demande = brouillon();
    String reponse =
        joindre(demande, INVESTISSEUR, "EIES", "etude d'impact.pdf", PDF)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.typePiece").value("EIES"))
            .andExpect(jsonPath("$.typeMime").value("application/pdf"))
            .andExpect(jsonPath("$.tailleOctets").value(PDF.length))
            .andExpect(jsonPath("$.empreinteSha256").value(ReglesPieces.empreinteSha256(PDF)))
            .andExpect(jsonPath("$.stockageCle").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long piece = JsonMapper.builder().build().readTree(reponse).path("id").asLong();

    mvc.perform(get(url(demande)).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].nomFichier").value("etude d'impact.pdf"));

    byte[] relu =
        mvc.perform(
                get(url(demande) + "/" + piece + "/contenu")
                    .with(jeton(INVESTISSEUR, "INVESTISSEUR")))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(relu).isEqualTo(PDF);

    // Téléchargement par défaut ; affichage en ligne pour le visualiseur (D9).
    mvc.perform(
            get(url(demande) + "/" + piece + "/contenu").with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(
            header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));
    mvc.perform(
            get(url(demande) + "/" + piece + "/contenu?inline=true")
                .with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(status().isOk())
        .andExpect(
            header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")));

    // L'agent du guichet (interne) peut consulter la pièce.
    mvc.perform(get(url(demande) + "/" + piece + "/contenu").with(jeton(GUICHET, "GUICHET")))
        .andExpect(status().isOk());
  }

  @Test
  void uneImagePngEstAcceptee() throws Exception {
    joindre(brouillon(), INVESTISSEUR, "Plan du site", "plan.png", PNG)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.typeMime").value("image/png"));
  }

  @Test
  void unFormatNonAccepteDonneDoc001MemeSousUnNomPdf() throws Exception {
    long demande = brouillon();
    joindre(
            demande,
            INVESTISSEUR,
            "EIES",
            "faux.pdf",
            "MZ exécutable".getBytes(StandardCharsets.UTF_8))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("DOC-001"));
    joindre(demande, INVESTISSEUR, "EIES", "vide.pdf", new byte[0])
        .andExpect(jsonPath("$.code").value("DOC-001"));
  }

  @Test
  void unFichierDePlusDeDixMoDonneDoc002() throws Exception {
    byte[] gros = Arrays.copyOf(PDF, (int) ReglesPieces.TAILLE_MAX_OCTETS + 1);
    joindre(brouillon(), INVESTISSEUR, "EIES", "gros.pdf", gros)
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("DOC-002"));
  }

  @Test
  void unAutreInvestisseurNAccedeNiALaDemandeNiAuxPieces() throws Exception {
    long demande = brouillon();
    long piece = idPiece(joindre(demande, INVESTISSEUR, "EIES", "a.pdf", PDF));
    joindre(demande, INVESTISSEUR_2, "EIES", "intrus.pdf", PDF)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DEM-002"));
    mvc.perform(get(url(demande)).with(jeton(INVESTISSEUR_2, "INVESTISSEUR")))
        .andExpect(jsonPath("$.code").value("DEM-002"));
    mvc.perform(
            get(url(demande) + "/" + piece + "/contenu")
                .with(jeton(INVESTISSEUR_2, "INVESTISSEUR")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DOC-003"));
  }

  @Test
  void unePieceSeRetireDuBrouillonMaisPasDUneDemandeDeposee() throws Exception {
    long demande = brouillon();
    long piece = idPiece(joindre(demande, INVESTISSEUR, "EIES", "a.pdf", PDF));
    mvc.perform(delete(url(demande) + "/" + piece).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(status().isNoContent());
    mvc.perform(get(url(demande)).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(jsonPath("$", hasSize(0)));

    long autre = idPiece(joindre(demande, INVESTISSEUR, "EIES", "b.pdf", PDF));
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        Statement st = c.createStatement()) {
      st.executeUpdate("UPDATE demande SET statut = 'deposee' WHERE id = " + demande);
    }
    joindre(demande, INVESTISSEUR, "EIES", "tard.pdf", PDF)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEM-003"));
    mvc.perform(delete(url(demande) + "/" + autre).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(jsonPath("$.code").value("DEM-003"));
  }

  @Test
  void unAgentNePeutPasJoindreDePiece() throws Exception {
    joindre(brouillon(), GUICHET, "EIES", "a.pdf", PDF)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERM-001"));
  }

  private ResultActions joindre(long demande, String qui, String type, String nom, byte[] contenu)
      throws Exception {
    String role = qui.equals(GUICHET) ? "GUICHET" : "INVESTISSEUR";
    return mvc.perform(
        multipart(url(demande))
            .file(new MockMultipartFile("fichier", nom, "application/pdf", contenu))
            .param("typePiece", type)
            .with(jeton(qui, role)));
  }

  private long idPiece(ResultActions r) throws Exception {
    String corps = r.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return JsonMapper.builder().build().readTree(corps).path("id").asLong();
  }

  private long brouillon() throws Exception {
    String corps =
        mvc.perform(
                post("/api/v1/demandes")
                    .with(jeton(INVESTISSEUR, "INVESTISSEUR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"typeDocumentCode\": \"PEX\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonMapper.builder().build().readTree(corps).path("id").asLong();
  }

  private static String url(long demande) {
    return "/api/v1/demandes/" + demande + "/pieces";
  }
}
