package gn.gov.mines.emine.demande.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.AVIS_DECISION;
import static gn.gov.mines.emine.shared.jooq.Tables.COMPLEMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.DEMANDE;
import static gn.gov.mines.emine.shared.jooq.Tables.DOCUMENT_DELIVRE;
import static gn.gov.mines.emine.shared.jooq.Tables.HISTORIQUE_STATUT_DEMANDE;
import static gn.gov.mines.emine.shared.jooq.Tables.ORGANISATION;
import static gn.gov.mines.emine.shared.jooq.Tables.PAIEMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.PASSAGE_DOSSIER;
import static gn.gov.mines.emine.shared.jooq.Tables.PREFECTURE;
import static gn.gov.mines.emine.shared.jooq.Tables.PROFIL;
import static gn.gov.mines.emine.shared.jooq.Tables.SUBSTANCE;
import static gn.gov.mines.emine.shared.jooq.Tables.TYPE_DOCUMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.UTILISATEUR;

import gn.gov.mines.emine.demande.domaine.CritereDemandes;
import gn.gov.mines.emine.demande.domaine.DemandeDetail;
import gn.gov.mines.emine.demande.domaine.GroupeATraiter;
import gn.gov.mines.emine.demande.domaine.Passage;
import gn.gov.mines.emine.demande.domaine.RegleATraiter;
import gn.gov.mines.emine.demande.domaine.RegleDelais;
import gn.gov.mines.emine.demande.domaine.VueDemandes;
import gn.gov.mines.emine.shared.jooq.Routines;
import gn.gov.mines.emine.shared.jooq.enums.MoyenPaiement;
import gn.gov.mines.emine.shared.jooq.enums.NatureAvis;
import gn.gov.mines.emine.shared.jooq.enums.ObjetPaiement;
import gn.gov.mines.emine.shared.jooq.enums.StatutDemande;
import gn.gov.mines.emine.shared.jooq.enums.StatutDocument;
import gn.gov.mines.emine.shared.jooq.enums.StatutPaiement;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import java.math.BigDecimal;
import java.time.Instant;
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
   * Supprime un brouillon (suppression logique : {@code deleted_at}, jamais physique). Renvoie 0 si
   * la demande n'est plus un brouillon.
   */
  public int supprimerBrouillon(long id, java.time.Instant le) {
    return dsl.update(DEMANDE)
        .set(DEMANDE.DELETED_AT, le)
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
      List<StatutDemande> statuts,
      CritereDemandes critere,
      Pagination pagination,
      RegleATraiter.Filtre moi) {
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
              DEMANDE.SIGNATAIRE_PROFIL_ID.in(
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
    if (critere.pilotage() != null) {
      condition = condition.and(conditionPilotage(critere.pilotage()));
    }
    if (critere.recherche() != null && !critere.recherche().isBlank()) {
      condition = condition.and(conditionRecherche(critere.recherche()));
    }
    long total =
        dsl.selectCount()
            .from(DEMANDE)
            .join(TYPE_DOCUMENT)
            .on(TYPE_DOCUMENT.ID.eq(DEMANDE.TYPE_DOCUMENT_ID))
            .where(condition)
            .fetchOne(0, Long.class);
    Field<Instant> date = DSL.coalesce(DEMANDE.DATE_DEPOT, DEMANDE.CREATED_AT);
    Field<?> colonne =
        switch (critere.tri()) {
          case NUMERO -> DEMANDE.NUMERO;
          case STATUT -> DEMANDE.STATUT;
          case DATE -> date;
          case ECHEANCE -> DSL.when(enCoursDepose(), joursRestants(Instant.now()));
        };
    List<SortField<?>> ordre = new java.util.ArrayList<>();
    if (critere.tri() == CritereDemandes.Tri.ECHEANCE) {
      // Jours restants croissants (retards d'abord), délai non commencé en dernier.
      ordre.add(critere.croissant() ? colonne.asc().nullsLast() : colonne.desc().nullsFirst());
      ordre.add(date.asc());
    } else {
      ordre.add(critere.croissant() ? colonne.asc().nullsFirst() : colonne.desc().nullsLast());
    }
    ordre.add(critere.croissant() ? DEMANDE.ID.asc() : DEMANDE.ID.desc());
    List<DemandeDetail> elements =
        selection(moi)
            .where(condition)
            .orderBy(ordre)
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
        DEMANDE.SIGNATAIRE_PROFIL_ID.in(
            DSL.select(PROFIL.ID).from(PROFIL).where(PROFIL.CODE.eq(profilCode)));
    return switch (groupe) {
      case PAYER_FRAIS ->
          DEMANDE
              .STATUT
              .eq(StatutDemande.deposee)
              .and(DSL.not(FRAIS_PAYES_COND))
              .and(DSL.not(PAIEMENT_EN_VERIFICATION_COND));
      case PAYER_DROITS ->
          DEMANDE.STATUT.eq(StatutDemande.decision).and(DSL.not(PAIEMENT_EN_VERIFICATION_COND));
      case COMPLETER -> DEMANDE.STATUT.eq(StatutDemande.complement_requis);
      case A_EXAMINER -> DEMANDE.STATUT.eq(StatutDemande.deposee).and(FRAIS_PAYES_COND);
      case SANS_REPONSE ->
          DEMANDE
              .STATUT
              .eq(StatutDemande.complement_requis)
              .and(
                  DSL.exists(
                      DSL.selectOne()
                          .from(COMPLEMENT)
                          .join(UTILISATEUR)
                          .on(UTILISATEUR.ID.eq(COMPLEMENT.DEMANDE_PAR))
                          .join(PROFIL)
                          .on(PROFIL.ID.eq(UTILISATEUR.PROFIL_ID))
                          .where(COMPLEMENT.DEMANDE_ID.eq(DEMANDE.ID))
                          .and(COMPLEMENT.REPONDU_LE.isNull())
                          .and(COMPLEMENT.CLOS_LE.isNull())
                          .and(COMPLEMENT.DATE_LIMITE.lt(MAINTENANT_BASE))
                          .and(PROFIL.CODE.eq(profilCode))));
      case AVIS_A_RENDRE ->
          DEMANDE
              .STATUT
              .eq(StatutDemande.en_instruction)
              .and(
                  DSL.exists(
                      DSL.selectOne()
                          .from(PROFIL)
                          .where(PROFIL.ID.eq(DSL.any(Routines.fnAvisRequis(DEMANDE.ID))))
                          .and(PROFIL.CODE.eq(profilCode))
                          .and(
                              DSL.notExists(
                                  DSL.selectOne()
                                      .from(AVIS_DECISION)
                                      .where(AVIS_DECISION.DEMANDE_ID.eq(DEMANDE.ID))
                                      .and(AVIS_DECISION.NATURE.eq(NatureAvis.avis))
                                      .and(AVIS_DECISION.PROFIL_ID.eq(PROFIL.ID))))));
      case A_DECIDER ->
          DEMANDE
              .STATUT
              .eq(StatutDemande.avis_rendu)
              .or(
                  DEMANDE
                      .STATUT
                      .eq(StatutDemande.recevable)
                      .and(DSL.cardinality(Routines.fnAvisRequis(DEMANDE.ID)).eq(0)))
              .and(signataire);
      case A_SIGNER -> DEMANDE.STATUT.eq(StatutDemande.droits_payes).and(signataire);
    };
  }

  /** Filtres d'un indicateur du tableau de bord (correctifs ergonomie). */
  static Condition conditionPilotage(CritereDemandes.Pilotage p) {
    Condition c = DSL.trueCondition();
    if (p.enRetard()) {
      c = c.and(conditionEnRetard(p.maintenant()));
    }
    if (p.traiteParMoiDepuis() != null) {
      c = c.and(conditionTraiteParMoi(p.traiteParMoiDepuis()));
    }
    if (p.etapeDepuis() != null) {
      c = c.and(ETAPE_DEPUIS_BRUT.ge(p.etapeDepuis()));
    }
    if (p.bloque()) {
      c = c.and(CONDITION_BLOQUE);
    }
    if (p.procheDepassement()) {
      c = c.and(conditionProcheDepassement(p.maintenant()));
    }
    return c;
  }

  /** Statuts d'une demande en cours de traitement (ni brouillon, ni terminée, ni rejetée). */
  static final List<StatutDemande> STATUTS_EN_COURS =
      VueDemandes.STATUTS_ACTIFS.stream().map(StatutDemande::lookupLiteral).toList();

  /** Instant courant de la base (délais de réponse des compléments). */
  static final Field<java.time.Instant> MAINTENANT_BASE =
      DSL.field("now()", COMPLEMENT.DATE_LIMITE.getDataType());

  /**
   * Dossier en retard ({@link RegleDelais}) : en cours, et ses jours de traitement dépassent le
   * délai de la fiche en vigueur au dépôt ({@code demande.delai_jours}, V18). Un dossier déjà en
   * retard le reste pendant une pause. Suppose la jointure sur le catalogue.
   */
  static Condition conditionEnRetard(Instant maintenant) {
    return enCoursDepose().and(joursRestants(maintenant).lt(0));
  }

  /** Dossier en cours, déposé (le délai court, est en pause, ou attend le paiement des frais). */
  static Condition enCoursDepose() {
    return DEMANDE.STATUT.in(STATUTS_EN_COURS).and(DEMANDE.DATE_DEPOT.isNotNull());
  }

  /**
   * Jours restants sur le délai du catalogue ({@link RegleDelais}) : délai moins les jours de
   * traitement calculés par la base ({@code fn_jours_traitement}). Négatif = jours de retard ;
   * {@code null} tant que le délai n'a pas commencé.
   */
  static Field<Integer> joursRestants(Instant maintenant) {
    return DEMANDE
        .DELAI_JOURS
        .cast(Integer.class)
        .minus(Routines.fnJoursTraitement(DEMANDE.ID, DSL.val(maintenant, DEMANDE.DATE_DEPOT)));
  }

  /** Motif de la pause du délai à ce jour ({@code complement}, {@code droits}) ou {@code null}. */
  static final Field<String> MOTIF_PAUSE = Routines.fnMotifPause(DEMANDE.ID);

  /** Le délai a commencé (frais payés, selon le paramétrage). */
  static final Condition DELAI_DEMARRE = Routines.fnDepartDelai(DEMANDE.ID).isNotNull();

  /**
   * Proche du dépassement ({@link RegleDelais}) : en cours, ni en retard ni en pause, et il reste
   * au plus le seuil de la règle des délais de la demande ({@code fn_seuil_proche}, V27).
   */
  static Condition conditionProcheDepassement(Instant maintenant) {
    return enCoursDepose()
        .and(MOTIF_PAUSE.isNull())
        .and(joursRestants(maintenant).between(DSL.val(0), Routines.fnSeuilProche(DEMANDE.ID)));
  }

  /** Le profil connecté a changé le statut du dossier depuis cet instant (historique en base). */
  static Condition conditionTraiteParMoi(Instant depuis) {
    return DEMANDE
        .STATUT
        .ne(StatutDemande.brouillon)
        .and(
            DSL.exists(
                DSL.selectOne()
                    .from(HISTORIQUE_STATUT_DEMANDE)
                    .where(HISTORIQUE_STATUT_DEMANDE.DEMANDE_ID.eq(DEMANDE.ID))
                    .and(HISTORIQUE_STATUT_DEMANDE.AUTEUR_ID.eq(Routines.fnCurrentUserId()))
                    .and(HISTORIQUE_STATUT_DEMANDE.ANCIEN_STATUT.isNotNull())
                    .and(HISTORIQUE_STATUT_DEMANDE.CREATED_AT.ge(depuis))));
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
  private static final String ORIGINE_COMPLEMENT_COL = "origine_complement";

  /** Seuil de ressemblance (pg_trgm) au-delà duquel un mot mal orthographié est retenu. */
  private static final double RESSEMBLANCE_MIN = 0.5;

  /** Longueur minimale d'un mot pour tolérer une faute de frappe (en deçà, trop d'à-peu-près). */
  private static final int LONGUEUR_TOLERANCE = 5;

  /**
   * Recherche tolérante (lot « Listes et recherches ») : chaque mot saisi doit se retrouver, dans
   * n'importe quel ordre, dans le numéro, le code ou le nom du document (français et anglais), le
   * nom du demandeur ou de son organisation ; accents, majuscules et apostrophes ignorés ({@code
   * fn_normaliser}, V23) ; une partie du numéro suffit ; un mot d'au moins 5 lettres mal
   * orthographié est retenu s'il ressemble assez à un mot du dossier (pg_trgm). Les jokers LIKE
   * saisis sont pris littéralement.
   */
  static Condition conditionRecherche(String recherche) {
    var u = UTILISATEUR.as("u_recherche");
    var o = ORGANISATION.as("o_recherche");
    Field<String> demandeur =
        DSL.select(DSL.concat(u.PRENOM, DSL.inline(" "), u.NOM))
            .from(u)
            .where(u.ID.eq(DEMANDE.DEMANDEUR_ID))
            .asField();
    Field<String> organisation =
        DSL.select(o.RAISON_SOCIALE).from(o).where(o.ID.eq(DEMANDE.ORGANISATION_ID)).asField();
    Field<String> texte =
        normaliser(
            DSL.field(
                "concat_ws(' ', {0}, {1}, {2}, {3}, {4}, {5})",
                String.class,
                DEMANDE.NUMERO,
                TYPE_DOCUMENT.CODE,
                TYPE_DOCUMENT.LIBELLE_FR,
                TYPE_DOCUMENT.LIBELLE_EN,
                demandeur,
                organisation));
    Condition c = DSL.trueCondition();
    for (String mot : recherche.strip().split("[\\s’']+")) {
      if (mot.isBlank()) continue;
      // La normalisation ne touche ni à « \ » ni aux jokers : l'échappement reste valable.
      Field<String> motif = normaliser(DSL.val("%" + echapperLike(mot) + "%"));
      Condition trouve = texte.like(motif, '\\');
      // Un numéro se cherche exactement (000009 ne doit pas trouver 000019) : pas de tolérance.
      if (mot.length() >= LONGUEUR_TOLERANCE && mot.chars().noneMatch(Character::isDigit)) {
        trouve =
            trouve.or(
                DSL.field(
                        "word_similarity({0}, {1})", Double.class, normaliser(DSL.val(mot)), texte)
                    .ge(RESSEMBLANCE_MIN));
      }
      c = c.and(trouve);
    }
    return c;
  }

  /** Forme de comparaison d'un texte (minuscules, sans accents), fonction de la base (V23). */
  private static Field<String> normaliser(Field<String> texte) {
    return DSL.function("fn_normaliser", String.class, texte);
  }

  static String echapperLike(String texte) {
    return texte.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  public Optional<DemandeDetail> lire(long id) {
    return lire(id, null);
  }

  /** La demande, avec l'indication « à traiter par le profil connecté » si le filtre est donné. */
  public Optional<DemandeDetail> lire(long id, RegleATraiter.Filtre moi) {
    return selection(moi)
        .where(DEMANDE.ID.eq(id))
        .and(DEMANDE.DELETED_AT.isNull())
        .fetchOptional(DemandeRepository::detail);
  }

  /**
   * Version de la fiche en vigueur au dépôt de la demande (V18) ; vide pour un brouillon ou une
   * demande invisible (RLS).
   */
  public Optional<Long> versionDocument(long id) {
    return dsl.select(DEMANDE.VERSION_DOCUMENT_ID)
        .from(DEMANDE)
        .where(DEMANDE.ID.eq(id))
        .fetchOptional(DEMANDE.VERSION_DOCUMENT_ID);
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

  /**
   * La somme attendue à ce stade est en cours de vérification (US-04.7) : frais (ou attestation)
   * pour une demande déposée, droits pour une demande en décision, payés par un virement déclaré
   * (en attente du relevé bancaire) ou « à vérifier ». Le demandeur n'a rien à payer pour l'instant
   * : la demande sort de « Frais à payer » et « Droits à payer ».
   */
  static final Condition PAIEMENT_EN_VERIFICATION_COND =
      DSL.exists(
          DSL.selectOne()
              .from(PAIEMENT)
              .where(PAIEMENT.DEMANDE_ID.eq(DEMANDE.ID))
              .and(
                  PAIEMENT
                      .MOYEN
                      .eq(MoyenPaiement.VIREMENT)
                      .and(PAIEMENT.STATUT.eq(StatutPaiement.en_attente))
                      .or(PAIEMENT.STATUT.eq(StatutPaiement.a_verifier)))
              .and(
                  DEMANDE
                      .STATUT
                      .eq(StatutDemande.deposee)
                      .and(
                          PAIEMENT.OBJET.in(ObjetPaiement.FRAIS_DOSSIER, ObjetPaiement.ATTESTATION))
                      .or(
                          DEMANDE
                              .STATUT
                              .eq(StatutDemande.decision)
                              .and(PAIEMENT.OBJET.eq(ObjetPaiement.DROITS)))));

  static final Field<Boolean> PAIEMENT_EN_VERIFICATION =
      DSL.field(PAIEMENT_EN_VERIFICATION_COND).as("paiement_en_verification");

  /**
   * Document délivré révoqué depuis (US-06.6) : la demande reste « délivrée » dans le circuit, mais
   * tous les écrans affichent « Document révoqué », comme la liste des documents délivrés.
   */
  static final Condition DOCUMENT_REVOQUE_COND =
      DSL.exists(
          DSL.selectOne()
              .from(DOCUMENT_DELIVRE)
              .where(DOCUMENT_DELIVRE.DEMANDE_ID.eq(DEMANDE.ID))
              .and(DOCUMENT_DELIVRE.STATUT.eq(StatutDocument.non_valide)));

  /** La fiche du dépôt demande des avis techniques (V18, {@code fn_avis_requis}). */
  static final Field<Boolean> AVIS_REQUIS =
      DSL.field(DSL.cardinality(Routines.fnAvisRequis(DEMANDE.ID)).gt(0)).as("avis_requis");

  static final Field<Boolean> DOCUMENT_REVOQUE =
      DSL.field(DOCUMENT_REVOQUE_COND).as("document_revoque");

  /** Dernier changement de statut (historique tenu par la base). */
  private static final Field<java.time.Instant> DERNIER_CHANGEMENT_STATUT =
      DSL.field(
          DSL.select(DSL.max(HISTORIQUE_STATUT_DEMANDE.CREATED_AT))
              .from(HISTORIQUE_STATUT_DEMANDE)
              .where(HISTORIQUE_STATUT_DEMANDE.DEMANDE_ID.eq(DEMANDE.ID)));

  /** Paiement des frais (ou de l'attestation) : date du paiement payé le plus récent. */
  private static final Field<java.time.Instant> FRAIS_PAYES_LE =
      DSL.field(
          DSL.select(DSL.max(PAIEMENT.DATE_PAIEMENT))
              .from(PAIEMENT)
              .where(PAIEMENT.DEMANDE_ID.eq(DEMANDE.ID))
              .and(PAIEMENT.OBJET.in(ObjetPaiement.FRAIS_DOSSIER, ObjetPaiement.ATTESTATION))
              .and(PAIEMENT.STATUT.eq(StatutPaiement.paye)));

  /**
   * Entrée dans l'étape actuelle (US-07.6) : dernier changement de statut, ou paiement des frais
   * d'une demande déposée — le statut ne change pas, mais la demande passe de « Paiement des frais
   * » au contrôle du guichet (frise, file « À examiner »). Le temps d'attente repart donc à chaque
   * changement d'étape.
   */
  static final Field<java.time.Instant> ETAPE_DEPUIS_BRUT =
      DSL.greatest(
          DERNIER_CHANGEMENT_STATUT,
          DSL.when(DEMANDE.STATUT.eq(StatutDemande.deposee), FRAIS_PAYES_LE));

  static final Field<java.time.Instant> ETAPE_DEPUIS = ETAPE_DEPUIS_BRUT.as("etape_depuis");

  /** Passages du dossier à l'étape actuelle (V11), lus sous la RLS : toujours 0 pour un usager. */
  static final Field<Integer> PASSAGES_BRUT =
      DSL.field(
          DSL.selectCount()
              .from(PASSAGE_DOSSIER)
              .where(PASSAGE_DOSSIER.DEMANDE_ID.eq(DEMANDE.ID))
              .and(PASSAGE_DOSSIER.CREATED_AT.ge(ETAPE_DEPUIS_BRUT)));

  static final Field<Integer> PASSAGES = PASSAGES_BRUT.as("passages");

  /** Dossier « Bloqué » : en cours et passé au moins 3 fois à l'étape actuelle. */
  static final Condition CONDITION_BLOQUE =
      DEMANDE
          .STATUT
          .in(VueDemandes.STATUTS_ACTIFS.stream().map(StatutDemande::lookupLiteral).toList())
          .and(PASSAGES_BRUT.ge(Passage.SEUIL_BLOCAGE));

  /** Colonne calculée : la demande est « à traiter » par le profil connecté. */
  static final String A_TRAITER_PAR_MOI = "a_traiter_par_moi";

  /** Colonne calculée : la demande est en retard sur le délai du catalogue, à ce jour. */
  static final String EN_RETARD = "en_retard";

  static final String JOURS_RESTANTS = "jours_restants";

  static final String PROCHE = "proche_depassement";

  static final String MOTIF_PAUSE_COL = "motif_pause";

  static final String DELAI_DEMARRE_COL = "delai_demarre";

  SelectOnConditionStep<? extends Record> selection(RegleATraiter.Filtre moi) {
    Field<Boolean> aTraiterParMoi =
        DSL.field(moi == null ? DSL.falseCondition() : conditionATraiter(moi))
            .as(A_TRAITER_PAR_MOI);
    Instant maintenant = Instant.now();
    Field<Boolean> enRetard = DSL.field(conditionEnRetard(maintenant)).as(EN_RETARD);
    Field<Integer> joursRestants =
        DSL.when(enCoursDepose(), joursRestants(maintenant)).as(JOURS_RESTANTS);
    Field<Boolean> proche = DSL.field(conditionProcheDepassement(maintenant)).as(PROCHE);
    Field<String> motifPause = DSL.when(enCoursDepose(), MOTIF_PAUSE).as(MOTIF_PAUSE_COL);
    Field<Boolean> demarre = DSL.field(DELAI_DEMARRE).as(DELAI_DEMARRE_COL);
    // Étape d'où vient le complément en cours (contrôle ou instruction) : avancement de la frise.
    Field<String> origine =
        DSL.field(
                DSL.select(COMPLEMENT.STATUT_ORIGINE.cast(String.class))
                    .from(COMPLEMENT)
                    .where(COMPLEMENT.DEMANDE_ID.eq(DEMANDE.ID))
                    .and(COMPLEMENT.REPONDU_LE.isNull())
                    .and(COMPLEMENT.CLOS_LE.isNull())
                    .limit(1))
            .as(ORIGINE_COMPLEMENT_COL);
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
            FRAIS_PAYES,
            PAIEMENT_EN_VERIFICATION,
            DOCUMENT_REVOQUE,
            ETAPE_DEPUIS,
            PASSAGES,
            aTraiterParMoi,
            enRetard,
            joursRestants,
            proche,
            motifPause,
            demarre,
            origine,
            AVIS_REQUIS)
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
        Boolean.TRUE.equals(r.get(PAIEMENT_EN_VERIFICATION)),
        r.get(DEMANDE.MOTIF_REJET),
        r.get(ETAPE_DEPUIS) == null ? r.get(DEMANDE.CREATED_AT) : r.get(ETAPE_DEPUIS),
        Boolean.TRUE.equals(r.get(A_TRAITER_PAR_MOI, Boolean.class)),
        passages(r),
        passages(r) >= Passage.SEUIL_BLOCAGE
            && VueDemandes.STATUTS_ACTIFS.contains(r.get(DEMANDE.STATUT).getLiteral()),
        Boolean.TRUE.equals(r.get(EN_RETARD, Boolean.class)),
        r.get(JOURS_RESTANTS, Integer.class),
        Boolean.TRUE.equals(r.get(PROCHE, Boolean.class)),
        r.get(MOTIF_PAUSE_COL, String.class) != null,
        r.get(MOTIF_PAUSE_COL, String.class),
        Boolean.TRUE.equals(r.get(DELAI_DEMARRE_COL, Boolean.class)),
        r.get(ORIGINE_COMPLEMENT_COL, String.class),
        Boolean.TRUE.equals(r.get(AVIS_REQUIS)),
        Boolean.TRUE.equals(r.get(DOCUMENT_REVOQUE)),
        null);
  }

  private static int passages(Record r) {
    Integer n = r.get(PASSAGES);
    return n == null ? 0 : n;
  }
}
