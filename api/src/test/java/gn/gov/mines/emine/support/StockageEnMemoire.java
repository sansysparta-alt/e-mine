package gn.gov.mines.emine.support;

import gn.gov.mines.emine.shared.stockage.StockageObjets;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Stockage objet en mémoire pour les tests qui n'éprouvent pas MinIO lui-même (délivrance) : évite
 * un conteneur supplémentaire. Le port {@link StockageObjets} est le même qu'en production.
 */
public class StockageEnMemoire implements StockageObjets {

  private final Map<String, byte[]> objets = new ConcurrentHashMap<>();

  @Override
  public void deposer(String compartiment, String cle, byte[] contenu, String typeMime) {
    objets.put(compartiment + "/" + cle, contenu.clone());
  }

  @Override
  public InputStream lire(String compartiment, String cle) {
    byte[] contenu = objets.get(compartiment + "/" + cle);
    if (contenu == null) {
      throw new IllegalStateException("Objet absent : " + compartiment + "/" + cle);
    }
    return new ByteArrayInputStream(contenu);
  }

  /** Contenu déposé, ou {@code null}. */
  public byte[] objet(String compartiment, String cle) {
    return objets.get(compartiment + "/" + cle);
  }

  /** Configuration à importer dans un test : remplace le stockage S3. */
  @TestConfiguration
  public static class Config {

    @Bean
    @Primary
    StockageEnMemoire stockageEnMemoire() {
      return new StockageEnMemoire();
    }
  }
}
