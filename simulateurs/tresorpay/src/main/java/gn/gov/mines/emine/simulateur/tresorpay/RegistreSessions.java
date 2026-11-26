package gn.gov.mines.emine.simulateur.tresorpay;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;

/**
 * Sessions en mémoire (le simulateur n'a pas de base) et interrupteur de disponibilité. Les 200
 * sessions les plus récentes sont gardées.
 */
@Component
public class RegistreSessions {

  private static final int MAX_SESSIONS = 200;

  private final Map<String, SessionPaiement> sessions = new ConcurrentHashMap<>();
  private final AtomicBoolean disponible = new AtomicBoolean(true);

  public void enregistrer(SessionPaiement session) {
    sessions.put(session.id(), session);
    if (sessions.size() > MAX_SESSIONS) {
      recentes(sessions.size()).stream().skip(MAX_SESSIONS).forEach(s -> sessions.remove(s.id()));
    }
  }

  public Optional<SessionPaiement> trouver(String id) {
    return Optional.ofNullable(id).map(sessions::get);
  }

  /** Met à jour une session de façon atomique ; renvoie la nouvelle valeur. */
  public Optional<SessionPaiement> modifier(String id, UnaryOperator<SessionPaiement> maj) {
    return Optional.ofNullable(sessions.computeIfPresent(id, (k, s) -> maj.apply(s)));
  }

  /** Session la plus récente d'une référence e-mine (interrogation, contrat §5). */
  public java.util.Optional<SessionPaiement> derniereDe(String reference) {
    return sessions.values().stream()
        .filter(s -> s.reference().equals(reference))
        .max(java.util.Comparator.comparing(SessionPaiement::creeeLe));
  }

  public List<SessionPaiement> recentes(int nombre) {
    return sessions.values().stream()
        .sorted(Comparator.comparing(SessionPaiement::creeeLe).reversed())
        .limit(nombre)
        .toList();
  }

  public boolean disponible() {
    return disponible.get();
  }

  public void definirDisponible(boolean valeur) {
    disponible.set(valeur);
  }
}
