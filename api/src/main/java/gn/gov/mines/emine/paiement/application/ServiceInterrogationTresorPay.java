package gn.gov.mines.emine.paiement.application;

import gn.gov.mines.emine.paiement.domaine.Paiement;
import gn.gov.mines.emine.paiement.domaine.PaiementGateway;
import gn.gov.mines.emine.paiement.domaine.PaiementGateway.EtatTransaction;
import gn.gov.mines.emine.paiement.domaine.PaiementGateway.PasserelleIndisponible;
import gn.gov.mines.emine.paiement.domaine.PaiementGateway.StatutTransaction;
import gn.gov.mines.emine.paiement.infrastructure.PaiementRepository;
import gn.gov.mines.emine.shared.jooq.enums.StatutPaiement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Interrogation de TrésorPay quand la notification n'arrive pas (US-04.5) : toutes les 30 s, chaque
 * paiement en ligne en attente depuis plus de 30 s est interrogé, jusqu'à la fin de sa session (15
 * min). La réponse s'applique comme une notification (payé, à vérifier si le montant diffère,
 * refusé, annulé). Au-delà de 15 min : {@code paye} si TrésorPay le confirme, {@code expire} si la
 * session s'est close sans paiement, sinon {@code a_verifier} (aucune réponse : le Responsable
 * fiscal tranche). Les références dont la date limite est passée sans paiement expirent.
 */
@Service
public class ServiceInterrogationTresorPay {

  private static final Logger LOG = LoggerFactory.getLogger(ServiceInterrogationTresorPay.class);

  /** Délai laissé à la notification avant la première interrogation. */
  public static final Duration DELAI_NOTIFICATION = Duration.ofSeconds(30);

  private final PaiementRepository paiements;
  private final PaiementGateway passerelle;
  private final ContexteSystemeNotification contexteSysteme;
  private final JsonMapper json;
  private final Clock horloge;

  public ServiceInterrogationTresorPay(
      PaiementRepository paiements,
      PaiementGateway passerelle,
      ContexteSystemeNotification contexteSysteme,
      JsonMapper json,
      Clock horloge) {
    this.paiements = paiements;
    this.passerelle = passerelle;
    this.contexteSysteme = contexteSysteme;
    this.json = json;
    this.horloge = horloge;
  }

  /** Résultat d'un passage : paiements interrogés, puis paiements dont le statut a changé. */
  public record Passage(int interroges, int modifies, int expires) {}

  public Passage executer() {
    return executer(horloge.instant());
  }

  /** Un passage à l'instant donné (les tests simulent l'écoulement des 15 minutes). */
  public Passage executer(Instant maintenant) {
    int expires = contexteSysteme.executerTache(paiements::expirerReferences);
    List<Paiement> attente =
        contexteSysteme.executerTache(
            () -> paiements.enAttenteAInterroger(maintenant.minus(DELAI_NOTIFICATION)));
    int modifies = 0;
    for (Paiement p : attente) {
      EtatTransaction etat;
      try {
        etat = passerelle.interroger(p.reference());
      } catch (PasserelleIndisponible e) {
        etat = null;
      } catch (RuntimeException e) {
        LOG.warn(
            "Interrogation de TrésorPay impossible pour {} : {}", p.reference(), e.getMessage());
        etat = null;
      }
      EtatTransaction reponse = etat;
      if (Boolean.TRUE.equals(
          contexteSysteme.executerTache(() -> appliquer(p.reference(), reponse, maintenant)))) {
        modifies++;
      }
    }
    if (expires > 0 || modifies > 0) {
      LOG.info(
          "TrésorPay : {} paiement(s) interrogé(s), {} mis à jour, {} référence(s) expirée(s)",
          attente.size(),
          modifies,
          expires);
    }
    return new Passage(attente.size(), modifies, expires);
  }

  /** Applique la réponse (ou son absence) à un paiement encore en attente ; vrai s'il a changé. */
  private boolean appliquer(String reference, EtatTransaction etat, Instant maintenant) {
    Paiement p = paiements.verrouillerParReference(reference).orElse(null);
    if (p == null || p.statut() != StatutPaiement.en_attente) {
      return false; // notification arrivée entre-temps : rien à faire
    }
    Map<String, Object> trace = new LinkedHashMap<>();
    trace.put("statut", etat == null ? "SANS_REPONSE" : etat.statut().name());
    if (etat != null && etat.montantGnf() != null) {
      trace.put("montantGnf", etat.montantGnf());
    }
    paiements.journaliserInterrogation(
        p.id(),
        reference,
        etat == null ? null : etat.transactionId(),
        json.writeValueAsString(trace));
    boolean sessionTerminee =
        p.dateExpiration() != null && !maintenant.isBefore(p.dateExpiration());
    StatutTransaction s = etat == null ? null : etat.statut();
    if (s == StatutTransaction.PAYE && etat.montantGnf() != null) {
      boolean exact = etat.montantGnf() == p.montantGnf();
      paiements.appliquerResultat(
          p.id(),
          exact ? StatutPaiement.paye : StatutPaiement.a_verifier,
          etat.transactionId(),
          etat.montantGnf());
      return true;
    }
    if (s == StatutTransaction.REFUSE) {
      paiements.appliquerResultat(p.id(), StatutPaiement.echoue, etat.transactionId(), null);
      return true;
    }
    if (s == StatutTransaction.ANNULE) {
      paiements.appliquerResultat(p.id(), StatutPaiement.annule, etat.transactionId(), null);
      return true;
    }
    if (!sessionTerminee) {
      return false; // session encore ouverte : on réinterrogera au prochain passage
    }
    if (s == StatutTransaction.EN_COURS || s == StatutTransaction.EXPIREE) {
      paiements.appliquerResultat(p.id(), StatutPaiement.expire, null, null);
    } else {
      // Pas de réponse, ou transaction inconnue de TrésorPay : le Responsable fiscal tranche.
      LOG.warn("Paiement {} : TrésorPay ne confirme rien après 15 min, à vérifier", reference);
      paiements.appliquerResultat(p.id(), StatutPaiement.a_verifier, null, null);
    }
    return true;
  }
}
