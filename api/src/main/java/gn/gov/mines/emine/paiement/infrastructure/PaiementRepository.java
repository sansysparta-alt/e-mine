package gn.gov.mines.emine.paiement.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.PAIEMENT;

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
        r.getCreatedAt());
  }
}
