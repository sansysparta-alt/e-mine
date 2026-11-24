package gn.gov.mines.emine.demande.domaine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Frise de suivi (US-07.3, point 6 de l'analyse) : les 11 statuts techniques regroupés en 6 étapes
 * visibles, dans l'<b>ordre réel</b> du circuit (choix du porteur du projet du 22/11/2026 : les
 * frais sont payés avant le contrôle du guichet, règle V8) — Dépôt, Paiement des frais, Contrôle,
 * Instruction, Paiement des droits, Document signé. Le cahier des charges les nommait « Dépôt,
 * Contrôle, Paiement 1, Instruction, Paiement 2, Signé » : écart d'ordre à signaler au Ministère.
 *
 * <p>Une seule définition, côté API : chaque étape est faite (avec sa date), en cours (en précisant
 * si l'action revient au demandeur), à venir, sans objet (pas de droits à payer), rejetée ou non
 * atteinte (après un rejet).
 */
public final class RegleSuivi {

  private RegleSuivi() {}

  /** Étapes de la frise, dans l'ordre. */
  public enum Etape {
    DEPOT("depot"),
    PAIEMENT_FRAIS("paiement-frais"),
    CONTROLE("controle"),
    INSTRUCTION("instruction"),
    PAIEMENT_DROITS("paiement-droits"),
    SIGNE("signe");

    private final String cle;

    Etape(String cle) {
      this.cle = cle;
    }

    public String cle() {
      return cle;
    }
  }

  /** État d'une étape. */
  public enum Etat {
    FAITE("faite"),
    EN_COURS("en-cours"),
    A_VENIR("a-venir"),
    SANS_OBJET("sans-objet"),
    REJETEE("rejetee"),
    NON_ATTEINTE("non-atteinte");

    private final String cle;

    Etat(String cle) {
      this.cle = cle;
    }

    public String cle() {
      return cle;
    }
  }

  /**
   * Dates des étapes franchies (heure UTC), {@code null} si l'étape ne l'est pas.
   *
   * @param decision décision du signataire (fin de l'instruction)
   * @param rejet passage au statut « rejetée »
   */
  public record Dates(
      Instant depot,
      Instant fraisPayes,
      Instant recevabilite,
      Instant decision,
      Instant droitsPayes,
      Instant delivre,
      Instant rejet) {}

  /**
   * Situation de la demande.
   *
   * @param droitsRequis le document a des droits à payer après la décision (catalogue)
   * @param origineComplement étape d'où vient le complément en cours ({@code deposee} ou {@code
   *     en_instruction}), {@code null} sans complément
   */
  public record Situation(
      String statut,
      boolean fraisPayes,
      boolean droitsRequis,
      String origineComplement,
      Dates dates) {}

  /**
   * Une étape de la frise.
   *
   * @param date date à laquelle l'étape a été franchie (ou rejetée), {@code null} sinon
   * @param actionDemandeur étape en cours dont l'action revient au demandeur (payer, compléter)
   */
  public record EtapeSuivi(Etape etape, Etat etat, Instant date, boolean actionDemandeur) {}

  /** Frise d'une demande déposée (vide pour un brouillon). */
  public static List<EtapeSuivi> suivi(Situation s) {
    if ("brouillon".equals(s.statut())) {
      return List.of();
    }
    Etape[] etapes = Etape.values();
    boolean rejetee = "rejetee".equals(s.statut());
    int courante = rejetee ? etapeDuRejet(s) : etapeCourante(s);
    boolean actionDemandeur = !rejetee && actionDemandeur(s);
    List<EtapeSuivi> frise = new ArrayList<>();
    for (int i = 0; i < etapes.length; i++) {
      Etape e = etapes[i];
      boolean sansObjet = e == Etape.PAIEMENT_DROITS && !s.droitsRequis();
      Etat etat;
      if (sansObjet) {
        etat = Etat.SANS_OBJET;
      } else if (i < courante) {
        etat = Etat.FAITE;
      } else if (i == courante) {
        etat = rejetee ? Etat.REJETEE : Etat.EN_COURS;
      } else {
        etat = rejetee ? Etat.NON_ATTEINTE : Etat.A_VENIR;
      }
      Instant date =
          etat == Etat.FAITE ? date(e, s.dates()) : etat == Etat.REJETEE ? s.dates().rejet() : null;
      frise.add(new EtapeSuivi(e, etat, date, etat == Etat.EN_COURS && actionDemandeur));
    }
    return List.copyOf(frise);
  }

  /**
   * Avancement d'une demande en cours (« Étape 3 sur 6 ») : rang de l'étape en cours parmi les
   * étapes qui s'appliquent (sans « Paiement des droits » pour un document sans droits).
   */
  public record Avancement(int etape, int total) {}

  /**
   * Avancement déduit de la frise (même règle, une seule définition). Une demande délivrée est à la
   * dernière étape (« Étape 6 sur 6 », homogène avec les demandes en cours) ; {@code null} pour un
   * brouillon, une demande rejetée ou archivée.
   */
  public static Avancement avancement(Situation s) {
    List<EtapeSuivi> applicables =
        suivi(s).stream().filter(e -> e.etat() != Etat.SANS_OBJET).toList();
    if ("delivree".equals(s.statut())) {
      return new Avancement(applicables.size(), applicables.size());
    }
    for (int i = 0; i < applicables.size(); i++) {
      if (applicables.get(i).etat() == Etat.EN_COURS) {
        return new Avancement(i + 1, applicables.size());
      }
    }
    return null;
  }

  /** Indice de l'étape en cours ; 6 (au-delà de la dernière) quand le document est délivré. */
  private static int etapeCourante(Situation s) {
    return switch (s.statut()) {
      case "deposee" -> s.fraisPayes() ? Etape.CONTROLE.ordinal() : Etape.PAIEMENT_FRAIS.ordinal();
      case "complement_requis" ->
          "en_instruction".equals(s.origineComplement())
              ? Etape.INSTRUCTION.ordinal()
              : Etape.CONTROLE.ordinal();
      case "recevable", "en_instruction", "avis_rendu" -> Etape.INSTRUCTION.ordinal();
      case "decision" -> Etape.PAIEMENT_DROITS.ordinal();
      case "droits_payes" -> Etape.SIGNE.ordinal();
      default -> Etape.values().length; // délivrée, archivée
    };
  }

  /** Étape où la demande a été rejetée : au contrôle (non recevable) ou à l'instruction. */
  private static int etapeDuRejet(Situation s) {
    return s.dates().recevabilite() == null
        ? Etape.CONTROLE.ordinal()
        : Etape.INSTRUCTION.ordinal();
  }

  /** L'étape en cours attend le demandeur : frais, complément ou droits à payer. */
  private static boolean actionDemandeur(Situation s) {
    return switch (s.statut()) {
      case "deposee" -> !s.fraisPayes();
      case "complement_requis", "decision" -> true;
      default -> false;
    };
  }

  private static Instant date(Etape e, Dates d) {
    return switch (e) {
      case DEPOT -> d.depot();
      case PAIEMENT_FRAIS -> d.fraisPayes();
      case CONTROLE -> d.recevabilite();
      case INSTRUCTION -> d.decision();
      case PAIEMENT_DROITS -> d.droitsPayes();
      case SIGNE -> d.delivre();
    };
  }
}
