package gn.gov.mines.emine.shared.session;

/**
 * Donne le contexte de l'utilisateur courant (US-01.5). Implémenté par le module {@code auth} à
 * partir du jeton OIDC, et par le module {@code paiement} pour le seul traitement d'une
 * notification TrésorPay vérifiée (US-04.4). Les fournisseurs sont consultés par ordre de priorité
 * ({@code @Order}) ; le premier qui renvoie un contexte l'emporte ; sans contexte, {@link
 * ContexteSession#ANONYME}.
 */
@FunctionalInterface
public interface FournisseurContexteSession {

  /** Contexte à appliquer, ou {@code null} si ce fournisseur ne s'applique pas. */
  ContexteSession contexteCourant();
}
