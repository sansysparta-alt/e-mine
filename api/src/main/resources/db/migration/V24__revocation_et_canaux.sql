-- Retours ergonomie du 24/11/2026 (ajouts au schéma fourni, à signaler au Ministère).
--
-- 1. Révocation datée : date et auteur posés par la base au passage « non valide », comme les
--    numéros ; l'usager voit quand et pourquoi son document a été révoqué.
ALTER TABLE document_delivre
  ADD COLUMN revoque_le  timestamptz,
  ADD COLUMN revoque_par bigint REFERENCES utilisateur(id);

UPDATE document_delivre SET revoque_le = updated_at WHERE statut = 'non_valide';

ALTER TABLE document_delivre
  ADD CONSTRAINT ck_document_delivre_revocation CHECK ((statut = 'valide') = (revoque_le IS NULL));

CREATE FUNCTION fn_document_delivre_revocation() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF OLD.statut = 'non_valide' AND NEW.statut = 'valide' THEN
    RAISE EXCEPTION 'Un document révoqué ne redevient pas valide'
      USING ERRCODE = 'P0001', HINT = 'DOC-006';
  END IF;
  IF OLD.statut = 'valide' AND NEW.statut = 'non_valide' THEN
    NEW.revoque_le := now();
    NEW.revoque_par := fn_current_user_id();
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER trg_document_delivre_revocation BEFORE UPDATE OF statut ON document_delivre
  FOR EACH ROW EXECUTE FUNCTION fn_document_delivre_revocation();

-- 2. Notification de l'usager à chaque révocation (événement administrable comme les autres).
INSERT INTO notification_evenement (modele, libelle_fr, libelle_en, sms) VALUES
  ('DOCUMENT_REVOQUE', 'Document révoqué', 'Document revoked', false);

CREATE FUNCTION fn_document_delivre_notifier() RETURNS trigger LANGUAGE plpgsql AS
$$
DECLARE
  d demande%ROWTYPE;
BEGIN
  SELECT * INTO d FROM demande WHERE id = NEW.demande_id;
  IF FOUND THEN
    PERFORM fn_notifier(d.demandeur_id, 'DOCUMENT_REVOQUE', d.id, NULL,
                        fn_contenu_demande(d.id)
                          || jsonb_build_object('motif', NEW.motif_invalidation,
                                                'document_numero', NEW.numero));
  END IF;
  RETURN NULL;
END $$;

CREATE TRIGGER trg_document_delivre_notifier AFTER UPDATE OF statut ON document_delivre
  FOR EACH ROW WHEN (OLD.statut = 'valide' AND NEW.statut = 'non_valide')
  EXECUTE FUNCTION fn_document_delivre_notifier();

-- 3. Canaux : l'espace en ligne est toujours actif (l'usager y voit tout son dossier) ; le SMS,
--    payant, n'est actif par défaut que là où l'usager doit agir (compléter, payer, retirer).
UPDATE notification_evenement SET interne = true;
UPDATE notification_evenement
   SET sms = modele IN ('COMPLEMENT_DEMANDE', 'COMPLEMENT_RELANCE', 'DECISION_DROITS',
                        'REFERENCE_EXPIRE_BIENTOT', 'DOCUMENT_DELIVRE');

ALTER TABLE notification_evenement
  ADD CONSTRAINT notification_evenement_interne_obligatoire CHECK (interne);
