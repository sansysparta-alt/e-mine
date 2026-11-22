package gn.gov.mines.emine.demande.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.PASSAGE_DOSSIER;
import static gn.gov.mines.emine.shared.jooq.Tables.UTILISATEUR;

import gn.gov.mines.emine.demande.domaine.Passage;
import gn.gov.mines.emine.shared.jooq.Routines;
import gn.gov.mines.emine.shared.jooq.enums.StatutDemande;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/**
 * Passages d'un dossier dans le traitement en série (V11). Journal en ajout seul ; la RLS le
 * réserve aux profils internes et impose l'utilisateur connecté comme auteur.
 */
@Repository
public class PassageRepository {

  private final DSLContext dsl;

  public PassageRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public void ajouter(long demandeId, String statut, String motif) {
    dsl.insertInto(PASSAGE_DOSSIER)
        .set(PASSAGE_DOSSIER.DEMANDE_ID, demandeId)
        .set(PASSAGE_DOSSIER.AUTEUR_ID, Routines.fnCurrentUserId())
        .set(PASSAGE_DOSSIER.STATUT, StatutDemande.lookupLiteral(statut))
        .set(PASSAGE_DOSSIER.MOTIF, motif)
        .execute();
  }

  /** Tous les passages du dossier, du plus récent au plus ancien. */
  public List<Passage> lister(long demandeId) {
    return dsl.select(
            UTILISATEUR.PRENOM,
            UTILISATEUR.NOM,
            PASSAGE_DOSSIER.STATUT,
            PASSAGE_DOSSIER.MOTIF,
            PASSAGE_DOSSIER.CREATED_AT)
        .from(PASSAGE_DOSSIER)
        .join(UTILISATEUR)
        .on(UTILISATEUR.ID.eq(PASSAGE_DOSSIER.AUTEUR_ID))
        .where(PASSAGE_DOSSIER.DEMANDE_ID.eq(demandeId))
        .orderBy(PASSAGE_DOSSIER.CREATED_AT.desc(), PASSAGE_DOSSIER.ID.desc())
        .fetch(
            r ->
                new Passage(
                    r.value1() + " " + r.value2(),
                    r.value3().getLiteral(),
                    r.value4(),
                    r.value5()));
  }
}
