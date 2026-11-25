package gn.gov.mines.emine.paiement.api;

import gn.gov.mines.emine.paiement.application.ServicePaiementEnLigne;
import gn.gov.mines.emine.paiement.application.ServicePaiements;
import gn.gov.mines.emine.paiement.domaine.Paiement;
import gn.gov.mines.emine.paiement.infrastructure.PaiementRepository;
import gn.gov.mines.emine.shared.jooq.enums.MoyenPaiement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Paiements d'une demande (US-04.2) : ce qui est à payer, historique, choix du moyen. Payer exige
 * le droit « payer » (US-01.6) ; la lecture est filtrée par la RLS.
 */
@RestController
@Validated
@Tag(name = "Paiements")
class PaiementController {

  private final ServicePaiements service;
  private final ServicePaiementEnLigne enLigne;

  PaiementController(ServicePaiements service, ServicePaiementEnLigne enLigne) {
    this.service = service;
    this.enLigne = enLigne;
  }

  @GetMapping("/api/v1/demandes/{id}/paiements/a-payer")
  @Operation(
      operationId = "lirePaiementAttendu",
      summary = "Ce qui est à payer pour la demande (montant du barème, moyens proposés)")
  APayerDto aPayer(@PathVariable long id) {
    ServicePaiements.APayer a = service.aPayer(id);
    return new APayerDto(
        a.objet().map(o -> o.objet().getLiteral()).orElse(null),
        a.objet().map(o -> o.montantGnf()).orElse(null),
        a.moyens().stream().map(m -> new MoyenDto(m.moyen().getLiteral(), m.disponible())).toList(),
        a.paiementOuvert().map(Paiement::reference).orElse(null),
        a.paiementOuvert().map(p -> p.moyen().getLiteral()).orElse(null),
        a.tentativesRestantes());
  }

  @GetMapping("/api/v1/demandes/{id}/paiements")
  @Operation(
      operationId = "listerPaiementsDemande",
      summary = "Paiements de la demande, du plus récent au plus ancien")
  List<PaiementDto> lister(@PathVariable long id) {
    return service.lister(id).stream().map(PaiementDto::depuis).toList();
  }

  @PostMapping("/api/v1/demandes/{id}/paiements")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PERM_PAYER')")
  @Operation(
      operationId = "choisirMoyenPaiement",
      summary =
          "Choisir le moyen de paiement : crée le paiement au montant du barème (référence"
              + " PAY-AAAA-NNNNNN), annule un paiement encore ouvert pour le même objet et, pour"
              + " TrésorPay, ouvre la session de paiement (3 tentatives par jour)")
  InitiationDto choisir(@PathVariable long id, @Valid @RequestBody ChoixMoyenDto corps) {
    ServicePaiementEnLigne.Initiation i = enLigne.payer(id, MoyenPaiement.valueOf(corps.moyen()));
    return new InitiationDto(PaiementDto.depuis(i.paiement()), i.urlPaiement());
  }

  /**
   * Paiement créé et, pour un paiement en ligne, adresse de la page TrésorPay vers laquelle
   * rediriger le navigateur (session de 15 minutes).
   */
  record InitiationDto(PaiementDto paiement, @Schema(nullable = true) String urlPaiement) {}

  @GetMapping("/api/v1/paiements")
  @Operation(
      operationId = "listerMesPaiements",
      summary =
          "Paiements du profil connecté (les siens pour un usager), du plus récent au plus ancien,"
              + " paginés, hors paiements annulés, avec le numéro de la demande ; recherche"
              + " tolérante, filtres par statut, objet et période, compteurs et total payé")
  PageMesPaiementsDto listerVisibles(
      @org.springframework.web.bind.annotation.RequestParam(required = false) Integer page,
      @org.springframework.web.bind.annotation.RequestParam(required = false) Integer taille,
      @org.springframework.web.bind.annotation.RequestParam(required = false) String recherche,
      @io.swagger.v3.oas.annotations.Parameter(
              description = "payes, en-cours, echoues ou rembourses ; absent : tous")
          @org.springframework.web.bind.annotation.RequestParam(required = false)
          String statut,
      @io.swagger.v3.oas.annotations.Parameter(
              description = "FRAIS_DOSSIER, DROITS, FORMATION ou ATTESTATION ; absent : tous")
          @org.springframework.web.bind.annotation.RequestParam(required = false)
          String objet,
      @org.springframework.web.bind.annotation.RequestParam(required = false)
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
          java.time.LocalDate du,
      @org.springframework.web.bind.annotation.RequestParam(required = false)
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
          java.time.LocalDate au) {
    var resultat =
        service.listerVisibles(
            gn.gov.mines.emine.shared.pagination.Pagination.de(page, taille),
            new PaiementRepository.Critere(recherche, groupe(statut), objet(objet), du, au));
    var p = resultat.page();
    var c = resultat.compteurs();
    return new PageMesPaiementsDto(
        p.elements().stream()
            .map(
                l ->
                    new LignePaiementDto(
                        PaiementDto.depuis(l.paiement()),
                        l.paiement().demandeId(),
                        l.numeroDemande(),
                        l.codeDocument()))
            .toList(),
        p.page(),
        p.taille(),
        p.total(),
        p.totalPages(),
        new CompteursPaiementsDto(
            c.tous(), c.payes(), c.enCours(), c.echoues(), c.rembourses(), c.totalPayeGnf()));
  }

  private static PaiementRepository.GroupeStatut groupe(String valeur) {
    if (valeur == null || valeur.isBlank()) {
      return null;
    }
    return switch (valeur) {
      case "payes" -> PaiementRepository.GroupeStatut.PAYES;
      case "en-cours" -> PaiementRepository.GroupeStatut.EN_COURS;
      case "echoues" -> PaiementRepository.GroupeStatut.ECHOUES;
      case "rembourses" -> PaiementRepository.GroupeStatut.REMBOURSES;
      default -> throw valeurInconnue("statut", "payes, en-cours, echoues ou rembourses");
    };
  }

  private static gn.gov.mines.emine.shared.jooq.enums.ObjetPaiement objet(String valeur) {
    if (valeur == null || valeur.isBlank()) {
      return null;
    }
    for (var o : gn.gov.mines.emine.shared.jooq.enums.ObjetPaiement.values()) {
      if (o.getLiteral().equals(valeur)) {
        return o;
      }
    }
    throw valeurInconnue("objet", "FRAIS_DOSSIER, DROITS, FORMATION ou ATTESTATION");
  }

  private static gn.gov.mines.emine.shared.erreur.ErreurMetier valeurInconnue(
      String champ, String attendu) {
    return new gn.gov.mines.emine.shared.erreur.ErreurMetier(
        gn.gov.mines.emine.shared.erreur.CodeErreur.VAL_001,
        java.util.Map.of(
            "champs",
            List.of(java.util.Map.of("champ", champ, "message", "Valeur attendue : " + attendu))));
  }

  /** Un paiement, avec la demande (identifiant et numéro) et le document concernés. */
  record LignePaiementDto(
      PaiementDto paiement,
      long demandeId,
      @io.swagger.v3.oas.annotations.media.Schema(nullable = true) String numeroDemande,
      String codeDocument) {}

  /**
   * Compteurs des filtres de statut (ils suivent la recherche, l'objet et la période) et total payé
   * (GNF entiers) pour ces mêmes critères.
   */
  record CompteursPaiementsDto(
      long tous, long payes, long enCours, long echoues, long rembourses, long totalPayeGnf) {}

  record PageMesPaiementsDto(
      List<LignePaiementDto> elements,
      int page,
      int taille,
      long total,
      int totalPages,
      CompteursPaiementsDto compteurs) {}

  @GetMapping("/api/v1/paiements/{reference}")
  @Operation(operationId = "lirePaiement", summary = "Un paiement, par sa référence")
  PaiementDto lire(@PathVariable @Pattern(regexp = "^PAY-[0-9]{4}-[0-9]{6}$") String reference) {
    return PaiementDto.depuis(service.lireParReference(reference));
  }

  /** Choix du moyen : aucun n'est présélectionné, le montant n'est jamais transmis. */
  record ChoixMoyenDto(@NotNull @Pattern(regexp = "^(TRESORPAY|VIREMENT)$") String moyen) {}

  /** Moyen proposé à l'écran. */
  record MoyenDto(String moyen, boolean disponible) {}

  /**
   * Paiement attendu.
   *
   * @param objet objet attendu, {@code null} si rien n'est à payer à ce stade (ou déjà payé)
   * @param montantGnf montant du barème, en GNF entiers
   * @param referenceOuverte paiement en cours pour cet objet (initié ou en attente), s'il existe
   * @param tentativesRestantes paiements en ligne encore possibles aujourd'hui (3 par jour)
   */
  record APayerDto(
      @Schema(nullable = true) String objet,
      @Schema(nullable = true) Long montantGnf,
      List<MoyenDto> moyens,
      @Schema(nullable = true) String referenceOuverte,
      @Schema(nullable = true) String moyenOuvert,
      int tentativesRestantes) {}
}
