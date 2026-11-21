package gn.gov.mines.emine.instruction.application;

import gn.gov.mines.emine.catalogue.Catalogue;
import gn.gov.mines.emine.catalogue.TypeDocument;
import gn.gov.mines.emine.demande.Demandes;
import gn.gov.mines.emine.demande.Demandes.DemandeResume;
import gn.gov.mines.emine.instruction.domaine.RegleRecevabilite;
import gn.gov.mines.emine.instruction.domaine.RegleRecevabilite.Examen;
import gn.gov.mines.emine.instruction.infrastructure.InstructionRepository;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recevabilité par l'agent du guichet (US-05.1) : examen des conditions (frais payés, pièces
 * obligatoires), puis déclaration recevable ou non recevable (motif obligatoire). Le droit «
 * instruire » est vérifié par le contrôleur ; la base refuse aussi la recevabilité sans frais payés
 * (V8, DEM-006) et toute transition non prévue (DEM-001).
 */
@Service
public class ServiceRecevabilite {

  private final Demandes demandes;
  private final Catalogue catalogue;
  private final InstructionRepository instruction;
  private final Clock horloge;

  public ServiceRecevabilite(
      Demandes demandes, Catalogue catalogue, InstructionRepository instruction, Clock horloge) {
    this.demandes = demandes;
    this.catalogue = catalogue;
    this.instruction = instruction;
    this.horloge = horloge;
  }

  /** Conditions de la recevabilité d'une demande visible (DEM-002 sinon). */
  @Transactional(readOnly = true)
  public Examen examiner(long id) {
    return examen(demande(id));
  }

  /**
   * Déclare la demande recevable : DEM-001 si elle n'est plus à examiner, DEM-006 si les frais ne
   * sont pas payés, DEM-007 si une pièce obligatoire manque.
   */
  @Transactional
  public String declarerRecevable(long id) {
    DemandeResume demande = demande(id);
    Examen examen = examen(demande);
    exigerAExaminer(examen, demande);
    if (!examen.fraisPayes()) {
      throw new ErreurMetier(CodeErreur.DEM_006, Map.of("id", id));
    }
    if (!examen.piecesManquantes().isEmpty()) {
      throw new ErreurMetier(
          CodeErreur.DEM_007, Map.of("piecesManquantes", examen.piecesManquantes()));
    }
    if (instruction.declarerRecevable(id, horloge.instant()) == 0) {
      throw dejaTraitee(demande);
    }
    return "recevable";
  }

  /**
   * Déclare la demande non recevable (statut « rejetée ») avec un motif de 10 à 2000 caractères
   * (VAL-001 sinon), communiqué au demandeur. Possible même si les frais ne sont pas payés.
   */
  @Transactional
  public String declarerNonRecevable(long id, String motif) {
    RegleRecevabilite.problemeMotif(motif)
        .ifPresent(
            message -> {
              throw new ErreurMetier(
                  CodeErreur.VAL_001,
                  Map.of("champs", List.of(Map.of("champ", "motif", "message", message))));
            });
    DemandeResume demande = demande(id);
    exigerAExaminer(examen(demande), demande);
    if (instruction.declarerNonRecevable(id, motif.strip()) == 0) {
      throw dejaTraitee(demande);
    }
    return "rejetee";
  }

  private Examen examen(DemandeResume demande) {
    List<String> requises =
        catalogue
            .trouverActif(demande.typeDocumentCode())
            .map(TypeDocument::piecesRequises)
            .orElse(List.of());
    return RegleRecevabilite.examiner(
        demande.statut(),
        demande.fraisPayes(),
        requises,
        demandes.typesPiecesFournies(demande.id()));
  }

  private static void exigerAExaminer(Examen examen, DemandeResume demande) {
    if (!examen.aExaminer()) {
      throw dejaTraitee(demande);
    }
  }

  private static ErreurMetier dejaTraitee(DemandeResume demande) {
    return new ErreurMetier(
        CodeErreur.DEM_001, Map.of("id", demande.id(), "statut", demande.statut()));
  }

  private DemandeResume demande(long id) {
    return demandes
        .lire(id)
        .orElseThrow(() -> new ErreurMetier(CodeErreur.DEM_002, Map.of("id", id)));
  }
}
