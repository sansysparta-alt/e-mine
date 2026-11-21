package gn.gov.mines.emine.shared.stockage;

import java.io.InputStream;

/**
 * Port du stockage objet (D9 : MinIO par défaut, API S3). Sert aux pièces jointes (US-03.2) et aux
 * documents délivrés (US-06.4). Aucune suppression : les objets sont conservés (pas de suppression
 * physique des données).
 */
public interface StockageObjets {

  /** Dépose un objet ; le compartiment est créé s'il n'existe pas encore. */
  void deposer(String compartiment, String cle, byte[] contenu, String typeMime);

  /** Lit un objet ; l'appelant ferme le flux. */
  InputStream lire(String compartiment, String cle);
}
