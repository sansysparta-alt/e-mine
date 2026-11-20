package gn.gov.mines.emine.demande.domaine;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Demande lue en base (visible selon la RLS), avec son demandeur et ses référentiels.
 *
 * @param numero {@code DEM-AAAA-NNNNNN}, {@code null} tant que la demande est un brouillon (V5)
 * @param statut statut de la base ({@code brouillon}, {@code deposee}…)
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
    Instant deposeLe) {

  public boolean estBrouillon() {
    return "brouillon".equals(statut);
  }

  /** Auteur de la demande et son organisation éventuelle. */
  public record Demandeur(long id, String prenom, String nom, String email, String organisation) {}
}
