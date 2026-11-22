package gn.gov.mines.emine.delivrance.domaine;

import java.time.Instant;

/**
 * Contenu du document officiel délivré (US-06.1), rendu par le gabarit générique paramétré par le
 * catalogue. Aucune valeur n'est calculée par le gabarit : tout est fourni ici.
 *
 * @param numero numéro du document {@code <CODE>-AAAA-NNNN}, attribué par la base (V10)
 * @param codeType code du document au catalogue (PEX, CAR…)
 * @param libelleDocument libellé de l'application (D2)
 * @param titulaire prénom et nom du demandeur
 * @param organisation raison sociale, ou {@code null} pour une personne physique
 * @param demandeNumero numéro de la demande {@code DEM-AAAA-NNNNNN}
 * @param decisionLe date de la décision favorable
 * @param observations observations de la décision, ou {@code null}
 * @param signataireFonction signataire du catalogue (D4), par exemple « Ministre »
 * @param signataireNom prénom et nom de la personne qui signe
 * @param delivreLe date de délivrance
 * @param urlVerification adresse publique de vérification (encodée dans le QR)
 * @param codeVerification code de vérification du registre
 */
public record ContenuDocument(
    String numero,
    String codeType,
    String libelleDocument,
    String titulaire,
    String organisation,
    String demandeNumero,
    Instant decisionLe,
    String observations,
    String signataireFonction,
    String signataireNom,
    Instant delivreLe,
    String urlVerification,
    String codeVerification) {}
