package gn.gov.mines.emine.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import gn.gov.mines.emine.support.TestIntegration;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exporte la description OpenAPI dans packages/api-client/openapi.json, d'où orval génère le client
 * TypeScript. La CI vérifie ensuite que le fichier versionné est à jour (git diff).
 */
@AutoConfigureMockMvc
class OpenApiTest extends TestIntegration {

  static final Path CIBLE = Path.of("..", "packages", "api-client", "openapi.json");

  @Autowired MockMvc mvc;

  @Test
  void exporterLaDescriptionOpenApi() throws Exception {
    String brut = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
    JsonMapper mapper =
        JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();
    JsonNode doc = mapper.readTree(brut);
    assertThat(doc.path("info").path("title").asString()).isEqualTo("API e-mine");
    assertThat(doc.path("paths").propertyNames()).allMatch(chemin -> chemin.startsWith("/api/v1/"));
    Files.writeString(CIBLE, mapper.writeValueAsString(doc).replace("\r\n", "\n") + "\n");
  }
}
