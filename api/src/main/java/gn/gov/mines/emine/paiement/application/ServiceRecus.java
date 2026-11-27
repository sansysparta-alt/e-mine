package gn.gov.mines.emine.paiement.application;

import gn.gov.mines.emine.catalogue.Catalogue;
import gn.gov.mines.emine.demande.Demandes;
import gn.gov.mines.emine.demande.Demandes.DemandeResume;
import gn.gov.mines.emine.paiement.domaine.MontantEnLettres;
import gn.gov.mines.emine.paiement.domaine.Paiement;
import gn.gov.mines.emine.paiement.domaine.Recu;
import gn.gov.mines.emine.paiement.infrastructure.GenerateurRecuPdf;
import gn.gov.mines.emine.paiement.infrastructure.PaiementRepository;
import gn.gov.mines.emine.paiement.infrastructure.ProprietesRecus;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.jooq.enums.StatutPaiement;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reçu de paiement (US-04.6) : PDF simple, sans signature en V1, disponible dès que le paiement est
 * confirmé par la notification signée. La RLS limite le reçu au demandeur et aux agents.
 */
@Service
@EnableConfigurationProperties(ProprietesRecus.class)
public class ServiceRecus {

  private final PaiementRepository paiements;
  private final Demandes demandes;
  private final Catalogue catalogue;
  private final GenerateurRecuPdf pdf;
  private final ProprietesRecus proprietes;

  public ServiceRecus(
      PaiementRepository paiements,
      Demandes demandes,
      Catalogue catalogue,
      GenerateurRecuPdf pdf,
      ProprietesRecus proprietes) {
    this.paiements = paiements;
    this.demandes = demandes;
    this.catalogue = catalogue;
    this.pdf = pdf;
    this.proprietes = proprietes;
  }

  /** Reçu prêt à télécharger : numéro (nom du fichier) et contenu PDF. */
  public record RecuPdf(String numero, byte[] contenu) {}

  @Transactional(readOnly = true)
  public RecuPdf recu(String reference) {
    Paiement p =
        paiements
            .lireParReference(reference)
            .orElseThrow(
                () -> new ErreurMetier(CodeErreur.PAY_005, Map.of("reference", reference)));
    if (p.statut() != StatutPaiement.paye || p.recuNumero() == null) {
      throw new ErreurMetier(
          CodeErreur.PAY_009, Map.of("reference", reference, "statut", p.statut().getLiteral()));
    }
    DemandeResume demande =
        demandes
            .lire(p.demandeId())
            .orElseThrow(() -> new ErreurMetier(CodeErreur.DEM_002, Map.of("id", p.demandeId())));
    String document =
        catalogue
            .pourDemande(demande.typeDocumentCode(), demande.versionDocumentId())
            .map(t -> t.libelleFr() + " (" + t.code() + ")")
            .orElse(demande.typeDocumentCode());
    long montant = p.montantRecuGnf() != null ? p.montantRecuGnf() : p.montantGnf();
    boolean tresorPay = p.moyen().getLiteral().equals("TRESORPAY");
    String code =
        paiements
            .codeVerification(reference)
            .orElseThrow(
                () -> new ErreurMetier(CodeErreur.PAY_005, Map.of("reference", reference)));
    Recu recu =
        new Recu(
            p.recuNumero(),
            p.reference(),
            p.datePaiement(),
            montant,
            MontantEnLettres.gnf(montant),
            libelle(p),
            tresorPay ? "TrésorPay" : "Virement bancaire",
            p.transactionExterneId(),
            demande.numero(),
            document,
            demande.demandeurNom(),
            demande.organisation(),
            tresorPay,
            proprietes.urlVerification(code));
    return new RecuPdf(p.recuNumero(), pdf.generer(recu));
  }

  /**
   * Objet du reçu ; un complément renvoie au paiement initial, et le reçu du paiement initial au
   * complément qui l'a soldé (US-04.12).
   */
  private String libelle(Paiement p) {
    if (p.complementDe() != null) {
      return libelleObjet(p)
          + " — complément du paiement "
          + paiements.reference(p.complementDe()).orElse("");
    }
    return paiements
        .complementPaye(p.id())
        .map(c -> libelleObjet(p) + " — montant total, soldé par le complément " + c)
        .orElse(libelleObjet(p));
  }

  private static String libelleObjet(Paiement p) {
    return switch (p.objet()) {
      case FRAIS_DOSSIER -> "Frais de dossier";
      case DROITS -> "Droits";
      case ATTESTATION -> "Attestation (paiement unique)";
      case FORMATION -> "Formation";
    };
  }
}
