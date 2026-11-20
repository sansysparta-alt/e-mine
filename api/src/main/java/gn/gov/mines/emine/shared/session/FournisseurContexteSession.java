package gn.gov.mines.emine.shared.session;

/**
 * Donne le contexte de l'utilisateur courant (US-01.5). Implémenté par le module {@code auth} à
 * partir du jeton OIDC ; en son absence, le contexte est {@link ContexteSession#ANONYME}.
 */
@FunctionalInterface
public interface FournisseurContexteSession {

  ContexteSession contexteCourant();
}
