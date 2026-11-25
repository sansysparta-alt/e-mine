package gn.gov.mines.emine.catalogue.domaine;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Workflow d'une famille de documents (US-02.5, V20) : étapes et intervenants affichés, règle de
 * paiement affichée, fourchette de délais déduite des fiches de ses documents. Gouvernance
 * (US-02.8, V27) : une version est proposée par l'administrateur, validée par le Ministre,
 * appliquée à sa date d'effet.
 *
 * @param documents documents de la famille (actifs ou non), par code
 * @param enVigueur version en vigueur aujourd'hui
 * @param aValider proposition en attente du Ministre, ou {@code null}
 * @param programmee version validée dont la date d'effet n'est pas arrivée, ou {@code null}
 */
public record Workflow(
    String code,
    int delaiMinJours,
    int delaiMaxJours,
    List<DocumentFamille> documents,
    VersionWorkflow enVigueur,
    VersionWorkflow aValider,
    VersionWorkflow programmee) {

  public Workflow {
    documents = List.copyOf(documents);
  }

  /**
   * Une version d'un workflow.
   *
   * @param etat {@code a_valider}, {@code en_vigueur}, {@code programmee}, {@code remplacee},
   *     {@code refusee} ou {@code retiree} ({@link
   *     gn.gov.mines.emine.shared.gouvernance.EtatVersion})
   * @param modifieePar administrateur qui l'a proposée, {@code null} pour la version initiale
   * @param modifieeLe date de la proposition
   * @param dateEffet date d'application aux demandes déposées (heure de Conakry)
   * @param decideePar personne qui a validé, refusé ou retiré, ou {@code null}
   * @param etapes étapes dans l'ordre
   */
  public record VersionWorkflow(
      int version,
      String etat,
      String libelle,
      String reglePaiement,
      String motif,
      String modifieePar,
      Instant modifieeLe,
      LocalDate dateEffet,
      String decideePar,
      Instant decideeLe,
      String motifRefus,
      List<Etape> etapes) {

    public VersionWorkflow {
      etapes = List.copyOf(etapes);
    }
  }

  /**
   * Document d'une famille : code, nom dans les deux langues et disponibilité à la demande (un
   * document désactivé reste dans sa famille, signalé comme tel).
   */
  public record DocumentFamille(String code, String libelleFr, String libelleEn, boolean actif) {}

  /**
   * Étape d'un workflow.
   *
   * @param acteur intervenant (Guichet unique, DNM, Ministre…)
   * @param libelle ce qui est fait à cette étape
   */
  public record Etape(String acteur, String libelle) {}

  /** Nombre d'étapes et de caractères acceptés (comme la base, V20). */
  public static final int ETAPES_MAX = 12;
}
