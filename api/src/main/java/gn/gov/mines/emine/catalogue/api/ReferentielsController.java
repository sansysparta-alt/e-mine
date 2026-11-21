package gn.gov.mines.emine.catalogue.api;

import static gn.gov.mines.emine.shared.jooq.Tables.PREFECTURE;
import static gn.gov.mines.emine.shared.jooq.Tables.SUBSTANCE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Référentiels publics utilisés par le formulaire de demande : préfectures et substances. */
@RestController
@RequestMapping("/api/v1/referentiels")
@Tag(name = "Catalogue")
class ReferentielsController {

  private final DSLContext dsl;

  ReferentielsController(DSLContext dsl) {
    this.dsl = dsl;
  }

  @GetMapping("/prefectures")
  @Transactional(readOnly = true)
  @Operation(operationId = "listerPrefectures", summary = "Préfectures (public)")
  List<PrefectureDto> prefectures() {
    return dsl.select(PREFECTURE.ID, PREFECTURE.REGION, PREFECTURE.NOM)
        .from(PREFECTURE)
        .orderBy(PREFECTURE.REGION, PREFECTURE.NOM)
        .fetch(
            r ->
                new PrefectureDto(
                    r.get(PREFECTURE.ID), r.get(PREFECTURE.REGION), r.get(PREFECTURE.NOM)));
  }

  @GetMapping("/substances")
  @Transactional(readOnly = true)
  @Operation(operationId = "listerSubstances", summary = "Substances minérales (public)")
  List<SubstanceDto> substances() {
    return dsl.select(SUBSTANCE.CODE, SUBSTANCE.LIBELLE_FR, SUBSTANCE.LIBELLE_EN)
        .from(SUBSTANCE)
        .orderBy(SUBSTANCE.LIBELLE_FR)
        .fetch(
            r ->
                new SubstanceDto(
                    r.get(SUBSTANCE.CODE),
                    r.get(SUBSTANCE.LIBELLE_FR),
                    r.get(SUBSTANCE.LIBELLE_EN)));
  }

  record PrefectureDto(short id, String region, String nom) {}

  record SubstanceDto(String code, String libelleFr, String libelleEn) {}
}
