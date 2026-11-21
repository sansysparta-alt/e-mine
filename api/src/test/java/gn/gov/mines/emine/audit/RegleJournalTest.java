package gn.gov.mines.emine.audit;

import static org.assertj.core.api.Assertions.assertThat;

import gn.gov.mines.emine.audit.domaine.RegleJournal;
import gn.gov.mines.emine.audit.domaine.RegleJournal.Changement;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** US-11.7 : lecture du journal d'audit (champs modifiés, masquage, objet), sans base. */
class RegleJournalTest {

  @Test
  void uneModificationNeMontreQueLesChampsChangesSansLesChampsTechniques() {
    Map<String, String> avant =
        Map.of("statut", "deposee", "numero", "DEM-2026-000001", "updated_at", "t1");
    Map<String, String> apres =
        Map.of("statut", "recevable", "numero", "DEM-2026-000001", "updated_at", "t2");
    assertThat(RegleJournal.changements("U", avant, apres, false))
        .containsExactly(new Changement("statut", "deposee", "recevable"));
  }

  @Test
  void uneCreationNeDetailleSesValeursQueDansLaFiche() {
    Map<String, String> apres = new HashMap<>();
    apres.put("numero", "DEM-2026-000001");
    apres.put("motif_rejet", null);
    assertThat(RegleJournal.changements("I", null, apres, false)).isEmpty();
    assertThat(RegleJournal.changements("I", null, apres, true))
        .containsExactly(new Changement("numero", null, "DEM-2026-000001"));
  }

  @Test
  void lesSecretsEtIdentifiantsDeCompteSontMasques() {
    Map<String, String> apres = Map.of("email", "x@exemple.test", "keycloak_id", "uuid-reel");
    assertThat(RegleJournal.changements("I", null, apres, true))
        .contains(new Changement("keycloak_id", null, RegleJournal.VALEUR_MASQUEE))
        .noneMatch(c -> "uuid-reel".equals(c.apres()));
    assertThat(
            RegleJournal.changements(
                "U", Map.of("mot_de_passe_hash", "a"), Map.of("mot_de_passe_hash", "b"), false))
        .containsExactly(
            new Changement(
                "mot_de_passe_hash", RegleJournal.VALEUR_MASQUEE, RegleJournal.VALEUR_MASQUEE));
  }

  @Test
  void lObjetEtSaDemandeSontRetrouvesPourLiensEtLibelles() {
    assertThat(RegleJournal.libelleObjet(null, Map.of("reference", "PAY-2026-000004")))
        .isEqualTo("PAY-2026-000004");
    assertThat(RegleJournal.demandeId("demande", "12", null, Map.of())).isEqualTo(12L);
    assertThat(RegleJournal.demandeId("paiement", "uuid", null, Map.of("demande_id", "12")))
        .isEqualTo(12L);
    assertThat(RegleJournal.demandeId("utilisateur", "3", null, Map.of())).isNull();
    assertThat(RegleJournal.categorie("avis_decision")).isEqualTo("decisions");
    assertThat(RegleJournal.categorie("compteur")).isEqualTo("autres");
  }
}
