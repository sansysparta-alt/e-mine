package gn.gov.mines.emine.paiement.application;

import gn.gov.mines.emine.auth.Permission;
import gn.gov.mines.emine.auth.UtilisateurConnecte;
import gn.gov.mines.emine.auth.UtilisateurCourant;
import gn.gov.mines.emine.paiement.infrastructure.RegleRemboursementRepository;
import gn.gov.mines.emine.paiement.infrastructure.RemboursementRepository;
import gn.gov.mines.emine.paiement.infrastructure.RemboursementRepository.Remboursement;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.gouvernance.EtatVersion;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remboursements (US-04.10). Le Responsable fiscal valide ou refuse (motif) chaque demande, puis
 * enregistre la référence de l'ordre de remboursement émis par le Trésor : l'argent est rendu hors
 * plateforme, e-mine trace la décision, le motif et l'ordre. Règle V1-4 : les frais ne sont
 * remboursés sur rejet de la demande que si la règle en vigueur à son dépôt le prévoit (règle
 * gouvernée : proposée par l'administrateur, validée par le Ministre).
 */
@Service
public class ServiceRemboursements {

  /** Motifs d'un remboursement (schéma fourni). */
  public static final String DOUBLE_PAIEMENT = "double_paiement";

  public static final String EXCEDENT = "excedent";
  public static final String AUTRE = "autre";
  public static final String REJET_DEMANDE = "rejet_demande";

  private final RemboursementRepository remboursements;
  private final RegleRemboursementRepository regle;
  private final UtilisateurCourant utilisateurCourant;

  public ServiceRemboursements(
      RemboursementRepository remboursements,
      RegleRemboursementRepository regle,
      UtilisateurCourant utilisateurCourant) {
    this.remboursements = remboursements;
    this.regle = regle;
    this.utilisateurCourant = utilisateurCourant;
  }

  /** Une page de remboursements et le nombre par statut. */
  public record Page(PageResultat<Remboursement> page, Map<String, Long> compteurs) {}

  @Transactional(readOnly = true)
  public Page lister(String statut, Pagination pagination) {
    return new Page(remboursements.lister(statut, pagination), remboursements.compter());
  }

  /**
   * Règle « À traiter » des remboursements (US-04.18, décision V1-13) : le Directeur valide ou
   * refuse les propositions (sauf les siennes : séparation des tâches) ; le Responsable fiscal
   * enregistre l'ordre du Trésor d'un remboursement validé. Une seule définition, côté API.
   */
  @Transactional(readOnly = true)
  public long aTraiter() {
    UtilisateurConnecte moi = moi();
    return remboursements.compterATraiter(
        moi.peut(Permission.VALIDER_REMBOURSEMENT), moi.peut(Permission.ENCAISSER), moi.id());
  }

  public boolean aTraiterParMoi(Remboursement r) {
    UtilisateurConnecte moi = moi();
    return switch (r.statut()) {
      case "demande" -> moi.peut(Permission.VALIDER_REMBOURSEMENT) && peutDecider(r, moi);
      case "valide" -> moi.peut(Permission.ENCAISSER);
      default -> false;
    };
  }

  /**
   * Séparation des tâches : celui qui a proposé ne décide pas. Une proposition de la règle
   * elle-même (frais d'une demande rejetée) a pour auteur enregistré celui qui a rejeté la demande
   * : elle n'est pas concernée.
   */
  private static boolean peutDecider(Remboursement r, UtilisateurConnecte moi) {
    return REJET_DEMANDE.equals(r.motif()) || r.demandeParId() != moi.id();
  }

  @Transactional(readOnly = true)
  public Remboursement lire(long id) {
    return remboursements
        .lire(id)
        .orElseThrow(() -> new ErreurMetier(CodeErreur.PAY_015, Map.of("id", id)));
  }

  /** Remboursements d'un paiement (détail du paiement, usager compris, selon la RLS). */
  @Transactional(readOnly = true)
  public List<Remboursement> duPaiement(UUID paiementId) {
    return remboursements.duPaiement(paiementId);
  }

  /**
   * Demande de remboursement (créée depuis une décision sur un écart, dans la même transaction). Un
   * remboursement sur rejet de la demande n'est possible que si la règle en vigueur le prévoit.
   */
  @Transactional
  public long demander(
      UUID paiementId, String motif, long montantGnf, Long ligneReleveId, String explication) {
    if (REJET_DEMANDE.equals(motif) && !regle.rembourserSiRejet()) {
      throw new ErreurMetier(CodeErreur.PAY_016, Map.of("motif", motif));
    }
    return remboursements.creer(
        paiementId, motif, montantGnf, moi().id(), ligneReleveId, explication);
  }

  /** Validation par le Directeur : le Responsable fiscal peut demander l'ordre au Trésor. */
  @Transactional
  public void valider(long id) {
    verrouiller(id, "demande");
    exigerSeparation(id);
    remboursements.valider(id, moi().id());
  }

  /** PAY-018 si le profil connecté a lui-même proposé ce remboursement. */
  private void exigerSeparation(long id) {
    Remboursement r = lire(id);
    if (!peutDecider(r, moi())) {
      throw new ErreurMetier(CodeErreur.PAY_018, Map.of("id", id));
    }
  }

  /** Refus motivé ; le remboursement d'un paiement entier rend au paiement son état « payé ». */
  @Transactional
  public void refuser(long id, String motif) {
    var r = verrouiller(id, "demande");
    exigerSeparation(id);
    String net = motif == null ? "" : motif.strip();
    if (net.length() < ServiceEcarts.MOTIF_MIN || net.length() > ServiceEcarts.MOTIF_MAX) {
      throw new ErreurMetier(
          CodeErreur.VAL_001,
          Map.of(
              "champs",
              List.of(
                  Map.of(
                      "champ",
                      "motif",
                      "message",
                      "Expliquez le motif en "
                          + ServiceEcarts.MOTIF_MIN
                          + " caractères au moins."))));
    }
    remboursements.refuser(id, moi().id(), net);
    remboursements.paiementRedevientPaye(
        r.get(gn.gov.mines.emine.shared.jooq.Tables.REMBOURSEMENT.PAIEMENT_ID));
  }

  /**
   * Exécution par le Trésor (hors plateforme) : la référence de l'ordre de remboursement est
   * enregistrée ; le demandeur est notifié par la base.
   */
  @Transactional
  public void executer(long id, String referenceOrdre) {
    verrouiller(id, "valide");
    String ref = referenceOrdre == null ? "" : referenceOrdre.strip();
    if (ref.length() < 3 || ref.length() > 60) {
      throw new ErreurMetier(
          CodeErreur.VAL_001,
          Map.of(
              "champs",
              List.of(
                  Map.of(
                      "champ",
                      "referenceOrdre",
                      "message",
                      "Indiquez la référence de l'ordre de remboursement du Trésor."))));
    }
    remboursements.executer(id, moi().id(), ref);
  }

  // --- Règle de remboursement (gouvernance) -------------------------------------------------

  /** La règle et ses versions, vues par le profil connecté. */
  public record DetailRegle(
      boolean rembourserSiRejet,
      List<RegleRemboursementRepository.Version> versions,
      boolean peutProposer,
      boolean peutDecider) {}

  @Transactional(readOnly = true)
  public DetailRegle regle() {
    UtilisateurConnecte moi = moi();
    var versions = regle.versions();
    boolean enAttente = versions.stream().anyMatch(v -> EtatVersion.A_VALIDER.equals(v.etat()));
    return new DetailRegle(
        regle.rembourserSiRejet(),
        versions,
        moi.peut(Permission.PARAMETRER) && !enAttente,
        moi.peut(Permission.VALIDER_CATALOGUE));
  }

  @Transactional
  public int proposerRegle(boolean rembourserSiRejet, String motif, LocalDate dateEffet) {
    if (!moi().peut(Permission.PARAMETRER)) {
      throw new ErreurMetier(CodeErreur.PERM_001);
    }
    return regle.proposer(rembourserSiRejet, motif.strip(), dateEffet);
  }

  @Transactional
  public LocalDate deciderRegle(int version, EtatVersion.Decision decision, String motif) {
    Permission requise =
        decision == EtatVersion.Decision.RETIRER
            ? Permission.PARAMETRER
            : Permission.VALIDER_CATALOGUE;
    if (!moi().peut(requise)) {
      throw new ErreurMetier(CodeErreur.PERM_001);
    }
    return regle.decider(version, decision, motif == null ? null : motif.strip());
  }

  private org.jooq.Record verrouiller(long id, String statutAttendu) {
    var r =
        remboursements
            .verrouiller(id)
            .orElseThrow(() -> new ErreurMetier(CodeErreur.PAY_015, Map.of("id", id)));
    String statut = r.get(gn.gov.mines.emine.shared.jooq.Tables.REMBOURSEMENT.STATUT);
    if (!statutAttendu.equals(statut)) {
      throw new ErreurMetier(CodeErreur.PAY_006, Map.of("id", id, "statut", statut));
    }
    return r;
  }

  private UtilisateurConnecte moi() {
    return utilisateurCourant.get().orElseThrow(() -> new ErreurMetier(CodeErreur.AUTH_001));
  }
}
