package gn.gov.mines.emine.paiement.domaine;

import gn.gov.mines.emine.shared.jooq.enums.MoyenPaiement;
import gn.gov.mines.emine.shared.jooq.enums.ObjetPaiement;
import gn.gov.mines.emine.shared.jooq.enums.StatutPaiement;
import java.time.Instant;
import java.util.UUID;

/**
 * Paiement lu en base (visible selon la RLS).
 *
 * @param reference {@code PAY-AAAA-NNNNNN}, attribuée par la base (D3)
 * @param montantGnf montant dû, en GNF entiers
 * @param montantRecuGnf montant reçu, {@code null} tant que rien n'est encaissé
 * @param recuNumero {@code REC-AAAA-NNNNNN}, attribué par la base au passage à {@code paye}
 * @param transactionExterneId identifiant de la transaction chez la passerelle, s'il est connu
 * @param justificatifPieceId justificatif d'un virement déclaré (US-04.7), sinon {@code null}
 * @param virementDeclareLe déclaration « J'ai effectué le virement », sinon {@code null}
 */
public record Paiement(
    UUID id,
    String reference,
    long demandeId,
    ObjetPaiement objet,
    MoyenPaiement moyen,
    long montantGnf,
    StatutPaiement statut,
    Instant dateExpiration,
    Long montantRecuGnf,
    Instant datePaiement,
    String recuNumero,
    Instant creeLe,
    String transactionExterneId,
    Long justificatifPieceId,
    Instant virementDeclareLe) {

  /** Paiement encore ouvert : il peut être remplacé (changement de moyen) ou confirmé. */
  public boolean ouvert() {
    return statut == StatutPaiement.initie || statut == StatutPaiement.en_attente;
  }

  /**
   * Paiement en cours de vérification par le service financier : virement déclaré (en attente du
   * relevé bancaire) ou paiement « à vérifier ». Le demandeur n'a rien à faire et ne peut plus
   * changer de moyen.
   */
  public boolean enVerification() {
    return (moyen == MoyenPaiement.VIREMENT && statut == StatutPaiement.en_attente)
        || statut == StatutPaiement.a_verifier;
  }

  /**
   * Virement choisi, pas encore déclaré, dont la date limite est passée : la référence n'est plus
   * valable (la tâche planifiée la passera à {@code expire}) ; une nouvelle doit être demandée.
   */
  public boolean referenceExpiree(Instant maintenant) {
    return virementAEffectuer() && dateExpiration != null && !dateExpiration.isAfter(maintenant);
  }

  /** Virement choisi, pas encore déclaré : le demandeur doit l'effectuer avant la date limite. */
  public boolean virementAEffectuer() {
    return moyen == MoyenPaiement.VIREMENT && statut == StatutPaiement.initie;
  }
}
