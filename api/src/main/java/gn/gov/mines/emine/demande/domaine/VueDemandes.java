package gn.gov.mines.emine.demande.domaine;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Vues de la liste des demandes selon le profil (correctifs J5) : <b>exclusives et exhaustives</b>
 * — chaque demande visible tombe dans une seule vue, et « Toutes » est la somme des vues du profil.
 */
public enum VueDemandes {
  /** Ce que le profil connecté doit faire maintenant (règle {@link RegleATraiter}). */
  A_TRAITER("a-traiter"),
  /** Agents : la demande attend le demandeur (frais, droits, complément). */
  ATTENTE_DEMANDEUR("attente-demandeur"),
  /** Le reste des demandes en cours de traitement. */
  EN_COURS("en-cours"),
  /** Demandeur : ses brouillons (jamais visibles d'un agent). */
  BROUILLONS("brouillons"),
  /** Document délivré (ou dossier archivé). */
  TERMINEES("terminees"),
  /** Demande rejetée (non recevable ou rejet motivé). */
  REJETEES("rejetees");

  /** Statuts d'une demande en cours de traitement. */
  public static final List<String> STATUTS_ACTIFS =
      List.of(
          "deposee",
          "complement_requis",
          "recevable",
          "en_instruction",
          "avis_rendu",
          "decision",
          "droits_payes");

  /** Groupes où l'action appartient au demandeur. */
  public static final List<GroupeATraiter> GROUPES_DEMANDEUR =
      List.of(GroupeATraiter.PAYER_FRAIS, GroupeATraiter.PAYER_DROITS, GroupeATraiter.COMPLETER);

  private final String cle;

  VueDemandes(String cle) {
    this.cle = cle;
  }

  public String cle() {
    return cle;
  }

  public static Optional<VueDemandes> depuis(String cle) {
    return Arrays.stream(values()).filter(v -> v.cle.equals(cle)).findFirst();
  }

  /** Vues proposées au profil, dans l'ordre d'affichage. */
  public static List<VueDemandes> pour(boolean interne, boolean aDesActions) {
    if (!interne) {
      return List.of(A_TRAITER, EN_COURS, BROUILLONS, TERMINEES, REJETEES);
    }
    return aDesActions
        ? List.of(A_TRAITER, ATTENTE_DEMANDEUR, EN_COURS, TERMINEES, REJETEES)
        : List.of(ATTENTE_DEMANDEUR, EN_COURS, TERMINEES, REJETEES);
  }
}
