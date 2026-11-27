package gn.gov.mines.emine.paiement.application;

import gn.gov.mines.emine.demande.Demandes;
import gn.gov.mines.emine.paiement.domaine.Paiement;
import gn.gov.mines.emine.paiement.infrastructure.PaiementRepository;
import gn.gov.mines.emine.paiement.infrastructure.ProprietesVirement;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Paiement par virement (US-04.7) : coordonnées du compte, déclaration du virement avec son
 * justificatif, consultation du justificatif. Le justificatif ne vaut pas encaissement : seul le
 * relevé bancaire, rapproché par le Responsable fiscal (US-04.8), confirme le paiement.
 */
@Service
@EnableConfigurationProperties(ProprietesVirement.class)
public class ServiceVirements {

  private final PaiementRepository paiements;
  private final Demandes demandes;
  private final ProprietesVirement coordonnees;

  public ServiceVirements(
      PaiementRepository paiements, Demandes demandes, ProprietesVirement coordonnees) {
    this.paiements = paiements;
    this.demandes = demandes;
    this.coordonnees = coordonnees;
  }

  /** Coordonnées du compte à créditer, communes à tous les virements. */
  public ProprietesVirement coordonnees() {
    return coordonnees;
  }

  /**
   * « J'ai effectué le virement » : le justificatif (PDF, JPG ou PNG, 5 Mo au plus) est joint à la
   * demande et rattaché au paiement, qui passe à {@code en_attente}. PAY-010 si le virement est
   * déjà déclaré, n'est pas un virement ou si sa référence a expiré.
   */
  @Transactional
  public Paiement declarer(String reference, String nomFichier, byte[] contenu) {
    Paiement paiement = lire(reference);
    if (!paiement.virementAEffectuer()) {
      throw new ErreurMetier(CodeErreur.PAY_010, Map.of("reference", reference));
    }
    long piece = demandes.deposerJustificatifVirement(paiement.demandeId(), nomFichier, contenu);
    if (paiements.declarerVirement(paiement.id(), piece) == 0) {
      // Référence expirée entre-temps : la transaction est annulée (pièce comprise).
      throw new ErreurMetier(CodeErreur.PAY_010, Map.of("reference", reference));
    }
    return paiements.lire(paiement.id()).orElseThrow();
  }

  /** Justificatif d'un virement déclaré (demandeur ou agent, selon la RLS). */
  @Transactional(readOnly = true)
  public Demandes.FichierJoint justificatif(String reference) {
    Paiement paiement = lire(reference);
    if (paiement.justificatifPieceId() == null) {
      throw new ErreurMetier(CodeErreur.DOC_003, Map.of("reference", reference));
    }
    return demandes.justificatifVirement(paiement.demandeId(), paiement.justificatifPieceId());
  }

  /** Nom et format du justificatif d'un virement déclaré, s'il existe. */
  @Transactional(readOnly = true)
  public java.util.Optional<Demandes.InfosFichier> infosJustificatif(Paiement paiement) {
    return paiement.justificatifPieceId() == null
        ? java.util.Optional.empty()
        : demandes.infosJustificatifVirement(paiement.demandeId(), paiement.justificatifPieceId());
  }

  private Paiement lire(String reference) {
    return paiements
        .lireParReference(reference)
        .orElseThrow(() -> new ErreurMetier(CodeErreur.PAY_005, Map.of("reference", reference)));
  }
}
