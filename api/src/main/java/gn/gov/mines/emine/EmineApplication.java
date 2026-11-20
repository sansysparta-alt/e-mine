package gn.gov.mines.emine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Point d'entrée de l'API e-mine (monolithe modulaire, un sous-paquet par module). */
@SpringBootApplication
public class EmineApplication {

  public static void main(String[] args) {
    SpringApplication.run(EmineApplication.class, args);
  }
}
