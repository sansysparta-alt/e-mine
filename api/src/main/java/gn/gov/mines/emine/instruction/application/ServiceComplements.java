package gn.gov.mines.emine.instruction.application;

import gn.gov.mines.emine.auth.UtilisateurConnecte;
import gn.gov.mines.emine.auth.UtilisateurCourant;
import gn.gov.mines.emine.demande.Demandes;
import gn.gov.mines.emine.demande.Demandes.DemandeResume;
import gn.gov.mines.emine.instruction.domaine.Complement;
import gn.gov.mines.emine.instruction.domaine.RegleComplement;
import gn.gov.mines.emine.instruction.domaine.RegleMotif;
import gn.gov.mines.emine.instruction.infrastructure.ComplementRepository;
import gn.gov.mines.emine.instruction.infrastructure.InstructionRepository;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.jooq.enums.StatutDemande;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compléments (US-05.2, règles dans {@link RegleComplement}) : un agent qui instruit demande un
 * complément ; le demandeur y répond depuis son suivi ; sans réponse dans le délai, l'agent relance
 * ou clôt le dossier avec un motif. Les droits (« instruire », « déposer ») sont vérifiés par le
 * contrôleur ; la base contrôle les transitions, fige la demande de complément et trace tout.
 */
@Service
public class ServiceComplements {

  private static final Duration DELAI_REPONSE =
      Duration.ofDays(RegleComplement.DELAI_REPONSE_JOURS);

  private final Demandes demandes;
  private final ComplementRepository complements;
  private final InstructionRepository instruction;
  private final UtilisateurCourant utilisateurCourant;
  private final Clock horloge;

  public ServiceComplements(
      Demandes demandes,
      ComplementRepository complements,
      InstructionRepository instruction,
      UtilisateurCourant utilisateurCourant,
      Clock horloge) {
    this.demandes = demandes;
    this.complements = complements;
    this.instruction = instruction;
    this.utilisateurCourant = utilisateurCourant;
    this.horloge = horloge;
  }

  /**
   * Compléments d'une demande visible, avec ce que l'utilisateur courant peut faire du complément
   * ouvert.
   *
   * @param peutDemander un complément peut être demandé à cette étape (agent qui instruit)
   * @param peutRepondre le demandeur peut répondre au complément ouvert
   * @param delaiDepasse le complément ouvert a dépassé son délai de réponse
   * @param piecesJointesDepuis pièces demandées déjà jointes depuis la demande de complément
   */
  public record EtatComplements(
      List<Complement> complements,
      boolean peutDemander,
      boolean peutRepondre,
      boolean delaiDepasse,
      List<String> piecesJointesDepuis) {}

  @Transactional(readOnly = true)
  public EtatComplements etat(long id, boolean peutInstruire) {
    DemandeResume demande = demande(id);
    List<Complement> liste = complements.lister(id);
    Optional<Complement> ouvert = liste.stream().filter(Complement::ouvert).findFirst();
    boolean demandeur = moi().map(u -> u.id() == demande.demandeurId()).orElse(false);
    List<String> jointes =
        ouvert
            .map(
                c ->
                    demandes.typesPiecesAjouteesDepuis(id, c.demandeLe()).stream()
                        .filter(c.piecesDemandees()::contains)
                        .distinct()
                        .toList())
            .orElse(List.of());
    return new EtatComplements(
        liste,
        peutInstruire && RegleComplement.peutEtreDemande(demande.statut()),
        demandeur && ouvert.isPresent(),
        ouvert.map(c -> c.delaiDepasse(horloge.instant())).orElse(false),
        jointes);
  }

  /**
   * Demande un complément : DEM-001 si la demande n'est pas à une étape où on peut en demander,
   * VAL-001 si le motif ou les pièces ne conviennent pas. Renvoie le complément ouvert.
   */
  @Transactional
  public Complement demander(long id, String motif, List<String> pieces) {
    RegleMotif.probleme(motif).ifPresent(m -> invalide("motif", m));
    List<String> attendues = RegleComplement.piecesNettoyees(pieces);
    RegleComplement.problemePieces(attendues).ifPresent(m -> invalide("pieces", m));
    DemandeResume demande = demande(id);
    if (!RegleComplement.peutEtreDemande(demande.statut())) {
      throw dejaTraitee(demande);
    }
    StatutDemande origine = StatutDemande.lookupLiteral(demande.statut());
    exiger(instruction.changerStatut(id, origine, StatutDemande.complement_requis), demande);
    complements.ouvrir(id, connecte().id(), demande.statut(), motif.strip(), attendues);
    return complements.ouvert(id).orElseThrow(() -> dejaTraitee(demande));
  }

  /**
   * Réponse du demandeur : chaque pièce demandée doit avoir été jointe depuis la demande de
   * complément, ou un message écrit si aucune pièce n'est demandée (DEM-010). La demande revient à
   * l'étape d'où le complément a été demandé ; renvoie ce statut.
   */
  @Transactional
  public String repondre(long id, String message) {
    RegleMotif.problemeFacultatif(message).ifPresent(m -> invalide("message", m));
    DemandeResume demande = demande(id);
    UtilisateurConnecte moi = connecte();
    if (moi.id() != demande.demandeurId()) {
      throw new ErreurMetier(CodeErreur.PERM_001, Map.of());
    }
    Complement c = ouvert(demande);
    List<String> jointes = demandes.typesPiecesAjouteesDepuis(id, c.demandeLe());
    if (!RegleComplement.reponseComplete(c.piecesDemandees(), jointes, message)) {
      throw new ErreurMetier(
          CodeErreur.DEM_010,
          Map.of(
              "piecesManquantes", RegleComplement.piecesManquantes(c.piecesDemandees(), jointes)));
    }
    exiger(
        complements.repondre(c.id(), moi.id(), RegleMotif.nettoyer(message), horloge.instant()),
        demande);
    exiger(
        instruction.changerStatut(
            id, StatutDemande.complement_requis, StatutDemande.lookupLiteral(c.statutOrigine())),
        demande);
    return c.statutOrigine();
  }

  /** Relance par l'agent après le délai de réponse (DEM-009 avant) : nouveau délai de 5 jours. */
  @Transactional
  public Complement relancer(long id) {
    DemandeResume demande = demande(id);
    Complement c = ouvertDepasse(demande);
    exiger(complements.relancer(c.id(), horloge.instant(), DELAI_REPONSE), demande);
    return complements.ouvert(id).orElseThrow(() -> dejaTraitee(demande));
  }

  /**
   * Clôture sans réponse après le délai (DEM-009 avant) : la demande est rejetée avec le motif de
   * l'agent, communiqué au demandeur. Décision d'un agent, jamais automatique.
   */
  @Transactional
  public String clore(long id, String motif) {
    RegleMotif.probleme(motif).ifPresent(m -> invalide("motif", m));
    DemandeResume demande = demande(id);
    Complement c = ouvertDepasse(demande);
    exiger(complements.clore(c.id(), connecte().id(), horloge.instant()), demande);
    exiger(instruction.rejeterDepuis(id, StatutDemande.complement_requis, motif.strip()), demande);
    return "rejetee";
  }

  /** Relance automatique à J+3 (tâche planifiée) ; renvoie le nombre de demandeurs relancés. */
  @Transactional
  public int relancerAutomatiquement() {
    return complements.relancerAutomatiquement();
  }

  private Complement ouvertDepasse(DemandeResume demande) {
    Complement c = ouvert(demande);
    if (!c.delaiDepasse(horloge.instant())) {
      throw new ErreurMetier(
          CodeErreur.DEM_009, Map.of("id", demande.id(), "dateLimite", c.dateLimite()));
    }
    return c;
  }

  /** Complément en attente de réponse d'une demande au statut « complément requis » (DEM-001). */
  private Complement ouvert(DemandeResume demande) {
    return Optional.of(demande)
        .filter(d -> "complement_requis".equals(d.statut()))
        .flatMap(d -> complements.ouvert(d.id()))
        .orElseThrow(() -> dejaTraitee(demande));
  }

  private static void invalide(String champ, String message) {
    throw new ErreurMetier(
        CodeErreur.VAL_001, Map.of("champs", List.of(Map.of("champ", champ, "message", message))));
  }

  private static void exiger(int lignes, DemandeResume demande) {
    if (lignes == 0) {
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

  private Optional<UtilisateurConnecte> moi() {
    return utilisateurCourant.get();
  }

  private UtilisateurConnecte connecte() {
    return moi().orElseThrow(() -> new IllegalStateException("Requête authentifiée sans compte"));
  }
}
