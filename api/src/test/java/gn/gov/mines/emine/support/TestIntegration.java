package gn.gov.mines.emine.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Classe de base des tests d'intégration : contexte Spring complet sur PostgreSQL de test. */
@SpringBootTest
public abstract class TestIntegration {

  @DynamicPropertySource
  static void baseDeDonnees(DynamicPropertyRegistry registry) {
    BaseDeDonneesDeTest.proprietes(registry);
  }
}
