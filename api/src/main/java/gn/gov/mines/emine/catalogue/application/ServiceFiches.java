package gn.gov.mines.emine.catalogue.application;

import gn.gov.mines.emine.auth.Permission;
import gn.gov.mines.emine.auth.UtilisateurConnecte;
import gn.gov.mines.emine.auth.UtilisateurCourant;
import gn.gov.mines.emine.catalogue.TypeDocument;
import gn.gov.mines.emine.catalogue.domaine.RegleFiche;
import gn.gov.mines.emine.catalogue.domaine.RegleFiche.Action;
import gn.gov.mines.emine.catalogue.domaine.RegleFiche.Changement;
import gn.gov.mines.emine.catalogue.domaine.VersionFiche;
import gn.gov.mines.emine.catalogue.domaine.VersionFiche.ContenuFiche;
import gn.gov.mines.emine.catalogue.domaine.VersionFiche.Profil;
import gn.gov.mines.emine.catalogue.infrastructure.TypeDocumentRepository;
import gn.gov.mines.emine.catalogue.infrastructure.VersionDocumentRepository;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administration des fiches du catalogue (US-02.2, US-02.4) : l'administrateur propose une version,
 * le Ministre l'approuve ou la refuse ; la base applique la version approuvée et trace tout.
 */
@Service
public class ServiceFiches {

  private final TypeDocumentRepository typesDocuments;
  private final VersionDocumentRepository versions;
  private final UtilisateurCourant utilisateurCourant;

  public ServiceFiches(
      TypeDocumentRepository typesDocuments,
      VersionDocumentRepository versions,
      UtilisateurCourant utilisateurCourant) {
    this.typesDocuments = typesDocuments;
    this.versions = versions;
    this.utilisateurCourant = utilisateurCourant;
  }

  /** Filtres de la liste du catalogue (administration). */
  public enum Filtre {
    TOUS,
    A_VALIDER,
    INACTIFS
  }

  /**
   * Ligne de la liste du catalogue.
   *
   * @param numeroProposee proposition en attente du Ministre, {@code null} s'il n'y en a pas
   * @param action action attendue du profil connecté sur cette proposition
   */
  public record LigneFiche(
      TypeDocument document,
      int numeroEnVigueur,
      Integer numeroProposee,
      Instant proposeeLe,
      Action action) {}

  /**
   * Page de la liste et nombre de documents de chaque filtre (pour la recherche donnée).
   *
   * @param compteurs nombre de documents par filtre
   */
  public record Liste(PageResultat<LigneFiche> page, Map<Filtre, Long> compteurs) {}

  @Transactional(readOnly = true)
  public Liste lister(Filtre filtre, String recherche, Pagination pagination) {
    UtilisateurConnecte moi = gestionnaire();
    Map<String, VersionDocumentRepository.Etat> etats = versions.etats();
    Predicate<TypeDocument> correspond = recherche(recherche);
    List<LigneFiche> lignes =
        typesDocuments.listerTous().stream()
            .filter(correspond)
            .map(
                t -> {
                  var e =
                      etats.getOrDefault(
                          t.code(), new VersionDocumentRepository.Etat(0, null, null));
                  return new LigneFiche(
                      t,
                      e.numeroEnVigueur(),
                      e.numeroProposee(),
                      e.proposeeLe(),
                      e.numeroProposee() == null ? Action.AUCUNE : action(moi));
                })
            .toList();
    Map<Filtre, Long> compteurs = new EnumMap<>(Filtre.class);
    for (Filtre f : Filtre.values()) {
      compteurs.put(f, lignes.stream().filter(l -> dans(l, f)).count());
    }
    List<LigneFiche> filtrees =
        lignes.stream().filter(l -> dans(l, filtre == null ? Filtre.TOUS : filtre)).toList();
    int debut = Math.min(pagination.decalage(), filtrees.size());
    int fin = Math.min(debut + pagination.taille(), filtrees.size());
    return new Liste(
        new PageResultat<>(
            filtrees.subList(debut, fin), pagination.page(), pagination.taille(), filtrees.size()),
        compteurs);
  }

  /**
   * Fiche d'un document et ses versions.
   *
   * @param document fiche en vigueur
   * @param enVigueur version en vigueur
   * @param proposition proposition en attente du Ministre, {@code null} s'il n'y en a pas
   * @param changements changements de la proposition par rapport à la fiche en vigueur
   * @param historique toutes les versions, de la plus récente à la plus ancienne, chacune avec ses
   *     changements par rapport à la version en vigueur avant elle
   * @param action action attendue du profil connecté sur la proposition
   * @param peutProposer le profil connecté peut proposer une modification maintenant
   * @param tarifsAVenir tarifs approuvés pas encore en vigueur (US-02.3)
   */
  public record Fiche(
      TypeDocument document,
      VersionFiche enVigueur,
      VersionFiche proposition,
      List<Changement> changements,
      List<VersionAvecChangements> historique,
      Action action,
      boolean peutProposer,
      List<VersionDocumentRepository.TarifAVenir> tarifsAVenir) {}

  /** Version et ses changements par rapport à la version en vigueur avant elle. */
  public record VersionAvecChangements(VersionFiche version, List<Changement> changements) {}

  @Transactional(readOnly = true)
  public Fiche lire(String code) {
    UtilisateurConnecte moi = gestionnaire();
    TypeDocument document =
        typesDocuments.listerTous().stream()
            .filter(t -> t.code().equals(code))
            .findFirst()
            .orElseThrow(() -> new ErreurMetier(CodeErreur.SYS_002, Map.of("code", code)));
    List<VersionFiche> toutes = versions.versions(document.id());
    VersionFiche enVigueur =
        toutes.stream()
            .filter(v -> VersionFiche.PUBLIEE.equals(v.statut()))
            .findFirst()
            .orElseThrow(() -> new ErreurMetier(CodeErreur.SYS_002, Map.of("code", code)));
    VersionFiche proposition =
        toutes.stream()
            .filter(v -> VersionFiche.A_VALIDER.equals(v.statut()))
            .findFirst()
            .orElse(null);
    List<VersionAvecChangements> historique =
        toutes.stream()
            .map(v -> new VersionAvecChangements(v, changementsDepuisPrecedente(v, toutes)))
            .toList();
    return new Fiche(
        document,
        enVigueur,
        proposition,
        proposition == null
            ? List.of()
            : RegleFiche.changements(enVigueur.contenu(), proposition.contenu()),
        historique,
        proposition == null ? Action.AUCUNE : action(moi),
        moi.peut(Permission.PARAMETRER) && proposition == null,
        versions.tarifsAVenir(document.id()));
  }

  /**
   * Saisie d'une proposition (avis en codes de profil).
   *
   * @param pieces pièces requises, une par ligne
   * @param tarifsDateEffet entrée en vigueur des montants, requise s'ils changent (US-02.3)
   */
  public record Proposition(
      String libelleFr,
      String libelleEn,
      List<String> pieces,
      int delaiJours,
      String instruitPar,
      String signataire,
      List<String> avis,
      boolean actif,
      Long fraisDossierGnf,
      Long droitsGnf,
      Long attestationGnf,
      LocalDate tarifsDateEffet,
      String motif,
      List<String> piecesEn,
      String instruitParEn) {}

  /**
   * Propose une nouvelle version de la fiche (administrateur) : DOC-008 si une proposition attend
   * déjà, DOC-010 si rien ne change, VAL-001 si un champ est invalide.
   *
   * @return numéro de la version proposée
   */
  @Transactional
  public int proposer(String code, Proposition p) {
    UtilisateurConnecte moi = gestionnaire();
    if (!moi.peut(Permission.PARAMETRER)) {
      throw new ErreurMetier(CodeErreur.PERM_001);
    }
    Fiche fiche = lire(code);
    if (fiche.proposition() != null) {
      throw new ErreurMetier(
          CodeErreur.DOC_008, Map.of("code", code, "numero", fiche.proposition().numero()));
    }
    List<String> pieces =
        RegleFiche.pieces(p.pieces())
            .orElseThrow(
                () ->
                    champInvalide(
                        "pieces",
                        "De 1 à 20 pièces, sans virgule, de 2 à 200 caractères chacune."));
    List<String> piecesEn =
        RegleFiche.piecesEn(p.piecesEn(), pieces.size())
            .orElseThrow(
                () ->
                    champInvalide(
                        "piecesEn",
                        "Traduisez chaque pièce (sans virgule, 2 à 200 caractères), ou aucune."));
    if (!versions.titresSignataires().contains(p.signataire())) {
      throw champInvalide("signataire", "Signataire inconnu du catalogue.");
    }
    Map<String, Profil> possibles =
        versions.profilsAvis().stream()
            .collect(java.util.stream.Collectors.toMap(Profil::code, x -> x));
    List<String> codesAvis = p.avis() == null ? List.of() : p.avis();
    if (!possibles.keySet().containsAll(codesAvis)
        || codesAvis.stream().distinct().count() != codesAvis.size()) {
      throw champInvalide(
          "avis", "Seuls les profils qui instruisent rendent un avis, une fois chacun.");
    }
    ContenuFiche contenu =
        new ContenuFiche(
            p.libelleFr().strip(),
            p.libelleEn() == null || p.libelleEn().isBlank() ? null : p.libelleEn().strip(),
            pieces,
            p.delaiJours(),
            p.instruitPar().strip(),
            p.signataire(),
            codesAvis.stream().map(possibles::get).toList(),
            p.actif(),
            p.fraisDossierGnf(),
            p.droitsGnf(),
            p.attestationGnf(),
            piecesEn,
            p.instruitParEn() == null || p.instruitParEn().isBlank()
                ? null
                : p.instruitParEn().strip());
    ContenuFiche actuel = fiche.enVigueur().contenu();
    if (!RegleFiche.memesObjets(actuel, contenu)) {
      throw champInvalide(
          "tarifs",
          "Les objets payés pour ce document (frais, droits, attestation) ne changent pas.");
    }
    boolean tarifsChangent = RegleFiche.changeTarifs(actuel, contenu);
    if (tarifsChangent
        && (p.tarifsDateEffet() == null || p.tarifsDateEffet().isBefore(aujourdhui()))) {
      throw champInvalide(
          "tarifsDateEffet", "Date d'entrée en vigueur : aujourd'hui ou plus tard.");
    }
    if (RegleFiche.changements(actuel, contenu).isEmpty()) {
      throw new ErreurMetier(CodeErreur.DOC_010, Map.of("code", code));
    }
    return versions.proposer(
        fiche.document().id(),
        contenu,
        tarifsChangent ? p.tarifsDateEffet() : null,
        p.motif().strip(),
        moi.id());
  }

  /** Le Ministre approuve la proposition : la base l'applique à la fiche. */
  @Transactional
  public void approuver(String code, int numero) {
    exiger(Permission.VALIDER_CATALOGUE);
    decider(code, numero, VersionFiche.PUBLIEE, null);
  }

  /** Le Ministre refuse la proposition, avec un motif. */
  @Transactional
  public void refuser(String code, int numero, String motif) {
    exiger(Permission.VALIDER_CATALOGUE);
    decider(code, numero, VersionFiche.REFUSEE, motif.strip());
  }

  /** L'administrateur retire sa proposition tant qu'elle n'est pas décidée. */
  @Transactional
  public void retirer(String code, int numero) {
    exiger(Permission.PARAMETRER);
    decider(code, numero, VersionFiche.RETIREE, null);
  }

  private void decider(String code, int numero, String statut, String motif) {
    short id =
        versions
            .typeDocument(code)
            .orElseThrow(() -> new ErreurMetier(CodeErreur.SYS_002, Map.of("code", code)));
    if (!versions.decider(id, numero, statut, motif)) {
      throw new ErreurMetier(CodeErreur.DOC_009, Map.of("code", code, "numero", numero));
    }
  }

  /** Titres de signataire et profils d'avis possibles (formulaire de proposition). */
  public record References(List<String> signataires, List<Profil> profilsAvis) {}

  @Transactional(readOnly = true)
  public References references() {
    gestionnaire();
    return new References(versions.titresSignataires(), versions.profilsAvis());
  }

  private static List<Changement> changementsDepuisPrecedente(
      VersionFiche v, List<VersionFiche> toutes) {
    // Référence : la dernière version publiée avant celle-ci (la v1 n'a pas de référence).
    return toutes.stream()
        .filter(a -> a.numero() < v.numero() && a.aEteEnVigueur())
        .filter(a -> a.decideeLe() != null && !a.decideeLe().isAfter(v.proposeeLe()))
        .findFirst()
        .map(a -> RegleFiche.changements(a.contenu(), v.contenu()))
        .orElse(List.of());
  }

  private static LocalDate aujourdhui() {
    return LocalDate.now(ZoneId.of("Africa/Conakry"));
  }

  private static ErreurMetier champInvalide(String champ, String message) {
    return new ErreurMetier(
        CodeErreur.VAL_001, Map.of("champs", List.of(Map.of("champ", champ, "message", message))));
  }

  private static boolean dans(LigneFiche l, Filtre f) {
    return switch (f) {
      case TOUS -> true;
      case A_VALIDER -> l.numeroProposee() != null;
      case INACTIFS -> !l.document().actif();
    };
  }

  private static Predicate<TypeDocument> recherche(String q) {
    if (q == null || q.isBlank()) {
      return t -> true;
    }
    String r = sansAccents(q);
    return t -> sansAccents(t.code() + " " + t.libelleFr()).contains(r);
  }

  private static String sansAccents(String s) {
    return Normalizer.normalize(s, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.FRENCH)
        .strip();
  }

  private static Action action(UtilisateurConnecte moi) {
    return RegleFiche.action(
        moi.peut(Permission.VALIDER_CATALOGUE), moi.peut(Permission.PARAMETRER));
  }

  private void exiger(Permission p) {
    if (!gestionnaire().peut(p)) {
      throw new ErreurMetier(CodeErreur.PERM_001);
    }
  }

  /** Administrateur ou Ministre : les seuls à consulter l'administration du catalogue. */
  private UtilisateurConnecte gestionnaire() {
    UtilisateurConnecte moi =
        utilisateurCourant.get().orElseThrow(() -> new ErreurMetier(CodeErreur.AUTH_001));
    if (!moi.peut(Permission.PARAMETRER) && !moi.peut(Permission.VALIDER_CATALOGUE)) {
      throw new ErreurMetier(CodeErreur.PERM_001);
    }
    return moi;
  }
}
