package gn.gov.mines.emine.shared;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.List;
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
    schema
        .getProperties()
        .forEach(
            (nom, propriete) -> {
              if (!Boolean.TRUE.equals(((Schema) propriete).getNullable())) {
                requises.add((String) nom);
              }
            });
    schema.setRequired(requises);
  }
}
