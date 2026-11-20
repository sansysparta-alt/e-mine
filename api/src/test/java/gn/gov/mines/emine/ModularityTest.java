package gn.gov.mines.emine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Vérifie les frontières entre modules (US-00.4) : un module n'utilise que l'API d'un autre. */
class ModularityTest {

  private final ApplicationModules modules = ApplicationModules.of(EmineApplication.class);

  @Test
  void lesFrontieresEntreModulesSontRespectees() {
    modules.verify();
  }

  @Test
  void lesOnzeModulesAttendusExistent() {
    assertThat(modules.stream().map(ApplicationModule::getIdentifier).map(Object::toString))
        .containsExactlyInAnyOrder(
            "shared",
            "auth",
            "admin",
            "audit",
            "catalogue",
            "demande",
            "paiement",
            "instruction",
            "delivrance",
            "cadastre",
            "notification");
  }

  @Test
  void documenterLesModules() {
    new Documenter(modules).writeModulesAsPlantUml();
  }
}
