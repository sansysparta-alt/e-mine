package gn.gov.mines.emine.catalogue;

import gn.gov.mines.emine.catalogue.infrastructure.TypeDocumentRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lecture du catalogue des documents (US-02.1). API publique du module {@code catalogue}. */
@Service
public class Catalogue {

  private final TypeDocumentRepository typesDocuments;

  public Catalogue(TypeDocumentRepository typesDocuments) {
    this.typesDocuments = typesDocuments;
  }

  /** Les documents actifs, triés par famille puis par libellé. */
  @Transactional(readOnly = true)
  public List<TypeDocument> lister() {
    return typesDocuments.listerActifs();
  }

  /** Document actif de ce code (PEX, COR…), s'il existe. */
  @Transactional(readOnly = true)
  public Optional<TypeDocument> trouverActif(String code) {
    return typesDocuments.listerActifs().stream().filter(t -> t.code().equals(code)).findFirst();
  }

  /** Document de ce code, actif ou retiré du catalogue (registre, vérification publique). */
  @Transactional(readOnly = true)
  public Optional<TypeDocument> trouver(String code) {
    return typesDocuments.listerTous().stream().filter(t -> t.code().equals(code)).findFirst();
  }

  /**
   * Le document tel qu'il s'applique à une demande (US-02.2) : selon la fiche en vigueur à son
   * dépôt (délai, avis requis, signataire), même si la fiche a changé ou si le document a été
   * retiré du catalogue depuis ; selon la fiche actuelle pour un brouillon.
   *
   * @param versionDocumentId version de la fiche au dépôt, {@code null} pour un brouillon
   */
  @Transactional(readOnly = true)
  public Optional<TypeDocument> pourDemande(String code, Long versionDocumentId) {
    return versionDocumentId == null
        ? trouverActif(code)
        : typesDocuments.selonVersion(versionDocumentId);
  }

  /** Document actif de cet identifiant, s'il existe. */
  @Transactional(readOnly = true)
  public Optional<TypeDocument> trouverActif(short id) {
    return typesDocuments.listerActifs().stream().filter(t -> t.id() == id).findFirst();
  }
}
