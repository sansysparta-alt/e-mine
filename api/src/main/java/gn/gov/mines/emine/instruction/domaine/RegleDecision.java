package gn.gov.mines.emine.instruction.domaine;

/**
 * Décision du signataire (US-05.4) : une demande est décidée par le profil que le catalogue désigne
 * comme signataire du document (D4, V9) — jamais par un profil codé en dur — dès la recevabilité si
 * le document ne requiert aucun avis, sinon une fois tous les avis techniques rendus (US-05.3).
 * Favorable : la demande attend le paiement des droits ; si le document n'a pas de droits à payer
 * (attestation payée au dépôt, carte d'orpailleur…), elle passe aussitôt à « droits payés » (prête
 * pour la délivrance). Défavorable : la demande est rejetée avec un motif obligatoire.
 */
public final class RegleDecision {

  private RegleDecision() {}

  /**
   * La demande attend une décision : « avis rendu », ou « recevable » pour un document sans avis
   * requis.
   */
  public static boolean aDecider(String statut, boolean avisRequis) {
    return "avis_rendu".equals(statut) || (!avisRequis && "recevable".equals(statut));
  }

  /** Ce profil est-il le signataire de ce document ? */
  public static boolean estSignataire(String profilCode, String signataireProfil) {
    return profilCode != null && profilCode.equals(signataireProfil);
  }

  /**
   * Statut final d'une décision favorable.
   *
   * @param droitsGnf droits du barème en vigueur, {@code null} si le document n'en a pas
   */
  public static String statutApresFavorable(Long droitsGnf) {
    return droitsGnf != null ? "decision" : "droits_payes";
  }
}
