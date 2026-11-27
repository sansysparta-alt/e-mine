package gn.gov.mines.emine.delivrance.application;

import gn.gov.mines.emine.auth.UtilisateurConnecte;
import gn.gov.mines.emine.auth.UtilisateurCourant;
import gn.gov.mines.emine.delivrance.domaine.DocumentDelivre;
import gn.gov.mines.emine.delivrance.domaine.RegleRevocation;
import gn.gov.mines.emine.delivrance.infrastructure.SignalementRepository;
import gn.gov.mines.emine.delivrance.infrastructure.SignalementRepository.Signalement;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signalements d'un document par la Brigade anti-fraude (US-06.8, décision V1-11) : la Brigade
 * signale (motif), sans effet sur le document ; le signataire du document décide : il l'annule
 * (révocation, même motif public) ou classe le signalement, toujours avec un motif. Aucune décision
 * automatique ; la base contrôle les droits de chacun.
 */
@Service
public class ServiceSignalements {

  /** Décision du signataire. */
  public enum Decision {
    ANNULER,
    CLASSER
  }

  private final SignalementRepository signalements;
  private final ServiceDelivrance delivrance;
  private final UtilisateurCourant utilisateurCourant;

  public ServiceSignalements(
      SignalementRepository signalements,
      ServiceDelivrance delivrance,
      UtilisateurCourant utilisateurCourant) {
    this.signalements = signalements;
    this.delivrance = delivrance;
    this.utilisateurCourant = utilisateurCourant;
  }

  /**
   * Ce que le profil connecté peut faire sur le document d'une demande, et ses signalements.
   *
   * @param peutAnnuler le profil connecté est le signataire du document, encore valide
   * @param peutSignaler la Brigade, sur un document valide
   */
  public record Etat(boolean peutAnnuler, boolean peutSignaler, List<Signalement> signalements) {}

  @Transactional(readOnly = true)
  public Etat etat(long demandeId) {
    DocumentDelivre d = delivrance.lire(demandeId);
    UtilisateurConnecte moi = moi();
    boolean signataire = moi.interne() && signalements.estSignataire(demandeId);
    return new Etat(
        d.valide() && signataire,
        d.valide() && RegleRevocation.peutSignaler(moi.profilCode()),
        moi.interne() ? signalements.duDocument(d.id()) : List.of());
  }

  @Transactional
  public long signaler(long demandeId, String motif) {
    if (!RegleRevocation.peutSignaler(moi().profilCode())) {
      throw new ErreurMetier(CodeErreur.PERM_001, Map.of("action", "signalement"));
    }
    DocumentDelivre d = delivrance.lire(demandeId);
    if (!d.valide()) {
      throw new ErreurMetier(CodeErreur.DOC_006, Map.of("numero", d.numero()));
    }
    return signalements.creer(d.id(), RegleRevocation.motifValide(motif));
  }

  /** Décision du signataire : annuler le document (révocation) ou classer le signalement. */
  @Transactional
  public void decider(long id, Decision decision, String motif) {
    Signalement s =
        signalements
            .lire(id)
            .orElseThrow(() -> new ErreurMetier(CodeErreur.DOC_012, Map.of("id", id)));
    String net = RegleRevocation.motifValide(motif);
    if (!"ouvert".equals(s.statut())) {
      throw new ErreurMetier(CodeErreur.DOC_006, Map.of("numero", s.numeroDocument()));
    }
    if (decision == Decision.ANNULER) {
      delivrance.revoquer(s.demandeId(), net);
    } else if (!signalements.estSignataire(s.demandeId())) {
      throw new ErreurMetier(CodeErreur.PERM_001, Map.of("action", "signalement"));
    }
    signalements.decider(id, decision == Decision.ANNULER ? "annule" : "classe", net);
  }

  /** Signalements que le profil connecté doit décider (règle « À traiter »). */
  @Transactional(readOnly = true)
  public PageResultat<Signalement> aDecider(Pagination pagination) {
    return signalements.listerADecider(pagination);
  }

  @Transactional(readOnly = true)
  public long nombreADecider() {
    return signalements.compterADecider();
  }

  private UtilisateurConnecte moi() {
    return utilisateurCourant.get().orElseThrow(() -> new ErreurMetier(CodeErreur.AUTH_001));
  }
}
