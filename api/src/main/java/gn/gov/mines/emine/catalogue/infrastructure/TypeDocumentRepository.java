package gn.gov.mines.emine.catalogue.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.PROFIL;
import static gn.gov.mines.emine.shared.jooq.Tables.TARIF;
import static gn.gov.mines.emine.shared.jooq.Tables.TYPE_DOCUMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.TYPE_DOCUMENT_AVIS;
import static gn.gov.mines.emine.shared.jooq.Tables.VERSION_DOCUMENT;
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
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * Lecture jOOQ des documents du catalogue et de leurs tarifs en vigueur (US-02.1), y compris selon
 * une version de leur fiche (US-02.2).
 */
@Repository
public class TypeDocumentRepository {

  /** Les dates d'effet des tarifs s'entendent à l'heure de Conakry. */
  private static final ZoneId CONAKRY = ZoneId.of("Africa/Conakry");

  private final DSLContext dsl;

  public TypeDocumentRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** Documents actifs du catalogue (public). */
  public List<TypeDocument> listerActifs() {
    return lister(TYPE_DOCUMENT.ACTIF.isTrue());
  }

  /** Tous les documents, actifs ou retirés du catalogue (administration, demandes en cours). */
  public List<TypeDocument> listerTous() {
    return lister(DSL.trueCondition());
  }

  private List<TypeDocument> lister(Condition condition) {
    Map<Short, Map<ObjetPaiement, Long>> tarifs = tarifsEnVigueur();
    Map<Short, List<TypeDocument.AvisRequis>> avis = avisRequis();
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
            TYPE_DOCUMENT.ACTIF,
            PROFIL.CODE,
            WORKFLOW.CODE,
            WORKFLOW.LIBELLE)
        .from(TYPE_DOCUMENT)
        .join(WORKFLOW)
        .on(WORKFLOW.ID.eq(TYPE_DOCUMENT.WORKFLOW_ID))
        .join(PROFIL)
        .on(PROFIL.ID.eq(TYPE_DOCUMENT.SIGNATAIRE_PROFIL_ID))
        .where(condition)
        .and(TYPE_DOCUMENT.DELETED_AT.isNull())
        .orderBy(WORKFLOW.ID, TYPE_DOCUMENT.LIBELLE_FR, TYPE_DOCUMENT.CODE)
        .fetch(
            r ->
                new TypeDocument(
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
                    r.get(PROFIL.CODE),
                    tarifs(tarifs.getOrDefault(r.get(TYPE_DOCUMENT.ID), Map.of())),
                    avis.getOrDefault(r.get(TYPE_DOCUMENT.ID), List.of()),
                    r.get(TYPE_DOCUMENT.ACTIF)));
  }

  /**
   * Le document tel que fixé par une version de sa fiche (V18) : celle en vigueur au dépôt d'une
   * demande. Les tarifs restent ceux en vigueur aujourd'hui (un paiement garde le montant de sa
   * création).
   */
  public Optional<TypeDocument> selonVersion(long versionId) {
    Map<Short, Map<ObjetPaiement, Long>> tarifs = tarifsEnVigueur();
    Map<Short, String> libellesProfils =
        dsl.select(PROFIL.ID, PROFIL.LIBELLE).from(PROFIL).fetchMap(PROFIL.ID, PROFIL.LIBELLE);
    Map<Short, String> codesProfils =
        dsl.select(PROFIL.ID, PROFIL.CODE).from(PROFIL).fetchMap(PROFIL.ID, PROFIL.CODE);
    return dsl.select(
            TYPE_DOCUMENT.ID,
            TYPE_DOCUMENT.CODE,
            TYPE_DOCUMENT.RENOUVELABLE,
            WORKFLOW.CODE,
            WORKFLOW.LIBELLE,
            VERSION_DOCUMENT.LIBELLE_FR,
            VERSION_DOCUMENT.LIBELLE_EN,
            VERSION_DOCUMENT.DELAI_JOURS,
            VERSION_DOCUMENT.PIECES_CLES,
            VERSION_DOCUMENT.INSTRUIT_PAR,
            VERSION_DOCUMENT.SIGNE_PAR,
            VERSION_DOCUMENT.SIGNATAIRE_PROFIL_ID,
            VERSION_DOCUMENT.AVIS_PROFILS,
            VERSION_DOCUMENT.ACTIF)
        .from(VERSION_DOCUMENT)
        .join(TYPE_DOCUMENT)
        .on(TYPE_DOCUMENT.ID.eq(VERSION_DOCUMENT.TYPE_DOCUMENT_ID))
        .join(WORKFLOW)
        .on(WORKFLOW.ID.eq(TYPE_DOCUMENT.WORKFLOW_ID))
        .where(VERSION_DOCUMENT.ID.eq(versionId))
        .fetchOptional(
            r ->
                new TypeDocument(
                    r.get(TYPE_DOCUMENT.ID),
                    r.get(TYPE_DOCUMENT.CODE),
                    r.get(VERSION_DOCUMENT.LIBELLE_FR),
                    r.get(VERSION_DOCUMENT.LIBELLE_EN),
                    new TypeDocument.Famille(r.get(WORKFLOW.CODE), r.get(WORKFLOW.LIBELLE)),
                    r.get(VERSION_DOCUMENT.DELAI_JOURS),
                    r.get(TYPE_DOCUMENT.RENOUVELABLE),
                    pieces(r.get(VERSION_DOCUMENT.PIECES_CLES)),
                    r.get(VERSION_DOCUMENT.INSTRUIT_PAR),
                    r.get(VERSION_DOCUMENT.SIGNE_PAR),
                    codesProfils.get(r.get(VERSION_DOCUMENT.SIGNATAIRE_PROFIL_ID)),
                    tarifs(tarifs.getOrDefault(r.get(TYPE_DOCUMENT.ID), Map.of())),
                    Arrays.stream(r.get(VERSION_DOCUMENT.AVIS_PROFILS))
                        .map(
                            id ->
                                new TypeDocument.AvisRequis(
                                    codesProfils.get(id), libellesProfils.get(id)))
                        .toList(),
                    r.get(VERSION_DOCUMENT.ACTIF)));
  }

  private static TypeDocument.Tarifs tarifs(Map<ObjetPaiement, Long> t) {
    return new TypeDocument.Tarifs(
        t.get(ObjetPaiement.FRAIS_DOSSIER),
        t.get(ObjetPaiement.DROITS),
        t.get(ObjetPaiement.ATTESTATION));
  }

  /** Avis requis par document (US-05.3), dans l'ordre du catalogue. */
  private Map<Short, List<TypeDocument.AvisRequis>> avisRequis() {
    Map<Short, List<TypeDocument.AvisRequis>> parDocument = new HashMap<>();
    dsl.select(TYPE_DOCUMENT_AVIS.TYPE_DOCUMENT_ID, PROFIL.CODE, PROFIL.LIBELLE)
        .from(TYPE_DOCUMENT_AVIS)
        .join(PROFIL)
        .on(PROFIL.ID.eq(TYPE_DOCUMENT_AVIS.PROFIL_ID))
        .orderBy(TYPE_DOCUMENT_AVIS.TYPE_DOCUMENT_ID, TYPE_DOCUMENT_AVIS.ORDRE)
        .fetch()
        .forEach(
            r ->
                parDocument
                    .computeIfAbsent(
                        r.get(TYPE_DOCUMENT_AVIS.TYPE_DOCUMENT_ID),
                        k -> new java.util.ArrayList<>())
                    .add(new TypeDocument.AvisRequis(r.get(PROFIL.CODE), r.get(PROFIL.LIBELLE))));
    return parDocument;
  }

  /**
   * Par document et par objet : montant de la version active dont la date d'effet est la plus
   * récente parmi celles passées (à date égale, la dernière version), comme {@code
   * fn_tarif_en_vigueur}.
   */
  private Map<Short, Map<ObjetPaiement, Long>> tarifsEnVigueur() {
    Map<Short, Map<ObjetPaiement, Long>> parDocument = new HashMap<>();
    dsl.select(TARIF.TYPE_DOCUMENT_ID, TARIF.OBJET, TARIF.MONTANT_GNF)
        .from(TARIF)
        .where(TARIF.ACTIF.isTrue())
        .and(TARIF.DATE_EFFET.le(LocalDate.now(CONAKRY)))
        .orderBy(TARIF.TYPE_DOCUMENT_ID, TARIF.OBJET, TARIF.DATE_EFFET.desc(), TARIF.VERSION.desc())
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
