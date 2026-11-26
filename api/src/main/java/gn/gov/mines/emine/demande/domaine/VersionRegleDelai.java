package gn.gov.mines.emine.demande.domaine;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Une version de la règle des délais (lot 4, V27) : proposée par l'administrateur, validée par le
 * Ministre, appliquée à sa date d'effet aux demandes déposées ensuite.
 *
 * @param etat état affiché ({@code a_valider}, {@code en_vigueur}, {@code programmee}, {@code
 *     remplacee}, {@code refusee}, {@code retiree} : {@link
 *     gn.gov.mines.emine.shared.gouvernance.EtatVersion})
 * @param proposeePar nom de l'administrateur, {@code null} pour la règle initiale
 * @param decideePar nom de la personne qui a validé, refusé ou retiré
 */
public record VersionRegleDelai(
    int version,
    String etat,
    RegleDelais.Parametres parametres,
    LocalDate dateEffet,
    String motif,
    String proposeePar,
    Instant proposeeLe,
    String decideePar,
    Instant decideeLe,
    String motifRefus) {}
