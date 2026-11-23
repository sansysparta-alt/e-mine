package gn.gov.mines.emine.catalogue.domaine;

import java.time.Instant;
import java.util.List;

/**
 * Workflow d'une famille de documents (US-02.5, V20) : étapes et intervenants affichés, règle de
 * paiement affichée, fourchette de délais déduite des fiches de ses documents.
 *
 * @param documents codes des documents de la famille (actifs ou non)
 * @param enVigueur version en vigueur
 */
public record Workflow(
    String code,
    int delaiMinJours,
    int delaiMaxJours,
    List<String> documents,
    VersionWorkflow enVigueur) {

  public Workflow {
    documents = List.copyOf(documents);
  }

  /**
   * Une version d'un workflow.
   *
   * @param modifieePar auteur de la modification, {@code null} pour la version initiale
   * @param etapes étapes dans l'ordre
   */
  public record VersionWorkflow(
      int version,
      String libelle,
      String reglePaiement,
      String motif,
      String modifieePar,
      Instant modifieeLe,
      List<Etape> etapes) {

    public VersionWorkflow {
      etapes = List.copyOf(etapes);
    }
  }

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
