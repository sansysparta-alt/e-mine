-- e-mine · V19 — Tarifs versionnés, approuvés par le Ministre (US-02.3, US-02.4). Cahier des
-- charges (application, diapo 32) : « Tarifs versionnés : barème par document, date d'entrée en
-- vigueur, validation du Ministre avant publication ». Le barème fait partie de la version de la
-- fiche (V18) : l'administrateur propose les montants et leur date d'entrée en vigueur, le Ministre
-- approuve ; la base ajoute alors une version de tarif (table « tarif » du schéma fourni) par montant
-- changé. Seule une version approuvée s'applique, à sa date d'effet ; un paiement garde le montant de
-- sa création. Les objets payés pour un document (frais de dossier, droits, attestation) ne changent
-- pas ici : ils décident du circuit de paiement. Ajouts au schéma fourni, à signaler au Ministère.

ALTER TABLE version_document
  ADD COLUMN frais_dossier_gnf bigint CHECK (frais_dossier_gnf IS NULL OR frais_dossier_gnf > 0),
  ADD COLUMN droits_gnf        bigint CHECK (droits_gnf IS NULL OR droits_gnf > 0),
  ADD COLUMN attestation_gnf   bigint CHECK (attestation_gnf IS NULL OR attestation_gnf > 0),
  ADD COLUMN tarifs_date_effet date;

COMMENT ON COLUMN version_document.tarifs_date_effet IS
  'Entrée en vigueur des montants proposés (heure de Conakry) ; appliquée au plus tôt le jour de l''approbation';

-- Montant d'un objet en vigueur à une date (heure de Conakry) : version active la plus récente dont
-- la date d'effet est passée.
CREATE FUNCTION fn_tarif_a_date(p_type_document smallint, p_objet objet_paiement, p_date date)
RETURNS bigint LANGUAGE sql STABLE AS
$$
  SELECT t.montant_gnf FROM tarif t
   WHERE t.type_document_id = p_type_document AND t.objet = p_objet AND t.actif
     AND t.date_effet <= p_date
    ORDER BY t.date_effet DESC, t.version DESC
   LIMIT 1
$$;

CREATE FUNCTION fn_aujourdhui_conakry() RETURNS date LANGUAGE sql STABLE AS
$$ SELECT (now() AT TIME ZONE 'Africa/Conakry')::date $$;

-- 1. Les versions existantes portent le barème en vigueur (aucune trace : simple reprise).
ALTER TABLE version_document DISABLE TRIGGER USER;
UPDATE version_document v
   SET frais_dossier_gnf = fn_tarif_a_date(v.type_document_id, 'FRAIS_DOSSIER', fn_aujourdhui_conakry()),
       droits_gnf        = fn_tarif_a_date(v.type_document_id, 'DROITS', fn_aujourdhui_conakry()),
       attestation_gnf   = fn_tarif_a_date(v.type_document_id, 'ATTESTATION', fn_aujourdhui_conakry()),
       tarifs_date_effet = (SELECT min(t.date_effet) FROM tarif t WHERE t.type_document_id = v.type_document_id);
ALTER TABLE version_document ENABLE TRIGGER USER;

-- 2. Contrôles du barème proposé : mêmes objets qu'aujourd'hui, date d'effet à partir d'aujourd'hui
--    quand un montant change ; barème figé après la proposition.
CREATE FUNCTION fn_version_document_tarifs() RETURNS trigger LANGUAGE plpgsql AS
$$
DECLARE
  o objet_paiement;
  actuel bigint;
  approuve bigint;
  propose bigint;
  v version_document%ROWTYPE;
  change boolean := false;
BEGIN
  IF TG_OP = 'UPDATE' THEN
    IF (NEW.frais_dossier_gnf, NEW.droits_gnf, NEW.attestation_gnf, NEW.tarifs_date_effet)
       IS DISTINCT FROM (OLD.frais_dossier_gnf, OLD.droits_gnf, OLD.attestation_gnf, OLD.tarifs_date_effet) THEN
      RAISE EXCEPTION 'Version % : une version proposée ne se modifie pas', OLD.id
        USING ERRCODE = 'P0001', HINT = 'DOC-009';
    END IF;
    RETURN NEW;
  END IF;
  -- Référence : le barème de la version publiée (approuvé, éventuellement pas encore en vigueur).
  SELECT * INTO v FROM version_document
   WHERE type_document_id = NEW.type_document_id AND statut = 'publiee';
  FOREACH o IN ARRAY ARRAY['FRAIS_DOSSIER', 'DROITS', 'ATTESTATION']::objet_paiement[] LOOP
    actuel := fn_tarif_a_date(NEW.type_document_id, o, fn_aujourdhui_conakry());
    approuve := CASE o WHEN 'FRAIS_DOSSIER' THEN v.frais_dossier_gnf
                       WHEN 'DROITS' THEN v.droits_gnf ELSE v.attestation_gnf END;
    propose := CASE o WHEN 'FRAIS_DOSSIER' THEN NEW.frais_dossier_gnf
                      WHEN 'DROITS' THEN NEW.droits_gnf ELSE NEW.attestation_gnf END;
    IF (actuel IS NULL) <> (propose IS NULL) THEN
      RAISE EXCEPTION 'Objet % : les objets payés pour un document ne changent pas ici', o
        USING ERRCODE = 'P0001', HINT = 'VAL-001';
    END IF;
    change := change OR approuve IS DISTINCT FROM propose;
  END LOOP;
  IF change AND (NEW.tarifs_date_effet IS NULL OR NEW.tarifs_date_effet < fn_aujourdhui_conakry()) THEN
    RAISE EXCEPTION 'Date d''entrée en vigueur des tarifs : aujourd''hui ou plus tard'
      USING ERRCODE = 'P0001', HINT = 'VAL-001';
  END IF;
  IF NOT change THEN
    NEW.tarifs_date_effet := NULL;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER trg_version_document_tarifs BEFORE INSERT OR UPDATE ON version_document
  FOR EACH ROW EXECUTE FUNCTION fn_version_document_tarifs();

-- 3. Publication du barème : une version de tarif par montant changé par rapport au barème approuvé
--    jusque-là, à la date d'effet (au plus tôt le jour de l'approbation), au nom du Ministre.
CREATE FUNCTION fn_version_document_publier_tarifs() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = emine, pg_temp AS
$$
DECLARE
  o objet_paiement;
  montant bigint;
  avant version_document%ROWTYPE;
  effet date := greatest(COALESCE(NEW.tarifs_date_effet, fn_aujourdhui_conakry()), fn_aujourdhui_conakry());
  signe text;
BEGIN
  -- Barème approuvé jusque-là : la version publiée que celle-ci remplace.
  SELECT * INTO avant FROM version_document
   WHERE type_document_id = NEW.type_document_id AND statut = 'remplacee' AND numero < NEW.numero
   ORDER BY numero DESC LIMIT 1;
  SELECT btrim(COALESCE(u.prenom, '') || ' ' || COALESCE(u.nom, '')) INTO signe
    FROM utilisateur u WHERE u.id = NEW.decidee_par;
  FOREACH o IN ARRAY ARRAY['FRAIS_DOSSIER', 'DROITS', 'ATTESTATION']::objet_paiement[] LOOP
    montant := CASE o WHEN 'FRAIS_DOSSIER' THEN NEW.frais_dossier_gnf
                      WHEN 'DROITS' THEN NEW.droits_gnf ELSE NEW.attestation_gnf END;
    CONTINUE WHEN montant IS NULL OR montant IS NOT DISTINCT FROM
      CASE o WHEN 'FRAIS_DOSSIER' THEN avant.frais_dossier_gnf
             WHEN 'DROITS' THEN avant.droits_gnf ELSE avant.attestation_gnf END;
    -- Un tarif approuvé plus tôt mais pas encore en vigueur est remplacé par celui-ci (désactivé,
    -- jamais supprimé).
    UPDATE tarif SET actif = false
     WHERE type_document_id = NEW.type_document_id AND objet = o AND actif AND date_effet > effet;
    INSERT INTO tarif (type_document_id, objet, montant_gnf, date_effet, version, valide_par)
    SELECT NEW.type_document_id, o, montant, effet, COALESCE(max(t.version), 0) + 1,
           format('Ministre %s, fiche v%s, le %s', signe, NEW.numero,
                  to_char(fn_aujourdhui_conakry(), 'DD/MM/YYYY'))
      FROM tarif t WHERE t.type_document_id = NEW.type_document_id AND t.objet = o;
  END LOOP;
  RETURN NULL;
END $$;
REVOKE ALL ON FUNCTION fn_version_document_publier_tarifs() FROM PUBLIC;

CREATE TRIGGER trg_version_document_publier_tarifs AFTER UPDATE OF statut ON version_document
  FOR EACH ROW WHEN (NEW.statut = 'publiee' AND OLD.statut = 'a_valider')
  EXECUTE FUNCTION fn_version_document_publier_tarifs();

-- Les tarifs ne s'écrivent plus que par une version approuvée.
REVOKE INSERT, UPDATE ON tarif FROM emine_app;
