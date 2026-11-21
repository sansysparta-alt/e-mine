package gn.gov.mines.emine.paiement.api;

import gn.gov.mines.emine.paiement.domaine.Paiement;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Paiement exposé par l'API (montants en GNF entiers, dates UTC).
 *
 * @param objet {@code FRAIS_DOSSIER}, {@code DROITS}, {@code FORMATION} ou {@code ATTESTATION}
 * @param moyen {@code TRESORPAY} ou {@code VIREMENT}
 * @param statut statut de la base ({@code initie}, {@code en_attente}, {@code paye}…)
 */
record PaiementDto(
    String reference,
    long demandeId,
    String objet,
    String moyen,
    long montantGnf,
    String statut,
    @Schema(nullable = true) Instant dateExpiration,
    @Schema(nullable = true) Long montantRecuGnf,
    @Schema(nullable = true) Instant datePaiement,
    @Schema(nullable = true) String recuNumero,
    Instant creeLe) {

  static PaiementDto depuis(Paiement p) {
    return new PaiementDto(
        p.reference(),
        p.demandeId(),
        p.objet().getLiteral(),
        p.moyen().getLiteral(),
        p.montantGnf(),
        p.statut().getLiteral(),
        p.dateExpiration(),
        p.montantRecuGnf(),
        p.datePaiement(),
        p.recuNumero(),
        p.creeLe());
  }
}
