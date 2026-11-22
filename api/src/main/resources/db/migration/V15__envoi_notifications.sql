-- US-07.1 : envoi fiable des notifications (SMS et e-mail).
--
-- La table `notification` sert de boîte d'envoi (outbox) : les triggers y écrivent dans la même
-- transaction que le changement métier, une tâche planifiée (db-scheduler) envoie ensuite. Aucun
-- message n'est perdu : un échec est retenté à 1, 5 puis 15 minutes, puis la notification passe en
-- « echec » et l'Administrateur Technique est alerté (journal de l'API). Envoi « au moins une fois ».

-- 1. Table de db-scheduler (tâches planifiées partagées entre les instances de l'API).
--    Table technique : la suppression d'une exécution terminée est propre à db-scheduler.
CREATE TABLE scheduled_tasks (
  task_name            text NOT NULL,
  task_instance        text NOT NULL,
  task_data            bytea,
  execution_time       timestamptz NOT NULL,
  picked               boolean NOT NULL,
  picked_by            text,
  last_success         timestamptz,
  last_failure         timestamptz,
  consecutive_failures integer,
  last_heartbeat       timestamptz,
  version              bigint NOT NULL,
  priority             smallint,
  PRIMARY KEY (task_name, task_instance)
);
CREATE INDEX execution_time_idx ON scheduled_tasks (execution_time);
CREATE INDEX last_heartbeat_idx ON scheduled_tasks (last_heartbeat);
CREATE INDEX priority_execution_time_idx ON scheduled_tasks (priority DESC, execution_time ASC);
GRANT DELETE ON scheduled_tasks TO emine_app;

-- 2. Suivi des essais d'envoi et lecture dans l'application.
ALTER TABLE notification
  ADD COLUMN prochain_essai_le timestamptz NOT NULL DEFAULT now(),
  ADD COLUMN derniere_erreur   text,
  ADD COLUMN lue_le            timestamptz;
DROP INDEX ix_notification_attente;
CREATE INDEX ix_notification_a_envoyer ON notification (prochain_essai_le)
  WHERE statut = 'en_attente';
CREATE INDEX ix_notification_destinataire ON notification (destinataire_id, created_at DESC);

-- 3. RLS : chacun ne lit (et ne marque comme lues) que ses propres notifications. Les triggers
--    métier écrivent pour le destinataire, quel que soit l'auteur du changement.
ALTER TABLE notification ENABLE ROW LEVEL SECURITY;
CREATE POLICY notification_lecture ON notification FOR SELECT TO emine_app
  USING (destinataire_id = fn_current_user_id());
CREATE POLICY notification_creation ON notification FOR INSERT TO emine_app
  WITH CHECK (true);
CREATE POLICY notification_maj ON notification FOR UPDATE TO emine_app
  USING (destinataire_id = fn_current_user_id())
  WITH CHECK (destinataire_id = fn_current_user_id());
CREATE POLICY notification_lecture_reporting ON notification FOR SELECT TO emine_readonly
  USING (true);

-- 4. Prise d'un lot à envoyer (tâche planifiée, sans utilisateur connecté). SECURITY DEFINER
--    limitée à cette opération : verrouille sans attendre (plusieurs instances), compte l'essai et
--    reporte le prochain essai (1, 5, 15 min) avant l'envoi : une instance arrêtée en plein envoi
--    ne bloque rien, la notification est reprise au prochain essai.
CREATE FUNCTION fn_notifications_a_envoyer(p_lot integer)
RETURNS TABLE (id bigint, canal text, modele text, contenu jsonb, tentative integer,
               email text, telephone text, langue text, prenom text,
               demande_id bigint, numero_demande text)
LANGUAGE sql SECURITY DEFINER SET search_path = emine, pg_temp AS
$$
  WITH lot AS (
    SELECT n.id FROM notification n
     WHERE n.statut = 'en_attente' AND n.canal IN ('sms', 'email')
       AND n.prochain_essai_le <= now()
     ORDER BY n.prochain_essai_le, n.id
     LIMIT least(greatest(p_lot, 1), 100)
     FOR UPDATE SKIP LOCKED),
  pris AS (
    UPDATE notification n
       SET tentatives = n.tentatives + 1,
           prochain_essai_le = now() + CASE n.tentatives
                                         WHEN 0 THEN interval '1 minute'
                                         WHEN 1 THEN interval '5 minutes'
                                         ELSE interval '15 minutes' END
      FROM lot WHERE n.id = lot.id
    RETURNING n.id, n.canal, n.modele, n.contenu, n.tentatives, n.destinataire_id, n.demande_id)
  SELECT p.id, p.canal::text, p.modele, p.contenu, p.tentatives::integer,
         u.email::text, u.telephone, u.langue::text, u.prenom, d.id, d.numero
    FROM pris p
    JOIN utilisateur u ON u.id = p.destinataire_id
    LEFT JOIN demande d ON d.id = p.demande_id
   ORDER BY p.id;
$$;

-- 5. Résultat d'un essai : envoyée, ou erreur (nouvel essai, puis « echec » après le 4e essai,
--    soit 3 reprises). Renvoie le nouveau statut (NULL si la notification n'était plus en attente).
CREATE FUNCTION fn_notification_resultat(p_id bigint, p_erreur text) RETURNS text
LANGUAGE sql SECURITY DEFINER SET search_path = emine, pg_temp AS
$$
  UPDATE notification
     SET statut = CASE WHEN p_erreur IS NULL THEN 'envoyee'
                       WHEN tentatives >= 4 THEN 'echec'
                       ELSE 'en_attente' END::statut_notification,
         envoyee_le = CASE WHEN p_erreur IS NULL THEN now() END,
         derniere_erreur = left(p_erreur, 500)
   WHERE id = p_id AND statut = 'en_attente'
  RETURNING statut::text;
$$;

REVOKE ALL ON FUNCTION fn_notifications_a_envoyer(integer) FROM PUBLIC;
REVOKE ALL ON FUNCTION fn_notification_resultat(bigint, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_notifications_a_envoyer(integer) TO emine_app;
GRANT EXECUTE ON FUNCTION fn_notification_resultat(bigint, text) TO emine_app;
