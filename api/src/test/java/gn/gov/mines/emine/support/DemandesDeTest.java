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

  /** Brouillon (pas encore déposé) de la persona pour ce type de document. */
  public static long brouillon(String keycloakId, String typeDocument) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO demande (type_document_id, demandeur_id)"
                    + " SELECT td.id, u.id FROM type_document td, utilisateur u"
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

  /** Paiement TrésorPay de l'objet donné, mené jusqu'à « payé » (les triggers s'appliquent). */
  public static void payer(long demande, String objet, long montant) throws SQLException {
    String reference;
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO paiement (demande_id, objet, moyen, montant_gnf)"
                    + " VALUES (?, ?::objet_paiement, 'TRESORPAY', ?) RETURNING reference")) {
      ps.setLong(1, demande);
      ps.setString(2, objet);
      ps.setLong(3, montant);
      ResultSet rs = ps.executeQuery();
      rs.next();
      reference = rs.getString(1);
    }
    changerStatutPaiement(reference, "en_attente", "paye");
  }

  /** Pièce jointe fictive (aucun fichier dans le stockage) d'un type donné. */
  public static void piece(long demande, String typePiece) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO piece_jointe (demande_id, type_piece, nom_fichier, mime_type,"
                    + " taille_octets, empreinte_sha256, stockage_cle, depose_par)"
                    + " SELECT d.id, ?, 'piece.pdf', 'application/pdf', 1000, repeat('a', 64),"
                    + " 'tests/' || gen_random_uuid(), d.demandeur_id FROM demande d WHERE d.id = ?")) {
      ps.setString(1, typePiece);
      ps.setLong(2, demande);
      ps.executeUpdate();
    }
  }

  /** Statut d'une demande et son motif de rejet, lus hors RLS. */
  public static String[] statutEtMotif(long demande) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT statut::text, motif_rejet, date_recevabilite IS NOT NULL FROM demande"
                    + " WHERE id = ?")) {
      ps.setLong(1, demande);
      ResultSet rs = ps.executeQuery();
      rs.next();
      return new String[] {rs.getString(1), rs.getString(2), String.valueOf(rs.getBoolean(3))};
    }
  }

  /** Auteur (identifiant Keycloak) du dernier changement de statut de la demande. */
  public static String auteurDernierStatut(long demande) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT u.keycloak_id::text FROM historique_statut_demande h"
                    + " JOIN utilisateur u ON u.id = h.auteur_id WHERE h.demande_id = ?"
                    + " ORDER BY h.id DESC LIMIT 1")) {
      ps.setLong(1, demande);
      ResultSet rs = ps.executeQuery();
      return rs.next() ? rs.getString(1) : null;
    }
  }

  /**
   * Mène la demande (déposée) jusqu'à « droits payés » comme le ferait le parcours : frais payés,
   * recevable, décision favorable du signataire (inscrite dans avis_decision), droits payés.
   */
  public static void pretePourLaDelivrance(
      long demande, String signataireKeycloakId, String objetFrais, long frais, Long droits)
      throws SQLException {
    payer(demande, objetFrais, frais);
    changerStatut(demande, "recevable");
    avisRendus(demande);
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO avis_decision (demande_id, auteur_id, nature, sens, motif)"
                    + " SELECT ?, id, 'decision', 'favorable', 'Dossier conforme.'"
                    + " FROM utilisateur WHERE keycloak_id = ?::uuid")) {
      ps.setLong(1, demande);
      ps.setString(2, signataireKeycloakId);
      ps.executeUpdate();
    }
    changerStatut(demande, "decision");
    if (droits != null) {
      payer(demande, "DROITS", droits);
    } else {
      changerStatut(demande, "droits_payes");
    }
  }

  /**
   * Complément ouvert il y a {@code joursAvant} jours par l'agent (identifiant Keycloak) : la
   * demande passe de {@code origine} à « complément requis » ; la base pose le délai de 5 jours.
   */
  public static void complementOuvert(
      long demande, String agentKeycloakId, String origine, int joursAvant, String... pieces)
      throws SQLException {
    changerStatut(demande, "complement_requis");
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO complement (demande_id, demande_par, statut_origine, motif,"
                    + " pieces_demandees, demande_le, date_limite)"
                    + " SELECT ?, u.id, ?::statut_demande, 'Merci de compléter le dossier.', ?,"
                    + " now() - make_interval(days => ?), now()"
                    + " FROM utilisateur u WHERE u.keycloak_id = ?::uuid")) {
      ps.setLong(1, demande);
      ps.setString(2, origine);
      ps.setArray(3, c.createArrayOf("text", pieces));
      ps.setInt(4, joursAvant);
      ps.setString(5, agentKeycloakId);
      ps.executeUpdate();
    }
  }

  /** Réponse au complément ouvert il y a {@code joursAvant} jours, inscrite hors RLS. */
  public static void complementRepondu(long demande, int joursAvant) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "UPDATE complement co SET repondu_le = now() - make_interval(days => ?),"
                    + " repondu_par = d.demandeur_id FROM demande d"
                    + " WHERE d.id = co.demande_id AND co.demande_id = ?"
                    + " AND co.repondu_le IS NULL AND co.clos_le IS NULL")) {
      ps.setInt(1, joursAvant);
      ps.setLong(2, demande);
      ps.executeUpdate();
    }
  }

  /** Date de dépôt de la demande ramenée à {@code joursAvant} jours. */
  public static void deposeeIlYA(long demande, int joursAvant) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "UPDATE demande SET date_depot = now() - make_interval(days => ?) WHERE id = ?")) {
      ps.setInt(1, joursAvant);
      ps.setLong(2, demande);
      ps.executeUpdate();
    }
  }

  /**
   * Nombre d'événements notifiés (un e-mail par événement ; chaque événement écrit aussi un SMS et
   * une notification interne selon ses canaux, US-07.2).
   */
  public static int notifications(long demande, String modele) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT count(*) FROM notification WHERE demande_id = ? AND modele = ?"
                    + " AND canal = 'email'")) {
      ps.setLong(1, demande);
      ps.setString(2, modele);
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getInt(1);
    }
  }

  /**
   * US-05.3 : pour une demande recevable dont le document requiert des avis, la fait passer à
   * l'instruction, inscrit un avis favorable de chaque profil requis (persona du seed) et la passe
   * à « avis rendu ». Sans avis requis, la demande reste recevable. Renvoie le nombre d'avis
   * inscrits.
   */
  public static int avisRendus(long demande) throws SQLException {
    if (avisRequis(demande) == 0) {
      return 0;
    }
    changerStatut(demande, "en_instruction");
    int n;
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO avis_decision (demande_id, auteur_id, nature, sens, motif)"
                    + " SELECT d.id, (SELECT u.id FROM utilisateur u WHERE u.profil_id ="
                    + " tda.profil_id ORDER BY u.id LIMIT 1), 'avis', 'favorable',"
                    + " 'Avis favorable : dossier technique conforme.' FROM demande d"
                    + " JOIN type_document_avis tda ON tda.type_document_id = d.type_document_id"
                    + " WHERE d.id = ?")) {
      ps.setLong(1, demande);
      n = ps.executeUpdate();
    }
    changerStatut(demande, "avis_rendu");
    return n;
  }

  /** Nombre d'avis requis par le catalogue pour le document de la demande. */
  public static int avisRequis(long demande) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT count(*) FROM type_document_avis tda JOIN demande d"
                    + " ON d.type_document_id = tda.type_document_id WHERE d.id = ?")) {
      ps.setLong(1, demande);
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getInt(1);
    }
  }
}
