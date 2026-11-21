package gn.gov.mines.emine.shared.session;

import static gn.gov.mines.emine.shared.jooq.Tables.DEMANDE;
import static gn.gov.mines.emine.shared.jooq.Tables.PAIEMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.PIECE_JOINTE;
import static gn.gov.mines.emine.shared.jooq.Tables.TYPE_DOCUMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.TestIntegration;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * US-01.5 : chaque transaction renseigne emine.user_id, emine.interne et emine.ip ; un investisseur
 * ne voit que ses demandes et paiements, un agent interne voit tout ; le journal d'audit trace
 * l'auteur et l'IP. Pool réduit à une connexion pour prouver que le contexte ne fuit pas d'une
 * transaction à l'autre.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=1")
class RlsTest extends TestIntegration {

  private static final ThreadLocal<ContexteSession> COURANT = new ThreadLocal<>();

  @TestConfiguration
  static class Contexte {
    @Bean
    @Primary
    FournisseurContexteSession fournisseurDeTest() {
      return COURANT::get;
    }
  }

  @Autowired DSLContext dsl;
  @Autowired TransactionTemplate tx;
  @Autowired JdbcTemplate jdbc;

  private long investisseurA;
  private long investisseurB;
  private long agentGuichet;
  private long demandeA;
  private long demandeB;
  private UUID paiementA;
  private long pieceA;

  @BeforeAll
  void donnees() throws SQLException {
    String suffixe = Long.toString(System.nanoTime());
    try (Connection c = BaseDeDonneesDeTest.connexionOwner()) {
      investisseurA = utilisateur(c, "INVESTISSEUR", "rls.a." + suffixe + "@exemple.test");
      investisseurB = utilisateur(c, "INVESTISSEUR", "rls.b." + suffixe + "@exemple.test");
      agentGuichet = utilisateur(c, "GUICHET", "rls.guichet." + suffixe + "@exemple.test");
      demandeA = demande(c, investisseurA);
      demandeB = demande(c, investisseurB);
      paiementA = paiement(c, demandeA, investisseurA);
      paiement(c, demandeB, investisseurB);
      pieceA = piece(c, demandeA, investisseurA);
    }
  }

  @AfterEach
  void oublierLeContexte() {
    COURANT.remove();
  }

  @Test
  void unInvestisseurNeVoitQueSesDemandes() {
    assertThat(demandesVisibles(new ContexteSession(investisseurA, false, null)))
        .containsExactly(demandeA);
    assertThat(demandesVisibles(new ContexteSession(investisseurB, false, null)))
        .containsExactly(demandeB);
  }

  @Test
  void unInvestisseurNeVoitQueSesPaiements() {
    List<UUID> visibles =
        enTant(
            new ContexteSession(investisseurA, false, null),
            () ->
                dsl.select(PAIEMENT.ID)
                    .from(PAIEMENT)
                    .where(PAIEMENT.DEMANDE_ID.in(demandeA, demandeB))
                    .fetch(PAIEMENT.ID));
    assertThat(visibles).containsExactly(paiementA);
  }

  @Test
  void lesPiecesJointesSuiventLaRlsDeLaDemandeV6() {
    java.util.function.Function<ContexteSession, List<Long>> visibles =
        ctx ->
            enTant(
                ctx,
                () ->
                    dsl.select(PIECE_JOINTE.ID)
                        .from(PIECE_JOINTE)
                        .where(PIECE_JOINTE.ID.eq(pieceA))
                        .fetch(PIECE_JOINTE.ID));
    assertThat(visibles.apply(new ContexteSession(investisseurA, false, null)))
        .containsExactly(pieceA);
    assertThat(visibles.apply(new ContexteSession(investisseurB, false, null))).isEmpty();
    assertThat(visibles.apply(new ContexteSession(agentGuichet, true, null)))
        .containsExactly(pieceA);
    assertThat(visibles.apply(null)).isEmpty();
  }

  @Test
  void unInvestisseurNePeutPasJoindreUnePieceALaDemandeDUnAutre() {
    assertThatThrownBy(
            () ->
                enTant(
                    new ContexteSession(investisseurB, false, null),
                    () ->
                        dsl.insertInto(PIECE_JOINTE)
                            .set(PIECE_JOINTE.DEMANDE_ID, demandeA)
                            .set(PIECE_JOINTE.TYPE_PIECE, "Intrusion")
                            .set(PIECE_JOINTE.NOM_FICHIER, "x.pdf")
                            .set(PIECE_JOINTE.MIME_TYPE, "application/pdf")
                            .set(PIECE_JOINTE.TAILLE_OCTETS, 10L)
                            .set(PIECE_JOINTE.EMPREINTE_SHA256, "b".repeat(64))
                            .set(PIECE_JOINTE.STOCKAGE_CLE, "intrus/" + System.nanoTime())
                            .set(PIECE_JOINTE.DEPOSE_PAR, investisseurB)
                            .execute()))
        .rootCause()
        .hasMessageContaining("row-level security");
  }

  @Test
  void unAgentInterneVoitToutesLesDemandes() {
    assertThat(demandesVisibles(new ContexteSession(agentGuichet, true, null)))
        .containsExactlyInAnyOrder(demandeA, demandeB);
  }

  @Test
  void sansContexteAucuneDemandeNestVisible() {
    assertThat(demandesVisibles(null)).isEmpty();
  }

  @Test
  void unInvestisseurNePeutPasDeposerAuNomDUnAutre() {
    assertThatThrownBy(
            () ->
                enTant(
                    new ContexteSession(investisseurA, false, null),
                    () ->
                        dsl.insertInto(DEMANDE)
                            .set(DEMANDE.DEMANDEUR_ID, investisseurB)
                            .set(
                                DEMANDE.TYPE_DOCUMENT_ID,
                                dsl.select(TYPE_DOCUMENT.ID)
                                    .from(TYPE_DOCUMENT)
                                    .where(TYPE_DOCUMENT.CODE.eq("PEX"))
                                    .fetchOne(TYPE_DOCUMENT.ID))
                            .execute()))
        .rootCause()
        .hasMessageContaining("row-level security");
  }

  @Test
  void leJournalDAuditTraceLAuteurEtLIp() throws SQLException {
    enTant(
        new ContexteSession(investisseurA, false, "203.0.113.7"),
        () ->
            dsl.update(DEMANDE)
                .set(DEMANDE.SUPERFICIE_HA, new BigDecimal("12.50"))
                .where(DEMANDE.ID.eq(demandeA))
                .execute());

    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT utilisateur_id, host(adresse_ip) FROM journal_audit"
                    + " WHERE table_nom = 'demande' AND objet_id = ? AND operation = 'U'"
                    + " ORDER BY id DESC LIMIT 1")) {
      ps.setString(1, Long.toString(demandeA));
      ResultSet rs = ps.executeQuery();
      assertThat(rs.next()).isTrue();
      assertThat(rs.getLong(1)).isEqualTo(investisseurA);
      assertThat(rs.getString(2)).isEqualTo("203.0.113.7");
    }
  }

  @Test
  void leContexteNeFuitPasHorsDeLaTransaction() {
    demandesVisibles(new ContexteSession(investisseurA, true, "203.0.113.7"));
    // Même connexion (pool à 1), hors transaction : SET LOCAL a disparu.
    assertThat(jdbc.queryForObject("SELECT current_setting('emine.user_id', true)", String.class))
        .isNullOrEmpty();
    assertThat(jdbc.queryForObject("SELECT current_setting('emine.interne', true)", String.class))
        .isNotEqualTo("on");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM demande", Integer.class)).isZero();
  }

  @Test
  void lesVuesAppliquentLaRlsDeLAppelant() {
    String sql = "SELECT count(*) FROM v_demande_suivi WHERE id IN (?, ?)";
    Integer vuesParA =
        enTant(
            new ContexteSession(investisseurA, false, null),
            () -> jdbc.queryForObject(sql, Integer.class, demandeA, demandeB));
    Integer vuesParAgent =
        enTant(
            new ContexteSession(agentGuichet, true, null),
            () -> jdbc.queryForObject(sql, Integer.class, demandeA, demandeB));
    assertThat(vuesParA).isEqualTo(1);
    assertThat(vuesParAgent).isEqualTo(2);
  }

  @Test
  void uneIpInvalideEstIgnoree() {
    assertThat(new ContexteSession(1L, false, "1.2.3.4'; DROP TABLE x;--").ip()).isNull();
    assertThat(new ContexteSession(1L, false, "2001:db8::1").ip()).isEqualTo("2001:db8::1");
  }

  private List<Long> demandesVisibles(ContexteSession ctx) {
    return enTant(
        ctx,
        () ->
            dsl.select(DEMANDE.ID)
                .from(DEMANDE)
                .where(DEMANDE.ID.in(demandeA, demandeB))
                .fetch(DEMANDE.ID));
  }

  private <T> T enTant(ContexteSession ctx, java.util.function.Supplier<T> action) {
    if (ctx == null) {
      COURANT.remove();
    } else {
      COURANT.set(ctx);
    }
    return tx.execute(statut -> action.get());
  }

  private static long utilisateur(Connection c, String profil, String email) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "INSERT INTO utilisateur (profil_id, nom, prenom, email, telephone, keycloak_id)"
                + " SELECT id, 'Fictif', 'Rls', ?, '+224600000000', gen_random_uuid()"
                + " FROM profil WHERE code = ? RETURNING id")) {
      ps.setString(1, email);
      ps.setString(2, profil);
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getLong(1);
    }
  }

  private static long demande(Connection c, long demandeur) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "INSERT INTO demande (type_document_id, demandeur_id)"
                + " SELECT id, ? FROM type_document WHERE code = 'PEX' RETURNING id")) {
      ps.setLong(1, demandeur);
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getLong(1);
    }
  }

  private static long piece(Connection c, long demande, long auteur) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "INSERT INTO piece_jointe (demande_id, type_piece, nom_fichier, mime_type,"
                + " taille_octets, empreinte_sha256, stockage_cle, depose_par)"
                + " VALUES (?, 'EIES', 'eies.pdf', 'application/pdf', 1000, repeat('a', 64), ?, ?)"
                + " RETURNING id")) {
      ps.setLong(1, demande);
      ps.setString(2, "rls/" + System.nanoTime());
      ps.setLong(3, auteur);
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getLong(1);
    }
  }

  private static UUID paiement(Connection c, long demande, long payeur) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "INSERT INTO paiement (demande_id, objet, moyen, montant_gnf, payeur_id)"
                + " VALUES (?, 'FRAIS_DOSSIER', 'TRESORPAY', 5000000, ?) RETURNING id")) {
      ps.setLong(1, demande);
      ps.setLong(2, payeur);
      ResultSet rs = ps.executeQuery();
      rs.next();
      return (UUID) rs.getObject(1);
    }
  }
}
