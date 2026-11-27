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
 * US-07.3, US-07.6 : les 11 statuts techniques regroupés en 7 étapes visibles, dans l'ordre réel du
 * circuit (dépôt, paiement des frais, contrôle, instruction, décision, paiement des droits,
 * document signé).
 */
class RegleSuiviTest {

  static final Instant J1 = Instant.parse("2026-11-01T09:00:00Z");
  static final Instant J2 = Instant.parse("2026-11-02T09:00:00Z");
  static final Instant J3 = Instant.parse("2026-11-03T09:00:00Z");
  static final Instant J4 = Instant.parse("2026-11-04T09:00:00Z");
  static final Instant J5 = Instant.parse("2026-11-05T09:00:00Z");
  static final Instant J6 = Instant.parse("2026-11-06T09:00:00Z");
  static final Instant J7 = Instant.parse("2026-11-07T09:00:00Z");
  static final Dates TOUTES = new Dates(J1, J2, J3, J4, J5, J6, J7, null);

  @Test
  void septEtapesDansLOrdreReelDuCircuitAvecLaDecision() {
    assertThat(cles(suivi("deposee", false, true, true, null, TOUTES)))
        .containsExactly(
            "depot",
            "paiement-frais",
            "controle",
            "instruction",
            "decision",
            "paiement-droits",
            "signe");
  }

  @Test
  void chaqueStatutTechniqueTombeDansUneEtapeVisible() {
    assertThat(etats(suivi("deposee", false, true, true, null, TOUTES)))
        .containsExactly(
            "faite", "en-cours", "a-venir", "a-venir", "a-venir", "a-venir", "a-venir");
    assertThat(etats(suivi("deposee", true, true, true, null, TOUTES)))
        .containsExactly("faite", "faite", "en-cours", "a-venir", "a-venir", "a-venir", "a-venir");
    assertThat(etats(suivi("en_instruction", true, true, true, null, TOUTES)))
        .containsExactly("faite", "faite", "faite", "en-cours", "a-venir", "a-venir", "a-venir");
    assertThat(etats(suivi("avis_rendu", true, true, true, null, TOUTES)))
        .as("tous les avis rendus : la décision du signataire est attendue")
        .containsExactly("faite", "faite", "faite", "faite", "en-cours", "a-venir", "a-venir");
    assertThat(etats(suivi("decision", true, true, true, null, TOUTES)))
        .containsExactly("faite", "faite", "faite", "faite", "faite", "en-cours", "a-venir");
    assertThat(etats(suivi("droits_payes", true, true, true, null, TOUTES)))
        .containsExactly("faite", "faite", "faite", "faite", "faite", "faite", "en-cours");
    for (String s : List.of("delivree", "archivee")) {
      assertThat(etats(suivi(s, true, true, true, null, TOUTES))).as(s).containsOnly("faite");
    }
    assertThat(suivi("brouillon", false, true, true, null, TOUTES)).isEmpty();
  }

  @Test
  void sansAvisTechniqueLInstructionEstSansObjetEtLaDecisionSuitLeControle() {
    assertThat(etats(suivi("recevable", true, true, false, null, TOUTES)))
        .containsExactly("faite", "faite", "faite", "sans-objet", "en-cours", "a-venir", "a-venir");
    assertThat(etats(suivi("delivree", true, true, false, null, TOUTES)).get(3))
        .isEqualTo("sans-objet");
  }

  @Test
  void leComplementResteALEtapeDOuIlVientEtAttendLeDemandeur() {
    List<EtapeSuivi> auControle = suivi("complement_requis", true, true, true, "deposee", TOUTES);
    assertThat(etats(auControle).get(2)).isEqualTo("en-cours");
    assertThat(auControle.get(2).actionDemandeur()).isTrue();
    List<EtapeSuivi> aLInstruction =
        suivi("complement_requis", true, true, true, "en_instruction", TOUTES);
    assertThat(etats(aLInstruction).get(3)).isEqualTo("en-cours");
    assertThat(aLInstruction.get(3).actionDemandeur()).isTrue();
  }

  @Test
  void lActionDuDemandeurEstSignaleeSurLEtapeEnCours() {
    assertThat(suivi("deposee", false, true, true, null, TOUTES).get(1).actionDemandeur()).isTrue();
    assertThat(suivi("deposee", true, true, true, null, TOUTES).get(2).actionDemandeur()).isFalse();
    assertThat(suivi("decision", true, true, true, null, TOUTES).get(5).actionDemandeur()).isTrue();
    assertThat(suivi("avis_rendu", true, true, true, null, TOUTES).get(4).actionDemandeur())
        .as("la décision attend le signataire, pas le demandeur")
        .isFalse();
  }

  @Test
  void sansDroitsAPayerLePaiementDesDroitsEstSansObjet() {
    assertThat(etats(suivi("droits_payes", true, false, true, null, TOUTES)))
        .containsExactly("faite", "faite", "faite", "faite", "faite", "sans-objet", "en-cours");
    assertThat(etats(suivi("deposee", true, false, true, null, TOUTES)).get(5))
        .isEqualTo("sans-objet");
  }

  @Test
  void unRejetSeSitueAuControleOuALaDecisionAvecSaDate() {
    Dates rejeteeAuControle = new Dates(J1, J2, null, null, null, null, null, J3);
    List<EtapeSuivi> controle = suivi("rejetee", true, true, true, null, rejeteeAuControle);
    assertThat(etats(controle))
        .containsExactly(
            "faite",
            "faite",
            "rejetee",
            "non-atteinte",
            "non-atteinte",
            "non-atteinte",
            "non-atteinte");
    assertThat(controle.get(2).date()).isEqualTo(J3);

    Dates rejeteeALaDecision = new Dates(J1, J2, J3, J4, null, null, null, J5);
    List<EtapeSuivi> decision = suivi("rejetee", true, true, true, null, rejeteeALaDecision);
    assertThat(etats(decision))
        .containsExactly(
            "faite", "faite", "faite", "faite", "rejetee", "non-atteinte", "non-atteinte");
    assertThat(decision.get(4).date()).isEqualTo(J5);
  }

  @Test
  void chaqueEtapeFaitePorteSaDate() {
    List<EtapeSuivi> f = suivi("delivree", true, true, true, null, TOUTES);
    assertThat(f.stream().map(EtapeSuivi::date).toList())
        .containsExactly(J1, J2, J3, J4, J5, J6, J7);
    assertThat(suivi("deposee", false, true, true, null, TOUTES).get(1).date())
        .as("une étape en cours n'a pas de date")
        .isNull();
  }

  @Test
  void lAvancementSuitLaFriseEtIgnoreLesEtapesSansObjet() {
    assertThat(avancement("deposee", false, true, true, null))
        .isEqualTo(new RegleSuivi.Avancement(2, 7));
    assertThat(avancement("deposee", true, true, true, null))
        .isEqualTo(new RegleSuivi.Avancement(3, 7));
    assertThat(avancement("complement_requis", true, true, true, "en_instruction"))
        .isEqualTo(new RegleSuivi.Avancement(4, 7));
    assertThat(avancement("avis_rendu", true, true, true, null))
        .isEqualTo(new RegleSuivi.Avancement(5, 7));
    assertThat(avancement("decision", true, true, true, null))
        .isEqualTo(new RegleSuivi.Avancement(6, 7));
    // Sans avis ni droits, le document compte 5 étapes : la décision est la 4e.
    assertThat(avancement("recevable", true, false, false, null))
        .isEqualTo(new RegleSuivi.Avancement(4, 5));
    assertThat(avancement("droits_payes", true, false, false, null))
        .isEqualTo(new RegleSuivi.Avancement(5, 5));
    // Délivrée : dernière étape, homogène avec les demandes en cours (« Étape 7 sur 7 »).
    assertThat(avancement("delivree", true, true, true, null))
        .isEqualTo(new RegleSuivi.Avancement(7, 7));
    for (String fini : new String[] {"brouillon", "rejetee", "archivee"}) {
      assertThat(avancement(fini, true, true, true, null)).as(fini).isNull();
    }
  }

  private static RegleSuivi.Avancement avancement(
      String statut, boolean frais, boolean droits, boolean avis, String origine) {
    return RegleSuivi.avancement(
        new Situation(
            statut,
            frais,
            droits,
            avis,
            origine,
            new Dates(J1, null, null, null, null, null, null, null)));
  }

  private static List<EtapeSuivi> suivi(
      String statut, boolean frais, boolean droits, boolean avis, String origine, Dates dates) {
    return RegleSuivi.suivi(new Situation(statut, frais, droits, avis, origine, dates));
  }

  private static List<String> cles(List<EtapeSuivi> f) {
    return f.stream().map(e -> e.etape().cle()).toList();
  }

  private static List<String> etats(List<EtapeSuivi> f) {
    return f.stream().map(e -> e.etat().cle()).toList();
  }
}
