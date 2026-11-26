package gn.gov.mines.emine.catalogue.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.TYPE_DOCUMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.UTILISATEUR;
import static gn.gov.mines.emine.shared.jooq.Tables.VERSION_WORKFLOW;
import static gn.gov.mines.emine.shared.jooq.Tables.WORKFLOW;
import static gn.gov.mines.emine.shared.jooq.Tables.WORKFLOW_ETAPE;

import gn.gov.mines.emine.catalogue.domaine.Workflow;
import gn.gov.mines.emine.catalogue.domaine.Workflow.DocumentFamille;
import gn.gov.mines.emine.catalogue.domaine.Workflow.Etape;
import gn.gov.mines.emine.catalogue.domaine.Workflow.VersionWorkflow;
import gn.gov.mines.emine.shared.gouvernance.EtatVersion;
import gn.gov.mines.emine.shared.jooq.Routines;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** Workflows et leurs versions (US-02.5, V20 ; gouvernance US-02.8, V27). */
@Repository
public class WorkflowRepository {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final DSLContext dsl;

  public WorkflowRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** Version en vigueur aujourd'hui d'un workflow (heure de Conakry), calculée par la base. */
  static Field<Integer> enVigueur(Field<Short> workflowId) {
    return DSL.function("fn_workflow_version_en_vigueur", Integer.class, workflowId);
  }

  /**
   * Les workflows, dans l'ordre du catalogue, avec leur version en vigueur, la proposition en
   * attente et la version programmée.
   */
  public List<Workflow> lister() {
    Map<Short, List<DocumentFamille>> documents = new HashMap<>();
    dsl.select(
            TYPE_DOCUMENT.WORKFLOW_ID,
            TYPE_DOCUMENT.CODE,
            TYPE_DOCUMENT.LIBELLE_FR,
            TYPE_DOCUMENT.LIBELLE_EN,
            TYPE_DOCUMENT.ACTIF)
        .from(TYPE_DOCUMENT)
        .where(TYPE_DOCUMENT.DELETED_AT.isNull())
        .orderBy(TYPE_DOCUMENT.CODE)
        .fetch()
        .forEach(
            r ->
                documents
                    .computeIfAbsent(r.value1(), k -> new ArrayList<>())
                    .add(
                        new DocumentFamille(
                            r.value2(),
                            r.value3(),
                            r.value4() == null ? r.value3() : r.value4(),
                            Boolean.TRUE.equals(r.value5()))));
    List<Workflow> liste = new ArrayList<>();
    for (var w : dsl.selectFrom(WORKFLOW).orderBy(WORKFLOW.ID).fetch()) {
      List<VersionWorkflow> versions = versions(w.getId());
      liste.add(
          new Workflow(
              w.getCode(),
              w.getDelaiMinJours(),
              w.getDelaiMaxJours(),
              documents.getOrDefault(w.getId(), List.of()),
              dans(versions, EtatVersion.EN_VIGUEUR),
              dans(versions, EtatVersion.A_VALIDER),
              dans(versions, EtatVersion.PROGRAMMEE)));
    }
    return liste;
  }

  /** Versions d'un workflow, de la plus récente à la plus ancienne. */
  public List<VersionWorkflow> versions(String code) {
    return dsl.select(WORKFLOW.ID)
        .from(WORKFLOW)
        .where(WORKFLOW.CODE.eq(code))
        .fetchOptional(WORKFLOW.ID)
        .map(this::versions)
        .orElse(List.of());
  }

  /** Version la plus récente dans cet état, ou {@code null}. */
  private static VersionWorkflow dans(List<VersionWorkflow> versions, String etat) {
    return versions.stream().filter(v -> etat.equals(v.etat())).findFirst().orElse(null);
  }

  private List<VersionWorkflow> versions(short workflowId) {
    Map<Integer, List<Etape>> etapes = new HashMap<>();
    dsl.select(
            WORKFLOW_ETAPE.VERSION,
            WORKFLOW_ETAPE.ACTEUR,
            WORKFLOW_ETAPE.LIBELLE,
            WORKFLOW_ETAPE.ACTEUR_EN,
            WORKFLOW_ETAPE.LIBELLE_EN)
        .from(WORKFLOW_ETAPE)
        .where(WORKFLOW_ETAPE.WORKFLOW_ID.eq(workflowId))
        .orderBy(WORKFLOW_ETAPE.VERSION, WORKFLOW_ETAPE.ORDRE)
        .fetch()
        .forEach(
            r ->
                etapes
                    .computeIfAbsent(r.value1(), k -> new ArrayList<>())
                    .add(new Etape(r.value2(), r.value3(), r.value4(), r.value5())));
    var proposeePar = UTILISATEUR.as("proposee_par");
    var decideePar = UTILISATEUR.as("decidee_par");
    Integer enVigueur = dsl.select(enVigueur(DSL.val(workflowId))).fetchOne(0, Integer.class);
    return dsl.select(
            VERSION_WORKFLOW.asterisk(),
            nom(proposeePar.PRENOM, proposeePar.NOM).as("auteur"),
            nom(decideePar.PRENOM, decideePar.NOM).as("decideur"))
        .from(VERSION_WORKFLOW)
        .leftJoin(proposeePar)
        .on(proposeePar.ID.eq(VERSION_WORKFLOW.MODIFIEE_PAR))
        .leftJoin(decideePar)
        .on(decideePar.ID.eq(VERSION_WORKFLOW.DECIDEE_PAR))
        .where(VERSION_WORKFLOW.WORKFLOW_ID.eq(workflowId))
        .orderBy(VERSION_WORKFLOW.VERSION.desc())
        .fetch(
            r -> {
              int version = r.get(VERSION_WORKFLOW.VERSION);
              return new VersionWorkflow(
                  version,
                  EtatVersion.etat(r.get(VERSION_WORKFLOW.STATUT), version, enVigueur),
                  r.get(VERSION_WORKFLOW.LIBELLE),
                  r.get(VERSION_WORKFLOW.REGLE_PAIEMENT),
                  r.get(VERSION_WORKFLOW.MOTIF),
                  vide(r.get("auteur", String.class)),
                  r.get(VERSION_WORKFLOW.MODIFIEE_LE),
                  r.get(VERSION_WORKFLOW.DATE_EFFET),
                  vide(r.get("decideur", String.class)),
                  r.get(VERSION_WORKFLOW.DECIDEE_LE),
                  r.get(VERSION_WORKFLOW.MOTIF_REFUS),
                  etapes.getOrDefault(version, List.of()),
                  r.get(VERSION_WORKFLOW.LIBELLE_EN),
                  r.get(VERSION_WORKFLOW.REGLE_PAIEMENT_EN));
            });
  }

  /** Le workflow existe-t-il ? */
  public Optional<String> code(String code) {
    return dsl.select(WORKFLOW.CODE)
        .from(WORKFLOW)
        .where(WORKFLOW.CODE.eq(code))
        .fetchOptional(WORKFLOW.CODE);
  }

  /**
   * Proposition d'une nouvelle version, à valider par le Ministre (fonction de la base : droits,
   * une seule proposition ouverte, date d'effet, DOC-010 si rien ne change ; tout est tracé).
   *
   * @return numéro de la version proposée
   */
  public int proposer(
      String code,
      String libelle,
      String reglePaiement,
      List<Etape> etapes,
      String motif,
      LocalDate dateEffet,
      String libelleEn,
      String reglePaiementEn) {
    String json =
        JSON.writeValueAsString(
            etapes.stream()
                .map(
                    e -> {
                      Map<String, String> m = new HashMap<>();
                      m.put("acteur", e.acteur());
                      m.put("libelle", e.libelle());
                      m.put("acteur_en", e.acteurEn());
                      m.put("libelle_en", e.libelleEn());
                      return m;
                    })
                .toList());
    return Routines.fnProposerWorkflow(
        dsl.configuration(),
        code,
        libelle,
        reglePaiement,
        JSONB.valueOf(json),
        motif,
        dateEffet,
        libelleEn,
        reglePaiementEn);
  }

  /**
   * Décision sur une proposition (fonction de la base : droits, statut, date d'effet).
   *
   * @return date d'effet retenue pour une validation, {@code null} sinon
   */
  public LocalDate decider(String code, int version, EtatVersion.Decision decision, String motif) {
    return Routines.fnDeciderWorkflow(dsl.configuration(), code, version, decision.code(), motif);
  }

  private static Field<String> nom(Field<String> prenom, Field<String> nom) {
    return DSL.trim(DSL.concat(DSL.coalesce(prenom, ""), DSL.val(" "), DSL.coalesce(nom, "")));
  }

  private static String vide(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
