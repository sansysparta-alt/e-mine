package gn.gov.mines.emine.catalogue.infrastructure;

import java.util.List;

/** Accès de test aux méthodes de paquet du dépôt du catalogue. */
public final class TypeDocumentRepositoryTestAcces {

  private TypeDocumentRepositoryTestAcces() {}

  public static List<String> pieces(String piecesCles) {
    return TypeDocumentRepository.pieces(piecesCles);
  }
}
