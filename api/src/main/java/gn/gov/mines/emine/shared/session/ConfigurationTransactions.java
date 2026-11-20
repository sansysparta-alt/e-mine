package gn.gov.mines.emine.shared.session;

import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Remplace le gestionnaire de transactions par défaut de Spring Boot : jOOQ et JDBC passent tous
 * deux par lui, donc chaque transaction reçoit le contexte de session (US-01.5).
 */
@Configuration(proxyBeanMethods = false)
class ConfigurationTransactions {

  @Bean
  PlatformTransactionManager transactionManager(
      DataSource dataSource, ObjectProvider<FournisseurContexteSession> fournisseur) {
    return new TransactionManagerAvecContexte(dataSource, fournisseur);
  }
}
