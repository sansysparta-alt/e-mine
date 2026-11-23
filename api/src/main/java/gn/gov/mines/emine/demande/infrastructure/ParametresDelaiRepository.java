package gn.gov.mines.emine.demande.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.PARAMETRE_DELAI;

import gn.gov.mines.emine.demande.domaine.RegleDelais;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/** Règle des délais (table {@code parametre_delai}, une ligne ; V17, V21). */
@Repository
public class ParametresDelaiRepository {

  private final DSLContext dsl;

  public ParametresDelaiRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public RegleDelais.Parametres lire() {
    var p = dsl.selectFrom(PARAMETRE_DELAI).fetchSingle();
    return new RegleDelais.Parametres(
        p.getDepart(),
        p.getPauseComplement(),
        p.getPauseDroits(),
        p.getSeuilProchePourcent(),
        p.getSeuilProcheMinJours());
  }

  /** Modifie la règle ; la base contrôle les droits et trace l'ancienne et la nouvelle valeur. */
  public void enregistrer(RegleDelais.Parametres p) {
    dsl.update(PARAMETRE_DELAI)
        .set(PARAMETRE_DELAI.DEPART, p.depart())
        .set(PARAMETRE_DELAI.PAUSE_COMPLEMENT, p.pauseComplement())
        .set(PARAMETRE_DELAI.PAUSE_DROITS, p.pauseDroits())
        .set(PARAMETRE_DELAI.SEUIL_PROCHE_POURCENT, (short) p.seuilProchePourcent())
        .set(PARAMETRE_DELAI.SEUIL_PROCHE_MIN_JOURS, (short) p.seuilProcheMinJours())
        .execute();
  }
}
