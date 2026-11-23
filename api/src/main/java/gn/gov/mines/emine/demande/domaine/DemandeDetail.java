package gn.gov.mines.emine.demande.domaine;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Demande lue en base (visible selon la RLS), avec son demandeur et ses référentiels.
 *
 * @param numero {@code DEM-AAAA-NNNNNN}, {@code null} tant que la demande est un brouillon (V5)
 * @param statut statut de la base ({@code brouillon}, {@code deposee}…)
 * @param fraisPayes frais de dossier (ou attestation) payés : condition de la recevabilité (V8)
 * @param motifRejet motif communiqué au demandeur si la demande est rejetée, sinon {@code null}
 * @param etapeDepuis entrée dans le statut actuel (dernier changement de statut), pour le temps
 *     d'attente à l'étape
 * @param aTraiterParMoi la demande attend une action du profil connecté (règle « À traiter »)
 * @param passages nombre de fois où le dossier a été passé à l'étape actuelle (0 pour un usager)
 * @param bloque dossier en cours passé au moins {@link Passage#SEUIL_BLOCAGE} fois à cette étape
 * @param joursRestants dossier en cours : jours restants sur le délai du catalogue, hors suspension
 *     (négatif = jours de retard) ; {@code null} sinon (US-05.6)
 * @param procheDepassement dossier en cours proche du dépassement ({@link RegleDelais})
 * @param delaiSuspendu dossier en cours dont le délai est en pause (complément, droits attendus)
 * @param motifSuspension motif de la pause : {@code complement} ou {@code droits} ; {@code null}
 *     sans pause
 * @param delaiDemarre le délai du catalogue a commencé (au paiement des frais, selon le
 *     paramétrage, {@link RegleDelais})
 * @param enRetard dossier en cours dont le délai du catalogue est dépassé ({@link RegleDelais}) :
 *     même règle que l'indicateur et le filtre « en retard »
 */
public record DemandeDetail(
    long id,
    String numero,
    String statut,
    String typeDocumentCode,
    Demandeur demandeur,
    Short prefectureId,
    String prefectureNom,
    String substanceCode,
    String substanceLibelle,
    BigDecimal superficieHa,
    Instant creeLe,
    Instant deposeLe,
    boolean fraisPayes,
    String motifRejet,
    Instant etapeDepuis,
    boolean aTraiterParMoi,
    int passages,
    boolean bloque,
    boolean enRetard,
    Integer joursRestants,
    boolean procheDepassement,
    boolean delaiSuspendu,
    String motifSuspension,
    boolean delaiDemarre,
    String origineComplement,
    RegleSuivi.Avancement avancement) {

  /** La même demande, avec son avancement dans le circuit (calculé avec le catalogue). */
  public DemandeDetail avec(RegleSuivi.Avancement a) {
    return new DemandeDetail(
        id,
        numero,
        statut,
        typeDocumentCode,
        demandeur,
        prefectureId,
        prefectureNom,
        substanceCode,
        substanceLibelle,
        superficieHa,
        creeLe,
        deposeLe,
        fraisPayes,
        motifRejet,
        etapeDepuis,
        aTraiterParMoi,
        passages,
        bloque,
        enRetard,
        joursRestants,
        procheDepassement,
        delaiSuspendu,
        motifSuspension,
        delaiDemarre,
        origineComplement,
        a);
  }

  public boolean estBrouillon() {
    return "brouillon".equals(statut);
  }

  /** Auteur de la demande et son organisation éventuelle. */
  public record Demandeur(long id, String prenom, String nom, String email, String organisation) {}
}
