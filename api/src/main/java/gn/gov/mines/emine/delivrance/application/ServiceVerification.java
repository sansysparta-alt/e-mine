package gn.gov.mines.emine.delivrance.application;

import gn.gov.mines.emine.catalogue.Catalogue;
import gn.gov.mines.emine.catalogue.TypeDocument;
import gn.gov.mines.emine.delivrance.domaine.DocumentDelivre;
import gn.gov.mines.emine.delivrance.infrastructure.DelivranceRepository;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vérification publique d'un document délivré (US-06.5), sans connexion : par le code du QR (128
 * bits aléatoires, impossible à deviner). Ne renvoie <b>aucune donnée personnelle</b> : ni
 * titulaire, ni demande, ni motif de révocation (il peut en contenir), seulement ce qui permet de
 * dire si le document est authentique et de comparer l'empreinte du fichier présenté.
 */
@Service
public class ServiceVerification {

  private static final Pattern CODE = Pattern.compile("^[0-9a-f]{32}$");

  private final DelivranceRepository registre;
  private final Catalogue catalogue;

  public ServiceVerification(DelivranceRepository registre, Catalogue catalogue) {
    this.registre = registre;
    this.catalogue = catalogue;
  }

  /**
   * Résultat public de la vérification.
   *
   * @param authentique document inscrit au registre et non révoqué
   * @param document libellé du document au catalogue
   * @param signataire fonction du signataire au catalogue (D4), jamais le nom d'une personne
   * @param empreinteSha256 empreinte du PDF délivré, pour comparer un fichier
   */
  public record Verification(
      boolean authentique,
      String numero,
      String codeType,
      String document,
      Instant delivreLe,
      String signataire,
      String empreinteSha256) {}

  /** DOC-007 si le code est mal formé ou inconnu (même réponse dans les deux cas). */
  @Transactional(readOnly = true)
  public Verification verifier(String code) {
    String normalise = code == null ? "" : code.strip().toLowerCase(java.util.Locale.ROOT);
    if (!CODE.matcher(normalise).matches()) {
      throw introuvable();
    }
    DocumentDelivre d = registre.parCode(normalise).orElseThrow(ServiceVerification::introuvable);
    TypeDocument type = catalogue.trouver(d.codeType()).orElse(null);
    return new Verification(
        d.valide(),
        d.numero(),
        d.codeType(),
        type == null ? d.codeType() : type.libelleFr(),
        d.delivreLe(),
        type == null ? null : type.signataire(),
        d.empreinteSha256());
  }

  private static ErreurMetier introuvable() {
    return new ErreurMetier(CodeErreur.DOC_007, Map.of());
  }
}
