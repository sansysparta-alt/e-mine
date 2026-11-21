package gn.gov.mines.emine.catalogue.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.TARIF;
import static gn.gov.mines.emine.shared.jooq.Tables.TYPE_DOCUMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.WORKFLOW;

import gn.gov.mines.emine.catalogue.TypeDocument;
import gn.gov.mines.emine.shared.jooq.enums.ObjetPaiement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/** Lecture jOOQ des documents actifs du catalogue et de leurs tarifs en vigueur (US-02.1). */
@Repository
public class TypeDocumentRepository {

  /** Les dates d'effet des tarifs s'entendent à l'heure de Conakry. */
  private static final ZoneId CONAKRY = ZoneId.of("Africa/Conakry");

  private final DSLContext dsl;

  public TypeDocumentRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public List<TypeDocument> listerActifs() {
    Map<Short, Map<ObjetPaiement, Long>> tarifs = tarifsEnVigueur();
    return dsl.select(
            TYPE_DOCUMENT.ID,
            TYPE_DOCUMENT.CODE,
            TYPE_DOCUMENT.LIBELLE_FR,
            TYPE_DOCUMENT.LIBELLE_EN,
            TYPE_DOCUMENT.DELAI_JOURS,
            TYPE_DOCUMENT.RENOUVELABLE,
            TYPE_DOCUMENT.PIECES_CLES,
            TYPE_DOCUMENT.INSTRUIT_PAR,
            TYPE_DOCUMENT.SIGNE_PAR,
            WORKFLOW.CODE,
            WORKFLOW.LIBELLE)
        .from(TYPE_DOCUMENT)
        .join(WORKFLOW)
        .on(WORKFLOW.ID.eq(TYPE_DOCUMENT.WORKFLOW_ID))
        .where(TYPE_DOCUMENT.ACTIF.isTrue())
        .and(TYPE_DOCUMENT.DELETED_AT.isNull())
        .orderBy(WORKFLOW.ID, TYPE_DOCUMENT.LIBELLE_FR, TYPE_DOCUMENT.CODE)
        .fetch(
            r -> {
              Map<ObjetPaiement, Long> t = tarifs.getOrDefault(r.get(TYPE_DOCUMENT.ID), Map.of());
              return new TypeDocument(
                  r.get(TYPE_DOCUMENT.ID),
                  r.get(TYPE_DOCUMENT.CODE),
                  r.get(TYPE_DOCUMENT.LIBELLE_FR),
                  r.get(TYPE_DOCUMENT.LIBELLE_EN),
                  new TypeDocument.Famille(r.get(WORKFLOW.CODE), r.get(WORKFLOW.LIBELLE)),
                  r.get(TYPE_DOCUMENT.DELAI_JOURS),
                  r.get(TYPE_DOCUMENT.RENOUVELABLE),
                  pieces(r.get(TYPE_DOCUMENT.PIECES_CLES)),
                  r.get(TYPE_DOCUMENT.INSTRUIT_PAR),
                  r.get(TYPE_DOCUMENT.SIGNE_PAR),
                  new TypeDocument.Tarifs(
                      t.get(ObjetPaiement.FRAIS_DOSSIER),
                      t.get(ObjetPaiement.DROITS),
                      t.get(ObjetPaiement.ATTESTATION)));
            });
  }

  /**
   * Par document et par objet : montant de la version active la plus récente dont la date d'effet
   * est passée.
   */
  private Map<Short, Map<ObjetPaiement, Long>> tarifsEnVigueur() {
    Map<Short, Map<ObjetPaiement, Long>> parDocument = new HashMap<>();
    dsl.select(TARIF.TYPE_DOCUMENT_ID, TARIF.OBJET, TARIF.MONTANT_GNF)
        .from(TARIF)
        .where(TARIF.ACTIF.isTrue())
        .and(TARIF.DATE_EFFET.le(LocalDate.now(CONAKRY)))
        .orderBy(TARIF.TYPE_DOCUMENT_ID, TARIF.OBJET, TARIF.VERSION.desc())
        .fetch()
        .forEach(
            r ->
                parDocument
                    .computeIfAbsent(
                        r.get(TARIF.TYPE_DOCUMENT_ID), k -> new EnumMap<>(ObjetPaiement.class))
                    .putIfAbsent(r.get(TARIF.OBJET), r.get(TARIF.MONTANT_GNF)));
    return parDocument;
  }

  /** « Statuts, capacités, carte de la zone » → [Statuts, Capacités, Carte de la zone]. */
  static List<String> pieces(String piecesCles) {
    if (piecesCles == null || piecesCles.isBlank()) {
      return List.of();
    }
    return Arrays.stream(piecesCles.split(","))
        .map(String::strip)
        .filter(p -> !p.isEmpty())
        .map(p -> p.substring(0, 1).toUpperCase(Locale.FRENCH) + p.substring(1))
        .toList();
  }
}
