package gn.gov.mines.emine.audit.domaine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Lecture du journal d'audit (US-11.7) : catégories proposées à l'administrateur, champs modifiés
 * d'une ligne (avant / après), champs masqués et libellé de l'objet concerné. Le journal est écrit
 * par les triggers de la base (non modifiable) ; ces règles ne servent qu'à le rendre lisible.
 */
public final class RegleJournal {

  /** Catégories d'objets et tables correspondantes, dans l'ordre d'affichage. */
  public static final Map<String, List<String>> CATEGORIES;

  static {
    Map<String, List<String>> c = new LinkedHashMap<>();
    c.put("demandes", List.of("demande"));
    c.put("paiements", List.of("paiement"));
    c.put("decisions", List.of("avis_decision"));
    c.put("pieces", List.of("piece_jointe"));
    c.put("comptes", List.of("utilisateur", "organisation"));
    c.put("catalogue", List.of("type_document", "version_document", "type_document_avis", "tarif"));
    CATEGORIES = java.util.Collections.unmodifiableMap(c);
  }

  /** Champs techniques sans intérêt pour la lecture (posés à chaque mise à jour). */
  static final Set<String> IGNORES = Set.of("updated_at", "version");

  /** Champs dont la valeur n'est jamais affichée (secrets, identifiants techniques de compte). */
  static final Set<String> MASQUES = Set.of("mot_de_passe_hash", "keycloak_id");

  /** Valeur affichée à la place d'un champ masqué. */
  public static final String VALEUR_MASQUEE = "••••••";

  /** Clés de l'objet lisible par un humain, par ordre de préférence. */
  private static final List<String> CLES_LIBELLE =
      List.of("numero", "reference", "recu_numero", "nom_fichier", "email", "code", "nif");

  private RegleJournal() {}

  /** Tables d'une catégorie, ou vide si elle est inconnue. */
  public static Optional<List<String>> tables(String categorie) {
    return Optional.ofNullable(CATEGORIES.get(categorie));
  }

  /** Catégorie d'une table, ou {@code autres}. */
  public static String categorie(String table) {
    return CATEGORIES.entrySet().stream()
        .filter(e -> e.getValue().contains(table))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse("autres");
  }

  /**
   * Champs à montrer : pour une modification, ceux dont la valeur a changé ; pour une création (ou
   * une suppression), toutes les valeurs renseignées si {@code complet}, aucune sinon. Les champs
   * masqués gardent leur nom mais jamais leur valeur.
   */
  public static List<Changement> changements(
      String operation, Map<String, String> avant, Map<String, String> apres, boolean complet) {
    Map<String, String> a = avant == null ? Map.of() : avant;
    Map<String, String> b = apres == null ? Map.of() : apres;
    boolean modification = "U".equals(operation);
    if (!modification && !complet) {
      return List.of();
    }
    Set<String> champs = new LinkedHashSet<>(b.keySet());
    champs.addAll(a.keySet());
    List<Changement> resultat = new ArrayList<>();
    for (String champ : champs) {
      if (IGNORES.contains(champ)) {
        continue;
      }
      String va = a.get(champ);
      String vb = b.get(champ);
      if (modification ? Objects.equals(va, vb) : va == null && vb == null) {
        continue;
      }
      if (MASQUES.contains(champ)) {
        resultat.add(
            new Changement(
                champ, va == null ? null : VALEUR_MASQUEE, vb == null ? null : VALEUR_MASQUEE));
      } else {
        resultat.add(new Changement(champ, va, vb));
      }
    }
    return resultat;
  }

  /** Libellé lisible de l'objet (numéro de demande, référence de paiement…), ou {@code null}. */
  public static String libelleObjet(Map<String, String> avant, Map<String, String> apres) {
    for (String cle : CLES_LIBELLE) {
      for (Map<String, String> valeurs : java.util.Arrays.asList(apres, avant)) {
        if (valeurs != null && valeurs.get(cle) != null) {
          return valeurs.get(cle);
        }
      }
    }
    return null;
  }

  /**
   * Demande concernée par la ligne (la demande elle-même, ou celle d'un paiement, d'une pièce…).
   */
  public static Long demandeId(
      String table, String objetId, Map<String, String> avant, Map<String, String> apres) {
    String valeur =
        "demande".equals(table)
            ? objetId
            : Optional.ofNullable(apres)
                .map(m -> m.get("demande_id"))
                .orElse(avant == null ? null : avant.get("demande_id"));
    if (valeur == null) {
      return null;
    }
    try {
      return Long.valueOf(valeur);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** Un champ et ses valeurs avant / après ({@code null} si absente). */
  public record Changement(String champ, String avant, String apres) {}
}
