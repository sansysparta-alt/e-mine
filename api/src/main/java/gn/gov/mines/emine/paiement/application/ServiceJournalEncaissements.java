package gn.gov.mines.emine.paiement.application;

import gn.gov.mines.emine.paiement.infrastructure.JournalEncaissementsRepository;
import gn.gov.mines.emine.paiement.infrastructure.JournalEncaissementsRepository.Critere;
import gn.gov.mines.emine.paiement.infrastructure.JournalEncaissementsRepository.Ligne;
import gn.gov.mines.emine.paiement.infrastructure.JournalEncaissementsRepository.Totaux;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Journal des encaissements (US-04.11) et indicateurs du Responsable fiscal : recettes,
 * remboursements de frais et solde pour une période, export CSV identique à l'écran. Les chiffres
 * sont calculés ici, jamais recomptés à l'écran.
 */
@Service
public class ServiceJournalEncaissements {

  private static final ZoneId CONAKRY = ZoneId.of("Africa/Conakry");
  private static final DateTimeFormatter JJ_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/uuuu");

  private final JournalEncaissementsRepository journal;
  private final ServiceEcarts ecarts;
  private final ServiceRemboursements remboursements;
  private final Clock horloge;

  public ServiceJournalEncaissements(
      JournalEncaissementsRepository journal,
      ServiceEcarts ecarts,
      ServiceRemboursements remboursements,
      Clock horloge) {
    this.journal = journal;
    this.ecarts = ecarts;
    this.remboursements = remboursements;
    this.horloge = horloge;
  }

  /** Une page du journal et les totaux des mêmes critères. */
  public record Page(PageResultat<Ligne> page, Totaux totaux) {}

  @Transactional(readOnly = true)
  public Page lister(Critere critere, Pagination pagination) {
    verifier(critere);
    return new Page(journal.lister(critere, pagination), journal.totaux(critere));
  }

  /**
   * Export CSV (UTF-8 avec marque d'ordre des octets, séparateur « ; », lisible par un tableur) :
   * les mêmes lignes que l'écran, dans l'ordre chronologique, puis le total.
   */
  @Transactional(readOnly = true)
  public void exporter(Critere critere, Writer sortie) {
    verifier(critere);
    try {
      sortie.write('﻿');
      sortie.write(
          "date;type;reference;recu_ou_ordre;demande;document;objet;moyen;payeur;montant_gnf\n");
      journal.parcourir(
          critere,
          l -> {
            try {
              sortie.write(
                  String.join(
                          ";",
                          l.date().atZone(CONAKRY).toLocalDate().format(JJ_MM_AAAA),
                          l.type(),
                          l.reference(),
                          csv(l.piece()),
                          csv(l.numeroDemande()),
                          csv(l.codeDocument()),
                          l.objet(),
                          l.moyen(),
                          csv(l.payeur()),
                          Long.toString(l.montantGnf()))
                      + "\n");
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          });
      Totaux t = journal.totaux(critere);
      sortie.write(";total;;;;;;;;" + t.soldeGnf() + "\n");
      sortie.flush();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Indicateurs du tableau de bord du Responsable fiscal : ce qu'il doit faire (à ce jour) et les
   * recettes du mois en cours (heure de Conakry), mêmes règles que les listes qu'ils ouvrent.
   */
  public record Indicateurs(
      long aVerifier,
      long remboursementsAValider,
      long ordresAEnregistrer,
      Totaux mois,
      LocalDate debutMois,
      LocalDate finMois) {}

  @Transactional(readOnly = true)
  public Indicateurs indicateurs() {
    LocalDate aujourdhui = LocalDate.now(horloge.withZone(CONAKRY));
    LocalDate debut = aujourdhui.withDayOfMonth(1);
    LocalDate fin = aujourdhui.withDayOfMonth(aujourdhui.lengthOfMonth());
    var r = remboursements.lister(null, Pagination.de(1, 10)).compteurs();
    return new Indicateurs(
        ecarts.compter(),
        r.get("demande"),
        r.get("valide"),
        journal.totaux(new Critere(debut, fin, null, null)),
        debut,
        fin);
  }

  private static String csv(String valeur) {
    if (valeur == null) {
      return "";
    }
    String v = valeur.replace("\"", "\"\"");
    return v.contains(";") || v.contains("\"") || v.contains("\n") ? "\"" + v + "\"" : v;
  }

  private static void verifier(Critere c) {
    if (c.du() != null && c.au() != null && c.au().isBefore(c.du())) {
      throw new ErreurMetier(
          CodeErreur.VAL_001,
          Map.of(
              "champs",
              List.of(
                  Map.of("champ", "au", "message", "La fin de la période précède son début."))));
    }
  }
}
