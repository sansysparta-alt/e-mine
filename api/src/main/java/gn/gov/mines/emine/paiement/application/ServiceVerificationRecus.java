package gn.gov.mines.emine.paiement.application;

import gn.gov.mines.emine.paiement.infrastructure.PaiementRepository;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vérification publique d'un reçu (US-04.14), sans connexion, par le code de son QR (128 bits
 * aléatoires, impossible à deviner). Ne renvoie <b>aucune donnée personnelle</b> : ni payeur, ni
 * organisation, ni transaction ; seulement ce qui permet de comparer le reçu présenté.
 */
@Service
public class ServiceVerificationRecus {

  private static final Pattern CODE = Pattern.compile("^[0-9a-f]{32}$");

  private final PaiementRepository paiements;

  public ServiceVerificationRecus(PaiementRepository paiements) {
    this.paiements = paiements;
  }

  /**
   * Reçu authentique.
   *
   * @param objet {@code FRAIS_DOSSIER}, {@code DROITS}, {@code ATTESTATION} ou {@code FORMATION}
   * @param moyen {@code TRESORPAY} ou {@code VIREMENT} (un virement n'a que la date du relevé)
   * @param rembourse la somme a été rendue par le Trésor depuis
   */
  public record VerificationRecu(
      String numero,
      String objet,
      String moyen,
      long montantGnf,
      Instant payeLe,
      String demandeNumero,
      boolean rembourse) {}

  /** PAY-017 si le code est mal formé ou inconnu (même réponse dans les deux cas). */
  @Transactional(readOnly = true)
  public VerificationRecu verifier(String code) {
    String normalise = code == null ? "" : code.strip().toLowerCase(Locale.ROOT);
    if (!CODE.matcher(normalise).matches()) {
      throw introuvable();
    }
    return paiements
        .parCodeRecu(normalise)
        .map(
            r ->
                new VerificationRecu(
                    r.getRecuNumero(),
                    r.getObjet().getLiteral(),
                    r.getMoyen().getLiteral(),
                    r.getMontantGnf(),
                    r.getDatePaiement(),
                    r.getDemandeNumero(),
                    Boolean.TRUE.equals(r.getRembourse())))
        .orElseThrow(ServiceVerificationRecus::introuvable);
  }

  private static ErreurMetier introuvable() {
    return new ErreurMetier(CodeErreur.PAY_017, Map.of());
  }
}
