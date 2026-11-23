package gn.gov.mines.emine.notification.infrastructure;

import gn.gov.mines.emine.notification.domaine.Message;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Met en page l'e-mail d'une notification : version HTML (gabarit {@code gabarits/courriel.html},
 * texte échappé par Thymeleaf) et version texte brut, avec le lien de suivi et le pied de page.
 */
@Component
public class MiseEnPageCourriel {

  private final TemplateEngine moteur;
  private final RedacteurNotifications redacteur;

  public MiseEnPageCourriel(RedacteurNotifications redacteur) {
    this.redacteur = redacteur;
    ClassLoaderTemplateResolver resolveur = new ClassLoaderTemplateResolver();
    resolveur.setPrefix("gabarits/");
    resolveur.setSuffix(".html");
    resolveur.setTemplateMode(TemplateMode.HTML);
    resolveur.setCharacterEncoding("UTF-8");
    resolveur.setCacheable(true);
    moteur = new TemplateEngine();
    moteur.setTemplateResolver(resolveur);
  }

  public String html(Message message, String langue) {
    boolean anglais = "en".equals(langue);
    Context contexte = new Context(anglais ? Locale.ENGLISH : Locale.FRENCH);
    contexte.setVariable("langue", anglais ? "en" : "fr");
    contexte.setVariable("sujet", message.sujet());
    contexte.setVariable("paragraphes", paragraphes(message.texte()));
    contexte.setVariable("lien", message.lienSuivi());
    contexte.setVariable("libelleLien", message.libelleLien());
    contexte.setVariable("entete", redacteur.commun(langue, "entete"));
    contexte.setVariable("pied", redacteur.commun(langue, "pied"));
    return moteur.process("courriel", contexte);
  }

  public String texte(Message message, String langue) {
    StringBuilder t = new StringBuilder(message.texte());
    if (message.lienSuivi() != null) {
      t.append("\n\n").append(message.libelleLien()).append(" : ").append(message.lienSuivi());
    }
    return t.append("\n\n-- \n").append(redacteur.commun(langue, "pied")).toString();
  }

  static List<String> paragraphes(String texte) {
    return Arrays.stream(texte.split("\\n\\s*\\n"))
        .map(String::strip)
        .filter(p -> !p.isEmpty())
        .toList();
  }
}
