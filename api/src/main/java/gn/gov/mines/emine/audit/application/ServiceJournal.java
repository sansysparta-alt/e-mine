package gn.gov.mines.emine.audit.application;

import gn.gov.mines.emine.audit.domaine.CritereJournal;
import gn.gov.mines.emine.audit.domaine.EntreeJournal;
import gn.gov.mines.emine.audit.domaine.RegleJournal;
import gn.gov.mines.emine.audit.infrastructure.JournalRepository;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultation du journal d'audit par l'administrateur (US-11.7, partiel V1) : filtres par
 * catégorie d'objet, recherche et période (dates de Conakry), lignes rendues lisibles.
 */
@Service
public class ServiceJournal {

  /** Fuseau des dates saisies (heure de Conakry). */
  static final ZoneId CONAKRY = ZoneId.of("Africa/Conakry");

  private final JournalRepository journal;

  public ServiceJournal(JournalRepository journal) {
    this.journal = journal;
  }

  /** Une page du journal et le nombre de lignes par catégorie. */
  public record Consultation(PageResultat<EntreeJournal> page, Map<String, Long> compteurs) {}

  /**
   * Consulte le journal : VAL-001 si la catégorie est inconnue ou si la période est inversée.
   *
   * @param du premier jour (inclus), ou {@code null}
   * @param au dernier jour (inclus), ou {@code null}
   */
  @Transactional(readOnly = true)
  public Consultation consulter(
      String categorie, String recherche, LocalDate du, LocalDate au, Pagination pagination) {
    List<String> tables =
        categorie == null || categorie.isBlank()
            ? List.of()
            : RegleJournal.tables(categorie)
                .orElseThrow(() -> champInvalide("categorie", "Catégorie inconnue."));
    if (du != null && au != null && du.isAfter(au)) {
      throw champInvalide("au", "La fin de la période précède son début.");
    }
    CritereJournal critere =
        new CritereJournal(
            tables,
            recherche,
            du == null ? null : du.atStartOfDay(CONAKRY).toInstant(),
            au == null ? null : au.plusDays(1).atStartOfDay(CONAKRY).toInstant());
    return new Consultation(
        journal.lister(critere, pagination), journal.compteursParCategorie(critere));
  }

  /** Une ligne du journal (SYS-002 si elle n'existe pas). */
  @Transactional(readOnly = true)
  public EntreeJournal lire(long id) {
    return journal
        .lire(id)
        .orElseThrow(() -> new ErreurMetier(CodeErreur.SYS_002, Map.of("id", id)));
  }

  private static ErreurMetier champInvalide(String champ, String message) {
    return new ErreurMetier(
        CodeErreur.VAL_001, Map.of("champs", List.of(Map.of("champ", champ, "message", message))));
  }
}
