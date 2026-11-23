package gn.gov.mines.emine.catalogue.domaine;

import static org.assertj.core.api.Assertions.assertThat;

import gn.gov.mines.emine.catalogue.domaine.RegleFiche.Action;
import gn.gov.mines.emine.catalogue.domaine.RegleFiche.Changement;
import gn.gov.mines.emine.catalogue.domaine.VersionFiche.ContenuFiche;
import gn.gov.mines.emine.catalogue.domaine.VersionFiche.Profil;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** US-02.2 : règles de la fiche (pièces, changements montrés au Ministre, action par profil). */
class RegleFicheTest {

  private static final ContenuFiche PEX =
      new ContenuFiche(
          "Permis d'exploitation",
          null,
          List.of("Étude de faisabilité", "EIES"),
          60,
          "DNM + Inspection",
          "Ministre",
          List.of(new Profil("DNM", "Agent DNM")),
          true);

  @Test
  void lesPiecesSontNettoyeesSansDoublonNiVirgule() {
    assertThat(RegleFiche.pieces(List.of("  EIES ", "", "eies", "Plan   de restauration")))
        .contains(List.of("EIES", "Plan de restauration"));
    assertThat(RegleFiche.pieces(List.of("Statuts, capacités"))).isEmpty();
    assertThat(RegleFiche.pieces(List.of(" ", ""))).isEmpty();
    assertThat(RegleFiche.pieces(List.of("x"))).isEmpty();
    String[] trop = new String[RegleFiche.PIECES_MAX + 1];
    Arrays.setAll(trop, i -> "Pièce " + i);
    assertThat(RegleFiche.pieces(List.of(trop))).isEmpty();
    assertThat(RegleFiche.piecesCles(List.of("A b", "C d"))).isEqualTo("A b, C d");
  }

  @Test
  void lesChangementsDonnentAncienneEtNouvelleValeurDansLOrdreDeLaFiche() {
    ContenuFiche apres =
        new ContenuFiche(
            "Permis d'exploitation",
            "Mining licence",
            List.of("Étude de faisabilité", "EIES"),
            45,
            "DNM + Inspection",
            "Directeur général",
            List.of(new Profil("DNM", "Agent DNM"), new Profil("GEOLOGUE", "Géologue (DNG)")),
            false);
    assertThat(RegleFiche.changements(PEX, apres))
        .containsExactly(
            new Changement("libelleEn", null, "Mining licence"),
            new Changement("delaiJours", "60", "45"),
            new Changement("signataire", "Ministre", "Directeur général"),
            new Changement("avis", "Agent DNM", "Agent DNM · Géologue (DNG)"),
            new Changement("actif", "true", "false"));
    assertThat(RegleFiche.changeSignataire(PEX, apres)).isTrue();
    assertThat(RegleFiche.changements(PEX, PEX)).isEmpty();
  }

  @Test
  void leMinistreDecideLAdministrateurAttendLesAutresNeFontRien() {
    assertThat(RegleFiche.action(true, false)).isEqualTo(Action.DECIDER);
    assertThat(RegleFiche.action(false, true)).isEqualTo(Action.ATTENDRE);
    assertThat(RegleFiche.action(false, false)).isEqualTo(Action.AUCUNE);
  }
}
