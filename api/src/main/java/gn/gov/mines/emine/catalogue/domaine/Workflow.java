package gn.gov.mines.emine.catalogue.domaine;

import java.time.Instant;
import java.util.List;

/**
 * Workflow d'une famille de documents (US-02.5, V20) : étapes et intervenants affichés, règle de
 * paiement affichée, fourchette de délais déduite des fiches de ses documents.
 *
 * @param documents documents de la famille (actifs ou non), par code
 * @param enVigueur version en vigueur
 */
public record Workflow(
    String code,
    int delaiMinJours,
    int delaiMaxJours,
    List<DocumentFamille> documents,
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
