package gn.gov.mines.emine.simulateur.tresorpay;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Pages du simulateur : accueil (interrupteur de disponibilité, sessions récentes) et page de
 * paiement avec ses scénarios de test (contrat §5 et §6).
 */
@Controller
class PagePaiementController {

  private final RegistreSessions registre;
  private final ExpediteurNotifications expediteur;
  private final Clock horloge;

  PagePaiementController(
      RegistreSessions registre, ExpediteurNotifications expediteur, Clock horloge) {
    this.registre = registre;
    this.expediteur = expediteur;
    this.horloge = horloge;
  }

  @GetMapping("/")
  String accueil(Model model) {
    model.addAttribute("disponible", registre.disponible());
    model.addAttribute("sessions", registre.recentes(20));
    model.addAttribute("scenarios", Scenario.values());
    model.addAttribute("maintenant", horloge.instant());
    return "accueil";
  }

  @PostMapping("/disponibilite")
  RedirectView disponibilite(@RequestParam boolean disponible) {
    registre.definirDisponible(disponible);
    return redirection("/");
  }

  @GetMapping("/payer/{id}")
  String page(
      @PathVariable String id,
      @RequestParam(required = false) String scenario,
      Model model,
      jakarta.servlet.http.HttpServletResponse reponse) {
    Optional<SessionPaiement> session = registre.trouver(id);
    if (session.isEmpty()) {
      reponse.setStatus(HttpStatus.NOT_FOUND.value());
      return "introuvable";
    }
    SessionPaiement s = session.get();
    model.addAttribute("paiement", s);
    model.addAttribute("urlRetour", urlRetour(s));
    if (s.terminee()) {
      return "terminee";
    }
    if (s.expiree(horloge.instant())) {
      return "expiree";
    }
    model.addAttribute("scenarios", Scenario.proposes());
    model.addAttribute("scenarioChoisi", Scenario.depuisCode(scenario));
    model.addAttribute(
        "secondesRestantes", s.expireLe().getEpochSecond() - horloge.instant().getEpochSecond());
    return "paiement";
  }

  @PostMapping("/payer/{id}")
  Object payer(@PathVariable String id, @RequestParam(required = false) String scenario) {
    Scenario choisi = Scenario.depuisCode(scenario);
    return terminer(id, choisi == Scenario.ABANDON ? Scenario.SUCCES : choisi);
  }

  @PostMapping("/payer/{id}/annuler")
  Object annuler(@PathVariable String id) {
    return terminer(id, Scenario.ABANDON);
  }

  /**
   * Réserve la session (un seul résultat même en cas de double clic), envoie la notification, puis
   * renvoie le navigateur vers e-mine.
   */
  private Object terminer(String id, Scenario scenario) {
    AtomicBoolean reservee = new AtomicBoolean(false);
    Optional<SessionPaiement> session =
        registre.modifier(
            id,
            s -> {
              if (s.terminee() || s.expiree(horloge.instant())) {
                return s;
              }
              reservee.set(true);
              return s.terminer(new SessionPaiement.Resultat(scenario, null, "envoi en cours"));
            });
    if (session.isEmpty()) {
      return redirection("/payer/" + URLEncoder.encode(id, StandardCharsets.UTF_8));
    }
    if (!reservee.get()) {
      // Déjà traitée ou expirée : la page l'explique et propose le retour.
      return redirection("/payer/" + URLEncoder.encode(id, StandardCharsets.UTF_8));
    }
    SessionPaiement.Resultat resultat = expediteur.jouer(session.get(), scenario);
    registre.modifier(id, s -> s.terminer(resultat));
    return redirection(urlRetour(session.get()));
  }

  static String urlRetour(SessionPaiement s) {
    String separateur = s.urlRetour().contains("?") ? "&" : "?";
    return s.urlRetour()
        + separateur
        + "reference="
        + URLEncoder.encode(s.reference(), StandardCharsets.UTF_8);
  }

  private static RedirectView redirection(String url) {
    RedirectView vue = new RedirectView(url);
    vue.setStatusCode(HttpStatus.SEE_OTHER);
    vue.setExposeModelAttributes(false);
    return vue;
  }
}
