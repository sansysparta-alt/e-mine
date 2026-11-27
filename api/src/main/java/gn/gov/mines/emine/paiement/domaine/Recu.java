package gn.gov.mines.emine.paiement.domaine;

import java.time.Instant;

/**
 * Contenu d'un reçu de paiement (US-04.6), simple et sans signature en V1, avec le QR de sa
 * vérification publique (US-04.14).
 *
 * @param numero {@code REC-AAAA-NNNNNN}, attribué par la base au passage à « payé » (D3)
 * @param reference référence du paiement {@code PAY-AAAA-NNNNNN}
 * @param montantGnf montant encaissé, en GNF entiers
 * @param montantEnLettres montant en toutes lettres
 * @param objet libellé de l'objet (« Frais de dossier »…)
 * @param moyen libellé du moyen (« TrésorPay »…)
 * @param transactionId identifiant de la transaction chez la passerelle
 * @param demandeNumero {@code DEM-AAAA-NNNNNN}
 * @param document libellé et code du document demandé
 * @param payeur prénom et nom du demandeur
 * @param organisation organisation du demandeur, {@code null} sinon
 * @param heureConnue l'heure du paiement est connue (TrésorPay) ; un virement n'a que la date du
 *     relevé bancaire : le reçu affiche la date seule
 * @param urlVerification page publique de vérification du reçu, encodée dans le QR
 */
public record Recu(
    String numero,
    String reference,
    Instant datePaiement,
    long montantGnf,
    String montantEnLettres,
    String objet,
    String moyen,
    String transactionId,
    String demandeNumero,
    String document,
    String payeur,
    String organisation,
    boolean heureConnue,
    String urlVerification) {}
