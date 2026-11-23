package gn.gov.mines.emine.auth;

/**
 * Permissions de la matrice des droits (table {@code permission}, US-01.6). Les droits d'un profil
 * sont lus dans {@code profil_permission} (la base fait foi, D2) et deviennent des autorités Spring
 * Security {@code PERM_<CODE>}. Dans un contrôleur :
 * {@code @PreAuthorize("hasAuthority('PERM_DEPOSER')")} ; un refus donne PERM-001 (403).
 */
public enum Permission {
  DEPOSER,
  PAYER,
  INSTRUIRE,
  SIGNER,
  ENCAISSER,
  VERIFIER,
  PARAMETRER,
  CADASTRE_LECTURE,
  CADASTRE_ECRITURE,
  PUBLIER,
  /** Approuver les versions du catalogue et des tarifs (Ministre, V18). */
  VALIDER_CATALOGUE;

  /** Préfixe des autorités Spring Security issues de la matrice. */
  public static final String PREFIXE = "PERM_";

  /** Autorité Spring Security, par exemple {@code PERM_DEPOSER}. */
  public String autorite() {
    return PREFIXE + name();
  }
}
