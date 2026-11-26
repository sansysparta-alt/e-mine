package gn.gov.mines.emine.paiement.domaine;

import gn.gov.mines.emine.shared.jooq.enums.MoyenPaiement;
import java.util.List;

/**
 * Moyens de paiement proposés (US-04.2) : TrésorPay et virement bancaire (US-04.7, décision V1-3 du
 * porteur du projet). Un moyen peut être affiché désactivé, avec sa raison.
 */
public final class MoyensPaiement {

  private MoyensPaiement() {}

  /** Moyen proposé à l'écran, actif ou non. */
  public record MoyenPropose(MoyenPaiement moyen, boolean disponible) {}

  /** Moyens dans l'ordre d'affichage ; aucun n'est présélectionné. */
  public static List<MoyenPropose> proposes() {
    return List.of(
        new MoyenPropose(MoyenPaiement.TRESORPAY, true),
        new MoyenPropose(MoyenPaiement.VIREMENT, true));
  }

  public static boolean disponible(MoyenPaiement moyen) {
    return proposes().stream().anyMatch(m -> m.moyen() == moyen && m.disponible());
  }
}
