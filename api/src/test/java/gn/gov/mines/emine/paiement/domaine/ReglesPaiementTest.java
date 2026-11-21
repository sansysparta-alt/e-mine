package gn.gov.mines.emine.paiement.domaine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gn.gov.mines.emine.catalogue.TypeDocument.Tarifs;
import gn.gov.mines.emine.paiement.domaine.RegleObjetAPayer.ObjetAPayer;
import gn.gov.mines.emine.shared.jooq.enums.MoyenPaiement;
import gn.gov.mines.emine.shared.jooq.enums.ObjetPaiement;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Règles du paiement sans base : objet attendu, tentatives du jour, moyens proposés. */
class ReglesPaiementTest {

  private static final Tarifs PERMIS = new Tarifs(5_000_000L, 15_000_000L, null);
  private static final Tarifs ATTESTATION = new Tarifs(null, null, 200_000L);

  @Test
  void fraisDeDossierAuDepotEtPendantUnComplement() {
    assertThat(RegleObjetAPayer.objetAPayer("deposee", PERMIS))
        .contains(new ObjetAPayer(ObjetPaiement.FRAIS_DOSSIER, 5_000_000L));
    assertThat(RegleObjetAPayer.objetAPayer("complement_requis", PERMIS))
        .contains(new ObjetAPayer(ObjetPaiement.FRAIS_DOSSIER, 5_000_000L));
  }

  @Test
  void paiementUniqueDUneAttestation() {
    assertThat(RegleObjetAPayer.objetAPayer("deposee", ATTESTATION))
        .contains(new ObjetAPayer(ObjetPaiement.ATTESTATION, 200_000L));
    assertThat(RegleObjetAPayer.objetAPayer("decision", ATTESTATION)).isEmpty();
  }

  @Test
  void droitsApresLaDecision() {
    assertThat(RegleObjetAPayer.objetAPayer("decision", PERMIS))
        .contains(new ObjetAPayer(ObjetPaiement.DROITS, 15_000_000L));
  }

  @Test
  void rienAAutresStatuts() {
    for (String s :
        new String[] {"brouillon", "recevable", "en_instruction", "avis_rendu", "droits_payes"}) {
      assertThat(RegleObjetAPayer.objetAPayer(s, PERMIS)).as(s).isEmpty();
    }
  }

  @Test
  void unMontantNulOuNegatifEstImpossible() {
    assertThatThrownBy(() -> new ObjetAPayer(ObjetPaiement.DROITS, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void troisTentativesParJourAHeureDeConakry() {
    assertThat(RegleTentatives.restantes(0)).isEqualTo(3);
    assertThat(RegleTentatives.restantes(2)).isEqualTo(1);
    assertThat(RegleTentatives.restantes(5)).isZero();
    Instant soir = Instant.parse("2026-11-21T23:30:00Z");
    assertThat(RegleTentatives.debutDuJour(soir)).isEqualTo(Instant.parse("2026-11-21T00:00:00Z"));
    assertThat(RegleTentatives.debutDuJourSuivant(soir))
        .isEqualTo(Instant.parse("2026-11-22T00:00:00Z"));
  }

  @Test
  void tresorPaySeulDisponibleEnV1() {
    assertThat(MoyensPaiement.disponible(MoyenPaiement.TRESORPAY)).isTrue();
    assertThat(MoyensPaiement.disponible(MoyenPaiement.VIREMENT)).isFalse();
    assertThat(MoyensPaiement.proposes())
        .extracting(MoyensPaiement.MoyenPropose::moyen)
        .containsExactly(MoyenPaiement.TRESORPAY, MoyenPaiement.VIREMENT);
  }
}
