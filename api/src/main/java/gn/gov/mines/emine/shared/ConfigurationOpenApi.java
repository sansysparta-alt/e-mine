package gn.gov.mines.emine.shared;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Description OpenAPI 3 de l'API (source du client TypeScript généré par orval). */
@Configuration(proxyBeanMethods = false)
class ConfigurationOpenApi {

  @Bean
  OpenAPI openApiEmine() {
    return new OpenAPI()
        .info(
            new Info()
                .title("API e-mine")
                .version("v1")
                .description(
                    "API du Ministère des Mines et de la Géologie (démonstration, données"
                        + " fictives)."));
  }

  /**
   * Convention : toute propriété d'un DTO est requise, sauf si elle est déclarée nullable (par
   * exemple {@code @Schema(nullable = true)}). Le client TypeScript reflète ainsi exactement les
   * réponses de l'API.
   */
  @Bean
  OpenApiCustomizer proprietesRequisesParDefaut() {
    return openApi -> {
      if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
        return;
      }
      openApi.getComponents().getSchemas().values().forEach(ConfigurationOpenApi::rendreRequises);
    };
  }

  @SuppressWarnings("rawtypes")
  private static void rendreRequises(Schema schema) {
    if (schema.getProperties() == null) {
      return;
    }
    List<String> requises = new ArrayList<>();
    Map<String, Schema> remplacees = new LinkedHashMap<>();
    schema
        .getProperties()
        .forEach(
            (nom, propriete) -> {
              Schema p = (Schema) propriete;
              if (!Boolean.TRUE.equals(p.getNullable())) {
                requises.add((String) nom);
              }
              if (p.get$ref() != null && p.getTypes() != null && p.getTypes().contains("null")) {
                remplacees.put((String) nom, referenceNullable(p.get$ref()));
              }
            });
    schema.getProperties().putAll(remplacees);
    schema.setRequired(requises);
  }

  /**
   * Objet facultatif ({@code @Schema(nullable = true)} sur un DTO) : en OpenAPI 3.1, {@code oneOf}
   * de la référence et de {@code null}, pour que le client TypeScript type {@code X | null}.
   */
  @SuppressWarnings("rawtypes")
  private static Schema referenceNullable(String ref) {
    Schema nul = new Schema<>();
    nul.addType("null");
    return new Schema<>().oneOf(List.of(new Schema<>().$ref(ref), nul));
  }
}
