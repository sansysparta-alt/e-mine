package gn.gov.mines.emine.notification.domaine;

/**
 * Message rédigé dans la langue du destinataire (US-07.1, US-07.2).
 *
 * @param sujet objet de l'e-mail
 * @param texte corps de l'e-mail (texte brut, paragraphes séparés par une ligne vide)
 * @param sms texte du SMS (160 caractères au plus)
 * @param lienSuivi adresse du suivi de la demande dans l'application, ou {@code null}
 * @param libelleLien texte du lien de suivi
 */
public record Message(
    String sujet, String texte, String sms, String lienSuivi, String libelleLien) {}
