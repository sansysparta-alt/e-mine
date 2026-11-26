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
import gn.gov.mines.emine.shared.jooq.Routines;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** Workflows et leurs versions (US-02.5, V20). */
@Repository
public class WorkflowRepository {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final DSLContext dsl;

  public WorkflowRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** Les workflows, dans l'ordre du catalogue, avec leur version en vigueur. */
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
      liste.add(
          new Workflow(
              w.getCode(),
              w.getDelaiMinJours(),
              w.getDelaiMaxJours(),
              documents.getOrDefault(w.getId(), List.of()),
              versions(w.getId()).stream()
                  .filter(v -> v.version() == w.getVersion())
                  .findFirst()
                  .orElseThrow()));
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

  private List<VersionWorkflow> versions(short workflowId) {
    Map<Integer, List<Etape>> etapes = new HashMap<>();
    dsl.select(WORKFLOW_ETAPE.VERSION, WORKFLOW_ETAPE.ACTEUR, WORKFLOW_ETAPE.LIBELLE)
        .from(WORKFLOW_ETAPE)
        .where(WORKFLOW_ETAPE.WORKFLOW_ID.eq(workflowId))
        .orderBy(WORKFLOW_ETAPE.VERSION, WORKFLOW_ETAPE.ORDRE)
        .fetch()
        .forEach(
            r ->
                etapes
                    .computeIfAbsent(r.value1(), k -> new ArrayList<>())
                    .add(new Etape(r.value2(), r.value3())));
    var auteur =
        DSL.trim(
            DSL.concat(
                DSL.coalesce(UTILISATEUR.PRENOM, ""),
                DSL.val(" "),
                DSL.coalesce(UTILISATEUR.NOM, "")));
    return dsl.select(VERSION_WORKFLOW.asterisk(), auteur.as("auteur"))
        .from(VERSION_WORKFLOW)
        .leftJoin(UTILISATEUR)
        .on(UTILISATEUR.ID.eq(VERSION_WORKFLOW.MODIFIEE_PAR))
        .where(VERSION_WORKFLOW.WORKFLOW_ID.eq(workflowId))
        .orderBy(VERSION_WORKFLOW.VERSION.desc())
        .fetch(
            r -> {
              String nom = r.get("auteur", String.class);
              return new VersionWorkflow(
                  r.get(VERSION_WORKFLOW.VERSION),
                  r.get(VERSION_WORKFLOW.LIBELLE),
                  r.get(VERSION_WORKFLOW.REGLE_PAIEMENT),
                  r.get(VERSION_WORKFLOW.MOTIF),
                  nom == null || nom.isBlank() ? null : nom,
                  r.get(VERSION_WORKFLOW.MODIFIEE_LE),
                  etapes.getOrDefault(r.get(VERSION_WORKFLOW.VERSION), List.of()));
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
   * Nouvelle version (fonction de la base, qui contrôle les droits et trace tout).
   *
   * @return numéro de la nouvelle version
   */
  public int modifier(
      String code, String libelle, String reglePaiement, List<Etape> etapes, String motif) {
    String json =
        JSON.writeValueAsString(
            etapes.stream()
                .map(e -> Map.of("acteur", e.acteur(), "libelle", e.libelle()))
                .toList());
    return Routines.fnModifierWorkflow(
        dsl.configuration(), code, libelle, reglePaiement, JSONB.valueOf(json), motif);
  }
}
