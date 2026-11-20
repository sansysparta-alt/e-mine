package gn.gov.mines.emine.shared.session;

import java.util.regex.Pattern;

/**
 * Contexte de l'utilisateur courant, transmis à PostgreSQL au début de chaque transaction (US-01.5)
 * pour la RLS et le journal d'audit : {@code emine.user_id}, {@code emine.interne}, {@code
 * emine.ip}.
 *
 * @param utilisateurId identifiant {@code utilisateur.id}, ou {@code null} si anonyme
 * @param interne {@code true} pour un profil interne du Ministère (voit toutes les demandes)
 * @param ip adresse IP du client, ou {@code null} si inconnue ou invalide
 */
public record ContexteSession(Long utilisateurId, boolean interne, String ip) {

  /**
   * Caractères admis dans une adresse IPv4 ou IPv6 (le type inet de PostgreSQL valide le reste).
   */
  private static final Pattern IP = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

  /** Aucun utilisateur : la RLS ne laisse rien voir des demandes et paiements. */
  public static final ContexteSession ANONYME = new ContexteSession(null, false, null);

  public ContexteSession {
    if (ip != null && !IP.matcher(ip).matches()) {
      ip = null;
    }
  }
}
