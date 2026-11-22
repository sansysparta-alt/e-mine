package gn.gov.mines.emine.demande.api;

import gn.gov.mines.emine.demande.domaine.Indicateurs;
import gn.gov.mines.emine.demande.domaine.RegleDelais;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Indicateurs du tableau de bord (correctifs ergonomie, US-05.6). Les montants sont en GNF entiers
 * ; les délais en jours de traitement selon la règle unique des délais ({@link RegleDelais}). Les
 * pourcentages sont calculés ici, jamais à l'écran.
 *
 * @param periode clé de la période ({@code mois}, {@code 30-jours}, {@code annee})
 * @param debut début de la période
 * @param fin fin de la période (instant du calcul)
 * @param delivreesDansLeDelai documents délivrés sur la période dans le délai du catalogue
 * @param tauxDansLeDelai part des documents délivrés sur la période qui l'ont été dans le délai du
 *     catalogue, en % arrondi ; {@code null} sans délivrance (les rejets n'y entrent pas)
 * @param rejetees demandes rejetées sur la période (affichées à part)
 * @param parametresDelai règle des délais en vigueur
 */
record IndicateursDto(
    String periode,
    Instant debut,
    Instant fin,
    long enRetard,
    long procheDepassement,
    long complementsAttendus,
    long fraisAttendus,
    long bloques,
    long traitesAujourdhui,
    long delivrees,
    long delivreesDansLeDelai,
    @Schema(nullable = true) Integer tauxDansLeDelai,
    long rejetees,
    @Schema(nullable = true) Double delaiMoyenJours,
    long encaissementsGnf,
    List<ParDocumentDto> parDocument,
    ParametresDelaiDto parametresDelai) {

  /**
   * Chiffres d'un document du catalogue.
   *
   * @param delaiJours délai du catalogue, en jours
   */
  record ParDocumentDto(
      String code,
      long enCours,
      long enRetard,
      int delaiJours,
      long delivrees,
      long delivreesDansLeDelai,
      @Schema(nullable = true) Integer tauxDansLeDelai,
      long rejetees,
      @Schema(nullable = true) Double delaiMoyenJours,
      long encaissementsGnf) {}

  /**
   * Règle des délais en vigueur (paramétrable, à valider par le Ministère).
   *
   * @param depart {@code paiement_frais} ou {@code depot}
   * @param pauseComplement délai en pause pendant un complément
   * @param pauseDroits délai en pause pendant l'attente du paiement des droits
   * @param seuilProchePourcent « proche du dépassement » : % du délai restant
   * @param seuilProcheMinJours « proche du dépassement » : jours restants au moins
   */
  record ParametresDelaiDto(
      @Schema(allowableValues = {"paiement_frais", "depot"}) String depart,
      boolean pauseComplement,
      boolean pauseDroits,
      int seuilProchePourcent,
      int seuilProcheMinJours) {}

  /** Part arrondie, en %, ou {@code null} sans dénominateur. */
  static Integer taux(long partie, long total) {
    return total == 0 ? null : (int) Math.round(100.0 * partie / total);
  }

  static IndicateursDto depuis(Indicateurs i) {
    RegleDelais.Parametres p = i.parametresDelai();
    return new IndicateursDto(
        i.periode().cle(),
        i.debut(),
        i.fin(),
        i.enRetard(),
        i.procheDepassement(),
        i.complementsAttendus(),
        i.fraisAttendus(),
        i.bloques(),
        i.traitesAujourdhui(),
        i.delivrees(),
        i.delivreesDansLeDelai(),
        taux(i.delivreesDansLeDelai(), i.delivrees()),
        i.rejetees(),
        i.delaiMoyenJours(),
        i.encaissementsGnf(),
        i.parDocument().stream()
            .map(
                d ->
                    new ParDocumentDto(
                        d.code(),
                        d.enCours(),
                        d.enRetard(),
                        d.delaiJours(),
                        d.delivrees(),
                        d.delivreesDansLeDelai(),
                        taux(d.delivreesDansLeDelai(), d.delivrees()),
                        d.rejetees(),
                        d.delaiMoyenJours(),
                        d.encaissementsGnf()))
            .toList(),
        new ParametresDelaiDto(
            p.depart(),
            p.pauseComplement(),
            p.pauseDroits(),
            p.seuilProchePourcent(),
            p.seuilProcheMinJours()));
  }
}
