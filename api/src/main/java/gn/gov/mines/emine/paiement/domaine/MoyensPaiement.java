package gn.gov.mines.emine.paiement.domaine;

import gn.gov.mines.emine.shared.jooq.enums.MoyenPaiement;
import java.util.List;

/**
 * Moyens de paiement proposés (US-04.2). En V1, TrésorPay seul est actif ; le virement est affiché
 * « bientôt disponible » (US-04.7, plan complet).
 */
public final class MoyensPaiement {

  private MoyensPaiement() {}

  /** Moyen proposé à l'écran, actif ou non. */
  public record MoyenPropose(MoyenPaiement moyen, boolean disponible) {}

  /** Moyens dans l'ordre d'affichage ; aucun n'est présélectionné. */
  public static List<MoyenPropose> proposes() {
    return List.of(
        new MoyenPropose(MoyenPaiement.TRESORPAY, true),
        new MoyenPropose(MoyenPaiement.VIREMENT, false));
  }

  public static boolean disponible(MoyenPaiement moyen) {
    return proposes().stream().anyMatch(m -> m.moyen() == moyen && m.disponible());
  }
}
