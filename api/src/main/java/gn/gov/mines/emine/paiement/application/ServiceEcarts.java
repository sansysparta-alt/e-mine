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
  private final UtilisateurCourant utilisateurCourant;

  public ServiceEcarts(
      EcartRepository ecarts, PaiementRepository paiements, UtilisateurCourant utilisateurCourant) {
    this.ecarts = ecarts;
    this.paiements = paiements;
    this.utilisateurCourant = utilisateurCourant;
  }

  /** Élément de la file et décisions possibles (règle unique {@link RegleEcarts}). */
  public record ElementAVerifier(Element element, List<Decision> decisions) {}

  @Transactional(readOnly = true)
  public PageResultat<ElementAVerifier> lister(Pagination pagination) {
    return ecarts.lister(pagination).map(e -> new ElementAVerifier(e, decisions(e)));
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
        || !RegleEcarts.pourLigne(ligne.nature(), paiementAVerifier).contains(decision)) {
      throw new ErreurMetier(
          CodeErreur.PAY_013, Map.of("ligne", id, "decision", decision.name().toLowerCase()));
    }
    long moi = moi();
    switch (decision) {
      case VALIDER -> {
        ecarts.confirmer(ligne.paiementId(), ligne.montantGnf(), ligne.dateOperation());
        ecarts.deciderLigne(id, decision.code(), motifNet, moi, null);
      }
      case REJETER -> {
        ecarts.rejeter(ligne.paiementId(), motifNet);
        ecarts.deciderLigne(id, decision.code(), motifNet, moi, null);
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
                .orElseThrow(() -> new ErreurMetier(CodeErreur.PAY_014, Map.of("reference", ref)));
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
    // Un virement « à vérifier » se décide par sa ligne de relevé, jamais à part.
    boolean seul = p.statut() == StatutPaiement.a_verifier && !ecarts.aUneLigneATraiter(p.id());
    if (!seul || !RegleEcarts.pourPaiement(p.montantGnf(), p.montantRecuGnf()).contains(decision)) {
      throw new ErreurMetier(
          CodeErreur.PAY_013,
          Map.of("reference", reference, "decision", decision.name().toLowerCase()));
    }
    if (decision == Decision.VALIDER) {
      ecarts.confirmer(p.id(), p.montantRecuGnf(), null);
    } else {
      ecarts.rejeter(p.id(), motifNet);
    }
  }

  private static List<Decision> decisions(Element e) {
    return "ligne".equals(e.type())
        ? RegleEcarts.pourLigne(e.nature(), e.paiementAVerifier())
        : RegleEcarts.pourPaiement(
            e.montantAttenduGnf() == null ? 0 : e.montantAttenduGnf(), e.montantRecuGnf());
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
