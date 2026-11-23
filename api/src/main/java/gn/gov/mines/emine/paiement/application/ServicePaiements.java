package gn.gov.mines.emine.paiement.application;

import gn.gov.mines.emine.auth.UtilisateurConnecte;
import gn.gov.mines.emine.auth.UtilisateurCourant;
import gn.gov.mines.emine.catalogue.Catalogue;
import gn.gov.mines.emine.catalogue.TypeDocument;
import gn.gov.mines.emine.demande.Demandes;
import gn.gov.mines.emine.demande.Demandes.DemandeResume;
import gn.gov.mines.emine.paiement.domaine.MoyensPaiement;
import gn.gov.mines.emine.paiement.domaine.Paiement;
import gn.gov.mines.emine.paiement.domaine.RegleObjetAPayer;
import gn.gov.mines.emine.paiement.domaine.RegleObjetAPayer.ObjetAPayer;
import gn.gov.mines.emine.paiement.domaine.RegleTentatives;
import gn.gov.mines.emine.paiement.infrastructure.PaiementRepository;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.jooq.enums.MoyenPaiement;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Paiements d'une demande (US-04.2) : ce qui est à payer, historique, choix du moyen. Le montant et
 * la référence sont fixés par le serveur (barème du catalogue, trigger de la base) ; la RLS
 * garantit qu'un usager n'agit que sur ses propres demandes.
 */
@Service
public class ServicePaiements {

  private final PaiementRepository paiements;
  private final Demandes demandes;
  private final Catalogue catalogue;
  private final UtilisateurCourant utilisateurCourant;
  private final Clock horloge;

  public ServicePaiements(
      PaiementRepository paiements,
      Demandes demandes,
      Catalogue catalogue,
      UtilisateurCourant utilisateurCourant,
      Clock horloge) {
    this.paiements = paiements;
    this.demandes = demandes;
    this.catalogue = catalogue;
    this.utilisateurCourant = utilisateurCourant;
    this.horloge = horloge;
  }

  /**
   * Ce qui est attendu pour la demande.
   *
   * @param objet objet et montant attendus, vide si rien n'est à payer à ce stade
   * @param moyens moyens proposés (aucun présélectionné)
   * @param paiementOuvert paiement en cours (initié ou en attente) pour cet objet, s'il existe
   */
  public record APayer(
      Optional<ObjetAPayer> objet,
      List<MoyensPaiement.MoyenPropose> moyens,
      Optional<Paiement> paiementOuvert,
      int tentativesRestantes) {}

  @Transactional(readOnly = true)
  public APayer aPayer(long demandeId) {
    DemandeResume demande = demande(demandeId);
    Optional<ObjetAPayer> objet = objetNonPaye(demande);
    Optional<Paiement> ouvert =
        objet.flatMap(
            o ->
                paiements.lister(demandeId).stream()
                    .filter(p -> p.objet() == o.objet() && p.ouvert())
                    .findFirst());
    int restantes =
        objet.map(o -> tentativesRestantes(demandeId, o)).orElse(RegleTentatives.MAX_PAR_JOUR);
    return new APayer(objet, MoyensPaiement.proposes(), ouvert, restantes);
  }

  @Transactional(readOnly = true)
  public List<Paiement> lister(long demandeId) {
    demande(demandeId);
    return paiements.lister(demandeId);
  }

  @Transactional(readOnly = true)
  public Paiement lireParReference(String reference) {
    return paiements
        .lireParReference(reference)
        .orElseThrow(() -> new ErreurMetier(CodeErreur.PAY_005, Map.of("reference", reference)));
  }

  /**
   * Choix du moyen de paiement (US-04.2) : crée un paiement {@code initie} au montant du barème. Un
   * paiement encore ouvert pour le même objet est annulé (changement de moyen) ; PAY-001 si l'objet
   * est déjà payé, PAY-007 si rien n'est attendu, PAY-008 si le moyen n'est pas proposé.
   */
  @Transactional
  public Paiement choisirMoyen(long demandeId, MoyenPaiement moyen) {
    if (!MoyensPaiement.disponible(moyen)) {
      throw new ErreurMetier(CodeErreur.PAY_008, Map.of("moyen", moyen.getLiteral()));
    }
    DemandeResume demande = demande(demandeId);
    ObjetAPayer objet = objetAttendu(demande);
    if (paiements.estPaye(demandeId, objet.objet())) {
      throw new ErreurMetier(CodeErreur.PAY_001, Map.of("objet", objet.objet().getLiteral()));
    }
    if (moyen == MoyenPaiement.TRESORPAY && tentativesRestantes(demandeId, objet) == 0) {
      throw new ErreurMetier(
          CodeErreur.PAY_002,
          Map.of(
              "prochaineTentative",
              RegleTentatives.debutDuJourSuivant(horloge.instant()).toString()));
    }
    paiements.annulerOuverts(demandeId, objet.objet());
    return paiements.creer(demandeId, objet.objet(), moyen, objet.montantGnf(), moi().id());
  }

  /**
   * Session ouverte chez la passerelle (US-04.3) : le paiement passe à {@code en_attente} jusqu'à
   * la notification signée, seule preuve du paiement.
   */
  @Transactional
  public Paiement enregistrerSession(Paiement paiement, String payloadJson, Instant expireLe) {
    paiements.passerEnAttente(paiement.id(), paiement.reference(), payloadJson, expireLe);
    return paiements.lire(paiement.id()).orElseThrow();
  }

  /** Annule un paiement dont la session n'a pas pu être ouverte (passerelle indisponible). */
  @Transactional
  public void annuler(Paiement paiement) {
    paiements.annuler(paiement.id());
  }

  /** Libellé de la demande pour la page de paiement : « Frais de dossier · DEM-2026-000001 ». */
  @Transactional(readOnly = true)
  public String libelle(Paiement paiement) {
    String objet =
        switch (paiement.objet()) {
          case FRAIS_DOSSIER -> "Frais de dossier";
          case DROITS -> "Droits";
          case ATTESTATION -> "Attestation";
          case FORMATION -> "Formation";
        };
    String numero = demande(paiement.demandeId()).numero();
    return numero == null ? objet : objet + " · " + numero;
  }

  private int tentativesRestantes(long demandeId, ObjetAPayer objet) {
    return RegleTentatives.restantes(
        paiements.compterTentatives(
            demandeId, objet.objet(), RegleTentatives.debutDuJour(horloge.instant())));
  }

  private DemandeResume demande(long demandeId) {
    return demandes
        .lire(demandeId)
        .orElseThrow(() -> new ErreurMetier(CodeErreur.DEM_002, Map.of("id", demandeId)));
  }

  /** Objet attendu selon le statut et le barème, PAY-007 sinon. */
  private ObjetAPayer objetAttendu(DemandeResume demande) {
    return RegleObjetAPayer.objetAPayer(demande.statut(), tarifs(demande))
        .orElseThrow(
            () ->
                new ErreurMetier(
                    CodeErreur.PAY_007, Map.of("id", demande.id(), "statut", demande.statut())));
  }

  /** Objet attendu et pas encore payé. */
  private Optional<ObjetAPayer> objetNonPaye(DemandeResume demande) {
    return RegleObjetAPayer.objetAPayer(demande.statut(), tarifs(demande))
        .filter(o -> !paiements.estPaye(demande.id(), o.objet()));
  }

  private TypeDocument.Tarifs tarifs(DemandeResume demande) {
    return catalogue
        .pourDemande(demande.typeDocumentCode(), demande.versionDocumentId())
        .map(TypeDocument::tarifs)
        .orElseThrow(
            () -> new ErreurMetier(CodeErreur.DEM_005, Map.of("code", demande.typeDocumentCode())));
  }

  private UtilisateurConnecte moi() {
    return utilisateurCourant.get().orElseThrow(() -> new ErreurMetier(CodeErreur.AUTH_001));
  }
}
