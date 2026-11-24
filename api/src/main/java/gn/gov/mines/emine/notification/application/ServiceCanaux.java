package gn.gov.mines.emine.notification.application;

import gn.gov.mines.emine.notification.domaine.CanauxEvenement;
import gn.gov.mines.emine.notification.infrastructure.CanauxRepository;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Canaux des notifications (US-02.7) : choisis par l'administrateur, immédiat et tracé. */
@Service
public class ServiceCanaux {

  private final CanauxRepository canaux;

  public ServiceCanaux(CanauxRepository canaux) {
    this.canaux = canaux;
  }

  @Transactional(readOnly = true)
  public List<CanauxEvenement> lister() {
    return canaux.lister();
  }

  /**
   * Enregistre les choix : SYS-002 pour un événement inconnu, VAL-001 si l'espace en ligne est
   * coupé (il est obligatoire : l'usager y voit toujours son dossier, V24).
   */
  @Transactional
  public List<CanauxEvenement> enregistrer(List<CanauxEvenement> choix) {
    Set<String> connus =
        canaux.lister().stream().map(CanauxEvenement::modele).collect(Collectors.toSet());
    for (CanauxEvenement c : choix) {
      if (!connus.contains(c.modele())) {
        throw new ErreurMetier(CodeErreur.SYS_002, Map.of("modele", c.modele()));
      }
      if (!c.interne()) {
        throw new ErreurMetier(
            CodeErreur.VAL_001,
            Map.of(
                "champs",
                List.of(
                    Map.of(
                        "champ",
                        c.modele(),
                        "message",
                        "L'espace en ligne reste actif pour chaque événement."))));
      }
    }
    choix.forEach(canaux::enregistrer);
    return canaux.lister();
  }
}
