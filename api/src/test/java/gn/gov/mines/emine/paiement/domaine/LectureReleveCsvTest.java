package gn.gov.mines.emine.paiement.domaine;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** US-04.8 : lecture d'un relevé CSV selon le contrat docs/contrats/releve-csv.md. */
class LectureReleveCsvTest {

  private static LectureReleveCsv.Resultat lire(String csv) {
    return LectureReleveCsv.lire(csv.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void litLesLignesDuContrat() {
    var r =
        lire(
            "﻿Date_Opération;Libellé;Montant_GNF;Sens\r\n"
                + "26/11/2026;VIR HORIZON PAY-2026-000123;5 000 000;C\r\n"
                + "2026-11-27;\"RETOUR; VIR\";200000;d\r\n"
                + "\r\n");
    assertThat(r.valide()).isTrue();
    assertThat(r.lignes()).hasSize(2);
    var premiere = r.lignes().get(0);
    assertThat(premiere.numero()).isEqualTo(2);
    assertThat(premiere.date()).isEqualTo(LocalDate.of(2026, 11, 26));
    assertThat(premiere.libelle()).isEqualTo("VIR HORIZON PAY-2026-000123");
    assertThat(premiere.montantGnf()).isEqualTo(5_000_000);
    assertThat(premiere.sens()).isEqualTo('C');
    var seconde = r.lignes().get(1);
    assertThat(seconde.libelle()).isEqualTo("RETOUR; VIR");
    assertThat(seconde.sens()).isEqualTo('D');
  }

  @Test
  void refuseUnEnTeteInattendu() {
    var r = lire("date;libelle;montant\n26/11/2026;VIR;100;C\n");
    assertThat(r.valide()).isFalse();
    assertThat(r.problemes().get(0).ligne()).isEqualTo(1);
  }

  @Test
  void citeChaqueLigneFautiveEtNImporteRien() {
    var r =
        lire(
            "date_operation;libelle;montant_gnf;sens\n"
                + "31/02/2026;VIR;100;C\n"
                + "26/11/2026;;100;C\n"
                + "26/11/2026;VIR;12,50;C\n"
                + "26/11/2026;VIR;100;X\n"
                + "26/11/2026;VIR;100\n"
                + "26/11/2026;VIR;100;C\n");
    assertThat(r.valide()).isFalse();
    assertThat(r.lignes()).isEmpty();
    assertThat(r.problemes())
        .extracting(LectureReleveCsv.Probleme::ligne)
        .containsExactly(2, 3, 4, 5, 6);
  }

  @Test
  void refuseUnFichierVideOuSansOperation() {
    assertThat(lire("").valide()).isFalse();
    assertThat(lire("date_operation;libelle;montant_gnf;sens\n\n").valide()).isFalse();
    assertThat(LectureReleveCsv.lire(new byte[] {(byte) 0xC3, 0x28}).valide()).isFalse();
  }

  @Test
  void refuseUnMontantNulOuNegatif() {
    assertThat(lire("date_operation;libelle;montant_gnf;sens\n26/11/2026;VIR;0;C\n").valide())
        .isFalse();
    assertThat(lire("date_operation;libelle;montant_gnf;sens\n26/11/2026;VIR;-5;C\n").valide())
        .isFalse();
  }
}
