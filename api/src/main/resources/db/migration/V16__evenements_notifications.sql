-- US-07.2 : le demandeur est notifié à chaque changement d'étape de sa demande (cahier des charges :
-- « SMS et e-mail à chaque changement d'étape ») et pour chaque événement de paiement.
--
-- Les canaux de chaque événement sont paramétrables (choix du porteur du projet du 22/11/2026 :
-- chaque SMS a un coût réel pour le Ministère) : table `notification_evenement`, modifiable par
-- l'administration du catalogue. Par défaut, SMS + e-mail + application pour chaque événement.
-- Une seule fonction écrit les notifications : `fn_notifier`.

-- 1. Événements et canaux ------------------------------------------------------------------------
CREATE TABLE notification_evenement (
  id          smallint GENERATED ALWAYS AS IDENTITY UNIQUE,
  modele      text PRIMARY KEY CHECK (modele ~ '^[A-Z_]{3,40}$'),
  libelle_fr  text NOT NULL,
  libelle_en  text NOT NULL,
  sms         boolean NOT NULL DEFAULT true,
  email       boolean NOT NULL DEFAULT true,
  interne     boolean NOT NULL DEFAULT true,
  updated_at  timestamptz NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_notification_evenement_maj BEFORE UPDATE ON notification_evenement
  FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
CREATE TRIGGER trg_notification_evenement_audit AFTER INSERT OR UPDATE OR DELETE ON notification_evenement
  FOR EACH ROW EXECUTE FUNCTION fn_audit();

INSERT INTO notification_evenement (modele, libelle_fr, libelle_en) VALUES
  ('DEMANDE_DEPOSEE',          'Demande déposée',                       'Application submitted'),
  ('PAIEMENT_CONFIRME',        'Paiement confirmé',                     'Payment confirmed'),
  ('DEMANDE_RECEVABLE',        'Dossier complet, instruction ouverte',  'File complete, review started'),
  ('COMPLEMENT_DEMANDE',       'Complément demandé',                    'Additional information requested'),
  ('COMPLEMENT_RELANCE',       'Rappel de complément',                  'Additional information reminder'),
  ('DECISION_DROITS',          'Décision favorable, droits à payer',    'Favourable decision, fees due'),
  ('DECISION_FAVORABLE',       'Décision favorable',                    'Favourable decision'),
  ('DEMANDE_REJETEE',          'Demande rejetée',                       'Application rejected'),
  ('DOCUMENT_DELIVRE',         'Document délivré',                      'Document issued'),
  ('PAIEMENT_A_VERIFIER',      'Paiement à vérifier',                   'Payment under review'),
  ('REFERENCE_EXPIRE_BIENTOT', 'Référence de paiement bientôt expirée', 'Payment reference expiring soon'),
  ('REMBOURSEMENT',            'Remboursement effectué',                'Refund completed');

-- 2. Écriture des notifications d'un événement, une par canal actif. Le canal « interne » (centre
--    de notifications de l'application) n'a rien à envoyer : il est « envoyee » dès l'écriture.
--    Un modèle inconnu est une erreur de programmation : refusé.
CREATE FUNCTION fn_notifier(p_destinataire bigint, p_modele text, p_demande bigint,
                            p_paiement uuid, p_contenu jsonb) RETURNS void
LANGUAGE plpgsql AS
$$
DECLARE
  e notification_evenement%ROWTYPE;
BEGIN
  IF p_destinataire IS NULL THEN
    RETURN;
  END IF;
  SELECT * INTO e FROM notification_evenement WHERE modele = p_modele;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Événement de notification inconnu : %', p_modele USING ERRCODE = 'P0001';
  END IF;
  INSERT INTO notification (destinataire_id, canal, modele, demande_id, paiement_id, contenu,
                            statut, envoyee_le)
  SELECT p_destinataire, c.canal, p_modele, p_demande, p_paiement, COALESCE(p_contenu, '{}'),
         CASE WHEN c.canal = 'interne' THEN 'envoyee' ELSE 'en_attente' END::statut_notification,
         CASE WHEN c.canal = 'interne' THEN now() END
    FROM (VALUES ('sms'::canal_notification, e.sms), ('email', e.email), ('interne', e.interne))
         AS c(canal, actif)
   WHERE c.actif;
END $$;

-- Libellés du document de la demande (FR/EN), repris dans les modèles par {document}.
CREATE FUNCTION fn_contenu_demande(p_demande bigint) RETURNS jsonb
LANGUAGE sql STABLE AS
$$
  SELECT jsonb_build_object('document_fr', td.libelle_fr,
                            'document_en', COALESCE(td.libelle_en, td.libelle_fr))
    FROM demande d JOIN type_document td ON td.id = d.type_document_id
   WHERE d.id = p_demande;
$$;

-- Montant en vigueur au barème pour un document et un objet (dernière version active).
CREATE FUNCTION fn_tarif_en_vigueur(p_type_document smallint, p_objet objet_paiement)
RETURNS bigint LANGUAGE sql STABLE AS
$$
  SELECT t.montant_gnf FROM tarif t
   WHERE t.type_document_id = p_type_document AND t.objet = p_objet AND t.actif
     AND t.date_effet <= current_date
   ORDER BY t.date_effet DESC, t.version DESC
   LIMIT 1;
$$;

-- 3. Changements d'étape de la demande -------------------------------------------------------------
CREATE FUNCTION fn_demande_notifier() RETURNS trigger LANGUAGE plpgsql AS
$$
DECLARE
  contenu jsonb := fn_contenu_demande(NEW.id);
  droits  bigint;
BEGIN
  IF NEW.statut = 'deposee' AND OLD.statut = 'brouillon' THEN
    PERFORM fn_notifier(NEW.demandeur_id, 'DEMANDE_DEPOSEE', NEW.id, NULL, contenu);
  ELSIF NEW.statut = 'recevable' THEN
    PERFORM fn_notifier(NEW.demandeur_id, 'DEMANDE_RECEVABLE', NEW.id, NULL, contenu);
  ELSIF NEW.statut = 'decision' THEN
    droits := fn_tarif_en_vigueur(NEW.type_document_id, 'DROITS');
    IF droits IS NOT NULL THEN
      PERFORM fn_notifier(NEW.demandeur_id, 'DECISION_DROITS', NEW.id, NULL,
                          contenu || jsonb_build_object('montant_gnf', droits));
    END IF;
  ELSIF NEW.statut = 'droits_payes' AND OLD.statut = 'decision'
        AND NOT EXISTS (SELECT 1 FROM paiement p
                         WHERE p.demande_id = NEW.id AND p.objet = 'DROITS' AND p.statut = 'paye') THEN
    -- Document sans droits : la décision favorable mène directement à la signature.
    PERFORM fn_notifier(NEW.demandeur_id, 'DECISION_FAVORABLE', NEW.id, NULL, contenu);
  ELSIF NEW.statut = 'rejetee' THEN
    PERFORM fn_notifier(NEW.demandeur_id, 'DEMANDE_REJETEE', NEW.id, NULL,
                        contenu || jsonb_build_object('motif', NEW.motif_rejet));
  ELSIF NEW.statut = 'delivree' THEN
    PERFORM fn_notifier(NEW.demandeur_id, 'DOCUMENT_DELIVRE', NEW.id, NULL, contenu);
  END IF;
  RETURN NULL;
END $$;

CREATE TRIGGER trg_demande_notifier AFTER UPDATE OF statut ON demande
  FOR EACH ROW WHEN (NEW.statut IS DISTINCT FROM OLD.statut)
  EXECUTE FUNCTION fn_demande_notifier();

-- 4. Paiements : les notifications existantes passent par fn_notifier ; « à vérifier » et
--    « remboursé » sont branchées dès maintenant (ces statuts arrivent avec le virement et le
--    remboursement, sprint « Paiement complet »).
CREATE OR REPLACE FUNCTION fn_paiement_confirme() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF NEW.objet IN ('DROITS','ATTESTATION') THEN
    UPDATE demande SET statut = 'droits_payes'
     WHERE id = NEW.demande_id AND statut = 'decision';
  END IF;
  PERFORM fn_notifier(NEW.payeur_id, 'PAIEMENT_CONFIRME', NEW.demande_id, NEW.id,
                      jsonb_build_object('reference', NEW.reference, 'montant_gnf', NEW.montant_gnf,
                                         'moyen', NEW.moyen, 'recu', NEW.recu_numero));
  RETURN NULL;
END $$;

CREATE FUNCTION fn_paiement_notifier() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF NEW.statut = 'a_verifier' THEN
    PERFORM fn_notifier(NEW.payeur_id, 'PAIEMENT_A_VERIFIER', NEW.demande_id, NEW.id,
                        jsonb_build_object('reference', NEW.reference,
                                           'montant_gnf', NEW.montant_gnf));
  ELSIF NEW.statut = 'rembourse' THEN
    PERFORM fn_notifier(NEW.payeur_id, 'REMBOURSEMENT', NEW.demande_id, NEW.id,
                        jsonb_build_object('reference', NEW.reference,
                                           'montant_gnf', COALESCE(
                                             (SELECT r.montant_gnf FROM remboursement r
                                               WHERE r.paiement_id = NEW.id AND r.statut = 'execute'
                                               ORDER BY r.created_at DESC LIMIT 1),
                                             NEW.montant_gnf)));
  END IF;
  RETURN NULL;
END $$;

CREATE TRIGGER trg_paiement_notifier AFTER UPDATE OF statut ON paiement
  FOR EACH ROW WHEN (NEW.statut IS DISTINCT FROM OLD.statut AND NEW.statut IN ('a_verifier', 'rembourse'))
  EXECUTE FUNCTION fn_paiement_notifier();

-- 5. Compléments : mêmes canaux paramétrables.
CREATE OR REPLACE FUNCTION fn_complement_notifier() RETURNS trigger LANGUAGE plpgsql AS
$$
DECLARE
  d demande%ROWTYPE;
BEGIN
  SELECT * INTO d FROM demande WHERE id = NEW.demande_id;
  PERFORM fn_notifier(d.demandeur_id, 'COMPLEMENT_DEMANDE', d.id, NULL,
                      fn_contenu_demande(d.id)
                      || jsonb_build_object('dateLimite', NEW.date_limite,
                                            'pieces', to_jsonb(NEW.pieces_demandees)));
  RETURN NULL;
END $$;

CREATE OR REPLACE FUNCTION fn_relancer_complements() RETURNS integer
LANGUAGE plpgsql SECURITY DEFINER SET search_path = emine, pg_temp AS
$$
DECLARE
  a record;
  n integer := 0;
BEGIN
  FOR a IN
    UPDATE complement c SET relance_le = now()
     WHERE c.repondu_le IS NULL AND c.clos_le IS NULL AND c.relance_le IS NULL
       AND now() >= c.demande_le + interval '3 days'
     RETURNING c.demande_id, c.date_limite
  LOOP
    PERFORM fn_notifier(d.demandeur_id, 'COMPLEMENT_RELANCE', d.id, NULL,
                        fn_contenu_demande(d.id) || jsonb_build_object('dateLimite', a.date_limite))
       FROM demande d WHERE d.id = a.demande_id;
    n := n + 1;
  END LOOP;
  RETURN n;
END $$;

-- 6. Référence de paiement proche de l'expiration (J-2) : tâche planifiée de l'API, idempotente
--    (une seule alerte par paiement). Concerne les références de virement (7 jours) ; une session
--    TrésorPay (15 min) n'est jamais concernée.
CREATE FUNCTION fn_alerter_references_expirantes() RETURNS integer
LANGUAGE plpgsql SECURITY DEFINER SET search_path = emine, pg_temp AS
$$
DECLARE
  p record;
  n integer := 0;
BEGIN
  FOR p IN
    SELECT pa.* FROM paiement pa
     WHERE pa.statut IN ('initie', 'en_attente')
       AND pa.date_expiration > now()
       AND pa.date_expiration <= now() + interval '2 days'
       AND pa.moyen = 'VIREMENT'
       AND NOT EXISTS (SELECT 1 FROM notification n
                        WHERE n.paiement_id = pa.id AND n.modele = 'REFERENCE_EXPIRE_BIENTOT')
     FOR UPDATE SKIP LOCKED
  LOOP
    PERFORM fn_notifier(p.payeur_id, 'REFERENCE_EXPIRE_BIENTOT', p.demande_id, p.id,
                        jsonb_build_object('reference', p.reference, 'montant_gnf', p.montant_gnf,
                                           'dateExpiration', p.date_expiration));
    n := n + 1;
  END LOOP;
  RETURN n;
END $$;

REVOKE ALL ON FUNCTION fn_alerter_references_expirantes() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_alerter_references_expirantes() TO emine_app;
GRANT SELECT ON notification_evenement TO emine_readonly;
