package gn.gov.mines.emine.instruction.domaine;

import java.time.Instant;

/**
 * Décision enregistrée dans {@code avis_decision} (nature « décision »).
 *
 * @param favorable sens de la décision
 * @param motif motif du rejet, ou observations d'une décision favorable ({@code null} si aucune)
 * @param auteur prénom et nom du signataire
 * @param auteurProfil libellé du profil du signataire
 */
public record DecisionRendue(
    boolean favorable, String motif, Instant renduLe, String auteur, String auteurProfil) {}
