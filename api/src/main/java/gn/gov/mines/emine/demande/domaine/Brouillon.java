package gn.gov.mines.emine.demande.domaine;

import java.math.BigDecimal;

/**
 * Contenu saisi d'une demande au statut brouillon (US-03.1). Seul le document est obligatoire tant
 * que la demande n'est pas déposée.
 *
 * @param typeDocumentCode code du document demandé (PEX, COR…)
 * @param prefectureId préfecture concernée, facultative
 * @param substanceCode substance principale, facultative
 * @param superficieHa superficie demandée en hectares, facultative (sans périmètre cartographique
 *     en V1)
 */
public record Brouillon(
    String typeDocumentCode, Short prefectureId, String substanceCode, BigDecimal superficieHa) {}
