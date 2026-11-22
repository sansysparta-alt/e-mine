package gn.gov.mines.emine.demande.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.AVIS_DECISION;
import static gn.gov.mines.emine.shared.jooq.Tables.COMPLEMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.DEMANDE;
import static gn.gov.mines.emine.shared.jooq.Tables.DOCUMENT_DELIVRE;
import static gn.gov.mines.emine.shared.jooq.Tables.HISTORIQUE_STATUT_DEMANDE;
import static gn.gov.mines.emine.shared.jooq.Tables.PAIEMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.TYPE_DOCUMENT;

import gn.gov.mines.emine.demande.domaine.RegleSuivi;
import gn.gov.mines.emine.shared.jooq.enums.NatureAvis;
import gn.gov.mines.emine.shared.jooq.enums.ObjetPaiement;
import gn.gov.mines.emine.shared.jooq.enums.StatutDemande;
import gn.gov.mines.emine.shared.jooq.enums.StatutPaiement;
import java.time.Instant;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * Données de la frise de suivi (US-07.3) : statut, paiements, recevabilité, décision, délivrance,
 * rejet et complément en cours d'une demande visible (RLS), lus en une requête.
 */
@Repository
public class SuiviRepository {

  private final DSLContext dsl;

  public SuiviRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * Situation d'une demande visible.
   *
   * @param typeDocumentCode code du document (droits à payer : catalogue)
   */
  public record Lecture(String typeDocumentCode, RegleSuivi.Situation situation) {}

  public Optional<Lecture> lire(long id, java.util.function.Predicate<String> droitsRequis) {
    Field<Instant> fraisLe = paiementLe(ObjetPaiement.FRAIS_DOSSIER, ObjetPaiement.ATTESTATION);
    Field<Instant> droitsLe = paiementLe(ObjetPaiement.DROITS);
    Field<Instant> decisionLe =
        DSL.field(
            DSL.select(DSL.max(AVIS_DECISION.RENDU_LE))
                .from(AVIS_DECISION)
                .where(AVIS_DECISION.DEMANDE_ID.eq(DEMANDE.ID))
                .and(AVIS_DECISION.NATURE.eq(NatureAvis.decision)));
    Field<Instant> delivreLe =
        DSL.field(
            DSL.select(DSL.max(DOCUMENT_DELIVRE.DELIVRE_LE))
                .from(DOCUMENT_DELIVRE)
                .where(DOCUMENT_DELIVRE.DEMANDE_ID.eq(DEMANDE.ID)));
    Field<Instant> rejetLe =
        DSL.field(
            DSL.select(DSL.max(HISTORIQUE_STATUT_DEMANDE.CREATED_AT))
                .from(HISTORIQUE_STATUT_DEMANDE)
                .where(HISTORIQUE_STATUT_DEMANDE.DEMANDE_ID.eq(DEMANDE.ID))
                .and(HISTORIQUE_STATUT_DEMANDE.NOUVEAU_STATUT.eq(StatutDemande.rejetee)));
    Field<StatutDemande> origine =
        DSL.field(
            DSL.select(COMPLEMENT.STATUT_ORIGINE)
                .from(COMPLEMENT)
                .where(COMPLEMENT.DEMANDE_ID.eq(DEMANDE.ID))
                .and(COMPLEMENT.REPONDU_LE.isNull())
                .and(COMPLEMENT.CLOS_LE.isNull()));
    return dsl.select(
            DEMANDE.STATUT,
            DEMANDE.DATE_DEPOT,
            DEMANDE.DATE_RECEVABILITE,
            TYPE_DOCUMENT.CODE,
            DemandeRepository.FRAIS_PAYES,
            fraisLe,
            droitsLe,
            decisionLe,
            delivreLe,
            rejetLe,
            origine)
        .from(DEMANDE)
        .join(TYPE_DOCUMENT)
        .on(TYPE_DOCUMENT.ID.eq(DEMANDE.TYPE_DOCUMENT_ID))
        .where(DEMANDE.ID.eq(id))
        .and(DEMANDE.DELETED_AT.isNull())
        .fetchOptional(
            r -> {
              String code = r.get(TYPE_DOCUMENT.CODE);
              StatutDemande o = r.get(origine);
              return new Lecture(
                  code,
                  new RegleSuivi.Situation(
                      r.get(DEMANDE.STATUT).getLiteral(),
                      Boolean.TRUE.equals(r.get(DemandeRepository.FRAIS_PAYES)),
                      droitsRequis.test(code),
                      o == null ? null : o.getLiteral(),
                      new RegleSuivi.Dates(
                          r.get(DEMANDE.DATE_DEPOT),
                          r.get(fraisLe),
                          r.get(DEMANDE.DATE_RECEVABILITE),
                          r.get(decisionLe),
                          r.get(droitsLe),
                          r.get(delivreLe),
                          r.get(rejetLe))));
            });
  }

  /** Date du paiement (payé) le plus récent de ces objets pour la demande. */
  private static Field<Instant> paiementLe(ObjetPaiement... objets) {
    return DSL.field(
        DSL.select(DSL.max(PAIEMENT.DATE_PAIEMENT))
            .from(PAIEMENT)
            .where(PAIEMENT.DEMANDE_ID.eq(DEMANDE.ID))
            .and(PAIEMENT.OBJET.in(objets))
            .and(PAIEMENT.STATUT.eq(StatutPaiement.paye)));
  }
}
