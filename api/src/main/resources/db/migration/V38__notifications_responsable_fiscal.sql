-- =====================================================================
-- V38 — Notifications du Responsable fiscal (US-04.17, R9 de la recette du 27/11/2026)
-- =====================================================================
-- Jusqu'ici, aucune notification n'était adressée à un agent ; la cloche n'avait donc rien à
-- montrer au Responsable fiscal. Il est désormais prévenu quand le Directeur décide d'un
-- remboursement qu'il a proposé (US-04.18) : validé (l'ordre du Trésor est à demander puis à
-- enregistrer) ou refusé (avec le motif). Pas de SMS par défaut (coût réel pour le Ministère) ;
-- canaux réglables comme les autres événements.

INSERT INTO notification_evenement (modele, libelle_fr, libelle_en, sms, email, interne) VALUES
  ('REMBOURSEMENT_VALIDE', 'Remboursement validé par le Directeur, ordre du Trésor à enregistrer',
   'Refund approved by the Director, Treasury order to record', false, true, true),
  ('REMBOURSEMENT_REFUSE', 'Remboursement refusé par le Directeur',
   'Refund refused by the Director', false, true, true);

CREATE FUNCTION fn_remboursement_notifier_auteur() RETURNS trigger LANGUAGE plpgsql AS
$$
DECLARE
  p paiement%ROWTYPE;
BEGIN
  -- Seul un auteur qui encaisse est prévenu (une proposition née de la règle sur rejet d'une
  -- demande a pour auteur le signataire qui a rejeté : elle ne le concerne plus).
  IF NOT EXISTS (SELECT 1 FROM utilisateur u
                   JOIN profil_permission pp ON pp.profil_id = u.profil_id
                   JOIN permission pm ON pm.id = pp.permission_id
                  WHERE u.id = NEW.demande_par AND pm.code = 'ENCAISSER') THEN
    RETURN NULL;
  END IF;
  SELECT * INTO p FROM paiement WHERE id = NEW.paiement_id;
  PERFORM fn_notifier(NEW.demande_par,
                      CASE NEW.statut WHEN 'valide' THEN 'REMBOURSEMENT_VALIDE'
                                      ELSE 'REMBOURSEMENT_REFUSE' END,
                      p.demande_id, p.id,
                      jsonb_build_object('numero', NEW.numero,
                                         'reference', p.reference,
                                         'montant_gnf', NEW.montant_gnf,
                                         'motif', COALESCE(NEW.motif_refus, '')));
  RETURN NULL;
END $$;

CREATE TRIGGER trg_remboursement_notifier_auteur AFTER UPDATE OF statut ON remboursement
  FOR EACH ROW WHEN (OLD.statut = 'demande' AND NEW.statut IN ('valide', 'refuse'))
  EXECUTE FUNCTION fn_remboursement_notifier_auteur();
