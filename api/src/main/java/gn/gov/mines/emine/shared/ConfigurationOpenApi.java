package gn.gov.mines.emine.shared;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
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
}
