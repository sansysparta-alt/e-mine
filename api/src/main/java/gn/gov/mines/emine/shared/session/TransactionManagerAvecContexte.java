package gn.gov.mines.emine.shared.session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionDefinition;

/**
 * Gestionnaire de transactions qui renseigne, à l'ouverture de chaque transaction, les variables de
 * session lues par la RLS et les triggers d'audit (US-01.5).
 *
 * <p>{@code set_config(nom, valeur, true)} équivaut à {@code SET LOCAL} : la valeur disparaît à la
 * fin de la transaction, ce qui est sûr avec le pool de connexions. La requête est paramétrée
 * (aucune concaténation de valeurs). Hors transaction, aucune variable n'est posée : la RLS ne
 * montre alors rien, d'où la règle « tout accès aux données dans une transaction ».
 */
public class TransactionManagerAvecContexte extends JdbcTransactionManager {

  static final String SQL =
      "SELECT set_config('emine.user_id', ?, true),"
          + " set_config('emine.interne', ?, true),"
          + " set_config('emine.ip', ?, true)";

  private final transient ObjectProvider<FournisseurContexteSession> fournisseur;

  public TransactionManagerAvecContexte(
      DataSource dataSource, ObjectProvider<FournisseurContexteSession> fournisseur) {
    super(dataSource);
    this.fournisseur = fournisseur;
  }

  @Override
  protected void prepareTransactionalConnection(Connection con, TransactionDefinition definition)
      throws SQLException {
    super.prepareTransactionalConnection(con, definition);
    ContexteSession ctx = contexte();
    try (PreparedStatement ps = con.prepareStatement(SQL)) {
      ps.setString(1, ctx.utilisateurId() == null ? "" : ctx.utilisateurId().toString());
      ps.setString(2, ctx.interne() ? "on" : "off");
      ps.setString(3, ctx.ip() == null ? "" : ctx.ip());
      ps.execute();
    }
  }

  private ContexteSession contexte() {
    FournisseurContexteSession f = fournisseur.getIfAvailable();
    ContexteSession ctx = f == null ? null : f.contexteCourant();
    return ctx == null ? ContexteSession.ANONYME : ctx;
  }
}
