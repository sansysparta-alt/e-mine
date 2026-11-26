package gn.gov.mines.emine.catalogue.infrastructure;

import static gn.gov.mines.emine.shared.jooq.Tables.PERMISSION;
import static gn.gov.mines.emine.shared.jooq.Tables.PROFIL;
import static gn.gov.mines.emine.shared.jooq.Tables.PROFIL_PERMISSION;
import static gn.gov.mines.emine.shared.jooq.Tables.TARIF;
import static gn.gov.mines.emine.shared.jooq.Tables.TITRE_SIGNATAIRE;
import static gn.gov.mines.emine.shared.jooq.Tables.TYPE_DOCUMENT;
import static gn.gov.mines.emine.shared.jooq.Tables.UTILISATEUR;
import static gn.gov.mines.emine.shared.jooq.Tables.VERSION_DOCUMENT;

import gn.gov.mines.emine.catalogue.domaine.RegleFiche;
import gn.gov.mines.emine.catalogue.domaine.VersionFiche;
import gn.gov.mines.emine.catalogue.domaine.VersionFiche.ContenuFiche;
import gn.gov.mines.emine.catalogue.domaine.VersionFiche.Profil;
import gn.gov.mines.emine.shared.jooq.tables.Utilisateur;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** Versions des fiches du catalogue (US-02.2, V18), lues et écrites sous la RLS de l'appelant. */
@Repository
public class VersionDocumentRepository {

  private static final Utilisateur PROPOSANT = UTILISATEUR.as("proposant");
  private static final Utilisateur DECIDEUR = UTILISATEUR.as("decideur");

  private final DSLContext dsl;

  public VersionDocumentRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** Identifiant du document de ce code (actif ou non). */
  public Optional<Short> typeDocument(String code) {
    return dsl.select(TYPE_DOCUMENT.ID)
        .from(TYPE_DOCUMENT)
        .where(TYPE_DOCUMENT.CODE.eq(code))
        .and(TYPE_DOCUMENT.DELETED_AT.isNull())
        .fetchOptional(TYPE_DOCUMENT.ID);
  }

  /** Versions d'un document, de la plus récente à la plus ancienne. */
  public List<VersionFiche> versions(short typeDocumentId) {
    Map<Short, Profil> profils = profils();
    return dsl.select(VERSION_DOCUMENT.asterisk(), nom(PROPOSANT), nom(DECIDEUR))
        .from(VERSION_DOCUMENT)
        .leftJoin(PROPOSANT)
        .on(PROPOSANT.ID.eq(VERSION_DOCUMENT.PROPOSEE_PAR))
        .leftJoin(DECIDEUR)
        .on(DECIDEUR.ID.eq(VERSION_DOCUMENT.DECIDEE_PAR))
        .where(VERSION_DOCUMENT.TYPE_DOCUMENT_ID.eq(typeDocumentId))
        .orderBy(VERSION_DOCUMENT.NUMERO.desc())
        .fetch(r -> version(r, profils));
  }

  /**
   * Par document : numéro de la version en vigueur et proposition ouverte (numéro, date), pour la
   * liste du catalogue.
   */
  public Map<String, Etat> etats() {
    Map<String, Etat> etats = new HashMap<>();
    dsl.select(
            TYPE_DOCUMENT.CODE,
            VERSION_DOCUMENT.NUMERO,
            VERSION_DOCUMENT.STATUT,
            VERSION_DOCUMENT.PROPOSEE_LE)
        .from(VERSION_DOCUMENT)
        .join(TYPE_DOCUMENT)
        .on(TYPE_DOCUMENT.ID.eq(VERSION_DOCUMENT.TYPE_DOCUMENT_ID))
        .where(VERSION_DOCUMENT.STATUT.in(VersionFiche.PUBLIEE, VersionFiche.A_VALIDER))
        .fetch()
        .forEach(
            r -> {
              Etat e = etats.getOrDefault(r.value1(), new Etat(0, null, null));
              etats.put(
                  r.value1(),
                  VersionFiche.PUBLIEE.equals(r.value3())
                      ? new Etat(r.value2(), e.numeroProposee(), e.proposeeLe())
                      : new Etat(e.numeroEnVigueur(), (int) r.value2(), r.value4()));
            });
    return etats;
  }

  /**
   * État des versions d'un document.
   *
   * @param numeroProposee proposition ouverte, {@code null} s'il n'y en a pas
   */
  public record Etat(int numeroEnVigueur, Integer numeroProposee, Instant proposeeLe) {}

  /** Nouvelle proposition ; numéro, date et profil signataire posés par la base. */
  public int proposer(
      short typeDocumentId,
      ContenuFiche c,
      java.time.LocalDate tarifsDateEffet,
      String motif,
      long auteurId) {
    Map<String, Short> idsProfils =
        dsl.select(PROFIL.CODE, PROFIL.ID).from(PROFIL).fetchMap(PROFIL.CODE, PROFIL.ID);
    Short[] avis = c.avis().stream().map(p -> idsProfils.get(p.code())).toArray(Short[]::new);
    return dsl.insertInto(VERSION_DOCUMENT)
        .set(VERSION_DOCUMENT.TYPE_DOCUMENT_ID, typeDocumentId)
        .set(VERSION_DOCUMENT.NUMERO, (short) 0)
        .set(VERSION_DOCUMENT.LIBELLE_FR, c.libelleFr())
        .set(VERSION_DOCUMENT.LIBELLE_EN, c.libelleEn())
        .set(VERSION_DOCUMENT.PIECES_CLES, RegleFiche.piecesCles(c.pieces()))
        .set(VERSION_DOCUMENT.DELAI_JOURS, (short) c.delaiJours())
        .set(VERSION_DOCUMENT.INSTRUIT_PAR, c.instruitPar())
        .set(
            VERSION_DOCUMENT.PIECES_CLES_EN,
            c.piecesEn().isEmpty() ? null : RegleFiche.piecesCles(c.piecesEn()))
        .set(VERSION_DOCUMENT.INSTRUIT_PAR_EN, c.instruitParEn())
        .set(VERSION_DOCUMENT.SIGNE_PAR, c.signataire())
        .set(
            VERSION_DOCUMENT.SIGNATAIRE_PROFIL_ID,
            DSL.select(TITRE_SIGNATAIRE.PROFIL_ID)
                .from(TITRE_SIGNATAIRE)
                .where(TITRE_SIGNATAIRE.LIBELLE.eq(c.signataire()))
                .<Short>asField())
        .set(VERSION_DOCUMENT.AVIS_PROFILS, avis)
        .set(VERSION_DOCUMENT.ACTIF, c.actif())
        .set(VERSION_DOCUMENT.FRAIS_DOSSIER_GNF, c.fraisDossierGnf())
        .set(VERSION_DOCUMENT.DROITS_GNF, c.droitsGnf())
        .set(VERSION_DOCUMENT.ATTESTATION_GNF, c.attestationGnf())
        .set(VERSION_DOCUMENT.TARIFS_DATE_EFFET, tarifsDateEffet)
        .set(VERSION_DOCUMENT.MOTIF, motif)
        .set(VERSION_DOCUMENT.PROPOSEE_PAR, auteurId)
        .returningResult(VERSION_DOCUMENT.NUMERO)
        .fetchOne(VERSION_DOCUMENT.NUMERO);
  }

  /**
   * Décision sur une proposition ouverte ({@code publiee}, {@code refusee} ou {@code retiree}) ;
   * auteur et date posés par la base, qui contrôle aussi les droits.
   *
   * @return {@code false} si la version n'attend plus de décision
   */
  public boolean decider(short typeDocumentId, int numero, String statut, String motifRefus) {
    return dsl.update(VERSION_DOCUMENT)
            .set(VERSION_DOCUMENT.STATUT, statut)
            .set(VERSION_DOCUMENT.MOTIF_REFUS, motifRefus)
            .where(VERSION_DOCUMENT.TYPE_DOCUMENT_ID.eq(typeDocumentId))
            .and(VERSION_DOCUMENT.NUMERO.eq((short) numero))
            .and(VERSION_DOCUMENT.STATUT.eq(VersionFiche.A_VALIDER))
            .execute()
        == 1;
  }

  /**
   * Tarif approuvé qui n'est pas encore en vigueur (US-02.3).
   *
   * @param objet {@code FRAIS_DOSSIER}, {@code DROITS} ou {@code ATTESTATION}
   */
  public record TarifAVenir(String objet, long montantGnf, java.time.LocalDate dateEffet) {}

  /** Tarifs approuvés d'un document dont la date d'effet n'est pas encore arrivée (Conakry). */
  public List<TarifAVenir> tarifsAVenir(short typeDocumentId) {
    return dsl.select(TARIF.OBJET, TARIF.MONTANT_GNF, TARIF.DATE_EFFET)
        .from(TARIF)
        .where(TARIF.TYPE_DOCUMENT_ID.eq(typeDocumentId))
        .and(TARIF.ACTIF.isTrue())
        .and(TARIF.DATE_EFFET.gt(java.time.LocalDate.now(java.time.ZoneId.of("Africa/Conakry"))))
        .orderBy(TARIF.DATE_EFFET, TARIF.OBJET)
        .fetch(r -> new TarifAVenir(r.value1().getLiteral(), r.value2(), r.value3()));
  }

  /** Titres de signataire possibles (D4), dans l'ordre. */
  public List<String> titresSignataires() {
    return dsl.select(TITRE_SIGNATAIRE.LIBELLE)
        .from(TITRE_SIGNATAIRE)
        .orderBy(TITRE_SIGNATAIRE.ORDRE)
        .fetch(TITRE_SIGNATAIRE.LIBELLE);
  }

  /** Profils qui peuvent rendre un avis technique : internes, qui instruisent, hors guichet. */
  public List<Profil> profilsAvis() {
    return dsl.selectDistinct(PROFIL.CODE, PROFIL.LIBELLE, PROFIL.NUMERO)
        .from(PROFIL)
        .join(PROFIL_PERMISSION)
        .on(PROFIL_PERMISSION.PROFIL_ID.eq(PROFIL.ID))
        .join(PERMISSION)
        .on(PERMISSION.ID.eq(PROFIL_PERMISSION.PERMISSION_ID))
        .where(PROFIL.EST_INTERNE.isTrue())
        .and(PROFIL.CODE.ne("GUICHET"))
        .and(PERMISSION.CODE.eq("INSTRUIRE"))
        .orderBy(PROFIL.NUMERO)
        .fetch(r -> new Profil(r.value1(), r.value2()));
  }

  private Map<Short, Profil> profils() {
    Map<Short, Profil> p = new HashMap<>();
    dsl.select(PROFIL.ID, PROFIL.CODE, PROFIL.LIBELLE)
        .from(PROFIL)
        .fetch()
        .forEach(r -> p.put(r.value1(), new Profil(r.value2(), r.value3())));
    return p;
  }

  private static Field<String> nom(Utilisateur u) {
    return DSL.trim(DSL.concat(DSL.coalesce(u.PRENOM, ""), DSL.val(" "), DSL.coalesce(u.NOM, "")))
        .as("nom_" + u.getName());
  }

  private static VersionFiche version(Record r, Map<Short, Profil> profils) {
    String proposant = r.get("nom_proposant", String.class);
    String decideur = r.get("nom_decideur", String.class);
    return new VersionFiche(
        r.get(VERSION_DOCUMENT.NUMERO),
        r.get(VERSION_DOCUMENT.STATUT),
        new ContenuFiche(
            r.get(VERSION_DOCUMENT.LIBELLE_FR),
            r.get(VERSION_DOCUMENT.LIBELLE_EN),
            TypeDocumentRepository.pieces(r.get(VERSION_DOCUMENT.PIECES_CLES)),
            r.get(VERSION_DOCUMENT.DELAI_JOURS),
            r.get(VERSION_DOCUMENT.INSTRUIT_PAR),
            r.get(VERSION_DOCUMENT.SIGNE_PAR),
            Arrays.stream(r.get(VERSION_DOCUMENT.AVIS_PROFILS)).map(profils::get).toList(),
            r.get(VERSION_DOCUMENT.ACTIF),
            r.get(VERSION_DOCUMENT.FRAIS_DOSSIER_GNF),
            r.get(VERSION_DOCUMENT.DROITS_GNF),
            r.get(VERSION_DOCUMENT.ATTESTATION_GNF),
            TypeDocumentRepository.pieces(r.get(VERSION_DOCUMENT.PIECES_CLES_EN)),
            r.get(VERSION_DOCUMENT.INSTRUIT_PAR_EN)),
        r.get(VERSION_DOCUMENT.MOTIF),
        proposant == null || proposant.isBlank() ? null : proposant,
        r.get(VERSION_DOCUMENT.PROPOSEE_LE),
        decideur == null || decideur.isBlank() ? null : decideur,
        r.get(VERSION_DOCUMENT.DECIDEE_LE),
        r.get(VERSION_DOCUMENT.MOTIF_REFUS),
        r.get(VERSION_DOCUMENT.TARIFS_DATE_EFFET));
  }
}
