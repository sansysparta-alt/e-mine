package gn.gov.mines.emine.instruction;

import static org.assertj.core.api.Assertions.assertThat;

import gn.gov.mines.emine.instruction.domaine.Complement;
import gn.gov.mines.emine.instruction.domaine.RegleComplement;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** US-05.2 : règles du complément, sans base de données. */
class RegleComplementTest {

  @Test
  void unComplementSeDemandeALExamenOuPendantLInstructionSeulement() {
    assertThat(RegleComplement.peutEtreDemande("deposee")).isTrue();
    assertThat(RegleComplement.peutEtreDemande("en_instruction")).isTrue();
    assertThat(RegleComplement.peutEtreDemande("recevable")).isFalse();
    assertThat(RegleComplement.peutEtreDemande("brouillon")).isFalse();
  }

  @Test
  void lesPiecesDemandeesSontNettoyeesEtBornees() {
    assertThat(RegleComplement.piecesNettoyees(Arrays.asList(" EIES ", "", null, "EIES", "RCCM")))
        .containsExactly("EIES", "RCCM");
    assertThat(RegleComplement.piecesNettoyees(null)).isEmpty();
    assertThat(RegleComplement.problemePieces(Collections.nCopies(21, "x"))).isPresent();
    assertThat(RegleComplement.problemePieces(List.of("x".repeat(201)))).isPresent();
    assertThat(RegleComplement.problemePieces(List.of("EIES"))).isEmpty();
  }

  @Test
  void laReponseExigeLesPiecesDemandeesOuUnMessage() {
    assertThat(RegleComplement.reponseComplete(List.of("EIES"), List.of(), "Voici.")).isFalse();
    assertThat(RegleComplement.reponseComplete(List.of("EIES"), List.of("EIES"), null)).isTrue();
    assertThat(RegleComplement.reponseComplete(List.of(), List.of(), "Court")).isFalse();
    assertThat(RegleComplement.reponseComplete(List.of(), List.of(), "Réponse complète.")).isTrue();
    assertThat(RegleComplement.piecesManquantes(List.of("EIES", "RCCM"), List.of("RCCM")))
        .containsExactly("EIES");
  }

  @Test
  void leDelaiEstDepasseSeulementPourUnComplementOuvert() {
    Instant limite = Instant.parse("2026-11-20T10:00:00Z");
    Complement ouvert = complement(limite, null, null);
    assertThat(ouvert.delaiDepasse(limite.minusSeconds(1))).isFalse();
    assertThat(ouvert.delaiDepasse(limite.plusSeconds(1))).isTrue();
    assertThat(complement(limite, limite, null).delaiDepasse(limite.plusSeconds(60))).isFalse();
    assertThat(complement(limite, null, limite).delaiDepasse(limite.plusSeconds(60))).isFalse();
  }

  private static Complement complement(Instant limite, Instant repondu, Instant clos) {
    return new Complement(
        1,
        "deposee",
        "Merci de compléter.",
        List.of(),
        limite.minusSeconds(5 * 86_400),
        limite,
        null,
        repondu,
        null,
        clos,
        "Agent Test",
        "Agent");
  }
}
