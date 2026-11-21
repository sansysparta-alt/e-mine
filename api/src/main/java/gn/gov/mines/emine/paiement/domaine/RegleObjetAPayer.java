package gn.gov.mines.emine.paiement.domaine;

import gn.gov.mines.emine.catalogue.TypeDocument.Tarifs;
import gn.gov.mines.emine.shared.jooq.enums.ObjetPaiement;
import java.util.Optional;
import java.util.Set;

/**
 * Ce que le demandeur doit payer selon le statut de sa demande (US-04.2, US-05.5) : montant fixé
 * par le serveur d'après le barème du catalogue, jamais par le navigateur.
 *
 * <ul>
 *   <li>déposée (ou complément demandé) : frais de dossier, ou paiement unique d'une attestation ;
 *   <li>décision favorable : droits.
 * </ul>
 */
public final class RegleObjetAPayer {

  /** Statuts où les frais de dossier peuvent être payés (avant la recevabilité). */
  private static final Set<String> STATUTS_FRAIS = Set.of("deposee", "complement_requis");

  private RegleObjetAPayer() {}

  /** Objet et montant attendus, ou vide si aucun paiement n'est attendu à ce stade. */
  public static Optional<ObjetAPayer> objetAPayer(String statutDemande, Tarifs tarifs) {
    if (STATUTS_FRAIS.contains(statutDemande)) {
      if (tarifs.fraisDossierGnf() != null) {
        return Optional.of(new ObjetAPayer(ObjetPaiement.FRAIS_DOSSIER, tarifs.fraisDossierGnf()));
      }
      if (tarifs.attestationGnf() != null) {
        return Optional.of(new ObjetAPayer(ObjetPaiement.ATTESTATION, tarifs.attestationGnf()));
      }
      return Optional.empty();
    }
    if ("decision".equals(statutDemande) && tarifs.droitsGnf() != null) {
      return Optional.of(new ObjetAPayer(ObjetPaiement.DROITS, tarifs.droitsGnf()));
    }
    return Optional.empty();
  }

  /**
   * Objet de paiement attendu et son montant.
   *
   * @param montantGnf montant en GNF entiers, strictement positif
   */
  public record ObjetAPayer(ObjetPaiement objet, long montantGnf) {

    public ObjetAPayer {
      if (montantGnf <= 0) {
        throw new IllegalArgumentException("Montant à payer non positif : " + montantGnf);
      }
    }
  }
}
