package gn.gov.mines.emine.demande.api;

import gn.gov.mines.emine.demande.domaine.Indicateurs;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Indicateurs du tableau de bord (correctifs ergonomie). Les montants sont en GNF entiers ; les
 * délais en jours calendaires (heure de Conakry) depuis le dépôt.
 *
 * @param periode clé de la période ({@code mois}, {@code 30-jours}, {@code annee})
 * @param debut début de la période
 * @param fin fin de la période (instant du calcul)
 */
record IndicateursDto(
    String periode,
    Instant debut,
    Instant fin,
    long enRetard,
    long complementsAttendus,
    long fraisAttendus,
    long bloques,
    long traitesAujourdhui,
    long delivrees,
    @Schema(nullable = true) Double delaiMoyenJours,
    long encaissementsGnf,
    List<ParDocumentDto> parDocument) {

  /** Chiffres d'un document du catalogue. */
  record ParDocumentDto(
      String code,
      long enCours,
      long enRetard,
      long delivrees,
      @Schema(nullable = true) Double delaiMoyenJours,
      long encaissementsGnf) {}

  static IndicateursDto depuis(Indicateurs i) {
    return new IndicateursDto(
        i.periode().cle(),
        i.debut(),
        i.fin(),
        i.enRetard(),
        i.complementsAttendus(),
        i.fraisAttendus(),
        i.bloques(),
        i.traitesAujourdhui(),
        i.delivrees(),
        i.delaiMoyenJours(),
        i.encaissementsGnf(),
        i.parDocument().stream()
            .map(
                d ->
                    new ParDocumentDto(
                        d.code(),
                        d.enCours(),
                        d.enRetard(),
                        d.delivrees(),
                        d.delaiMoyenJours(),
                        d.encaissementsGnf()))
            .toList());
  }
}
