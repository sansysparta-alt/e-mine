package gn.gov.mines.emine.audit.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.JOURNAL_AUDIT;
import static gn.gov.mines.emine.shared.jooq.Tables.PROFIL;
import static gn.gov.mines.emine.shared.jooq.Tables.UTILISATEUR;

import gn.gov.mines.emine.audit.domaine.CritereJournal;
import gn.gov.mines.emine.audit.domaine.EntreeJournal;
import gn.gov.mines.emine.audit.domaine.RegleJournal;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lecture du journal d'audit (jOOQ), du plus récent au plus ancien, avec l'auteur de chaque ligne.
 * Le journal n'est jamais modifié (trigger {@code trg_journal_audit_noupdate}) ; le droit de le
 * consulter est vérifié par le contrôleur.
 */
@Repository
public class JournalRepository {

  private static final JsonMapper JSON =
      JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

  private final DSLContext dsl;

  public JournalRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * Page du journal selon les critères : champs modifiés, ou valeurs enregistrées pour une création
   * (l'écran en résume l'essentiel).
   */
  public PageResultat<EntreeJournal> lister(CritereJournal critere, Pagination pagination) {
    Condition condition = condition(critere, true);
    long total = dsl.fetchCount(JOURNAL_AUDIT, condition);
    List<EntreeJournal> elements =
        selection()
            .where(condition)
            .orderBy(JOURNAL_AUDIT.CREATED_AT.desc(), JOURNAL_AUDIT.ID.desc())
            .limit(pagination.taille())
            .offset(pagination.decalage())
            .fetch(r -> entree(r, true));
    return new PageResultat<>(elements, pagination.page(), pagination.taille(), total);
  }

  /** Nombre de lignes par catégorie, pour la recherche et la période (toutes catégories). */
  public Map<String, Long> compteursParCategorie(CritereJournal critere) {
    Map<String, Long> compteurs = new LinkedHashMap<>();
    RegleJournal.CATEGORIES.keySet().forEach(c -> compteurs.put(c, 0L));
    dsl.select(JOURNAL_AUDIT.TABLE_NOM, DSL.count())
        .from(JOURNAL_AUDIT)
        .where(condition(critere, false))
        .groupBy(JOURNAL_AUDIT.TABLE_NOM)
        .fetch()
        .forEach(
            r ->
                compteurs.merge(
                    RegleJournal.categorie(r.value1()), r.value2().longValue(), Long::sum));
    return compteurs;
  }

  /** Une ligne du journal, avec toutes les valeurs d'une création. */
  public Optional<EntreeJournal> lire(long id) {
    return selection().where(JOURNAL_AUDIT.ID.eq(id)).fetchOptional(r -> entree(r, true));
  }

  private static Condition condition(CritereJournal critere, boolean avecTables) {
    Condition c = DSL.trueCondition();
    if (avecTables && !critere.tables().isEmpty()) {
      c = c.and(JOURNAL_AUDIT.TABLE_NOM.in(critere.tables()));
    }
    if (critere.debut() != null) {
      c = c.and(JOURNAL_AUDIT.CREATED_AT.ge(critere.debut()));
    }
    if (critere.fin() != null) {
      c = c.and(JOURNAL_AUDIT.CREATED_AT.lt(critere.fin()));
    }
    String q = critere.recherche() == null ? "" : critere.recherche().strip();
    if (q.matches("\\d{1,18}")) {
      // Un identifiant : l'objet lui-même, ou ce qui se rattache à cette demande.
      c =
          c.and(
              JOURNAL_AUDIT
                  .OBJET_ID
                  .eq(q)
                  .or(cle(JOURNAL_AUDIT.NOUVELLES_VALEURS, "demande_id").eq(q))
                  .or(cle(JOURNAL_AUDIT.ANCIENNES_VALEURS, "demande_id").eq(q)));
    } else if (!q.isEmpty()) {
      String motif = "%" + echapperLike(q) + "%";
      c =
          c.and(
              texte(JOURNAL_AUDIT.NOUVELLES_VALEURS)
                  .likeIgnoreCase(motif, '\\')
                  .or(texte(JOURNAL_AUDIT.ANCIENNES_VALEURS).likeIgnoreCase(motif, '\\')));
    }
    return c;
  }

  private static org.jooq.Field<String> cle(org.jooq.Field<JSONB> champ, String cle) {
    return DSL.field("{0} ->> {1}", String.class, champ, DSL.inline(cle));
  }

  private static org.jooq.Field<String> texte(org.jooq.Field<JSONB> champ) {
    return DSL.field("{0}::text", String.class, champ);
  }

  /** Échappe les caractères spéciaux de LIKE (le texte cherché est pris littéralement). */
  static String echapperLike(String texte) {
    return texte.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private SelectOnConditionStep<? extends Record> selection() {
    return dsl.select(
            JOURNAL_AUDIT.ID,
            JOURNAL_AUDIT.CREATED_AT,
            JOURNAL_AUDIT.TABLE_NOM,
            JOURNAL_AUDIT.OPERATION,
            JOURNAL_AUDIT.OBJET_ID,
            JOURNAL_AUDIT.ANCIENNES_VALEURS,
            JOURNAL_AUDIT.NOUVELLES_VALEURS,
            JOURNAL_AUDIT.ADRESSE_IP,
            UTILISATEUR.PRENOM,
            UTILISATEUR.NOM,
            PROFIL.LIBELLE,
            PROFIL.CODE)
        .from(JOURNAL_AUDIT)
        .leftJoin(UTILISATEUR)
        .on(UTILISATEUR.ID.eq(JOURNAL_AUDIT.UTILISATEUR_ID))
        .leftJoin(PROFIL)
        .on(PROFIL.ID.eq(UTILISATEUR.PROFIL_ID));
  }

  private static EntreeJournal entree(Record r, boolean complet) {
    Map<String, String> avant = valeurs(r.get(JOURNAL_AUDIT.ANCIENNES_VALEURS));
    Map<String, String> apres = valeurs(r.get(JOURNAL_AUDIT.NOUVELLES_VALEURS));
    String table = r.get(JOURNAL_AUDIT.TABLE_NOM);
    String operation = r.get(JOURNAL_AUDIT.OPERATION).strip();
    String objetId = r.get(JOURNAL_AUDIT.OBJET_ID);
    String prenom = r.get(UTILISATEUR.PRENOM);
    RegleJournal.Libelle libelle = RegleJournal.libelleObjet(table, avant, apres);
    String auteur = prenom == null ? null : (prenom + " " + r.get(UTILISATEUR.NOM)).strip();
    return new EntreeJournal(
        r.get(JOURNAL_AUDIT.ID),
        r.get(JOURNAL_AUDIT.CREATED_AT),
        auteur,
        r.get(PROFIL.LIBELLE),
        r.get(PROFIL.CODE),
        table,
        RegleJournal.categorie(table),
        operation,
        objetId,
        libelle == null ? null : libelle.fr(),
        libelle == null ? null : libelle.en(),
        RegleJournal.demandeId(table, objetId, avant, apres),
        RegleJournal.changements(operation, avant, apres, complet),
        r.get(JOURNAL_AUDIT.ADRESSE_IP));
  }

  /** Valeurs d'une ligne JSON en texte (objets et tableaux sérialisés), ou {@code null}. */
  static Map<String, String> valeurs(JSONB json) {
    if (json == null) {
      return null;
    }
    JsonNode noeud = JSON.readTree(json.data());
    Map<String, String> valeurs = new LinkedHashMap<>();
    for (Map.Entry<String, JsonNode> e : noeud.properties()) {
      JsonNode v = e.getValue();
      valeurs.put(e.getKey(), v.isNull() ? null : v.isValueNode() ? v.asString() : v.toString());
    }
    return valeurs;
  }
}
