package gn.gov.mines.emine.delivrance.application;

import gn.gov.mines.emine.auth.UtilisateurConnecte;
import gn.gov.mines.emine.auth.UtilisateurCourant;
import gn.gov.mines.emine.catalogue.Catalogue;
import gn.gov.mines.emine.catalogue.TypeDocument;
import gn.gov.mines.emine.delivrance.domaine.ContenuDocument;
import gn.gov.mines.emine.delivrance.domaine.DocumentDelivre;
import gn.gov.mines.emine.delivrance.domaine.RegleRevocation;
import gn.gov.mines.emine.delivrance.domaine.SignatureService;
import gn.gov.mines.emine.delivrance.domaine.SignatureService.DocumentSigne;
import gn.gov.mines.emine.delivrance.domaine.SignatureService.Signataire;
import gn.gov.mines.emine.delivrance.domaine.SignatureService.SignatureIndisponible;
import gn.gov.mines.emine.delivrance.infrastructure.DelivranceRepository;
import gn.gov.mines.emine.delivrance.infrastructure.DelivranceRepository.DecisionFavorable;
import gn.gov.mines.emine.delivrance.infrastructure.GenerateurDocumentPdf;
import gn.gov.mines.emine.delivrance.infrastructure.ProprietesDelivrance;
import gn.gov.mines.emine.demande.Demandes;
import gn.gov.mines.emine.demande.Demandes.DemandeResume;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.stockage.StockageObjets;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Délivrance d'un document (US-06.3, US-06.4) : quand les droits sont payés, le signataire du
 * catalogue (D4) déclenche « Signer et délivrer » ; le système établit le PDF/A (gabarit générique,
 * QR), le signe (port {@link SignatureService}), l'archive dans le stockage, l'inscrit au registre
 * avec son empreinte SHA-256 et passe la demande à « délivrée ». Tout se fait dans une seule
 * transaction : en cas d'échec, ni numéro consommé ni statut changé.
 */
@Service
@EnableConfigurationProperties(ProprietesDelivrance.class)
public class ServiceDelivrance {

  private static final Logger LOG = LoggerFactory.getLogger(ServiceDelivrance.class);

  private final Demandes demandes;
  private final Catalogue catalogue;
  private final DelivranceRepository registre;
  private final GenerateurDocumentPdf generateur;
  private final SignatureService signature;
  private final StockageObjets stockage;
  private final ProprietesDelivrance proprietes;
  private final UtilisateurCourant utilisateurCourant;
  private final Clock horloge;

  public ServiceDelivrance(
      Demandes demandes,
      Catalogue catalogue,
      DelivranceRepository registre,
      GenerateurDocumentPdf generateur,
      SignatureService signature,
      StockageObjets stockage,
      ProprietesDelivrance proprietes,
      UtilisateurCourant utilisateurCourant,
      Clock horloge) {
    this.demandes = demandes;
    this.catalogue = catalogue;
    this.registre = registre;
    this.generateur = generateur;
    this.signature = signature;
    this.stockage = stockage;
    this.proprietes = proprietes;
    this.utilisateurCourant = utilisateurCourant;
    this.horloge = horloge;
  }

  /**
   * Ce qui est possible sur la demande pour l'utilisateur courant.
   *
   * @param aDelivrer la demande est prête (droits payés)
   * @param peutDelivrer l'utilisateur est le signataire du catalogue et la demande est prête
   * @param signataire libellé du signataire du catalogue
   */
  public record EtatDelivrance(boolean aDelivrer, boolean peutDelivrer, String signataire) {}

  @Transactional(readOnly = true)
  public EtatDelivrance etat(long demandeId) {
    DemandeResume demande = demande(demandeId);
    TypeDocument type = type(demande);
    boolean aDelivrer = "droits_payes".equals(demande.statut());
    boolean signataire =
        utilisateurCourant
            .get()
            .map(u -> type.signataireProfil().equals(u.profilCode()))
            .orElse(false);
    return new EtatDelivrance(aDelivrer, aDelivrer && signataire, type.signataire());
  }

  /**
   * Signe et délivre : DEM-008 si l'utilisateur n'est pas le signataire du catalogue, DEM-001 si la
   * demande n'est pas prête (ou déjà délivrée), DOC-004 si la signature est indisponible, SYS-003
   * si le stockage l'est.
   */
  @Transactional
  public DocumentDelivre delivrer(long demandeId) {
    UtilisateurConnecte moi = moi();
    DemandeResume demande = demande(demandeId);
    TypeDocument type = type(demande);
    if (!type.signataireProfil().equals(moi.profilCode())) {
      throw new ErreurMetier(CodeErreur.DEM_008, Map.of("signataire", type.signataire()));
    }
    // Verrou et contrôle de la transition d'abord : une seconde délivrance s'arrête ici.
    if (registre.passerADelivree(demandeId) == 0) {
      throw new ErreurMetier(
          CodeErreur.DEM_001, Map.of("id", demandeId, "statut", demande.statut()));
    }
    String numero = registre.prochainNumero(type.code());
    String code = registre.nouveauCode();
    DecisionFavorable decision =
        registre
            .decisionFavorable(demandeId)
            .orElseThrow(() -> new ErreurMetier(CodeErreur.DEM_001, Map.of("id", demandeId)));
    String signataireNom = (moi.prenom() + " " + moi.nom()).strip();
    byte[] pdf =
        generateur.generer(
            new ContenuDocument(
                numero,
                type.code(),
                type.libelleFr(),
                demande.demandeurNom(),
                demande.organisation(),
                demande.numero(),
                decision.le(),
                decision.observations(),
                type.signataire(),
                signataireNom,
                horloge.instant(),
                proprietes.urlVerification(code),
                code));
    DocumentSigne signe;
    try {
      signe =
          signature.signer(
              pdf, new Signataire(signataireNom, "Délivrance du document " + numero, null));
    } catch (SignatureIndisponible e) {
      LOG.error("Délivrance de la demande {} impossible : {}", demandeId, e.getMessage());
      throw new ErreurMetier(CodeErreur.DOC_004, Map.of("id", demandeId));
    }
    String empreinte = sha256(signe.pdf());
    stockage.deposer(
        proprietes.compartiment(),
        DocumentDelivre.cleStockage(numero),
        signe.pdf(),
        "application/pdf");
    registre.inscrire(
        numero,
        type.id(),
        demandeId,
        moi.id(),
        signe.niveau() + ";certificat-sha256=" + signe.empreinteCertificat(),
        signe.signeLe(),
        empreinte,
        code);
    return registre.parDemande(demandeId).orElseThrow();
  }

  /**
   * Révoque le document délivré de la demande (US-06.6) : PERM-001 hors profils Directeur et
   * Brigade, VAL-001 si le motif ne convient pas, DOC-006 s'il est déjà révoqué. Effet immédiat :
   * la vérification publique affiche « NON VALIDE ».
   */
  @Transactional
  public DocumentDelivre revoquer(long demandeId, String motif) {
    UtilisateurConnecte moi = moi();
    if (!RegleRevocation.peutRevoquer(moi.profilCode())) {
      throw new ErreurMetier(CodeErreur.PERM_001, Map.of("action", "revocation"));
    }
    RegleRevocation.problemeMotif(motif)
        .ifPresent(
            message -> {
              throw new ErreurMetier(
                  CodeErreur.VAL_001,
                  Map.of(
                      "champs", java.util.List.of(Map.of("champ", "motif", "message", message))));
            });
    DocumentDelivre document = lire(demandeId);
    if (registre.revoquer(document.id(), motif.strip()) == 0) {
      throw new ErreurMetier(CodeErreur.DOC_006, Map.of("numero", document.numero()));
    }
    LOG.info("Document {} révoqué", document.numero());
    return registre.parDemande(demandeId).orElseThrow();
  }

  /** Document délivré d'une demande visible (DEM-002 / DOC-005 sinon). */
  @Transactional(readOnly = true)
  public DocumentDelivre lire(long demandeId) {
    demande(demandeId);
    return registre
        .parDemande(demandeId)
        .orElseThrow(() -> new ErreurMetier(CodeErreur.DOC_005, Map.of("id", demandeId)));
  }

  /** « Documents délivrés » : ceux des demandes visibles du profil connecté (les siennes). */
  @Transactional(readOnly = true)
  public DelivranceRepository.PageDocuments listerVisibles(
      gn.gov.mines.emine.shared.pagination.Pagination pagination,
      String recherche,
      DelivranceRepository.Validite validite) {
    return registre.listerVisibles(pagination, recherche, validite);
  }

  /** Document et flux du PDF signé archivé. */
  public record Contenu(DocumentDelivre document, InputStream flux) {}

  @Transactional(readOnly = true)
  public Contenu contenu(long demandeId) {
    DocumentDelivre document = lire(demandeId);
    return new Contenu(document, stockage.lire(proprietes.compartiment(), document.cleStockage()));
  }

  static String sha256(byte[] contenu) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private TypeDocument type(DemandeResume demande) {
    return catalogue
        .pourDemande(demande.typeDocumentCode(), demande.versionDocumentId())
        .orElseThrow(
            () -> new ErreurMetier(CodeErreur.DEM_005, Map.of("code", demande.typeDocumentCode())));
  }

  private DemandeResume demande(long id) {
    return demandes
        .lire(id)
        .orElseThrow(() -> new ErreurMetier(CodeErreur.DEM_002, Map.of("id", id)));
  }

  private UtilisateurConnecte moi() {
    return utilisateurCourant
        .get()
        .orElseThrow(() -> new IllegalStateException("Requête authentifiée sans compte"));
  }
}
