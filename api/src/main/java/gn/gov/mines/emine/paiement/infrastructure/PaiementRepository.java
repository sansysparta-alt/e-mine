package gn.gov.mines.emine.paiement.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.PAIEMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.PAIEMENT_EVENEMENT;

import gn.gov.mines.emine.paiement.domaine.Paiement;
import gn.gov.mines.emine.shared.jooq.Tables;
import gn.gov.mines.emine.shared.jooq.enums.MoyenPaiement;
import gn.gov.mines.emine.shared.jooq.enums.ObjetPaiement;
import gn.gov.mines.emine.shared.jooq.enums.StatutPaiement;
import gn.gov.mines.emine.shared.jooq.tables.records.PaiementRecord;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * Accès jOOQ aux paiements. Toujours appelé dans une transaction : la RLS ne montre à un usager que
 * les paiements de ses demandes. Référence, expiration et reçu sont posés par les triggers ({@code
 * fn_paiement_avant}) ; les changements de statut passent par {@code transition_statut}.
 */
@Repository
public class PaiementRepository {

  private final DSLContext dsl;

  /** Instant courant de la base (transaction). */
  private static final Field<Instant> MAINTENANT =
      DSL.field("now()", PAIEMENT.DATE_EXPIRATION.getDataType());

  public PaiementRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** Crée un paiement au statut {@code initie} ; la base attribue la référence PAY-AAAA-NNNNNN. */
  public Paiement creer(
      long demandeId, ObjetPaiement objet, MoyenPaiement moyen, long montantGnf, long payeurId) {
    PaiementRecord r =
        dsl.insertInto(PAIEMENT)
            .set(PAIEMENT.DEMANDE_ID, demandeId)
            .set(PAIEMENT.OBJET, objet)
            .set(PAIEMENT.MOYEN, moyen)
            .set(PAIEMENT.MONTANT_GNF, montantGnf)
            .set(PAIEMENT.PAYEUR_ID, payeurId)
            .returning()
            .fetchOne();
    return versDomaine(r);
  }

  /**
   * Annule les paiements encore ouverts (initié, en attente) de cet objet : changement de moyen ou
   * nouvelle tentative (US-04.2). Renvoie le nombre de paiements annulés.
   */
  public int annulerOuverts(long demandeId, ObjetPaiement objet) {
    return dsl.update(PAIEMENT)
        .set(PAIEMENT.STATUT, StatutPaiement.annule)
        .where(PAIEMENT.DEMANDE_ID.eq(demandeId))
        .and(PAIEMENT.OBJET.eq(objet))
        .and(PAIEMENT.STATUT.in(StatutPaiement.initie, StatutPaiement.en_attente))
        .execute();
  }

  /**
   * Paiements en ligne en attente dont la notification tarde (US-04.5) : TrésorPay, {@code
   * en_attente}, session ouverte depuis plus longtemps que {@code avant}.
   */
  public List<Paiement> enAttenteAInterroger(Instant avant) {
    return dsl.selectFrom(PAIEMENT)
        .where(PAIEMENT.MOYEN.eq(MoyenPaiement.TRESORPAY))
        .and(PAIEMENT.STATUT.eq(StatutPaiement.en_attente))
        .and(PAIEMENT.UPDATED_AT.le(avant))
        .orderBy(PAIEMENT.UPDATED_AT)
        .fetch(PaiementRepository::versDomaine);
  }

  /** Trace d'une interrogation de la passerelle (réponse reçue ou absence de réponse). */
  public void journaliserInterrogation(
      UUID paiementId, String reference, String transactionId, String payloadJson) {
    dsl.insertInto(PAIEMENT_EVENEMENT)
        .set(PAIEMENT_EVENEMENT.PAIEMENT_ID, paiementId)
        .set(PAIEMENT_EVENEMENT.REFERENCE_RECUE, reference)
        .set(PAIEMENT_EVENEMENT.TRANSACTION_ID, transactionId)
        .set(PAIEMENT_EVENEMENT.TYPE_EVENEMENT, "INTERROGATION")
        .set(PAIEMENT_EVENEMENT.PAYLOAD, JSONB.valueOf(payloadJson))
        .set(PAIEMENT_EVENEMENT.SIGNATURE_VALIDE, true)
        .set(PAIEMENT_EVENEMENT.TRAITE, true)
        .onConflictDoNothing()
        .execute();
  }

  /**
   * Références dont la date limite est passée sans paiement (US-04.5) : virement non déclaré (7
   * jours) ou session en ligne jamais ouverte → {@code expire}. Renvoie le nombre de paiements.
   */
  public int expirerReferences() {
    return dsl.update(PAIEMENT)
        .set(PAIEMENT.STATUT, StatutPaiement.expire)
        .where(PAIEMENT.STATUT.eq(StatutPaiement.initie))
        .and(
            PAIEMENT.DATE_EXPIRATION.lt(DSL.field("now()", PAIEMENT.DATE_EXPIRATION.getDataType())))
        .execute();
  }

  /**
   * Déclaration d'un virement (US-04.7) : justificatif rattaché, passage à {@code en_attente}
   * (transition contrôlée par la base). Seulement un virement {@code initie} dont la référence n'a
   * pas expiré ; renvoie le nombre de lignes modifiées (0 sinon).
   */
  public int declarerVirement(UUID id, long justificatifPieceId) {
    return dsl.update(PAIEMENT)
        .set(PAIEMENT.STATUT, StatutPaiement.en_attente)
        .set(PAIEMENT.JUSTIFICATIF_PIECE_ID, justificatifPieceId)
        .set(PAIEMENT.VIREMENT_DECLARE_LE, MAINTENANT)
        .where(PAIEMENT.ID.eq(id))
        .and(PAIEMENT.MOYEN.eq(MoyenPaiement.VIREMENT))
        .and(PAIEMENT.STATUT.eq(StatutPaiement.initie))
        .and(PAIEMENT.DATE_EXPIRATION.gt(MAINTENANT))
        .execute();
  }

  /**
   * Un paiement de cet objet est en cours de vérification (virement déclaré, ou « à vérifier ») :
   * le demandeur ne peut pas en ouvrir un autre.
   */
  public boolean estEnVerification(long demandeId, ObjetPaiement objet) {
    return dsl.fetchExists(
        PAIEMENT,
        PAIEMENT
            .DEMANDE_ID
            .eq(demandeId)
            .and(PAIEMENT.OBJET.eq(objet))
            .and(
                PAIEMENT
                    .MOYEN
                    .eq(MoyenPaiement.VIREMENT)
                    .and(PAIEMENT.STATUT.eq(StatutPaiement.en_attente))
                    .or(PAIEMENT.STATUT.eq(StatutPaiement.a_verifier))));
  }

  /** Vrai si cet objet est déjà payé pour la demande. */
  public boolean estPaye(long demandeId, ObjetPaiement objet) {
    return dsl.fetchExists(
        PAIEMENT,
        PAIEMENT
            .DEMANDE_ID
            .eq(demandeId)
            .and(PAIEMENT.OBJET.eq(objet))
            .and(PAIEMENT.STATUT.eq(StatutPaiement.paye)));
  }

  /** Paiements créés depuis {@code depuis} pour cet objet et ce moyen (tentatives du jour). */
  public long compterDepuis(
      long demandeId, ObjetPaiement objet, MoyenPaiement moyen, Instant depuis) {
    return dsl.fetchCount(
        PAIEMENT,
        PAIEMENT
            .DEMANDE_ID
            .eq(demandeId)
            .and(PAIEMENT.OBJET.eq(objet))
            .and(PAIEMENT.MOYEN.eq(moyen))
            .and(PAIEMENT.CREATED_AT.ge(depuis)));
  }

  /**
   * Tentatives de paiement en ligne du jour (US-04.3) : paiements de cet objet pour lesquels une
   * session a réellement été ouverte chez la passerelle (événement INITIATION) depuis {@code
   * depuis}. Une passerelle indisponible ne consomme donc pas de tentative.
   */
  public long compterTentatives(long demandeId, ObjetPaiement objet, Instant depuis) {
    return dsl.fetchCount(
        dsl.select(PAIEMENT.ID)
            .from(PAIEMENT)
            .join(PAIEMENT_EVENEMENT)
            .on(PAIEMENT_EVENEMENT.PAIEMENT_ID.eq(PAIEMENT.ID))
            .where(PAIEMENT.DEMANDE_ID.eq(demandeId))
            .and(PAIEMENT.OBJET.eq(objet))
            .and(PAIEMENT_EVENEMENT.TYPE_EVENEMENT.eq("INITIATION"))
            .and(PAIEMENT.CREATED_AT.ge(depuis)));
  }

  /** Session ouverte chez la passerelle : {@code initie → en_attente} et événement INITIATION. */
  public void passerEnAttente(UUID id, String reference, String payloadJson, Instant expireLe) {
    dsl.update(PAIEMENT)
        .set(PAIEMENT.STATUT, StatutPaiement.en_attente)
        .set(PAIEMENT.DATE_EXPIRATION, expireLe)
        .where(PAIEMENT.ID.eq(id))
        .execute();
    dsl.insertInto(PAIEMENT_EVENEMENT)
        .set(PAIEMENT_EVENEMENT.PAIEMENT_ID, id)
        .set(PAIEMENT_EVENEMENT.REFERENCE_RECUE, reference)
        .set(PAIEMENT_EVENEMENT.TYPE_EVENEMENT, "INITIATION")
        .set(PAIEMENT_EVENEMENT.PAYLOAD, JSONB.valueOf(payloadJson))
        .set(PAIEMENT_EVENEMENT.SIGNATURE_VALIDE, true)
        .set(PAIEMENT_EVENEMENT.TRAITE, true)
        .execute();
  }

  /**
   * Journalise une notification refusée (signature, marchand ou horodatage invalide) : aucun lien
   * avec un paiement, aucun identifiant de transaction (qui pourrait bloquer la vraie
   * notification).
   */
  public void journaliserNotificationRefusee(String payloadJson) {
    dsl.insertInto(PAIEMENT_EVENEMENT)
        .set(PAIEMENT_EVENEMENT.TYPE_EVENEMENT, "NOTIFICATION")
        .set(PAIEMENT_EVENEMENT.PAYLOAD, JSONB.valueOf(payloadJson))
        .set(PAIEMENT_EVENEMENT.SIGNATURE_VALIDE, false)
        .set(PAIEMENT_EVENEMENT.TRAITE, false)
        .execute();
  }

  /**
   * Enregistre une notification vérifiée. Renvoie l'identifiant de l'événement, ou vide si cette
   * transaction a déjà été reçue (contrainte {@code UNIQUE (transaction_id, type_evenement)} :
   * idempotence et anti-rejeu).
   */
  public Optional<Long> enregistrerNotification(
      String transactionId, String reference, String payloadJson) {
    return dsl.insertInto(PAIEMENT_EVENEMENT)
        .set(PAIEMENT_EVENEMENT.TRANSACTION_ID, transactionId)
        .set(PAIEMENT_EVENEMENT.REFERENCE_RECUE, reference)
        .set(PAIEMENT_EVENEMENT.TYPE_EVENEMENT, "NOTIFICATION")
        .set(PAIEMENT_EVENEMENT.PAYLOAD, JSONB.valueOf(payloadJson))
        .set(PAIEMENT_EVENEMENT.SIGNATURE_VALIDE, true)
        .set(PAIEMENT_EVENEMENT.TRAITE, false)
        .onConflictDoNothing()
        .returning(PAIEMENT_EVENEMENT.ID)
        .fetchOptional(PAIEMENT_EVENEMENT.ID);
  }

  /** Lie l'événement à son paiement et note s'il a été appliqué. */
  public void marquerEvenement(long evenementId, UUID paiementId, boolean traite) {
    dsl.update(PAIEMENT_EVENEMENT)
        .set(PAIEMENT_EVENEMENT.PAIEMENT_ID, paiementId)
        .set(PAIEMENT_EVENEMENT.TRAITE, traite)
        .where(PAIEMENT_EVENEMENT.ID.eq(evenementId))
        .execute();
  }

  /** Paiement de cette référence, verrouillé jusqu'à la fin de la transaction. */
  public Optional<Paiement> verrouillerParReference(String reference) {
    return dsl.selectFrom(PAIEMENT)
        .where(PAIEMENT.REFERENCE.eq(reference))
        .forUpdate()
        .fetchOptional(PaiementRepository::versDomaine);
  }

  /**
   * Résultat d'une notification appliqué au paiement ({@code transition_statut} contrôle le
   * passage). Au passage à {@code paye}, la base attribue le reçu REC-AAAA-NNNNNN.
   */
  public void appliquerResultat(
      UUID id, StatutPaiement statut, String transactionId, Long montantRecuGnf) {
    dsl.update(PAIEMENT)
        .set(PAIEMENT.STATUT, statut)
        .set(PAIEMENT.TRANSACTION_EXTERNE_ID, transactionId)
        .set(PAIEMENT.MONTANT_RECU_GNF, montantRecuGnf)
        .where(PAIEMENT.ID.eq(id))
        .execute();
  }

  /** Annule un paiement (session impossible à ouvrir, par exemple). */
  public void annuler(UUID id) {
    dsl.update(PAIEMENT)
        .set(PAIEMENT.STATUT, StatutPaiement.annule)
        .where(PAIEMENT.ID.eq(id))
        .and(PAIEMENT.STATUT.in(StatutPaiement.initie, StatutPaiement.en_attente))
        .execute();
  }

  /** Paiements d'une demande, du plus récent au plus ancien. */
  public List<Paiement> lister(long demandeId) {
    return dsl.selectFrom(PAIEMENT)
        .where(PAIEMENT.DEMANDE_ID.eq(demandeId))
        .orderBy(PAIEMENT.CREATED_AT.desc(), PAIEMENT.REFERENCE.desc())
        .fetch(PaiementRepository::versDomaine);
  }

  /** Un paiement avec le numéro de sa demande et le code de son document (liste « Paiements »). */
  public record LignePaiement(Paiement paiement, String numeroDemande, String codeDocument) {}

  /** Groupes de statuts de la liste « Paiements & reçus » (filtres de l'usager). */
  public enum GroupeStatut {
    PAYES(StatutPaiement.paye),
    EN_COURS(StatutPaiement.initie, StatutPaiement.en_attente, StatutPaiement.a_verifier),
    ECHOUES(StatutPaiement.echoue, StatutPaiement.rejete, StatutPaiement.expire),
    REMBOURSES(StatutPaiement.remboursement_demande, StatutPaiement.rembourse);

    final List<StatutPaiement> statuts;

    GroupeStatut(StatutPaiement... statuts) {
      this.statuts = List.of(statuts);
    }

    Condition condition() {
      return PAIEMENT.STATUT.in(statuts);
    }
  }

  /**
   * Critères de la liste : recherche (référence, reçu, numéro de demande, document), groupe de
   * statuts, objet et période (date du paiement, à défaut de l'opération, à l'heure de Conakry).
   */
  public record Critere(
      String recherche, GroupeStatut groupe, ObjetPaiement objet, LocalDate du, LocalDate au) {}

  /**
   * Nombre de paiements par groupe de statuts et total payé (GNF), pour les autres critères : les
   * compteurs servent à choisir le filtre de statut ; le total payé suit tous les autres filtres.
   */
  public record Compteurs(
      long tous, long payes, long enCours, long echoues, long rembourses, long totalPayeGnf) {}

  /** Une page de la liste et les compteurs de ses filtres. */
  public record PagePaiements(PageResultat<LignePaiement> page, Compteurs compteurs) {}

  /**
   * Paiements visibles du profil connecté (RLS de {@code paiement} et {@code demande} : ses propres
   * paiements pour un usager), du plus récent au plus ancien, hors paiements annulés.
   */
  public PagePaiements listerVisibles(Pagination pagination, Critere critere) {
    var demande = Tables.DEMANDE;
    var type = Tables.TYPE_DOCUMENT;
    Condition base = PAIEMENT.STATUT.ne(StatutPaiement.annule).and(autres(critere));
    var c =
        dsl.select(
                DSL.count(),
                DSL.count().filterWhere(GroupeStatut.PAYES.condition()),
                DSL.count().filterWhere(GroupeStatut.EN_COURS.condition()),
                DSL.count().filterWhere(GroupeStatut.ECHOUES.condition()),
                DSL.count().filterWhere(GroupeStatut.REMBOURSES.condition()),
                DSL.sum(PAIEMENT.MONTANT_GNF).filterWhere(GroupeStatut.PAYES.condition()))
            .from(PAIEMENT)
            .join(demande)
            .on(demande.ID.eq(PAIEMENT.DEMANDE_ID))
            .join(type)
            .on(type.ID.eq(demande.TYPE_DOCUMENT_ID))
            .where(base)
            .fetchOne();
    var compteurs =
        new Compteurs(
            c.value1(),
            c.value2(),
            c.value3(),
            c.value4(),
            c.value5(),
            c.value6() == null ? 0 : c.value6().longValueExact());
    Condition filtre = critere.groupe() == null ? base : base.and(critere.groupe().condition());
    long total =
        critere.groupe() == null
            ? compteurs.tous()
            : switch (critere.groupe()) {
              case PAYES -> compteurs.payes();
              case EN_COURS -> compteurs.enCours();
              case ECHOUES -> compteurs.echoues();
              case REMBOURSES -> compteurs.rembourses();
            };
    List<LignePaiement> elements =
        dsl.select(PAIEMENT.fields())
            .select(demande.NUMERO, type.CODE)
            .from(PAIEMENT)
            .join(demande)
            .on(demande.ID.eq(PAIEMENT.DEMANDE_ID))
            .join(type)
            .on(type.ID.eq(demande.TYPE_DOCUMENT_ID))
            .where(filtre)
            .orderBy(PAIEMENT.CREATED_AT.desc(), PAIEMENT.REFERENCE.desc())
            .limit(pagination.taille())
            .offset(pagination.decalage())
            .fetch(
                r ->
                    new LignePaiement(
                        versDomaine(r.into(PAIEMENT)), r.get(demande.NUMERO), r.get(type.CODE)));
    return new PagePaiements(
        new PageResultat<>(elements, pagination.page(), pagination.taille(), total), compteurs);
  }

  private static final ZoneId CONAKRY = ZoneId.of("Africa/Conakry");

  /** Critères autres que le statut : recherche tolérante, objet, période. */
  private static Condition autres(Critere critere) {
    Condition c = DSL.noCondition();
    if (critere.objet() != null) {
      c = c.and(PAIEMENT.OBJET.eq(critere.objet()));
    }
    Field<Instant> date = DSL.coalesce(PAIEMENT.DATE_PAIEMENT, PAIEMENT.CREATED_AT);
    if (critere.du() != null) {
      c = c.and(date.ge(critere.du().atStartOfDay(CONAKRY).toInstant()));
    }
    if (critere.au() != null) {
      c = c.and(date.lt(critere.au().plusDays(1).atStartOfDay(CONAKRY).toInstant()));
    }
    if (critere.recherche() != null && !critere.recherche().isBlank()) {
      Field<String> texte =
          normaliser(
              DSL.concat(
                  PAIEMENT.REFERENCE,
                  DSL.inline(" "),
                  DSL.coalesce(PAIEMENT.RECU_NUMERO, DSL.inline("")),
                  DSL.inline(" "),
                  DSL.coalesce(Tables.DEMANDE.NUMERO, DSL.inline("")),
                  DSL.inline(" "),
                  Tables.TYPE_DOCUMENT.LIBELLE_FR,
                  DSL.inline(" "),
                  DSL.coalesce(Tables.TYPE_DOCUMENT.LIBELLE_EN, DSL.inline(""))));
      for (String mot : critere.recherche().strip().split("\\s+")) {
        c = c.and(texte.contains(normaliser(DSL.val(mot))));
      }
    }
    return c;
  }

  private static Field<String> normaliser(Field<String> texte) {
    return DSL.function("fn_normaliser", String.class, texte);
  }

  public Optional<Paiement> lireParReference(String reference) {
    return dsl.selectFrom(PAIEMENT)
        .where(PAIEMENT.REFERENCE.eq(reference))
        .fetchOptional(PaiementRepository::versDomaine);
  }

  /** Code du QR du reçu (V33), sous la RLS du paiement. */
  public Optional<String> codeVerification(String reference) {
    return dsl.select(PAIEMENT.CODE_VERIFICATION)
        .from(PAIEMENT)
        .where(PAIEMENT.REFERENCE.eq(reference))
        .fetchOptional(PAIEMENT.CODE_VERIFICATION);
  }

  /**
   * Reçu vérifié publiquement par le code de son QR (US-04.14), sans connexion : fonction de la
   * base qui ne renvoie aucune donnée personnelle.
   */
  public Optional<gn.gov.mines.emine.shared.jooq.tables.records.FnVerifierRecuRecord> parCodeRecu(
      String code) {
    return dsl.selectFrom(gn.gov.mines.emine.shared.jooq.Tables.FN_VERIFIER_RECU.call(code))
        .fetchOptional();
  }

  public Optional<Paiement> lire(UUID id) {
    return dsl.selectFrom(PAIEMENT)
        .where(PAIEMENT.ID.eq(id))
        .fetchOptional(PaiementRepository::versDomaine);
  }

  static Paiement versDomaine(PaiementRecord r) {
    return new Paiement(
        r.getId(),
        r.getReference(),
        r.getDemandeId(),
        r.getObjet(),
        r.getMoyen(),
        r.getMontantGnf(),
        r.getStatut(),
        r.getDateExpiration(),
        r.getMontantRecuGnf(),
        r.getDatePaiement(),
        r.getRecuNumero(),
        r.getCreatedAt(),
        r.getTransactionExterneId(),
        r.getJustificatifPieceId(),
        r.getVirementDeclareLe(),
        r.getMotifRejet());
  }
}
