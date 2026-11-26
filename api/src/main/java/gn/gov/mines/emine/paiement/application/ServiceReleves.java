package gn.gov.mines.emine.paiement.application;

import gn.gov.mines.emine.auth.UtilisateurCourant;
import gn.gov.mines.emine.paiement.domaine.LectureReleveCsv;
import gn.gov.mines.emine.paiement.domaine.LectureReleveCsv.LigneLue;
import gn.gov.mines.emine.paiement.infrastructure.ProprietesVirement;
import gn.gov.mines.emine.paiement.infrastructure.ReleveRepository;
import gn.gov.mines.emine.paiement.infrastructure.ReleveRepository.FiltreLignes;
import gn.gov.mines.emine.paiement.infrastructure.ReleveRepository.Ligne;
import gn.gov.mines.emine.paiement.infrastructure.ReleveRepository.Releve;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relevés bancaires (US-04.8) : le Responsable fiscal importe le relevé CSV du compte des virements
 * ; la base rapproche chaque ligne d'un paiement (référence et montant). Seul le cas conforme
 * confirme un paiement ; les écarts et les lignes inconnues attendent sa décision (US-04.9). Un
 * même fichier ne s'importe qu'une fois.
 */
@Service
public class ServiceReleves {

  private static final ZoneId CONAKRY = ZoneId.of("Africa/Conakry");
  private static final DateTimeFormatter JJ_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/uuuu");

  private final ReleveRepository releves;
  private final UtilisateurCourant utilisateurCourant;
  private final ProprietesVirement compte;
  private final Clock horloge;
  private final boolean exempleActif;

  public ServiceReleves(
      ReleveRepository releves,
      UtilisateurCourant utilisateurCourant,
      ProprietesVirement compte,
      Clock horloge,
      @Value("${emine.releves.exemple-actif:false}") boolean exempleActif) {
    this.releves = releves;
    this.utilisateurCourant = utilisateurCourant;
    this.compte = compte;
    this.horloge = horloge;
    this.exempleActif = exempleActif;
  }

  /**
   * Import d'un relevé CSV, puis rapprochement automatique. Banque par défaut : celle du compte des
   * virements ; date du relevé par défaut : la dernière date d'opération. PAY-011 si ce fichier a
   * déjà été importé ; VAL-001 (lignes citées) si son format ne respecte pas le contrat.
   */
  @Transactional
  public Releve importer(String banque, LocalDate dateReleve, String nomFichier, byte[] contenu) {
    if (contenu.length > LectureReleveCsv.TAILLE_MAX_OCTETS) {
      throw new ErreurMetier(
          CodeErreur.VAL_001,
          Map.of("champs", List.of(champ("fichier", "Le relevé dépasse 2 Mo."))));
    }
    String empreinte = sha256(contenu);
    if (releves.empreinteConnue(empreinte)) {
      throw new ErreurMetier(CodeErreur.PAY_011, Map.of("fichier", nom(nomFichier)));
    }
    LectureReleveCsv.Resultat lu = LectureReleveCsv.lire(contenu);
    if (!lu.valide()) {
      throw new ErreurMetier(
          CodeErreur.VAL_001,
          Map.of(
              "champs",
              lu.problemes().stream()
                  .map(
                      p ->
                          champ(
                              "fichier",
                              p.ligne() > 0
                                  ? "Ligne " + p.ligne() + " : " + p.message()
                                  : p.message()))
                  .toList()));
    }
    LocalDate date =
        dateReleve != null
            ? dateReleve
            : lu.lignes().stream().map(LigneLue::date).max(Comparator.naturalOrder()).orElseThrow();
    String nomBanque = banque == null || banque.isBlank() ? compte.banque() : banque.strip();
    long moi =
        utilisateurCourant.get().orElseThrow(() -> new ErreurMetier(CodeErreur.AUTH_001)).id();
    long id =
        releves.enregistrer(
            nomBanque.length() > 120 ? nomBanque.substring(0, 120) : nomBanque,
            date,
            nom(nomFichier),
            empreinte,
            moi,
            lu.lignes());
    releves.rapprocher(id);
    return releves.lire(id).orElseThrow();
  }

  @Transactional(readOnly = true)
  public PageResultat<Releve> lister(Pagination pagination) {
    return releves.lister(pagination);
  }

  @Transactional(readOnly = true)
  public Releve lire(long id) {
    return releves
        .lire(id)
        .orElseThrow(() -> new ErreurMetier(CodeErreur.PAY_012, Map.of("id", id)));
  }

  @Transactional(readOnly = true)
  public PageResultat<Ligne> lignes(long id, FiltreLignes filtre, Pagination pagination) {
    lire(id);
    return releves.lignes(id, filtre, pagination);
  }

  /** Le relevé d'exemple de démonstration est proposé (désactivé en recette et production). */
  public boolean exempleActif() {
    return exempleActif;
  }

  /**
   * Relevé d'exemple (démonstration) : une ligne conforme par virement déclaré en attente, plus une
   * ligne sans référence, au format du contrat. PAY-012 s'il est désactivé.
   */
  @Transactional(readOnly = true)
  public String exemple() {
    if (!exempleActif) {
      throw new ErreurMetier(CodeErreur.PAY_012, Map.of("releve", "exemple"));
    }
    String aujourdhui = LocalDate.now(horloge.withZone(CONAKRY)).format(JJ_MM_AAAA);
    StringBuilder csv = new StringBuilder("date_operation;libelle;montant_gnf;sens\n");
    for (ReleveRepository.VirementAttendu v : releves.virementsAttendus()) {
      String payeur = v.nomPayeur() == null ? "" : " " + v.nomPayeur().toUpperCase();
      csv.append(aujourdhui)
          .append(";VIR")
          .append(payeur.replace(";", " "))
          .append(' ')
          .append(v.reference())
          .append(';')
          .append(v.montantGnf())
          .append(";C\n");
    }
    csv.append(aujourdhui).append(";VIR SANS REFERENCE;250000;C\n");
    return csv.toString();
  }

  private static Map<String, String> champ(String champ, String message) {
    return Map.of("champ", champ, "message", message);
  }

  private static String nom(String nomFichier) {
    String n = nomFichier == null || nomFichier.isBlank() ? "releve.csv" : nomFichier.strip();
    n = n.replaceAll("[\\\\/]", "_");
    return n.length() > 200 ? n.substring(0, 200) : n;
  }

  private static String sha256(byte[] contenu) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
