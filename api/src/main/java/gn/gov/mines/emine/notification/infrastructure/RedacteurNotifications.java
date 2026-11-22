package gn.gov.mines.emine.notification.infrastructure;

import gn.gov.mines.emine.notification.domaine.Message;
import gn.gov.mines.emine.notification.domaine.NotificationAEnvoyer;
import gn.gov.mines.emine.notification.domaine.RegleEssais;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Rédige les notifications dans la langue du destinataire ({@code utilisateur.langue}) à partir des
 * modèles {@code messages/notifications[_en].properties} : objet, texte de l'e-mail et SMS. Les
 * valeurs viennent du contenu écrit par le trigger métier, avec les formats de l'application
 * (montants en GNF entiers, dates JJ/MM/AAAA à l'heure de Conakry). Un modèle inconnu ou une valeur
 * manquante est une erreur : on n'envoie jamais un message incomplet.
 */
@Component
public class RedacteurNotifications {

  private static final ZoneId CONAKRY = ZoneId.of("Africa/Conakry");
  private static final DateTimeFormatter JJ_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final Pattern VARIABLE = Pattern.compile("\\{([a-zA-Z_]+)}");
  private static final String BUNDLE = "messages/notifications";

  private final String urlApplication;

  public RedacteurNotifications(
      @Value("${emine.notifications.url-application:http://localhost:3001}")
          String urlApplication) {
    this.urlApplication = urlApplication.replaceAll("/+$", "");
  }

  public Message rediger(NotificationAEnvoyer n) {
    ResourceBundle modeles = modeles(n.langue());
    Map<String, String> valeurs = valeurs(n);
    String sujet = remplir(texte(modeles, n.modele() + ".sujet"), valeurs);
    String corps =
        remplir(texte(modeles, "commun.salutation"), valeurs)
            + "\n\n"
            + remplir(texte(modeles, n.modele() + ".texte"), valeurs);
    String sms = remplir(texte(modeles, n.modele() + ".sms"), valeurs);
    if (sms.length() > RegleEssais.LONGUEUR_SMS_MAX) {
      throw new IllegalStateException(
          "SMS trop long (" + sms.length() + " caractères) pour le modèle " + n.modele());
    }
    String lien = n.demandeId() == null ? null : urlApplication + "/demandes/" + n.demandeId();
    return new Message(sujet, corps, sms, lien, texte(modeles, "commun.lien"));
  }

  /** Textes communs de l'e-mail (pied de page…) dans la langue donnée. */
  public String commun(String langue, String cle) {
    return texte(modeles(langue), "commun." + cle);
  }

  static ResourceBundle modeles(String langue) {
    Locale locale = "en".equals(langue) ? Locale.ENGLISH : Locale.ROOT;
    return ResourceBundle.getBundle(
        BUNDLE,
        locale,
        ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
  }

  private static String texte(ResourceBundle modeles, String cle) {
    try {
      return modeles.getString(cle);
    } catch (MissingResourceException e) {
      throw new IllegalStateException("Modèle de notification inconnu : " + cle, e);
    }
  }

  static Map<String, String> valeurs(NotificationAEnvoyer n) {
    Map<String, String> v = new HashMap<>();
    n.contenu().forEach((cle, valeur) -> v.put(cle, formater(cle, valeur)));
    if (n.numeroDemande() != null) {
      v.put("numero", n.numeroDemande());
    }
    v.put("prenom", n.prenom());
    return v;
  }

  static String formater(String cle, Object valeur) {
    if (valeur instanceof Number nombre && cle.endsWith("_gnf")) {
      return montant(nombre.longValue());
    }
    if (valeur instanceof Collection<?> liste) {
      return liste.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }
    if (valeur instanceof String s && cle.toLowerCase(Locale.ROOT).startsWith("date")) {
      return date(s);
    }
    return String.valueOf(valeur);
  }

  /** Montant en GNF entiers, chiffres groupés par 3 (espace simple : compatible SMS). */
  static String montant(long gnf) {
    String chiffres = Long.toString(Math.abs(gnf));
    StringBuilder groupe = new StringBuilder();
    for (int i = 0; i < chiffres.length(); i++) {
      if (i > 0 && (chiffres.length() - i) % 3 == 0) {
        groupe.append(' ');
      }
      groupe.append(chiffres.charAt(i));
    }
    return (gnf < 0 ? "-" : "") + groupe + " GNF";
  }

  static String date(String horodatage) {
    try {
      return OffsetDateTime.parse(horodatage).atZoneSameInstant(CONAKRY).format(JJ_MM_AAAA);
    } catch (DateTimeParseException e) {
      return horodatage;
    }
  }

  static String remplir(String modele, Map<String, String> valeurs) {
    Matcher m = VARIABLE.matcher(modele);
    StringBuilder resultat = new StringBuilder();
    while (m.find()) {
      String valeur = valeurs.get(m.group(1));
      if (valeur == null) {
        throw new IllegalStateException("Valeur manquante pour {" + m.group(1) + "}");
      }
      m.appendReplacement(resultat, Matcher.quoteReplacement(valeur));
    }
    m.appendTail(resultat);
    return resultat.toString();
  }
}
