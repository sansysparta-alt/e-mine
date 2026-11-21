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
    Instant creeLe) {

  /** Paiement encore ouvert : il peut être remplacé (changement de moyen) ou confirmé. */
  public boolean ouvert() {
    return statut == StatutPaiement.initie || statut == StatutPaiement.en_attente;
  }
}
