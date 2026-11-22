package gn.gov.mines.emine.demande.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.DEMANDE;
import static gn.gov.mines.emine.shared.jooq.Tables.ORGANISATION;
import static gn.gov.mines.emine.shared.jooq.Tables.PAIEMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.PREFECTURE;
import static gn.gov.mines.emine.shared.jooq.Tables.PROFIL;
import static gn.gov.mines.emine.shared.jooq.Tables.SUBSTANCE;
import static gn.gov.mines.emine.shared.jooq.Tables.TYPE_DOCUMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.UTILISATEUR;

import gn.gov.mines.emine.demande.domaine.CritereDemandes;
import gn.gov.mines.emine.demande.domaine.DemandeDetail;
import gn.gov.mines.emine.demande.domaine.GroupeATraiter;
import gn.gov.mines.emine.demande.domaine.RegleATraiter;
import gn.gov.mines.emine.demande.domaine.VueDemandes;
import gn.gov.mines.emine.shared.jooq.enums.ObjetPaiement;
import gn.gov.mines.emine.shared.jooq.enums.StatutDemande;
import gn.gov.mines.emine.shared.jooq.enums.StatutPaiement;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;
import org.jooq.SortField;
import org.jooq.impl.DSL;
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
  public PageResultat<DemandeDetail> lister(
      List<StatutDemande> statuts, CritereDemandes critere, Pagination pagination) {
    Condition condition = DEMANDE.DELETED_AT.isNull();
    if (!statuts.isEmpty()) {
      condition = condition.and(DEMANDE.STATUT.in(statuts));
    }
    if (critere.typeDocumentCode() != null) {
      condition = condition.and(TYPE_DOCUMENT.CODE.eq(critere.typeDocumentCode()));
    }
    if (critere.fraisPayes() != null) {
      condition =
          condition.and(critere.fraisPayes() ? FRAIS_PAYES_COND : DSL.not(FRAIS_PAYES_COND));
    }
    if (critere.signataireProfil() != null) {
      condition =
          condition.and(
              TYPE_DOCUMENT.SIGNATAIRE_PROFIL_ID.in(
                  DSL.select(PROFIL.ID)
                      .from(PROFIL)
                      .where(PROFIL.CODE.eq(critere.signataireProfil()))));
    }
    if (critere.aTraiter() != null) {
      condition = condition.and(conditionATraiter(critere.aTraiter()));
    }
    if (critere.exclureBrouillons()) {
      condition = condition.and(DEMANDE.STATUT.ne(StatutDemande.brouillon));
    }
    if (critere.vue() != null) {
      condition = condition.and(conditionVue(critere.vue()));
    }
    if (critere.recherche() != null && !critere.recherche().isBlank()) {
      String motif = "%" + echapperLike(critere.recherche().strip()) + "%";
      condition =
          condition.and(
              DEMANDE
                  .NUMERO
                  .likeIgnoreCase(motif, '\\')
                  .or(TYPE_DOCUMENT.CODE.likeIgnoreCase(motif, '\\'))
                  .or(TYPE_DOCUMENT.LIBELLE_FR.likeIgnoreCase(motif, '\\')));
    }
    long total =
        dsl.selectCount()
            .from(DEMANDE)
            .join(TYPE_DOCUMENT)
            .on(TYPE_DOCUMENT.ID.eq(DEMANDE.TYPE_DOCUMENT_ID))
            .where(condition)
            .fetchOne(0, Long.class);
    Field<?> colonne =
        switch (critere.tri()) {
          case NUMERO -> DEMANDE.NUMERO;
          case STATUT -> DEMANDE.STATUT;
          case DATE -> DSL.coalesce(DEMANDE.DATE_DEPOT, DEMANDE.CREATED_AT);
        };
    SortField<?> ordre =
        critere.croissant() ? colonne.asc().nullsFirst() : colonne.desc().nullsLast();
    List<DemandeDetail> elements =
        selection()
            .where(condition)
            .orderBy(ordre, critere.croissant() ? DEMANDE.ID.asc() : DEMANDE.ID.desc())
            .limit(pagination.taille())
            .offset(pagination.decalage())
            .fetch(DemandeRepository::detail);
    return new PageResultat<>(elements, pagination.page(), pagination.taille(), total);
  }

  /** Condition de la règle « À traiter » : un des groupes retenus (aucun groupe = rien). */
  static Condition conditionATraiter(RegleATraiter.Filtre filtre) {
    Condition c = DSL.falseCondition();
    for (GroupeATraiter g : filtre.groupes()) {
      c = c.or(condition(g, filtre.profilCode()));
    }
    return c;
  }

  private static Condition condition(GroupeATraiter groupe, String profilCode) {
    Condition signataire =
        TYPE_DOCUMENT.SIGNATAIRE_PROFIL_ID.in(
            DSL.select(PROFIL.ID).from(PROFIL).where(PROFIL.CODE.eq(profilCode)));
    return switch (groupe) {
      case PAYER_FRAIS -> DEMANDE.STATUT.eq(StatutDemande.deposee).and(DSL.not(FRAIS_PAYES_COND));
      case PAYER_DROITS -> DEMANDE.STATUT.eq(StatutDemande.decision);
      case COMPLETER -> DEMANDE.STATUT.eq(StatutDemande.complement_requis);
      case A_EXAMINER -> DEMANDE.STATUT.eq(StatutDemande.deposee).and(FRAIS_PAYES_COND);
      case A_DECIDER -> DEMANDE.STATUT.eq(StatutDemande.recevable).and(signataire);
      case A_SIGNER -> DEMANDE.STATUT.eq(StatutDemande.droits_payes).and(signataire);
    };
  }

  /** Condition d'une vue : exclusive des autres vues du même profil. */
  static Condition conditionVue(CritereDemandes.Vue v) {
    Condition aTraiter = conditionATraiter(v.aTraiter());
    Condition attente =
        conditionATraiter(
            new RegleATraiter.Filtre(VueDemandes.GROUPES_DEMANDEUR, v.aTraiter().profilCode()));
    Condition actives =
        DEMANDE.STATUT.in(
            VueDemandes.STATUTS_ACTIFS.stream().map(StatutDemande::lookupLiteral).toList());
    return switch (v.vue()) {
      case A_TRAITER -> aTraiter;
      case ATTENTE_DEMANDEUR -> attente.and(DSL.not(aTraiter));
      case EN_COURS ->
          v.interne()
              ? actives.and(DSL.not(aTraiter)).and(DSL.not(attente))
              : actives.and(DSL.not(aTraiter));
      case BROUILLONS -> DEMANDE.STATUT.eq(StatutDemande.brouillon);
      case TERMINEES -> DEMANDE.STATUT.in(StatutDemande.delivree, StatutDemande.archivee);
      case REJETEES -> DEMANDE.STATUT.eq(StatutDemande.rejetee);
    };
  }

  /** Nombre de demandes visibles (RLS) de chaque vue, pour les pastilles de la liste. */
  public Map<VueDemandes, Long> compterVues(
      List<VueDemandes> vues, RegleATraiter.Filtre aTraiter, boolean interne) {
    Map<VueDemandes, Long> nombres = new LinkedHashMap<>();
    for (VueDemandes v : vues) {
      nombres.put(
          v,
          dsl.selectCount()
              .from(DEMANDE)
              .join(TYPE_DOCUMENT)
              .on(TYPE_DOCUMENT.ID.eq(DEMANDE.TYPE_DOCUMENT_ID))
              .where(DEMANDE.DELETED_AT.isNull())
              .and(conditionVue(new CritereDemandes.Vue(v, aTraiter, interne)))
              .fetchOne(0, Long.class));
    }
    return nombres;
  }

  /** Nombre de demandes visibles (RLS) de chaque groupe « à traiter ». */
  public Map<GroupeATraiter, Long> compterATraiter(RegleATraiter.Filtre filtre) {
    Map<GroupeATraiter, Long> nombres = new LinkedHashMap<>();
    for (GroupeATraiter g : filtre.groupes()) {
      nombres.put(
          g,
          dsl.selectCount()
              .from(DEMANDE)
              .join(TYPE_DOCUMENT)
              .on(TYPE_DOCUMENT.ID.eq(DEMANDE.TYPE_DOCUMENT_ID))
              .where(DEMANDE.DELETED_AT.isNull())
              .and(condition(g, filtre.profilCode()))
              .fetchOne(0, Long.class));
    }
    return nombres;
  }

  /** Nombre de demandes visibles (RLS) par statut. */
  public Map<String, Long> compteursParStatut() {
    Map<String, Long> compteurs = new LinkedHashMap<>();
    dsl.select(DEMANDE.STATUT, DSL.count())
        .from(DEMANDE)
        .where(DEMANDE.DELETED_AT.isNull())
        .groupBy(DEMANDE.STATUT)
        .fetch()
        .forEach(r -> compteurs.put(r.value1().getLiteral(), r.value2().longValue()));
    return compteurs;
  }

  /** Échappe les caractères spéciaux de LIKE (le texte cherché est pris littéralement). */
  static String echapperLike(String texte) {
    return texte.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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

  /**
   * Frais de dossier (ou attestation, paiement unique) payés : condition de la recevabilité (V8),
   * affichée « Frais payés » à côté du statut. Lecture soumise à la RLS du paiement.
   */
  static final Condition FRAIS_PAYES_COND =
      DSL.exists(
          DSL.selectOne()
              .from(PAIEMENT)
              .where(PAIEMENT.DEMANDE_ID.eq(DEMANDE.ID))
              .and(PAIEMENT.OBJET.in(ObjetPaiement.FRAIS_DOSSIER, ObjetPaiement.ATTESTATION))
              .and(PAIEMENT.STATUT.eq(StatutPaiement.paye)));

  static final Field<Boolean> FRAIS_PAYES = DSL.field(FRAIS_PAYES_COND).as("frais_payes");

  SelectOnConditionStep<? extends Record> selection() {
    return dsl.select(
            DEMANDE.ID,
            DEMANDE.NUMERO,
            DEMANDE.STATUT,
            DEMANDE.SUPERFICIE_HA,
            DEMANDE.CREATED_AT,
            DEMANDE.DATE_DEPOT,
            DEMANDE.MOTIF_REJET,
            TYPE_DOCUMENT.CODE,
            UTILISATEUR.ID,
            UTILISATEUR.PRENOM,
            UTILISATEUR.NOM,
            UTILISATEUR.EMAIL,
            ORGANISATION.RAISON_SOCIALE,
            PREFECTURE.ID,
            PREFECTURE.NOM,
            SUBSTANCE.CODE,
            SUBSTANCE.LIBELLE_FR,
            FRAIS_PAYES)
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
        r.get(DEMANDE.DATE_DEPOT),
        Boolean.TRUE.equals(r.get(FRAIS_PAYES)),
        r.get(DEMANDE.MOTIF_REJET));
  }
}
