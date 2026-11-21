package gn.gov.mines.emine.demande.api;

import gn.gov.mines.emine.demande.application.ServicePieces;
import gn.gov.mines.emine.demande.domaine.Piece;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Pièces jointes d'une demande (US-03.2) : ajout, liste, téléchargement, retrait. */
@RestController
@RequestMapping("/api/v1/demandes/{demandeId}/pieces")
@Tag(name = "Demandes")
class PieceController {

  private final ServicePieces service;

  PieceController(ServicePieces service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PERM_DEPOSER')")
  @Operation(
      operationId = "ajouterPiece",
      summary = "Joindre une pièce (PDF, JPG ou PNG, 10 Mo au plus)")
  PieceDto ajouter(
      @PathVariable long demandeId,
      @RequestParam("typePiece") String typePiece,
      @RequestPart("fichier") MultipartFile fichier)
      throws IOException {
    if (typePiece == null || typePiece.isBlank() || typePiece.length() > 200) {
      throw new ErreurMetier(
          CodeErreur.VAL_001,
          Map.of(
              "champs", List.of(Map.of("champ", "typePiece", "message", "Type de pièce requis."))));
    }
    return PieceDto.depuis(
        service.ajouter(demandeId, typePiece, fichier.getOriginalFilename(), fichier.getBytes()));
  }

  @GetMapping
  @Operation(operationId = "listerPieces", summary = "Pièces jointes de la demande")
  List<PieceDto> lister(@PathVariable long demandeId) {
    return service.lister(demandeId).stream().map(PieceDto::depuis).toList();
  }

  @GetMapping(value = "/{pieceId}/contenu", produces = MediaType.ALL_VALUE)
  @Operation(
      operationId = "telechargerPiece",
      summary = "Télécharger une pièce jointe (ou l'afficher dans le visualiseur : inline=true)")
  ResponseEntity<InputStreamResource> telecharger(
      @PathVariable long demandeId,
      @PathVariable long pieceId,
      @RequestParam(defaultValue = "false") boolean inline) {
    ServicePieces.Contenu contenu = service.contenu(demandeId, pieceId);
    Piece p = contenu.piece();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(p.typeMime()))
        .contentLength(p.tailleOctets())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(p.nomFichier(), java.nio.charset.StandardCharsets.UTF_8)
                .build()
                .toString())
        .header("X-Content-Type-Options", "nosniff")
        .body(new InputStreamResource(contenu.flux()));
  }

  @DeleteMapping("/{pieceId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('PERM_DEPOSER')")
  @Operation(operationId = "retirerPiece", summary = "Retirer une pièce d'un brouillon")
  void retirer(@PathVariable long demandeId, @PathVariable long pieceId) {
    service.retirer(demandeId, pieceId);
  }

  /** Pièce jointe telle que publiée par l'API (sans la clé de stockage). */
  record PieceDto(
      long id,
      String typePiece,
      String nomFichier,
      String typeMime,
      long tailleOctets,
      String empreinteSha256,
      Instant deposeLe) {

    static PieceDto depuis(Piece p) {
      return new PieceDto(
          p.id(),
          p.typePiece(),
          p.nomFichier(),
          p.typeMime(),
          p.tailleOctets(),
          p.empreinteSha256(),
          p.deposeLe());
    }
  }
}
