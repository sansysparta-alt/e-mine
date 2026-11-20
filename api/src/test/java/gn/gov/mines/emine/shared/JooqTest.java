package gn.gov.mines.emine.shared;

import static gn.gov.mines.emine.shared.jooq.Tables.PROFIL;
import static gn.gov.mines.emine.shared.jooq.Tables.TYPE_DOCUMENT;
import static org.assertj.core.api.Assertions.assertThat;

import gn.gov.mines.emine.shared.jooq.enums.StatutDemande;
import gn.gov.mines.emine.shared.jooq.enums.StatutPaiement;
import gn.gov.mines.emine.support.TestIntegration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** US-00.3 : code jOOQ généré depuis le schéma migré, utilisable par l'API en emine_app. */
class JooqTest extends TestIntegration {

  @Autowired DSLContext dsl;

  @Test
  void lesTablesGenereesSontLisibles() {
    assertThat(dsl.fetchCount(PROFIL)).isEqualTo(16);
    assertThat(dsl.select(TYPE_DOCUMENT.CODE).from(TYPE_DOCUMENT).fetch(TYPE_DOCUMENT.CODE))
        .hasSize(15)
        .contains("PEX", "PRE", "COR");
  }

  @Test
  void lesEnumsPostgresDeviennentDesEnumsJava() {
    List<String> enBase =
        dsl.fetch("SELECT unnest(enum_range(NULL::statut_demande))::text")
            .getValues(0, String.class);
    assertThat(Arrays.stream(StatutDemande.values()).map(StatutDemande::getLiteral))
        .containsExactlyElementsOf(enBase);
    assertThat(StatutPaiement.values()).hasSize(10);
  }

  @Test
  void lesDatesSontDesInstant() {
    Instant creation =
        dsl.select(PROFIL.CREATED_AT).from(PROFIL).limit(1).fetchOne(PROFIL.CREATED_AT);
    assertThat(creation).isBefore(Instant.now().plusSeconds(1));
  }
}
