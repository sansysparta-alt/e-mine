-- e-mine · V12 — Compléments demandés au demandeur (US-05.2, choix du porteur du projet du
-- 22/11/2026). Un agent qui instruit demande un complément (motif, pièces attendues) ; le demandeur
-- a 5 jours pour répondre depuis son suivi ; il est relancé à J+3 ; le délai de traitement du
-- catalogue est suspendu pendant le complément. Sans réponse à J+5, rien n'est rejeté
-- automatiquement : le dossier est signalé à l'agent, qui relance ou clôt avec un motif.
-- Ajouts au schéma fourni, à signaler au Ministère.

-- 1. Transitions : le complément rend la demande à l'étape d'où il a été demandé (le schéma fourni
--    ne prévoyait que le retour à « déposée ») ; clôture sans réponse = rejet motivé par un agent.
INSERT INTO transition_statut (entite, de, vers) VALUES
  ('demande', 'complement_requis', 'en_instruction'),
  ('demande', 'complement_requis', 'rejetee')
ON CONFLICT DO NOTHING;

-- 2. Compléments : un seul ouvert à la fois par demande ; historique conservé.
CREATE TABLE complement (
  id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  demande_id        bigint NOT NULL REFERENCES demande(id),
  demande_par       bigint NOT NULL REFERENCES utilisateur(id),
  statut_origine    statut_demande NOT NULL CHECK (statut_origine IN ('deposee', 'en_instruction')),
  motif             text NOT NULL CHECK (char_length(btrim(motif)) BETWEEN 10 AND 2000),
  pieces_demandees  text[] NOT NULL DEFAULT '{}',
  demande_le        timestamptz NOT NULL DEFAULT now(),
  date_limite       timestamptz NOT NULL,
  relance_le        timestamptz,
  repondu_le        timestamptz,
  repondu_par       bigint REFERENCES utilisateur(id),
  reponse           text CHECK (reponse IS NULL OR char_length(reponse) <= 2000),
  clos_le           timestamptz,
  clos_par          bigint REFERENCES utilisateur(id),
  CHECK (date_limite > demande_le),
  CHECK ((repondu_le IS NULL) = (repondu_par IS NULL)),
  CHECK ((clos_le IS NULL) = (clos_par IS NULL)),
  CHECK (repondu_le IS NULL OR clos_le IS NULL)
);

CREATE UNIQUE INDEX complement_un_seul_ouvert ON complement (demande_id)
  WHERE repondu_le IS NULL AND clos_le IS NULL;
CREATE INDEX complement_demande_idx ON complement (demande_id, demande_le);

COMMENT ON TABLE complement IS
  'Complément demandé au demandeur : motif, pièces attendues, date limite (5 jours), relance, réponse';

-- Délai de réponse (5 jours) posé par la base ; colonnes figées après la demande ; l'usager ne peut
-- que répondre (réponse, date, auteur), jamais modifier la demande de complément.
CREATE FUNCTION fn_complement_controle() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF TG_OP = 'INSERT' THEN
    NEW.date_limite := NEW.demande_le + interval '5 days';
    RETURN NEW;
  END IF;
  IF NEW.demande_id <> OLD.demande_id OR NEW.demande_par <> OLD.demande_par
     OR NEW.statut_origine <> OLD.statut_origine OR NEW.motif <> OLD.motif
     OR NEW.pieces_demandees <> OLD.pieces_demandees OR NEW.demande_le <> OLD.demande_le THEN
    RAISE EXCEPTION 'Complément % : la demande de complément ne se modifie pas', OLD.id
      USING ERRCODE = 'P0001', HINT = 'DEM-001';
  END IF;
  IF OLD.repondu_le IS NOT NULL OR OLD.clos_le IS NOT NULL THEN
    RAISE EXCEPTION 'Complément % déjà traité', OLD.id USING ERRCODE = 'P0001', HINT = 'DEM-001';
  END IF;
  -- L'usager ne fait que répondre ; la relance automatique s'exécute en propriétaire du schéma.
  IF NOT fn_est_interne() AND current_user <> 'emine_owner'
     AND (NEW.date_limite IS DISTINCT FROM OLD.date_limite
          OR NEW.relance_le IS DISTINCT FROM OLD.relance_le
          OR NEW.clos_le IS DISTINCT FROM OLD.clos_le
          OR NEW.repondu_par IS DISTINCT FROM fn_current_user_id()) THEN
    RAISE EXCEPTION 'Complément % : seul l''agent relance ou clôt', OLD.id
      USING ERRCODE = 'P0001', HINT = 'PERM-001';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER trg_complement_controle BEFORE INSERT OR UPDATE ON complement
  FOR EACH ROW EXECUTE FUNCTION fn_complement_controle();
CREATE TRIGGER trg_complement_audit AFTER INSERT OR UPDATE ON complement
  FOR EACH ROW EXECUTE FUNCTION fn_audit();
CREATE TRIGGER trg_complement_nodelete BEFORE DELETE ON complement
  FOR EACH ROW EXECUTE FUNCTION fn_interdire_modification();

-- Notification au demandeur (envoi réel par l'outbox, US-07.1).
CREATE FUNCTION fn_complement_notifier() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  INSERT INTO notification (destinataire_id, canal, modele, demande_id, contenu)
  SELECT d.demandeur_id, 'email', 'COMPLEMENT_DEMANDE', d.id,
         jsonb_build_object('numero', d.numero, 'dateLimite', NEW.date_limite,
                            'pieces', to_jsonb(NEW.pieces_demandees))
    FROM demande d WHERE d.id = NEW.demande_id;
  RETURN NULL;
END $$;

CREATE TRIGGER trg_complement_notifier AFTER INSERT ON complement
  FOR EACH ROW EXECUTE FUNCTION fn_complement_notifier();

-- RLS : l'agent voit et gère tout ; l'usager voit les compléments de ses demandes et y répond.
ALTER TABLE complement ENABLE ROW LEVEL SECURITY;
CREATE POLICY complement_lecture_app ON complement FOR SELECT TO emine_app
  USING (fn_est_interne()
         OR EXISTS (SELECT 1 FROM demande d
                     WHERE d.id = complement.demande_id AND d.demandeur_id = fn_current_user_id()));
CREATE POLICY complement_creation ON complement FOR INSERT TO emine_app
  WITH CHECK (fn_est_interne() AND demande_par = fn_current_user_id());
CREATE POLICY complement_maj ON complement FOR UPDATE TO emine_app
  USING (fn_est_interne()
         OR EXISTS (SELECT 1 FROM demande d
                     WHERE d.id = complement.demande_id AND d.demandeur_id = fn_current_user_id()))
  WITH CHECK (fn_est_interne()
         OR EXISTS (SELECT 1 FROM demande d
                     WHERE d.id = complement.demande_id AND d.demandeur_id = fn_current_user_id()));
CREATE POLICY complement_lecture ON complement FOR SELECT TO emine_readonly USING (true);
GRANT SELECT ON complement TO emine_readonly;

-- 3. Suspension du délai de traitement : jours calendaires (heure de Conakry) passés en complément
--    jusqu'à un instant donné. Lue sous la RLS de l'appelant.
CREATE FUNCTION fn_jours_suspendus(p_demande bigint, p_jusqua timestamptz) RETURNS integer
LANGUAGE sql STABLE AS
$$
  SELECT COALESCE(sum(
           (LEAST(COALESCE(c.repondu_le, c.clos_le, p_jusqua), p_jusqua) AT TIME ZONE 'Africa/Conakry')::date
           - (c.demande_le AT TIME ZONE 'Africa/Conakry')::date), 0)::integer
    FROM complement c
   WHERE c.demande_id = p_demande AND c.demande_le <= p_jusqua
$$;

-- 4. Relance automatique à J+3 (tâche planifiée de l'API) : idempotente, une seule relance
--    automatique par complément. SECURITY DEFINER limitée à cette mise à jour, sans paramètre.
CREATE FUNCTION fn_relancer_complements() RETURNS integer
LANGUAGE plpgsql SECURITY DEFINER SET search_path = emine, pg_temp AS
$$
DECLARE
  n integer;
BEGIN
  WITH a_relancer AS (
    UPDATE complement c SET relance_le = now()
     WHERE c.repondu_le IS NULL AND c.clos_le IS NULL AND c.relance_le IS NULL
       AND now() >= c.demande_le + interval '3 days'
     RETURNING c.demande_id, c.date_limite)
  INSERT INTO notification (destinataire_id, canal, modele, demande_id, contenu)
  SELECT d.demandeur_id, 'email', 'COMPLEMENT_RELANCE', d.id,
         jsonb_build_object('numero', d.numero, 'dateLimite', a.date_limite)
    FROM a_relancer a JOIN demande d ON d.id = a.demande_id;
  GET DIAGNOSTICS n = ROW_COUNT;
  RETURN n;
END $$;

REVOKE ALL ON FUNCTION fn_relancer_complements() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_relancer_complements() TO emine_app;
