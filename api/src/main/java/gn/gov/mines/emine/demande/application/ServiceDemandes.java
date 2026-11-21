package gn.gov.mines.emine.demande.application;

import gn.gov.mines.emine.auth.UtilisateurConnecte;
import gn.gov.mines.emine.auth.UtilisateurCourant;
import gn.gov.mines.emine.catalogue.Catalogue;
import gn.gov.mines.emine.catalogue.TypeDocument;
import gn.gov.mines.emine.demande.domaine.Brouillon;
import gn.gov.mines.emine.demande.domaine.DemandeDetail;
import gn.gov.mines.emine.demande.infrastructure.DemandeRepository;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.jooq.enums.StatutDemande;
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
  private final Catalogue catalogue;
  private final UtilisateurCourant utilisateurCourant;

  public ServiceDemandes(
      DemandeRepository demandes, Catalogue catalogue, UtilisateurCourant utilisateurCourant) {
    this.demandes = demandes;
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

  @Transactional(readOnly = true)
  public DemandeDetail lire(long id) {
    return lireVisible(id);
  }

  /** Nombre maximal de demandes renvoyées par la liste (V1, sans pagination). */
  public static final int LIMITE_LISTE = 200;

  /**
   * « Mes demandes » (US-03.5) : la RLS limite un usager à ses propres demandes ; un agent interne
   * voit toutes les demandes (base des files de travail de l'instruction).
   */
  @Transactional(readOnly = true)
  public List<DemandeDetail> lister(String statut, String typeDocumentCode) {
    StatutDemande filtre = null;
    if (statut != null) {
      filtre =
          java.util.Arrays.stream(StatutDemande.values())
              .filter(s -> s.getLiteral().equals(statut))
              .findFirst()
              .orElseThrow(() -> champInvalide("statut", "Statut inconnu."));
    }
    return demandes.lister(filtre, typeDocumentCode, LIMITE_LISTE);
  }

  private DemandeDetail lireVisible(long id) {
    return demandes
        .lire(id)
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
