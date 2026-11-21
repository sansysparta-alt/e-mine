package gn.gov.mines.emine.instruction;

import static org.assertj.core.api.Assertions.assertThat;

import gn.gov.mines.emine.instruction.domaine.RegleMotif;
import gn.gov.mines.emine.instruction.domaine.RegleRecevabilite;
import gn.gov.mines.emine.instruction.domaine.RegleRecevabilite.Examen;
import java.util.List;
import org.junit.jupiter.api.Test;

/** US-05.1 : règle de recevabilité (pièces obligatoires et frais payés), sans base. */
class RegleRecevabiliteTest {

  private static final List<String> REQUISES = List.of("Statuts", "Capacités", "Carte");

  @Test
  void recevableSeulementSiDeposeeFraisPayesEtPiecesCompletes() {
    assertThat(examen("deposee", true, REQUISES).peutEtreRecevable()).isTrue();
    assertThat(examen("deposee", false, REQUISES).peutEtreRecevable()).isFalse();
    assertThat(examen("recevable", true, REQUISES).peutEtreRecevable()).isFalse();
    Examen incomplet = examen("deposee", true, List.of("Statuts", "Autre pièce"));
    assertThat(incomplet.peutEtreRecevable()).isFalse();
    assertThat(incomplet.piecesManquantes()).containsExactly("Capacités", "Carte");
  }

  @Test
  void leMotifFaitDeDixADeuxMilleCaracteresHorsEspaces() {
    assertThat(RegleMotif.probleme(null)).isPresent();
    assertThat(RegleMotif.probleme("   neuf car  ")).isPresent();
    assertThat(RegleMotif.probleme("  dix caract  ")).isEmpty();
    assertThat(RegleMotif.probleme("x".repeat(2000))).isEmpty();
    assertThat(RegleMotif.probleme("x".repeat(2001))).isPresent();
  }

  private static Examen examen(String statut, boolean frais, List<String> fournies) {
    return RegleRecevabilite.examiner(statut, frais, REQUISES, fournies);
  }
}
