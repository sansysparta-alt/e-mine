package gn.gov.mines.emine.demande.application;

import gn.gov.mines.emine.demande.domaine.RegleDelais;
import gn.gov.mines.emine.demande.infrastructure.ParametresDelaiRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Règle des délais (US-02.6) : consultée et modifiée par l'administrateur, immédiat et tracé. */
@Service
public class ServiceParametresDelai {

  private final ParametresDelaiRepository parametres;

  public ServiceParametresDelai(ParametresDelaiRepository parametres) {
    this.parametres = parametres;
  }

  @Transactional(readOnly = true)
  public RegleDelais.Parametres lire() {
    return parametres.lire();
  }

  @Transactional
  public RegleDelais.Parametres enregistrer(RegleDelais.Parametres p) {
    parametres.enregistrer(p);
    return parametres.lire();
  }
}
