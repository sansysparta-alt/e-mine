package gn.gov.mines.emine.simulateur.tresorpay;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

/**
 * Simulateur TrésorPay (D5, US-04.1) : application indépendante de l'API e-mine, qui joue le rôle
 * de la passerelle de paiement du Trésor selon le contrat {@code docs/contrats/tresorpay.md}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SimulateurTresorPayApplication {

  public static void main(String[] args) {
    SpringApplication.run(SimulateurTresorPayApplication.class, args);
  }

  @Bean
  Clock horloge() {
    return Clock.systemUTC();
  }

  @Bean
  HttpClient clientHttp() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  }
}
