package gn.gov.mines.emine.paiement.application;

import gn.gov.mines.emine.paiement.domaine.Paiement;
import gn.gov.mines.emine.paiement.domaine.PaiementGateway;
import gn.gov.mines.emine.paiement.domaine.PaiementGateway.DemandeSession;
import gn.gov.mines.emine.paiement.domaine.PaiementGateway.PasserelleIndisponible;
import gn.gov.mines.emine.paiement.domaine.PaiementGateway.SessionOuverte;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.jooq.enums.MoyenPaiement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Paiement en ligne (US-04.3) : crée le paiement, ouvre la session chez TrésorPay puis renvoie
 * l'adresse de la page de paiement. L'appel à la passerelle se fait hors transaction (aucune
 * connexion à la base n'est retenue pendant l'attente réseau) ; si la passerelle est indisponible,
 * le paiement est annulé et l'usager reçoit PAY-003.
 */
@Service
public class ServicePaiementEnLigne {

  private final ServicePaiements paiements;
  private final PaiementGateway passerelle;
  private final JsonMapper json;
  private final String urlApplication;

  public ServicePaiementEnLigne(
      ServicePaiements paiements,
      PaiementGateway passerelle,
      JsonMapper json,
      @Value("${emine.tresorpay.url-application:http://localhost:3001}") String urlApplication) {
    this.paiements = paiements;
    this.passerelle = passerelle;
    this.json = json;
    this.urlApplication = urlApplication.replaceAll("/$", "");
  }

  /**
   * Paiement créé, et adresse de la page de paiement pour un moyen en ligne.
   *
   * @param urlPaiement adresse vers laquelle rediriger le navigateur, {@code null} hors paiement en
   *     ligne
   */
  public record Initiation(Paiement paiement, String urlPaiement) {}

  public Initiation payer(long demandeId, MoyenPaiement moyen) {
    Paiement paiement = paiements.choisirMoyen(demandeId, moyen);
    if (moyen != MoyenPaiement.TRESORPAY) {
      return new Initiation(paiement, null);
    }
    SessionOuverte session;
    try {
      session =
          passerelle.ouvrirSession(
              new DemandeSession(
                  paiement.reference(),
                  paiement.montantGnf(),
                  paiements.libelle(paiement),
                  urlApplication + "/demandes/" + demandeId + "/paiement/retour"));
    } catch (PasserelleIndisponible e) {
      paiements.annuler(paiement);
      throw new ErreurMetier(CodeErreur.PAY_003, Map.of("reference", paiement.reference()));
    } catch (RuntimeException e) {
      // Réponse inattendue (configuration) : le paiement ne reste pas ouvert inutilement.
      paiements.annuler(paiement);
      throw e;
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("sessionId", session.sessionId());
    payload.put("expireLe", session.expireLe().toString());
    Paiement enAttente =
        paiements.enregistrerSession(
            paiement, json.writeValueAsString(payload), session.expireLe());
    return new Initiation(enAttente, session.urlPaiement());
  }
}
