package gn.gov.mines.emine.shared;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Horloge UTC commune, remplaçable dans les tests (délais, anti-rejeu, tentatives du jour). */
@Configuration(proxyBeanMethods = false)
class ConfigurationHorloge {

  @Bean
  Clock horloge() {
    return Clock.systemUTC();
  }
}
