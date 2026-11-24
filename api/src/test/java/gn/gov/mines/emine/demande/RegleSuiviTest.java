package gn.gov.mines.emine.demande;

import static org.assertj.core.api.Assertions.assertThat;

import gn.gov.mines.emine.demande.domaine.RegleSuivi;
import gn.gov.mines.emine.demande.domaine.RegleSuivi.Dates;
import gn.gov.mines.emine.demande.domaine.RegleSuivi.EtapeSuivi;
import gn.gov.mines.emine.demande.domaine.RegleSuivi.Situation;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * US-07.3 : les 11 statuts techniques regroupés en 6 étapes visibles, dans l'ordre réel du circuit
 * (dépôt, paiement des frais, contrôle, instruction, paiement des droits, document signé).
 */
class RegleSuiviTest {

  static final Instant J1 = Instant.parse("2026-11-01T09:00:00Z");
  static final Instant J2 = Instant.parse("2026-11-02T09:00:00Z");
  static final Instant J3 = Instant.parse("2026-11-03T09:00:00Z");
  static final Instant J4 = Instant.parse("2026-11-04T09:00:00Z");
  static final Instant J5 = Instant.parse("2026-11-05T09:00:00Z");
  static final Instant J6 = Instant.parse("2026-11-06T09:00:00Z");
  static final Dates TOUTES = new Dates(J1, J2, J3, J4, J5, J6, null);

  @Test
  void sixEtapesDansLOrdreReelDuCircuit() {
    assertThat(cles(suivi("deposee", false, true, null, TOUTES)))
        .containsExactly(
            "depot", "paiement-frais", "controle", "instruction", "paiement-droits", "signe");
  }

  @Test
  void chaqueStatutTechniqueTombeDansUneEtapeVisible() {
    assertThat(etats(suivi("deposee", false, true, null, TOUTES)))
        .containsExactly("faite", "en-cours", "a-venir", "a-venir", "a-venir", "a-venir");
    assertThat(etats(suivi("deposee", true, true, null, TOUTES)))
        .containsExactly("faite", "faite", "en-cours", "a-venir", "a-venir", "a-venir");
    for (String s : List.of("recevable", "en_instruction", "avis_rendu")) {
      assertThat(etats(suivi(s, true, true, null, TOUTES)))
          .as(s)
          .containsExactly("faite", "faite", "faite", "en-cours", "a-venir", "a-venir");
    }
    assertThat(etats(suivi("decision", true, true, null, TOUTES)))
        .containsExactly("faite", "faite", "faite", "faite", "en-cours", "a-venir");
    assertThat(etats(suivi("droits_payes", true, true, null, TOUTES)))
        .containsExactly("faite", "faite", "faite", "faite", "faite", "en-cours");
    for (String s : List.of("delivree", "archivee")) {
      assertThat(etats(suivi(s, true, true, null, TOUTES))).as(s).containsOnly("faite");
    }
    assertThat(suivi("brouillon", false, true, null, TOUTES)).isEmpty();
  }

  @Test
  void leComplementResteALEtapeDOuIlVientEtAttendLeDemandeur() {
    List<EtapeSuivi> auControle = suivi("complement_requis", true, true, "deposee", TOUTES);
    assertThat(etats(auControle).get(2)).isEqualTo("en-cours");
    assertThat(auControle.get(2).actionDemandeur()).isTrue();
    List<EtapeSuivi> aLInstruction =
        suivi("complement_requis", true, true, "en_instruction", TOUTES);
    assertThat(etats(aLInstruction).get(3)).isEqualTo("en-cours");
    assertThat(aLInstruction.get(3).actionDemandeur()).isTrue();
  }

  @Test
  void lActionDuDemandeurEstSignaleeSurLEtapeEnCours() {
    assertThat(suivi("deposee", false, true, null, TOUTES).get(1).actionDemandeur()).isTrue();
    assertThat(suivi("deposee", true, true, null, TOUTES).get(2).actionDemandeur()).isFalse();
    assertThat(suivi("decision", true, true, null, TOUTES).get(4).actionDemandeur()).isTrue();
    assertThat(suivi("en_instruction", true, true, null, TOUTES).get(3).actionDemandeur())
        .isFalse();
  }

  @Test
  void sansDroitsAPayerLePaiementDesDroitsEstSansObjet() {
    assertThat(etats(suivi("droits_payes", true, false, null, TOUTES)))
        .containsExactly("faite", "faite", "faite", "faite", "sans-objet", "en-cours");
    assertThat(etats(suivi("deposee", true, false, null, TOUTES)).get(4)).isEqualTo("sans-objet");
  }

  @Test
  void unRejetSeSitueAuControleOuALInstructionAvecSaDate() {
    Dates rejeteeAuControle = new Dates(J1, J2, null, null, null, null, J3);
    List<EtapeSuivi> controle = suivi("rejetee", true, true, null, rejeteeAuControle);
    assertThat(etats(controle))
        .containsExactly(
            "faite", "faite", "rejetee", "non-atteinte", "non-atteinte", "non-atteinte");
    assertThat(controle.get(2).date()).isEqualTo(J3);

    Dates rejeteeALInstruction = new Dates(J1, J2, J3, null, null, null, J4);
    assertThat(etats(suivi("rejetee", true, true, null, rejeteeALInstruction)))
        .containsExactly("faite", "faite", "faite", "rejetee", "non-atteinte", "non-atteinte");
  }

  @Test
  void chaqueEtapeFaitePorteSaDate() {
    List<EtapeSuivi> f = suivi("delivree", true, true, null, TOUTES);
    assertThat(f.stream().map(EtapeSuivi::date).toList()).containsExactly(J1, J2, J3, J4, J5, J6);
    assertThat(suivi("deposee", false, true, null, TOUTES).get(1).date())
        .as("une étape en cours n'a pas de date")
        .isNull();
  }

  @Test
  void lAvancementSuitLaFriseEtIgnoreLesEtapesSansObjet() {
    assertThat(avancement("deposee", false, true, null)).isEqualTo(new RegleSuivi.Avancement(2, 6));
    assertThat(avancement("deposee", true, true, null)).isEqualTo(new RegleSuivi.Avancement(3, 6));
    assertThat(avancement("complement_requis", true, true, "en_instruction"))
        .isEqualTo(new RegleSuivi.Avancement(4, 6));
    assertThat(avancement("decision", true, true, null)).isEqualTo(new RegleSuivi.Avancement(5, 6));
    // Sans droits à payer, le document compte 5 étapes : la signature est la 5e.
    assertThat(avancement("droits_payes", true, false, null))
        .isEqualTo(new RegleSuivi.Avancement(5, 5));
    for (String fini : new String[] {"brouillon", "delivree", "rejetee", "archivee"}) {
      assertThat(avancement(fini, true, true, null)).as(fini).isNull();
    }
  }

  private static RegleSuivi.Avancement avancement(
      String statut, boolean frais, boolean droits, String origine) {
    return RegleSuivi.avancement(
        new Situation(
            statut, frais, droits, origine, new Dates(J1, null, null, null, null, null, null)));
  }

  private static List<EtapeSuivi> suivi(
      String statut, boolean frais, boolean droits, String origine, Dates dates) {
    return RegleSuivi.suivi(new Situation(statut, frais, droits, origine, dates));
  }

  private static List<String> cles(List<EtapeSuivi> f) {
    return f.stream().map(e -> e.etape().cle()).toList();
  }

  private static List<String> etats(List<EtapeSuivi> f) {
    return f.stream().map(e -> e.etat().cle()).toList();
  }
}
