package gn.gov.mines.emine.demande.domaine;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Règle « À traiter » (correctifs J5) : ce que le profil connecté doit faire <b>maintenant</b>.
 *
 * <ul>
 *   <li>demandeur (droit « déposer », profil externe) : payer les frais, payer les droits,
 *       compléter — sur ses propres demandes (RLS) ;
 *   <li>agent du guichet (droit « instruire », profil qui ne rend pas d'avis technique) : examiner
 *       la recevabilité des demandes dont les frais sont payés ;
 *   <li>profil désigné par le catalogue pour un avis technique (DNM, Géologue, Inspection) : rendre
 *       son avis sur les demandes à l'instruction (US-05.3) ;
 *   <li>tout agent qui instruit : relancer ou clore les compléments demandés par son profil restés
 *       sans réponse dans le délai (US-05.2) ;
 *   <li>signataire (droit « signer », profil interne) : décider puis signer, seulement pour les
 *       documents dont son profil est le signataire au catalogue (D4).
 * </ul>
 *
 * Un profil sans action (responsable fiscal, administrateur…) n'a rien « à traiter ».
 */
public final class RegleATraiter {

  private RegleATraiter() {}

  /** Groupes « à traiter » du profil, dans l'ordre d'affichage. */
  /**
   * @param rendDesAvis le catalogue désigne ce profil pour au moins un avis technique
   */
  public static List<GroupeATraiter> groupesPour(
      boolean interne, Set<String> permissions, boolean rendDesAvis) {
    List<GroupeATraiter> groupes = new ArrayList<>();
    if (!interne && permissions.contains("DEPOSER")) {
      groupes.addAll(
          List.of(
              GroupeATraiter.PAYER_FRAIS, GroupeATraiter.PAYER_DROITS, GroupeATraiter.COMPLETER));
    }
    if (interne && permissions.contains("INSTRUIRE")) {
      groupes.add(rendDesAvis ? GroupeATraiter.AVIS_A_RENDRE : GroupeATraiter.A_EXAMINER);
      groupes.add(GroupeATraiter.SANS_REPONSE);
    }
    if (interne && permissions.contains("SIGNER")) {
      groupes.addAll(List.of(GroupeATraiter.A_DECIDER, GroupeATraiter.A_SIGNER));
    }
    return List.copyOf(groupes);
  }

  /**
   * Filtre « à traiter » appliqué à une liste de demandes.
   *
   * @param groupes groupes retenus (tous ceux du profil, ou un seul)
   * @param profilCode profil connecté, pour les groupes du signataire
   */
  public record Filtre(List<GroupeATraiter> groupes, String profilCode) {

    public Filtre {
      groupes = List.copyOf(groupes);
    }
  }
}
