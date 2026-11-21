package gn.gov.mines.emine.paiement.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.PAIEMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.PAIEMENT_EVENEMENT;

import gn.gov.mines.emine.paiement.domaine.Paiement;
import gn.gov.mines.emine.shared.jooq.enums.MoyenPaiement;
import gn.gov.mines.emine.shared.jooq.enums.ObjetPaiement;
import gn.gov.mines.emine.shared.jooq.enums.StatutPaiement;
import gn.gov.mines.emine.shared.jooq.tables.records.PaiementRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

/**
 * Accès jOOQ aux paiements. Toujours appelé dans une transaction : la RLS ne montre à un usager que
 * les paiements de ses demandes. Référence, expiration et reçu sont posés par les triggers ({@code
 * fn_paiement_avant}) ; les changements de statut passent par {@code transition_statut}.
 */
@Repository
public class PaiementRepository {

  private final DSLContext dsl;

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

  public Optional<Paiement> lireParReference(String reference) {
    return dsl.selectFrom(PAIEMENT)
        .where(PAIEMENT.REFERENCE.eq(reference))
        .fetchOptional(PaiementRepository::versDomaine);
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
        r.getTransactionExterneId());
  }
}
