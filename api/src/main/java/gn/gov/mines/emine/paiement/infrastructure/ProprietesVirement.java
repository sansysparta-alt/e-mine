package gn.gov.mines.emine.paiement.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Coordonnées du compte qui reçoit les virements (US-04.7), lues dans la configuration : elles ne
 * se modifient pas depuis l'application (un changement de compte bancaire est une opération
 * sensible, faite au déploiement). Valeurs fictives en démonstration (dépôt public).
 *
 * @param beneficiaire titulaire du compte ({@code VIREMENT_BENEFICIAIRE})
 * @param banque banque teneuse du compte ({@code VIREMENT_BANQUE})
 * @param iban IBAN du compte ({@code VIREMENT_IBAN})
 * @param bic code BIC de la banque ({@code VIREMENT_BIC})
 */
@ConfigurationProperties("emine.virement")
public record ProprietesVirement(
    @DefaultValue("Trésor public — recettes du Ministère des Mines (démonstration)")
        String beneficiaire,
    @DefaultValue("Banque de démonstration") String banque,
    @DefaultValue("GN00 0000 0000 0000 0000 0000 000") String iban,
    @DefaultValue("DEMOGNGNXXX") String bic) {}
