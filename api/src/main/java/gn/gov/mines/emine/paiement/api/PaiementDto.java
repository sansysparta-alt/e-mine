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
 * @param virementDeclareLe virement déclaré effectué par le demandeur (US-04.7), sinon {@code null}
 * @param justificatif un justificatif de virement est joint
 * @param motifRejet motif d'un paiement rejeté par le Responsable fiscal (US-04.9)
 * @param justificatifFichier nom et format du justificatif, pour un paiement lu seul ; {@code null}
 *     sinon
 * @param coordonnees compte à créditer, pour un virement lu seul ({@code GET
 *     /paiements/{reference}}) ; {@code null} sinon
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
    Instant creeLe,
    @Schema(nullable = true) Instant virementDeclareLe,
    boolean justificatif,
    @Schema(nullable = true) String motifRejet,
    @Schema(nullable = true) FichierDto justificatifFichier,
    @Schema(nullable = true) CoordonneesVirementDto coordonnees) {

  /** Fichier joint : nom et format (PDF, JPG ou PNG). */
  record FichierDto(String nomFichier, String typeMime) {}

  /** Compte à créditer par virement (valeurs de la configuration, fictives en démonstration). */
  record CoordonneesVirementDto(String beneficiaire, String banque, String iban, String bic) {}

  static PaiementDto depuis(Paiement p) {
    return depuis(p, null, null);
  }

  static PaiementDto depuis(
      Paiement p, FichierDto justificatifFichier, CoordonneesVirementDto coordonnees) {
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
        p.creeLe(),
        p.virementDeclareLe(),
        p.justificatifPieceId() != null,
        p.motifRejet(),
        justificatifFichier,
        p.moyen() == gn.gov.mines.emine.shared.jooq.enums.MoyenPaiement.VIREMENT
            ? coordonnees
            : null);
  }
}
