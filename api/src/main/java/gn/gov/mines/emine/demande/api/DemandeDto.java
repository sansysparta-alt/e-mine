package gn.gov.mines.emine.demande.api;

import gn.gov.mines.emine.demande.domaine.DemandeDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Demande telle que publiée par l'API. Les champs facultatifs sont à plat (et non des objets
 * imbriqués) pour que le client TypeScript généré reflète exactement leur nullabilité.
 *
 * @param numero {@code DEM-AAAA-NNNNNN}, attribué au dépôt ; {@code null} pour un brouillon
 * @param fraisPayes frais de dossier (ou attestation) payés, affiché « Frais payés »
 * @param motifRejet motif de la non-recevabilité ou du rejet, {@code null} sinon
 * @param etapeDepuis entrée dans le statut actuel : l'écran affiche « En attente depuis N jours »
 * @param aTraiterParMoi la demande attend une action du profil connecté (règle « À traiter » de
 *     l'API) : l'écran adapte le libellé et le ton du statut sans recoder la règle
 */
record DemandeDto(
    long id,
    @Schema(nullable = true) String numero,
    String statut,
    String typeDocumentCode,
    Demandeur demandeur,
    @Schema(nullable = true) Short prefectureId,
    @Schema(nullable = true) String prefectureNom,
    @Schema(nullable = true) String substanceCode,
    @Schema(nullable = true) String substanceLibelle,
    @Schema(nullable = true) BigDecimal superficieHa,
    Instant creeLe,
    @Schema(nullable = true) Instant deposeLe,
    boolean fraisPayes,
    @Schema(nullable = true) String motifRejet,
    Instant etapeDepuis,
    boolean aTraiterParMoi) {

  /** Auteur de la demande. */
  record Demandeur(
      String prenom, String nom, String email, @Schema(nullable = true) String organisation) {}

  static DemandeDto depuis(DemandeDetail d) {
    return new DemandeDto(
        d.id(),
        d.numero(),
        d.statut(),
        d.typeDocumentCode(),
        new Demandeur(
            d.demandeur().prenom(),
            d.demandeur().nom(),
            d.demandeur().email(),
            d.demandeur().organisation()),
        d.prefectureId(),
        d.prefectureNom(),
        d.substanceCode(),
        d.substanceLibelle(),
        d.superficieHa(),
        d.creeLe(),
        d.deposeLe(),
        d.fraisPayes(),
        d.motifRejet(),
        d.etapeDepuis(),
        d.aTraiterParMoi());
  }
}
