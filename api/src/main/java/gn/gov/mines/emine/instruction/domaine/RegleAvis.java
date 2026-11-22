package gn.gov.mines.emine.instruction.domaine;

import java.util.Collection;
import java.util.List;

/**
 * Avis techniques motivés (US-05.3, choix du porteur du projet du 22/11/2026) : les avis sont
 * requis <b>selon le catalogue</b> ({@code type_document_avis}, V13).
 *
 * <ul>
 *   <li>une demande déclarée recevable dont le document requiert des avis part à l'instruction
 *       technique ({@code en_instruction}) ;
 *   <li>chaque profil requis (DNM, Géologue, Inspection…) rend un seul avis, favorable ou
 *       défavorable, toujours motivé ; il peut aussi demander un complément (US-05.2) ;
 *   <li>quand tous les avis sont rendus, la demande passe à « avis rendu » et le signataire du
 *       catalogue décide au vu des avis (un avis ne décide jamais à sa place) ;
 *   <li>un document sans avis requis est décidé dès la recevabilité (V9).
 * </ul>
 */
public final class RegleAvis {

  /** Statut où les avis se rendent. */
  public static final String STATUT_INSTRUCTION = "en_instruction";

  private RegleAvis() {}

  /** Statut après une déclaration de recevabilité. */
  public static String statutApresRecevabilite(List<String> profilsRequis) {
    return profilsRequis.isEmpty() ? "recevable" : STATUT_INSTRUCTION;
  }

  /** Le profil doit-il rendre son avis maintenant ? */
  public static boolean aRendre(
      String statut, List<String> profilsRequis, String profil, Collection<String> dejaRendus) {
    return STATUT_INSTRUCTION.equals(statut)
        && profil != null
        && profilsRequis.contains(profil)
        && !dejaRendus.contains(profil);
  }

  /** Profils requis qui n'ont pas encore rendu leur avis. */
  public static List<String> manquants(List<String> profilsRequis, Collection<String> rendus) {
    return profilsRequis.stream().filter(p -> !rendus.contains(p)).toList();
  }
}
