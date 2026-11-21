package gn.gov.mines.emine.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.TestIntegration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

/**
 * V8 : le paiement des frais ne rend plus la demande recevable ; c'est l'agent du guichet qui la
 * déclare recevable, et la base le refuse tant que les frais ne sont pas payés (DEM-006). Le
 * paiement des droits fait toujours passer la demande à « droits payés ».
 */
class RecevabiliteTest extends TestIntegration {

  @Test
  void payerLesFraisNeRendPlusLaDemandeRecevable() throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner()) {
      long demande = demandeDeposee(c, "PEX");
      payer(c, demande, "FRAIS_DOSSIER", 5_000_000);
      assertThat(statut(c, demande)).isEqualTo("deposee");
    }
  }

  @Test
  void leGuichetDeclareRecevableUneDemandeDontLesFraisSontPayes() throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner()) {
      long demande = demandeDeposee(c, "PEX");
      payer(c, demande, "FRAIS_DOSSIER", 5_000_000);
      changerStatut(c, demande, "recevable");
      assertThat(statut(c, demande)).isEqualTo("recevable");
    }
  }

  @Test
  void sansFraisPayesLaRecevabiliteEstRefuseeAvecDem006() throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner()) {
      long demande = demandeDeposee(c, "PEX");
      assertThatThrownBy(() -> changerStatut(c, demande, "recevable"))
          .isInstanceOfSatisfying(
              PSQLException.class,
              e -> assertThat(e.getServerErrorMessage().getHint()).isEqualTo("DEM-006"));
      assertThat(statut(c, demande)).isEqualTo("deposee");
    }
  }

  @Test
  void lePaiementUniqueDUneAttestationVautFraisDeDossier() throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner()) {
      long demande = demandeDeposee(c, "EXC");
      payer(c, demande, "ATTESTATION", 200_000);
      assertThat(statut(c, demande)).isEqualTo("deposee");
      changerStatut(c, demande, "recevable");
      assertThat(statut(c, demande)).isEqualTo("recevable");
    }
  }

  @Test
  void lePaiementDesDroitsFaitToujoursPasserADroitsPayes() throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner()) {
      long demande = demandeDeposee(c, "PEX");
      payer(c, demande, "FRAIS_DOSSIER", 5_000_000);
      for (String s : new String[] {"recevable", "en_instruction", "avis_rendu", "decision"}) {
        changerStatut(c, demande, s);
      }
      payer(c, demande, "DROITS", 15_000_000);
      assertThat(statut(c, demande)).isEqualTo("droits_payes");
    }
  }

  private static long demandeDeposee(Connection c, String type) throws SQLException {
    try (Statement st = c.createStatement()) {
      ResultSet rs =
          st.executeQuery(
              "WITH u AS (INSERT INTO utilisateur (profil_id, nom, prenom, email, telephone,"
                  + " keycloak_id) SELECT id, 'Fictif', 'Recevabilite', 'recev."
                  + UUID.randomUUID()
                  + "@exemple.test', '+224600000000', gen_random_uuid() FROM profil"
                  + " WHERE code = 'INVESTISSEUR' RETURNING id)"
                  + " INSERT INTO demande (type_document_id, demandeur_id, statut)"
                  + " SELECT td.id, u.id, 'deposee' FROM type_document td, u WHERE td.code = '"
                  + type
                  + "' RETURNING id");
      rs.next();
      return rs.getLong(1);
    }
  }

  /** Paiement TrésorPay mené jusqu'au statut « payé » par les transitions autorisées. */
  private static void payer(Connection c, long demande, String objet, long montant)
      throws SQLException {
    UUID id;
    try (PreparedStatement ps =
        c.prepareStatement(
            "INSERT INTO paiement (demande_id, objet, moyen, montant_gnf)"
                + " VALUES (?, ?::objet_paiement, 'TRESORPAY', ?) RETURNING id")) {
      ps.setLong(1, demande);
      ps.setString(2, objet);
      ps.setLong(3, montant);
      ResultSet rs = ps.executeQuery();
      rs.next();
      id = (UUID) rs.getObject(1);
    }
    for (String s : new String[] {"en_attente", "paye"}) {
      try (PreparedStatement ps =
          c.prepareStatement(
              "UPDATE paiement SET statut = ?::statut_paiement, version = version WHERE id = ?")) {
        ps.setString(1, s);
        ps.setObject(2, id);
        ps.executeUpdate();
      }
    }
  }

  private static void changerStatut(Connection c, long demande, String statut) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement("UPDATE demande SET statut = ?::statut_demande WHERE id = ?")) {
      ps.setString(1, statut);
      ps.setLong(2, demande);
      ps.executeUpdate();
    }
  }

  private static String statut(Connection c, long demande) throws SQLException {
    try (Statement st = c.createStatement()) {
      ResultSet rs = st.executeQuery("SELECT statut::text FROM demande WHERE id = " + demande);
      rs.next();
      return rs.getString(1);
    }
  }
}
