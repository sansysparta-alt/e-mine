package gn.gov.mines.emine.paiement.application;

import gn.gov.mines.emine.auth.UtilisateurCourant;
import gn.gov.mines.emine.paiement.domaine.Paiement;
import gn.gov.mines.emine.paiement.domaine.RegleEcarts;
import gn.gov.mines.emine.paiement.domaine.RegleEcarts.Decision;
import gn.gov.mines.emine.paiement.infrastructure.EcartRepository;
import gn.gov.mines.emine.paiement.infrastructure.EcartRepository.Element;
import gn.gov.mines.emine.paiement.infrastructure.EcartRepository.LigneADecider;
import gn.gov.mines.emine.paiement.infrastructure.PaiementRepository;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.jooq.enums.StatutPaiement;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Écarts « À vérifier » (US-04.9) : le Responsable fiscal décide, élément par élément, ce que le
 * rapprochement automatique n'a pas confirmé. Chaque décision est motivée quand elle l'exige et
 * tracée (ligne de relevé : auteur, date, motif ; paiement : journal d'audit, motif du rejet
 * communiqué au demandeur).
 */
@Service
public class ServiceEcarts {

  /** Longueur minimale d'un motif (comme les autres motifs communiqués). */
  public static final int MOTIF_MIN = 10;

  public static final int MOTIF_MAX = 2000;

  private final EcartRepository ecarts;
  private final PaiementRepository paiements;
  private final ServiceRemboursements remboursements;
  private final UtilisateurCourant utilisateurCourant;
  private final java.time.Clock horloge;
  private final int delaiComplementJours;

  public ServiceEcarts(
      EcartRepository ecarts,
      PaiementRepository paiements,
      ServiceRemboursements remboursements,
      UtilisateurCourant utilisateurCourant,
      java.time.Clock horloge,
      @org.springframework.beans.factory.annotation.Value(
              "${emine.paiement.complement-delai-jours:15}")
          int delaiComplementJours) {
    this.ecarts = ecarts;
    this.paiements = paiements;
    this.remboursements = remboursements;
    this.utilisateurCourant = utilisateurCourant;
    this.horloge = horloge;
    this.delaiComplementJours = delaiComplementJours;
  }

  /** Échéance indicative d'un complément demandé maintenant (provisoire : 15 jours). */
  private java.time.Instant echeanceComplement() {
    return horloge.instant().plus(java.time.Duration.ofDays(delaiComplementJours));
  }

  /** Élément de la file et décisions possibles (règle unique {@link RegleEcarts}). */
  public record ElementAVerifier(Element element, List<Decision> decisions) {}

  @Transactional(readOnly = true)
  public PageResultat<ElementAVerifier> lister(Pagination pagination) {
    return ecarts.lister(pagination).map(e -> new ElementAVerifier(e, decisions(e)));
  }

  /** Virements déclarés qui attendent le prochain relevé (R3). */
  @Transactional(readOnly = true)
  public long virementsAttendus() {
    return ecarts.virementsAttendus();
  }

  /**
   * Virements auxquels une ligne non traitée peut être associée (R4) : en cours, du même montant.
   * PAY-013 si la ligne n'existe pas, est déjà traitée ou ne s'associe pas.
   */
  @Transactional(readOnly = true)
  public List<EcartRepository.PaiementAssociable> associables(long ligneId) {
    LigneADecider ligne =
        ecarts
            .lireLigne(ligneId)
            .orElseThrow(() -> new ErreurMetier(CodeErreur.PAY_013, Map.of("ligne", ligneId)));
    boolean aVerifier = "a_verifier".equals(ligne.statutPaiement());
    if (ligne.decision() != null
        || !RegleEcarts.pourLigne(ligne.nature(), aVerifier, ligne.complement())
            .contains(Decision.ASSOCIER)) {
      throw new ErreurMetier(CodeErreur.PAY_013, Map.of("ligne", ligneId));
    }
    return ecarts.associables(ligne.montantGnf());
  }

  /** Nombre d'éléments à vérifier : compteur du menu et du tableau de bord. */
  @Transactional(readOnly = true)
  public long compter() {
    return ecarts.compter();
  }

  /**
   * Décision sur une ligne de relevé non traitée. PAY-013 si elle est déjà traitée ou si la
   * décision n'est pas possible pour elle ; PAY-014 si le paiement à associer n'est pas un virement
   * en cours du même montant ; VAL-001 si le motif obligatoire manque.
   */
  @Transactional
  public void deciderLigne(long id, Decision decision, String motif, String reference) {
    String motifNet = motif(decision, motif);
    LigneADecider ligne =
        ecarts
            .verrouillerLigne(id)
            .orElseThrow(() -> new ErreurMetier(CodeErreur.PAY_013, Map.of("ligne", id)));
    boolean paiementAVerifier = "a_verifier".equals(ligne.statutPaiement());
    if (ligne.decision() != null
        || !RegleEcarts.pourLigne(ligne.nature(), paiementAVerifier, ligne.complement())
            .contains(decision)) {
      throw new ErreurMetier(
          CodeErreur.PAY_013, Map.of("ligne", id, "decision", decision.name().toLowerCase()));
    }
    long moi = moi();
    switch (decision) {
      case VALIDER -> {
        ecarts.confirmer(ligne.paiementId(), ligne.montantGnf(), ligne.dateOperation());
        ecarts.deciderLigne(id, decision.code(), motifNet, moi, null);
        long excedent = ligne.montantGnf() - montantAttendu(ligne.paiementId());
        if (excedent > 0) {
          remboursements.demander(
              ligne.paiementId(),
              ServiceRemboursements.EXCEDENT,
              excedent,
              id,
              "Excédent reçu sur le paiement validé");
        }
      }
      case REJETER -> {
        ecarts.rejeter(ligne.paiementId(), motifNet);
        ecarts.deciderLigne(id, decision.code(), motifNet, moi, null);
        remboursements.demander(
            ligne.paiementId(),
            ServiceRemboursements.AUTRE,
            ligne.montantGnf(),
            id,
            "Somme reçue d'un paiement rejeté : " + motifNet);
      }
      case COMPLETER -> {
        // US-04.12 : le paiement n'est pas rejeté ; la somme reçue est gardée, le reste est dû.
        ecarts.demanderComplement(
            ligne.paiementId(), ligne.montantGnf(), motifNet, echeanceComplement());
        ecarts.deciderLigne(id, decision.code(), motifNet, moi, null);
      }
      case REMBOURSER -> {
        ecarts.deciderLigne(id, decision.code(), motifNet, moi, null);
        boolean doublon = "doublon".equals(ligne.nature());
        remboursements.demander(
            ligne.paiementId(),
            doublon ? ServiceRemboursements.DOUBLE_PAIEMENT : ServiceRemboursements.AUTRE,
            ligne.montantGnf(),
            id,
            doublon
                ? "Versement en double"
                : "Versement tardif sur une référence expirée ou annulée"
                    + (motifNet == null ? "" : " : " + motifNet));
      }
      case ASSOCIER -> {
        String ref = reference == null ? "" : reference.strip().toUpperCase();
        if (!ref.matches("^PAY-[0-9]{4}-[0-9]{6}$")) {
          throw new ErreurMetier(
              CodeErreur.VAL_001,
              Map.of(
                  "champs",
                  List.of(
                      Map.of(
                          "champ",
                          "reference",
                          "message",
                          "Indiquez la référence du paiement (PAY-AAAA-NNNNNN)."))));
        }
        var paiement =
            ecarts
                .virementAssociable(ref, ligne.montantGnf())
                .orElseThrow(
                    () ->
                        // R4 : un paiement déjà confirmé le dit clairement (versement en double).
                        ecarts.statut(ref).filter(s -> s == StatutPaiement.paye).isPresent()
                            ? new ErreurMetier(CodeErreur.PAY_020, Map.of("reference", ref))
                            : new ErreurMetier(CodeErreur.PAY_014, Map.of("reference", ref)));
        ecarts.confirmer(paiement, ligne.montantGnf(), ligne.dateOperation());
        ecarts.deciderLigne(id, decision.code(), motifNet, moi, paiement);
      }
      case CLASSER -> ecarts.deciderLigne(id, decision.code(), motifNet, moi, null);
    }
  }

  /**
   * Décision sur un paiement en ligne « à vérifier » sans ligne de relevé (montant divergent
   * notifié par TrésorPay). PAY-013 si la décision n'est pas possible.
   */
  @Transactional
  public void deciderPaiement(String reference, Decision decision, String motif) {
    String motifNet = motif(decision, motif);
    Paiement p =
        paiements
            .verrouillerParReference(reference)
            .orElseThrow(
                () -> new ErreurMetier(CodeErreur.PAY_005, Map.of("reference", reference)));
    // Un virement « à vérifier » se décide par sa ligne de relevé, jamais à part ; un complément
    // attendu se décide seulement une fois son échéance dépassée (US-04.12).
    boolean seul = ecarts.seulADecider(p.id());
    boolean echu = p.statut() == StatutPaiement.complement_attendu;
    if (!seul
        || !RegleEcarts.pourPaiement(p.montantGnf(), p.montantRecuGnf(), echu, p.estComplement())
            .contains(decision)) {
      throw new ErreurMetier(
          CodeErreur.PAY_013,
          Map.of("reference", reference, "decision", decision.name().toLowerCase()));
    }
    if (decision == Decision.COMPLETER) {
      ecarts.demanderComplement(p.id(), p.montantRecuGnf(), motifNet, echeanceComplement());
    } else if (decision == Decision.VALIDER) {
      ecarts.confirmer(p.id(), p.montantRecuGnf(), null);
      long excedent = p.montantRecuGnf() - p.montantGnf();
      if (excedent > 0) {
        remboursements.demander(
            p.id(), ServiceRemboursements.EXCEDENT, excedent, null, "Excédent reçu en ligne");
      }
    } else {
      ecarts.rejeter(p.id(), motifNet);
      if (p.montantRecuGnf() != null && p.montantRecuGnf() > 0) {
        remboursements.demander(
            p.id(),
            ServiceRemboursements.AUTRE,
            p.montantRecuGnf(),
            null,
            "Somme reçue d'un paiement rejeté : " + motifNet);
      }
    }
  }

  private long montantAttendu(java.util.UUID paiementId) {
    return paiements.lire(paiementId).map(Paiement::montantGnf).orElse(0L);
  }

  private static List<Decision> decisions(Element e) {
    return "ligne".equals(e.type())
        ? RegleEcarts.pourLigne(e.nature(), e.paiementAVerifier(), e.complement())
        : RegleEcarts.pourPaiement(
            e.montantAttenduGnf() == null ? 0 : e.montantAttenduGnf(),
            e.montantRecuGnf(),
            e.complementEchu(),
            e.complement());
  }

  /** Motif nettoyé ; obligatoire (10 à 2 000 caractères) pour un rejet ou un classement. */
  private static String motif(Decision decision, String motif) {
    String net = motif == null ? "" : motif.strip();
    if (decision.motifObligatoire() && net.length() < MOTIF_MIN) {
      throw erreurMotif("Expliquez le motif en " + MOTIF_MIN + " caractères au moins.");
    }
    if (net.length() > MOTIF_MAX) {
      throw erreurMotif("Le motif dépasse " + MOTIF_MAX + " caractères.");
    }
    return net.isEmpty() ? null : net;
  }

  private static ErreurMetier erreurMotif(String message) {
    return new ErreurMetier(
        CodeErreur.VAL_001,
        Map.of("champs", List.of(Map.of("champ", "motif", "message", message))));
  }

  private long moi() {
    return utilisateurCourant.get().orElseThrow(() -> new ErreurMetier(CodeErreur.AUTH_001)).id();
  }
}
