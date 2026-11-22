package gn.gov.mines.emine.demande.application;

import gn.gov.mines.emine.auth.UtilisateurConnecte;
import gn.gov.mines.emine.auth.UtilisateurCourant;
import gn.gov.mines.emine.catalogue.Catalogue;
import gn.gov.mines.emine.catalogue.TypeDocument;
import gn.gov.mines.emine.demande.domaine.Brouillon;
import gn.gov.mines.emine.demande.domaine.CritereDemandes;
import gn.gov.mines.emine.demande.domaine.DemandeDetail;
import gn.gov.mines.emine.demande.domaine.GroupeATraiter;
import gn.gov.mines.emine.demande.domaine.Indicateurs;
import gn.gov.mines.emine.demande.domaine.Periode;
import gn.gov.mines.emine.demande.domaine.RegleATraiter;
import gn.gov.mines.emine.demande.domaine.VueDemandes;
import gn.gov.mines.emine.demande.infrastructure.DemandeRepository;
import gn.gov.mines.emine.demande.infrastructure.IndicateursRepository;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.jooq.enums.StatutDemande;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cas d'usage du brouillon de demande (US-03.1) : créer, modifier tant qu'il n'est pas déposé,
 * relire. Le droit « déposer » est vérifié par le contrôleur ; la RLS garantit qu'un usager n'agit
 * que sur ses propres demandes.
 */
@Service
public class ServiceDemandes {

  private final DemandeRepository demandes;
  private final IndicateursRepository indicateurs;
  private final Catalogue catalogue;
  private final UtilisateurCourant utilisateurCourant;

  public ServiceDemandes(
      DemandeRepository demandes,
      IndicateursRepository indicateurs,
      Catalogue catalogue,
      UtilisateurCourant utilisateurCourant) {
    this.demandes = demandes;
    this.indicateurs = indicateurs;
    this.catalogue = catalogue;
    this.utilisateurCourant = utilisateurCourant;
  }

  @Transactional
  public DemandeDetail creer(Brouillon brouillon) {
    UtilisateurConnecte moi = moi();
    TypeDocument type = typeDocument(brouillon.typeDocumentCode());
    long id =
        demandes.creerBrouillon(
            moi.id(),
            moi.organisationId(),
            type.id(),
            brouillon.prefectureId() == null ? null : prefecture(brouillon.prefectureId()),
            brouillon.substanceCode() == null ? null : substance(brouillon.substanceCode()),
            brouillon.superficieHa());
    return lireVisible(id);
  }

  @Transactional
  public DemandeDetail modifier(long id, Brouillon brouillon) {
    TypeDocument type = typeDocument(brouillon.typeDocumentCode());
    int modifiees =
        demandes.modifierBrouillon(
            id,
            type.id(),
            brouillon.prefectureId() == null ? null : prefecture(brouillon.prefectureId()),
            brouillon.substanceCode() == null ? null : substance(brouillon.substanceCode()),
            brouillon.superficieHa());
    if (modifiees == 0) {
      lireVisible(id); // DEM-002 si elle n'existe pas ou n'est pas visible
      throw new ErreurMetier(CodeErreur.DEM_003, Map.of("id", id));
    }
    return lireVisible(id);
  }

  /**
   * Supprime un brouillon de l'usager (suppression logique) : DEM-002 s'il n'est pas visible,
   * DEM-003 s'il a déjà été déposé (une demande déposée ne se supprime jamais).
   */
  @Transactional
  public void supprimerBrouillon(long id) {
    lireVisible(id);
    if (demandes.supprimerBrouillon(id, java.time.Instant.now()) == 0) {
      throw new ErreurMetier(CodeErreur.DEM_003, Map.of("id", id));
    }
  }

  @Transactional(readOnly = true)
  public DemandeDetail lire(long id) {
    DemandeDetail d = lireVisible(id);
    // Règle du socle : un brouillon reste privé à son auteur, même pour un agent (DEM-002).
    if (d.estBrouillon()
        && utilisateurCourant.get().map(UtilisateurConnecte::interne).orElse(false)) {
      throw new ErreurMetier(CodeErreur.DEM_002, Map.of("id", id));
    }
    return d;
  }

  /**
   * « Mes demandes » (US-03.5) : la RLS limite un usager à ses propres demandes ; un agent interne
   * voit toutes les demandes (base des files de travail de l'instruction).
   */
  @Transactional(readOnly = true)
  public PageResultat<DemandeDetail> lister(CritereDemandes critere, Pagination pagination) {
    List<StatutDemande> filtre =
        critere.statuts().stream()
            .map(
                statut ->
                    java.util.Arrays.stream(StatutDemande.values())
                        .filter(s -> s.getLiteral().equals(statut))
                        .findFirst()
                        .orElseThrow(() -> champInvalide("statut", "Statut inconnu.")))
            .toList();
    // Règle du socle appliquée par l'API : un agent ne voit jamais les brouillons des usagers.
    boolean interne = utilisateurCourant.get().map(UtilisateurConnecte::interne).orElse(false);
    return demandes.lister(
        filtre, interne ? critere.sansBrouillons() : critere, pagination, filtreMoi());
  }

  /**
   * Filtre « À traiter » du profil connecté (correctifs J5) : tous ses groupes, ou le seul groupe
   * demandé (VAL-001 s'il n'en fait pas partie).
   */
  public RegleATraiter.Filtre filtreATraiter(String groupe) {
    UtilisateurConnecte moi = moi();
    List<GroupeATraiter> groupes = groupesDe(moi);
    if (groupe == null || groupe.isBlank()) {
      return new RegleATraiter.Filtre(groupes, moi.profilCode());
    }
    GroupeATraiter g =
        GroupeATraiter.depuis(groupe)
            .filter(groupes::contains)
            .orElseThrow(() -> champInvalide("groupe", "Groupe inconnu pour ce profil."));
    return new RegleATraiter.Filtre(List.of(g), moi.profilCode());
  }

  /** Vue demandée, validée pour le profil connecté (VAL-001 sinon). */
  public CritereDemandes.Vue vue(String cle) {
    UtilisateurConnecte moi = moi();
    RegleATraiter.Filtre aTraiter = filtreATraiter(null);
    VueDemandes vue =
        VueDemandes.depuis(cle)
            .filter(v -> vuesDe(moi, aTraiter).contains(v))
            .orElseThrow(() -> champInvalide("vue", "Vue inconnue pour ce profil."));
    return new CritereDemandes.Vue(vue, aTraiter, moi.interne());
  }

  /** Nombre de demandes de chaque vue du profil connecté (exclusives : leur somme = toutes). */
  @Transactional(readOnly = true)
  public Map<VueDemandes, Long> vues() {
    UtilisateurConnecte moi = moi();
    RegleATraiter.Filtre aTraiter = filtreATraiter(null);
    return demandes.compterVues(vuesDe(moi, aTraiter), aTraiter, moi.interne());
  }

  private static List<VueDemandes> vuesDe(UtilisateurConnecte moi, RegleATraiter.Filtre aTraiter) {
    return VueDemandes.pour(moi.interne(), !aTraiter.groupes().isEmpty());
  }

  /** Nombre de demandes de chaque groupe « à traiter » du profil connecté. */
  @Transactional(readOnly = true)
  public Map<GroupeATraiter, Long> aTraiter() {
    return demandes.compterATraiter(filtreATraiter(null));
  }

  private List<GroupeATraiter> groupesDe(UtilisateurConnecte moi) {
    boolean rendDesAvis =
        moi.interne()
            && catalogue.lister().stream()
                .anyMatch(t -> t.profilsAvis().contains(moi.profilCode()));
    return RegleATraiter.groupesPour(
        moi.interne(),
        moi.permissions().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()),
        rendDesAvis);
  }

  /** Nombre de demandes visibles par statut (tableau de bord). */
  @Transactional(readOnly = true)
  public Map<String, Long> compteurs() {
    Map<String, Long> compteurs = demandes.compteursParStatut();
    if (utilisateurCourant.get().map(UtilisateurConnecte::interne).orElse(false)) {
      compteurs.remove("brouillon");
    }
    return compteurs;
  }

  /**
   * Indicateurs du tableau de bord (correctifs ergonomie) sur la période demandée (mois en cours
   * par défaut) ; retards et attentes « à ce jour ». VAL-001 si la période est inconnue.
   */
  @Transactional(readOnly = true)
  public Indicateurs indicateurs(String periode) {
    return indicateurs.calculer(periode(periode), Instant.now());
  }

  /**
   * Filtres d'un indicateur du tableau de bord appliqués à la liste, ou {@code null} sans filtre :
   * en retard (à ce jour), traités aujourd'hui par le profil connecté, entrés dans leur statut
   * pendant la période.
   */
  public CritereDemandes.Pilotage pilotage(
      Boolean enRetard, Boolean traiteParMoi, String periode, Boolean bloque) {
    Instant maintenant = Instant.now();
    CritereDemandes.Pilotage p =
        new CritereDemandes.Pilotage(
            Boolean.TRUE.equals(enRetard),
            Boolean.TRUE.equals(traiteParMoi) ? Periode.debutDuJour(maintenant) : null,
            periode == null ? null : periode(periode).debut(maintenant),
            Boolean.TRUE.equals(bloque),
            maintenant);
    return p.vide() ? null : p;
  }

  private static Periode periode(String cle) {
    if (cle == null || cle.isBlank()) {
      return Periode.MOIS;
    }
    return Periode.depuis(cle).orElseThrow(() -> champInvalide("periode", "Période inconnue."));
  }

  /** Filtre « à traiter » du profil connecté, ou {@code null} hors requête utilisateur. */
  private RegleATraiter.Filtre filtreMoi() {
    return utilisateurCourant
        .get()
        .map(moi -> new RegleATraiter.Filtre(groupesDe(moi), moi.profilCode()))
        .orElse(null);
  }

  private DemandeDetail lireVisible(long id) {
    return demandes
        .lire(id, filtreMoi())
        .orElseThrow(() -> new ErreurMetier(CodeErreur.DEM_002, Map.of("id", id)));
  }

  private TypeDocument typeDocument(String code) {
    return catalogue
        .trouverActif(code)
        .orElseThrow(
            () -> new ErreurMetier(CodeErreur.DEM_005, Map.of("code", String.valueOf(code))));
  }

  private short prefecture(short id) {
    return demandes
        .prefecture(id)
        .orElseThrow(() -> champInvalide("prefectureId", "Préfecture inconnue."));
  }

  private short substance(String code) {
    return demandes
        .substance(code)
        .orElseThrow(() -> champInvalide("substanceCode", "Substance inconnue."));
  }

  private static ErreurMetier champInvalide(String champ, String message) {
    return new ErreurMetier(
        CodeErreur.VAL_001, Map.of("champs", List.of(Map.of("champ", champ, "message", message))));
  }

  private UtilisateurConnecte moi() {
    return utilisateurCourant
        .get()
        .orElseThrow(() -> new IllegalStateException("Requête authentifiée sans compte"));
  }
}
