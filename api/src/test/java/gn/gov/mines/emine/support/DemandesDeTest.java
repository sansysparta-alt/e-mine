package gn.gov.mines.emine.support;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Prépare des demandes et des paiements directement en base (en propriétaire du schéma, hors RLS)
 * pour les tests du paiement. Les triggers s'appliquent comme en production.
 */
public final class DemandesDeTest {

  private DemandesDeTest() {}

  /** Demande déposée par la persona (identifiant Keycloak) pour ce type de document. */
  public static long deposee(String keycloakId, String typeDocument) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO demande (type_document_id, demandeur_id, statut)"
                    + " SELECT td.id, u.id, 'deposee' FROM type_document td, utilisateur u"
                    + " WHERE td.code = ? AND u.keycloak_id = ?::uuid RETURNING id")) {
      ps.setString(1, typeDocument);
      ps.setString(2, keycloakId);
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getLong(1);
    }
  }

  /** Fait suivre à la demande les statuts donnés, par les transitions autorisées. */
  public static void changerStatut(long demande, String... statuts) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement("UPDATE demande SET statut = ?::statut_demande WHERE id = ?")) {
      for (String s : statuts) {
        ps.setString(1, s);
        ps.setLong(2, demande);
        ps.executeUpdate();
      }
    }
  }

  /** Passe un paiement aux statuts donnés (transitions contrôlées par la base). */
  public static void changerStatutPaiement(String reference, String... statuts)
      throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "UPDATE paiement SET statut = ?::statut_paiement WHERE reference = ?")) {
      for (String s : statuts) {
        ps.setString(1, s);
        ps.setString(2, reference);
        ps.executeUpdate();
      }
    }
  }

  /** Statut d'un paiement, lu hors RLS. */
  public static String statutPaiement(String reference) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement("SELECT statut::text FROM paiement WHERE reference = ?")) {
      ps.setString(1, reference);
      ResultSet rs = ps.executeQuery();
      return rs.next() ? rs.getString(1) : null;
    }
  }
}
