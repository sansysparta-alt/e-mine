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
 * @param passages nombre de fois où un agent a passé le dossier à l'étape actuelle
 * @param bloque dossier passé au moins 3 fois à cette étape : signalé « Bloqué » aux décideurs
 * @param enRetard dossier en cours dont le délai du catalogue est dépassé (calculé par l'API)
 * @param joursRestants dossier en cours : jours restants sur le délai du catalogue, hors suspension
 *     (négatif = jours de retard), {@code null} sinon (US-05.6)
 * @param procheDepassement il reste au plus le seuil paramétré du délai (20 % et 2 jours au moins
 *     par défaut), sans retard ni pause
 * @param delaiSuspendu le délai est en pause (complément en cours, ou droits attendus)
 * @param motifSuspension motif de la pause : {@code complement} ou {@code droits}
 * @param delaiDemarre le délai a commencé (frais payés) ; sinon il attend le paiement des frais
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
    boolean aTraiterParMoi,
    int passages,
    boolean bloque,
    boolean enRetard,
    @Schema(nullable = true) Integer joursRestants,
    boolean procheDepassement,
    boolean delaiSuspendu,
    @Schema(
            nullable = true,
            allowableValues = {"complement", "droits"})
        String motifSuspension,
    boolean delaiDemarre,
    @Schema(
            description =
                "Le document délivré a été révoqué : la demande reste délivrée dans le circuit,"
                    + " mais s'affiche « Document révoqué » partout")
        boolean documentRevoque,
    @Schema(
            nullable = true,
            description =
                "Étape en cours dans le circuit (« Étape 3 sur 6 »), d'après la frise de suivi ;"
                    + " absente pour un brouillon, une demande délivrée, rejetée ou archivée")
        Avancement avancement) {

  /** Rang de l'étape en cours parmi les étapes qui s'appliquent au document. */
  record Avancement(int etape, int total) {}

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
        d.aTraiterParMoi(),
        d.passages(),
        d.bloque(),
        d.enRetard(),
        d.joursRestants(),
        d.procheDepassement(),
        d.delaiSuspendu(),
        d.motifSuspension(),
        d.delaiDemarre(),
        d.documentRevoque(),
        d.avancement() == null
            ? null
            : new Avancement(d.avancement().etape(), d.avancement().total()));
  }
}
