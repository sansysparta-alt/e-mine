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
    boolean bloque) {

  public boolean estBrouillon() {
    return "brouillon".equals(statut);
  }

  /** Auteur de la demande et son organisation éventuelle. */
  public record Demandeur(long id, String prenom, String nom, String email, String organisation) {}
}
