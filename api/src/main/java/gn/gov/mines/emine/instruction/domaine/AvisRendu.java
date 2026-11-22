package gn.gov.mines.emine.instruction.domaine;

import java.time.Instant;

/**
 * Avis technique rendu sur une demande (US-05.3).
 *
 * @param profilCode profil de l'auteur au moment de l'avis
 * @param auteur prénom et nom de l'auteur
 * @param favorable sens de l'avis
 * @param motif motivation (toujours présente)
 */
public record AvisRendu(
    String profilCode,
    String profilLibelle,
    String auteur,
    boolean favorable,
    String motif,
    Instant renduLe) {}
