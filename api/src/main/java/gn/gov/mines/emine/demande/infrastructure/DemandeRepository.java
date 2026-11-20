package gn.gov.mines.emine.demande.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.DEMANDE;
import static gn.gov.mines.emine.shared.jooq.Tables.ORGANISATION;
import static gn.gov.mines.emine.shared.jooq.Tables.PREFECTURE;
import static gn.gov.mines.emine.shared.jooq.Tables.SUBSTANCE;
import static gn.gov.mines.emine.shared.jooq.Tables.TYPE_DOCUMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.UTILISATEUR;

import gn.gov.mines.emine.demande.domaine.DemandeDetail;
import gn.gov.mines.emine.shared.jooq.enums.StatutDemande;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;
import org.springframework.stereotype.Repository;

/**
 * Accès jOOQ aux demandes. Toujours appelé dans une transaction : la RLS ne montre à un usager que
 * ses propres demandes, et refuse d'écrire au nom d'un autre.
 */
@Repository
public class DemandeRepository {

  private final DSLContext dsl;

  public DemandeRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public long creerBrouillon(
      long demandeurId,
      Long organisationId,
      short typeDocumentId,
      Short prefectureId,
      Short substanceId,
      BigDecimal superficieHa) {
    return dsl.insertInto(DEMANDE)
        .set(DEMANDE.DEMANDEUR_ID, demandeurId)
        .set(DEMANDE.ORGANISATION_ID, organisationId)
        .set(DEMANDE.TYPE_DOCUMENT_ID, typeDocumentId)
        .set(DEMANDE.PREFECTURE_ID, prefectureId)
        .set(DEMANDE.SUBSTANCE_ID, substanceId)
        .set(DEMANDE.SUPERFICIE_HA, superficieHa)
        .returning(DEMANDE.ID)
        .fetchOne(DEMANDE.ID);
  }

  /** Met à jour un brouillon ; renvoie le nombre de lignes modifiées (0 si non brouillon). */
  public int modifierBrouillon(
      long id, short typeDocumentId, Short prefectureId, Short substanceId, BigDecimal superficie) {
    return dsl.update(DEMANDE)
        .set(DEMANDE.TYPE_DOCUMENT_ID, typeDocumentId)
        .set(DEMANDE.PREFECTURE_ID, prefectureId)
        .set(DEMANDE.SUBSTANCE_ID, substanceId)
        .set(DEMANDE.SUPERFICIE_HA, superficie)
        .where(DEMANDE.ID.eq(id))
        .and(DEMANDE.STATUT.eq(StatutDemande.brouillon))
        .and(DEMANDE.DELETED_AT.isNull())
        .execute();
  }

  /**
   * Dépose un brouillon : statut {@code deposee} et déclaration horodatée. Le numéro et la date de
   * dépôt sont posés par le trigger {@code fn_demande_avant} (V5). Renvoie 0 si la demande n'est
   * plus un brouillon.
   */
  public int deposer(long id) {
    return dsl.update(DEMANDE)
        .set(DEMANDE.STATUT, StatutDemande.deposee)
        .set(DEMANDE.DECLARATION_HONNEUR_LE, java.time.Instant.now())
        .where(DEMANDE.ID.eq(id))
        .and(DEMANDE.STATUT.eq(StatutDemande.brouillon))
        .and(DEMANDE.DELETED_AT.isNull())
        .execute();
  }

  /**
   * Demandes visibles selon la RLS (les siennes pour un usager, toutes pour un agent), filtrées, de
   * la plus récente à la plus ancienne (date de dépôt, à défaut de création).
   */
  public List<DemandeDetail> lister(StatutDemande statut, String typeDocumentCode, int limite) {
    var condition = DEMANDE.DELETED_AT.isNull();
    if (statut != null) {
      condition = condition.and(DEMANDE.STATUT.eq(statut));
    }
    if (typeDocumentCode != null) {
      condition = condition.and(TYPE_DOCUMENT.CODE.eq(typeDocumentCode));
    }
    return selection()
        .where(condition)
        .orderBy(
            org.jooq.impl.DSL.coalesce(DEMANDE.DATE_DEPOT, DEMANDE.CREATED_AT).desc(),
            DEMANDE.ID.desc())
        .limit(limite)
        .fetch(DemandeRepository::detail);
  }

  public Optional<DemandeDetail> lire(long id) {
    return selection()
        .where(DEMANDE.ID.eq(id))
        .and(DEMANDE.DELETED_AT.isNull())
        .fetchOptional(DemandeRepository::detail);
  }

  public Optional<Short> prefecture(short id) {
    return dsl.select(PREFECTURE.ID)
        .from(PREFECTURE)
        .where(PREFECTURE.ID.eq(id))
        .fetchOptional(PREFECTURE.ID);
  }

  public Optional<Short> substance(String code) {
    return dsl.select(SUBSTANCE.ID)
        .from(SUBSTANCE)
        .where(SUBSTANCE.CODE.eq(code))
        .fetchOptional(SUBSTANCE.ID);
  }

  SelectOnConditionStep<? extends Record> selection() {
    return dsl.select(
            DEMANDE.ID,
            DEMANDE.NUMERO,
            DEMANDE.STATUT,
            DEMANDE.SUPERFICIE_HA,
            DEMANDE.CREATED_AT,
            DEMANDE.DATE_DEPOT,
            TYPE_DOCUMENT.CODE,
            UTILISATEUR.ID,
            UTILISATEUR.PRENOM,
            UTILISATEUR.NOM,
            UTILISATEUR.EMAIL,
            ORGANISATION.RAISON_SOCIALE,
            PREFECTURE.ID,
            PREFECTURE.NOM,
            SUBSTANCE.CODE,
            SUBSTANCE.LIBELLE_FR)
        .from(DEMANDE)
        .join(TYPE_DOCUMENT)
        .on(TYPE_DOCUMENT.ID.eq(DEMANDE.TYPE_DOCUMENT_ID))
        .join(UTILISATEUR)
        .on(UTILISATEUR.ID.eq(DEMANDE.DEMANDEUR_ID))
        .leftJoin(ORGANISATION)
        .on(ORGANISATION.ID.eq(DEMANDE.ORGANISATION_ID))
        .leftJoin(PREFECTURE)
        .on(PREFECTURE.ID.eq(DEMANDE.PREFECTURE_ID))
        .leftJoin(SUBSTANCE)
        .on(SUBSTANCE.ID.eq(DEMANDE.SUBSTANCE_ID));
  }

  static DemandeDetail detail(Record r) {
    return new DemandeDetail(
        r.get(DEMANDE.ID),
        r.get(DEMANDE.NUMERO),
        r.get(DEMANDE.STATUT).getLiteral(),
        r.get(TYPE_DOCUMENT.CODE),
        new DemandeDetail.Demandeur(
            r.get(UTILISATEUR.ID),
            r.get(UTILISATEUR.PRENOM),
            r.get(UTILISATEUR.NOM),
            r.get(UTILISATEUR.EMAIL),
            r.get(ORGANISATION.RAISON_SOCIALE)),
        r.get(PREFECTURE.ID),
        r.get(PREFECTURE.NOM),
        r.get(SUBSTANCE.CODE),
        r.get(SUBSTANCE.LIBELLE_FR),
        r.get(DEMANDE.SUPERFICIE_HA),
        r.get(DEMANDE.CREATED_AT),
        r.get(DEMANDE.DATE_DEPOT));
  }
}
