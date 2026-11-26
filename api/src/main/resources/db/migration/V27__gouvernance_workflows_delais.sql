-- e-mine · V27 — Gouvernance des workflows et des délais (lot 4, US-02.8). Décision du porteur du
-- projet (24 et 25/11/2026) : comme le catalogue (V18, V19), toute modification d'un workflow ou de
-- la règle des délais est **proposée par l'administrateur** (droit PARAMETRER), **validée par le
-- Ministre** (droit VALIDER_CATALOGUE), puis **appliquée à sa date d'effet**. Les demandes en cours
-- gardent la version de leur dépôt. Tout est tracé au journal d'audit. Remplace la modification
-- immédiate de V20 (workflows) et V21 (délais). Ajouts au schéma fourni, à signaler au Ministère.
--
-- Une seule règle pour les deux : une proposition ouverte à la fois ; date d'effet à partir
-- d'aujourd'hui (heure de Conakry) ; à l'approbation, la date passée devient aujourd'hui et une date
-- ne précède jamais celle d'une version déjà approuvée ; la version en vigueur à une date est la plus
-- récente version approuvée dont la date d'effet est arrivée.

-- =================================================================================================
-- 1. Workflows
-- =================================================================================================

ALTER TABLE version_workflow DISABLE TRIGGER trg_version_workflow_nodelete;

ALTER TABLE version_workflow
  ADD COLUMN statut      text,
  ADD COLUMN date_effet  date,
  ADD COLUMN decidee_par bigint REFERENCES utilisateur(id),
  ADD COLUMN decidee_le  timestamptz,
  ADD COLUMN motif_refus text
    CHECK (motif_refus IS NULL OR char_length(btrim(motif_refus)) BETWEEN 10 AND 2000);

-- Versions existantes : appliquées dès leur enregistrement (V20, sans approbation).
UPDATE version_workflow
   SET statut = 'publiee', date_effet = (modifiee_le AT TIME ZONE 'Africa/Conakry')::date,
       decidee_le = modifiee_le;

ALTER TABLE version_workflow
  ALTER COLUMN statut SET NOT NULL,
  ALTER COLUMN statut SET DEFAULT 'a_valider',
  ALTER COLUMN date_effet SET NOT NULL,
  ADD CONSTRAINT version_workflow_statut
    CHECK (statut IN ('a_valider', 'publiee', 'refusee', 'retiree')),
  ADD CONSTRAINT version_workflow_refus CHECK ((statut = 'refusee') = (motif_refus IS NOT NULL)),
  ADD CONSTRAINT version_workflow_decidee CHECK (statut = 'a_valider' OR decidee_le IS NOT NULL);

CREATE UNIQUE INDEX version_workflow_une_ouverte ON version_workflow (workflow_id)
  WHERE statut = 'a_valider';

COMMENT ON COLUMN version_workflow.modifiee_par IS 'Administrateur qui a proposé la version';
COMMENT ON COLUMN version_workflow.modifiee_le IS 'Date de la proposition';
COMMENT ON COLUMN version_workflow.date_effet IS
  'Date à partir de laquelle la version approuvée s''applique aux demandes déposées (Conakry)';

-- Le contenu d'une version ne change plus ; seule la décision s'enregistre (fonction ci-dessous).
DROP TRIGGER trg_version_workflow_nodelete ON version_workflow;
CREATE TRIGGER trg_version_workflow_nodelete BEFORE DELETE ON version_workflow
  FOR EACH ROW EXECUTE FUNCTION fn_interdire_modification();

CREATE FUNCTION fn_version_workflow_fige() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF (NEW.workflow_id, NEW.version, NEW.libelle, NEW.regle_paiement, NEW.motif, NEW.modifiee_par,
      NEW.modifiee_le)
     IS DISTINCT FROM
     (OLD.workflow_id, OLD.version, OLD.libelle, OLD.regle_paiement, OLD.motif, OLD.modifiee_par,
      OLD.modifiee_le) THEN
    RAISE EXCEPTION 'Version % : une version proposée ne se modifie pas', OLD.version
      USING ERRCODE = 'P0001', HINT = 'DOC-009';
  END IF;
  IF OLD.statut <> 'a_valider' THEN
    RAISE EXCEPTION 'Version % : déjà décidée', OLD.version USING ERRCODE = 'P0001', HINT = 'DOC-009';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_version_workflow_fige BEFORE UPDATE ON version_workflow
  FOR EACH ROW EXECUTE FUNCTION fn_version_workflow_fige();
CREATE TRIGGER trg_version_workflow_audit_maj AFTER UPDATE ON version_workflow
  FOR EACH ROW EXECUTE FUNCTION fn_audit();

-- Version en vigueur d'un workflow à une date (aujourd'hui par défaut).
CREATE FUNCTION fn_workflow_version_en_vigueur(p_workflow smallint,
                                               p_date date DEFAULT fn_aujourdhui_conakry())
RETURNS integer LANGUAGE sql STABLE AS
$$
  SELECT max(version) FROM version_workflow
   WHERE workflow_id = p_workflow AND statut = 'publiee' AND date_effet <= p_date
$$;

COMMENT ON COLUMN workflow.version IS
  'Version initiale (schéma fourni). La version en vigueur se lit par fn_workflow_version_en_vigueur (V27)';
COMMENT ON COLUMN workflow.libelle IS
  'Libellé initial (schéma fourni). Celui en vigueur est celui de la version en vigueur (V27)';

-- Proposition : nouvelle version complète, à valider. Remplace fn_modifier_workflow (V20).
DROP FUNCTION fn_modifier_workflow(text, text, text, jsonb, text);

CREATE FUNCTION fn_proposer_workflow(p_code text, p_libelle text, p_regle_paiement text,
                                     p_etapes jsonb, p_motif text, p_date_effet date)
RETURNS integer
LANGUAGE plpgsql SECURITY DEFINER SET search_path = emine, pg_temp AS
$$
DECLARE
  w workflow%ROWTYPE;
  derniere integer;
  v integer;
  n integer;
  e jsonb;
  i integer := 0;
  identiques boolean;
BEGIN
  IF NOT fn_a_permission('PARAMETRER') OR fn_current_user_id() IS NULL THEN
    RAISE EXCEPTION 'Seul l''administrateur propose un workflow' USING ERRCODE = 'P0001', HINT = 'PERM-001';
  END IF;
  SELECT * INTO w FROM workflow WHERE code = p_code FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Workflow % inconnu', p_code USING ERRCODE = 'P0001', HINT = 'SYS-002';
  END IF;
  IF EXISTS (SELECT 1 FROM version_workflow WHERE workflow_id = w.id AND statut = 'a_valider') THEN
    RAISE EXCEPTION 'Workflow % : une proposition attend déjà la décision du Ministre', p_code
      USING ERRCODE = 'P0001', HINT = 'DOC-008';
  END IF;
  IF p_date_effet IS NULL OR p_date_effet < fn_aujourdhui_conakry() THEN
    RAISE EXCEPTION 'La date d''effet est aujourd''hui ou plus tard' USING ERRCODE = 'P0001', HINT = 'VAL-001';
  END IF;
  n := jsonb_array_length(COALESCE(p_etapes, '[]'::jsonb));
  IF n NOT BETWEEN 1 AND 12 THEN
    RAISE EXCEPTION 'Un workflow a de 1 à 12 étapes' USING ERRCODE = 'P0001', HINT = 'VAL-001';
  END IF;
  -- Rien ne change par rapport à la dernière version approuvée (en vigueur ou programmée) ?
  SELECT max(version) INTO derniere FROM version_workflow WHERE workflow_id = w.id AND statut = 'publiee';
  SELECT vw.libelle = btrim(p_libelle) AND vw.regle_paiement = btrim(p_regle_paiement)
         AND (SELECT jsonb_agg(jsonb_build_object('acteur', acteur, 'libelle', libelle) ORDER BY ordre)
                FROM workflow_etape WHERE workflow_id = w.id AND version = derniere)
             = (SELECT jsonb_agg(jsonb_build_object('acteur', btrim(x->>'acteur'), 'libelle', btrim(x->>'libelle'))
                                 ORDER BY o)
                  FROM jsonb_array_elements(p_etapes) WITH ORDINALITY AS t(x, o))
    INTO identiques
    FROM version_workflow vw WHERE vw.workflow_id = w.id AND vw.version = derniere;
  IF identiques THEN
    RAISE EXCEPTION 'Workflow % : aucune modification', p_code USING ERRCODE = 'P0001', HINT = 'DOC-010';
  END IF;
  SELECT max(version) + 1 INTO v FROM version_workflow WHERE workflow_id = w.id;
  INSERT INTO version_workflow (workflow_id, version, libelle, regle_paiement, motif, modifiee_par,
                                statut, date_effet)
  VALUES (w.id, v, btrim(p_libelle), btrim(p_regle_paiement), btrim(p_motif), fn_current_user_id(),
          'a_valider', p_date_effet);
  FOR e IN SELECT x FROM jsonb_array_elements(p_etapes) AS t(x) LOOP
    i := i + 1;
    INSERT INTO workflow_etape (workflow_id, ordre, acteur, libelle, version)
    VALUES (w.id, i, btrim(e->>'acteur'), btrim(e->>'libelle'), v);
  END LOOP;
  RETURN v;
END $$;
REVOKE ALL ON FUNCTION fn_proposer_workflow(text, text, text, jsonb, text, date) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_proposer_workflow(text, text, text, jsonb, text, date) TO emine_app;

-- Décision : le Ministre approuve ou refuse (motif), l'administrateur retire sa proposition.
CREATE FUNCTION fn_decider_workflow(p_code text, p_version integer, p_decision text,
                                    p_motif text DEFAULT NULL)
RETURNS date
LANGUAGE plpgsql SECURITY DEFINER SET search_path = emine, pg_temp AS
$$
DECLARE
  vw version_workflow%ROWTYPE;
  effet date;
BEGIN
  SELECT v.* INTO vw FROM version_workflow v JOIN workflow w ON w.id = v.workflow_id
   WHERE w.code = p_code AND v.version = p_version FOR UPDATE OF v;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Version % du workflow % inconnue', p_version, p_code
      USING ERRCODE = 'P0001', HINT = 'SYS-002';
  END IF;
  IF vw.statut <> 'a_valider' THEN
    RAISE EXCEPTION 'Version % : déjà décidée', p_version USING ERRCODE = 'P0001', HINT = 'DOC-009';
  END IF;
  IF p_decision IN ('approuver', 'refuser') AND NOT fn_a_permission('VALIDER_CATALOGUE') THEN
    RAISE EXCEPTION 'Seul le Ministre décide d''un workflow' USING ERRCODE = 'P0001', HINT = 'PERM-001';
  ELSIF p_decision = 'retirer' AND NOT fn_a_permission('PARAMETRER') THEN
    RAISE EXCEPTION 'Seul l''administrateur retire sa proposition' USING ERRCODE = 'P0001', HINT = 'PERM-001';
  ELSIF p_decision NOT IN ('approuver', 'refuser', 'retirer') THEN
    RAISE EXCEPTION 'Décision % inconnue', p_decision USING ERRCODE = 'P0001', HINT = 'VAL-001';
  END IF;
  IF p_decision = 'approuver' THEN
    effet := greatest(vw.date_effet, fn_aujourdhui_conakry(),
                      (SELECT max(date_effet) FROM version_workflow
                        WHERE workflow_id = vw.workflow_id AND statut = 'publiee'));
    UPDATE version_workflow SET statut = 'publiee', date_effet = effet,
           decidee_par = fn_current_user_id(), decidee_le = now()
     WHERE id = vw.id;
    RETURN effet;
  END IF;
  UPDATE version_workflow
     SET statut = CASE p_decision WHEN 'refuser' THEN 'refusee' ELSE 'retiree' END,
         motif_refus = CASE p_decision WHEN 'refuser' THEN btrim(p_motif) END,
         decidee_par = fn_current_user_id(), decidee_le = now()
   WHERE id = vw.id;
  RETURN NULL;
END $$;
REVOKE ALL ON FUNCTION fn_decider_workflow(text, integer, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_decider_workflow(text, integer, text, text) TO emine_app;

-- Une demande déposée prend la version du workflow **en vigueur** à son dépôt (et non la dernière).
CREATE OR REPLACE FUNCTION fn_demande_workflow_version() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF NEW.statut <> 'brouillon' AND (TG_OP = 'INSERT' OR OLD.statut = 'brouillon') THEN
    SELECT fn_workflow_version_en_vigueur(td.workflow_id) INTO NEW.workflow_version
      FROM type_document td WHERE td.id = NEW.type_document_id;
  ELSIF TG_OP = 'UPDATE' AND OLD.statut <> 'brouillon'
        AND NEW.workflow_version IS DISTINCT FROM OLD.workflow_version THEN
    RAISE EXCEPTION 'Demande % : la version du workflow du dépôt ne se modifie pas', OLD.id
      USING ERRCODE = 'P0001', HINT = 'DEM-001';
  END IF;
  RETURN NEW;
END $$;

-- =================================================================================================
-- 2. Règle des délais
-- =================================================================================================

CREATE TABLE version_regle_delai (
  version                integer PRIMARY KEY CHECK (version > 0),
  statut                 text NOT NULL DEFAULT 'a_valider'
                           CHECK (statut IN ('a_valider', 'publiee', 'refusee', 'retiree')),
  depart                 text NOT NULL CHECK (depart IN ('depot', 'paiement_frais')),
  pause_complement       boolean NOT NULL,
  pause_droits           boolean NOT NULL,
  seuil_proche_pourcent  smallint NOT NULL CHECK (seuil_proche_pourcent BETWEEN 1 AND 100),
  seuil_proche_min_jours smallint NOT NULL CHECK (seuil_proche_min_jours BETWEEN 0 AND 30),
  date_effet             date NOT NULL,
  motif                  text NOT NULL CHECK (char_length(btrim(motif)) BETWEEN 10 AND 2000),
  proposee_par           bigint REFERENCES utilisateur(id),
  proposee_le            timestamptz NOT NULL DEFAULT now(),
  decidee_par            bigint REFERENCES utilisateur(id),
  decidee_le             timestamptz,
  motif_refus            text
    CHECK (motif_refus IS NULL OR char_length(btrim(motif_refus)) BETWEEN 10 AND 2000),
  CHECK ((statut = 'refusee') = (motif_refus IS NOT NULL)),
  CHECK (statut = 'a_valider' OR decidee_le IS NOT NULL)
);
CREATE UNIQUE INDEX version_regle_delai_une_ouverte ON version_regle_delai ((true))
  WHERE statut = 'a_valider';
COMMENT ON TABLE version_regle_delai IS
  'Versions de la règle des délais : proposées par l''administrateur, approuvées par le Ministre (V27)';

-- Version 1 : la règle actuelle (V17, V21), en vigueur depuis sa dernière modification.
INSERT INTO version_regle_delai (version, statut, depart, pause_complement, pause_droits,
                                 seuil_proche_pourcent, seuil_proche_min_jours, date_effet, motif,
                                 proposee_le, decidee_le)
SELECT 1, 'publiee', depart, pause_complement, pause_droits, seuil_proche_pourcent,
       seuil_proche_min_jours, '2026-01-01',
       'Règle initiale (choix provisoires du porteur du projet, analyse § 3, points 17 à 22)',
       updated_at, updated_at
  FROM parametre_delai;

COMMENT ON TABLE parametre_delai IS
  'Règle des délais avant V27, conservée pour l''historique ; la règle se lit dans version_regle_delai';
REVOKE INSERT, UPDATE ON parametre_delai FROM emine_app;

CREATE FUNCTION fn_version_regle_delai_fige() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF (NEW.version, NEW.depart, NEW.pause_complement, NEW.pause_droits, NEW.seuil_proche_pourcent,
      NEW.seuil_proche_min_jours, NEW.motif, NEW.proposee_par, NEW.proposee_le)
     IS DISTINCT FROM
     (OLD.version, OLD.depart, OLD.pause_complement, OLD.pause_droits, OLD.seuil_proche_pourcent,
      OLD.seuil_proche_min_jours, OLD.motif, OLD.proposee_par, OLD.proposee_le) THEN
    RAISE EXCEPTION 'Version % : une version proposée ne se modifie pas', OLD.version
      USING ERRCODE = 'P0001', HINT = 'DOC-009';
  END IF;
  IF OLD.statut <> 'a_valider' THEN
    RAISE EXCEPTION 'Version % : déjà décidée', OLD.version USING ERRCODE = 'P0001', HINT = 'DOC-009';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_version_regle_delai_fige BEFORE UPDATE ON version_regle_delai
  FOR EACH ROW EXECUTE FUNCTION fn_version_regle_delai_fige();
CREATE TRIGGER trg_version_regle_delai_nodelete BEFORE DELETE ON version_regle_delai
  FOR EACH ROW EXECUTE FUNCTION fn_interdire_modification();
CREATE TRIGGER trg_version_regle_delai_audit AFTER INSERT OR UPDATE ON version_regle_delai
  FOR EACH ROW EXECUTE FUNCTION fn_audit();

-- L'application ne l'écrit que par les fonctions ci-dessous.
REVOKE INSERT, UPDATE ON version_regle_delai FROM emine_app;
GRANT SELECT ON version_regle_delai TO emine_app, emine_readonly;

-- Version en vigueur à une date (aujourd'hui par défaut).
CREATE FUNCTION fn_regle_delai_version_en_vigueur(p_date date DEFAULT fn_aujourdhui_conakry())
RETURNS integer LANGUAGE sql STABLE AS
$$
  SELECT max(version) FROM version_regle_delai WHERE statut = 'publiee' AND date_effet <= p_date
$$;

CREATE FUNCTION fn_proposer_regle_delai(p_depart text, p_pause_complement boolean,
                                        p_pause_droits boolean, p_seuil_pourcent smallint,
                                        p_seuil_min_jours smallint, p_motif text, p_date_effet date)
RETURNS integer
LANGUAGE plpgsql SECURITY DEFINER SET search_path = emine, pg_temp AS
$$
DECLARE
  d version_regle_delai%ROWTYPE;
  v integer;
BEGIN
  IF NOT fn_a_permission('PARAMETRER') OR fn_current_user_id() IS NULL THEN
    RAISE EXCEPTION 'Seul l''administrateur propose une règle des délais'
      USING ERRCODE = 'P0001', HINT = 'PERM-001';
  END IF;
  PERFORM pg_advisory_xact_lock(hashtext('version_regle_delai'));
  IF EXISTS (SELECT 1 FROM version_regle_delai WHERE statut = 'a_valider') THEN
    RAISE EXCEPTION 'Une proposition attend déjà la décision du Ministre'
      USING ERRCODE = 'P0001', HINT = 'DOC-008';
  END IF;
  IF p_date_effet IS NULL OR p_date_effet < fn_aujourdhui_conakry() THEN
    RAISE EXCEPTION 'La date d''effet est aujourd''hui ou plus tard' USING ERRCODE = 'P0001', HINT = 'VAL-001';
  END IF;
  SELECT * INTO d FROM version_regle_delai
   WHERE statut = 'publiee' ORDER BY version DESC LIMIT 1;
  IF (d.depart, d.pause_complement, d.pause_droits, d.seuil_proche_pourcent, d.seuil_proche_min_jours)
     IS NOT DISTINCT FROM (p_depart, p_pause_complement, p_pause_droits, p_seuil_pourcent, p_seuil_min_jours) THEN
    RAISE EXCEPTION 'Règle des délais : aucune modification' USING ERRCODE = 'P0001', HINT = 'DOC-010';
  END IF;
  SELECT max(version) + 1 INTO v FROM version_regle_delai;
  INSERT INTO version_regle_delai (version, depart, pause_complement, pause_droits,
                                   seuil_proche_pourcent, seuil_proche_min_jours, date_effet, motif,
                                   proposee_par)
  VALUES (v, p_depart, p_pause_complement, p_pause_droits, p_seuil_pourcent, p_seuil_min_jours,
          p_date_effet, btrim(p_motif), fn_current_user_id());
  RETURN v;
END $$;
REVOKE ALL ON FUNCTION fn_proposer_regle_delai(text, boolean, boolean, smallint, smallint, text, date) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_proposer_regle_delai(text, boolean, boolean, smallint, smallint, text, date) TO emine_app;

CREATE FUNCTION fn_decider_regle_delai(p_version integer, p_decision text, p_motif text DEFAULT NULL)
RETURNS date
LANGUAGE plpgsql SECURITY DEFINER SET search_path = emine, pg_temp AS
$$
DECLARE
  r version_regle_delai%ROWTYPE;
  effet date;
BEGIN
  SELECT * INTO r FROM version_regle_delai WHERE version = p_version FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Version % de la règle des délais inconnue', p_version
      USING ERRCODE = 'P0001', HINT = 'SYS-002';
  END IF;
  IF r.statut <> 'a_valider' THEN
    RAISE EXCEPTION 'Version % : déjà décidée', p_version USING ERRCODE = 'P0001', HINT = 'DOC-009';
  END IF;
  IF p_decision IN ('approuver', 'refuser') AND NOT fn_a_permission('VALIDER_CATALOGUE') THEN
    RAISE EXCEPTION 'Seul le Ministre décide de la règle des délais'
      USING ERRCODE = 'P0001', HINT = 'PERM-001';
  ELSIF p_decision = 'retirer' AND NOT fn_a_permission('PARAMETRER') THEN
    RAISE EXCEPTION 'Seul l''administrateur retire sa proposition' USING ERRCODE = 'P0001', HINT = 'PERM-001';
  ELSIF p_decision NOT IN ('approuver', 'refuser', 'retirer') THEN
    RAISE EXCEPTION 'Décision % inconnue', p_decision USING ERRCODE = 'P0001', HINT = 'VAL-001';
  END IF;
  IF p_decision = 'approuver' THEN
    effet := greatest(r.date_effet, fn_aujourdhui_conakry(),
                      (SELECT max(date_effet) FROM version_regle_delai WHERE statut = 'publiee'));
    UPDATE version_regle_delai SET statut = 'publiee', date_effet = effet,
           decidee_par = fn_current_user_id(), decidee_le = now()
     WHERE version = r.version;
    RETURN effet;
  END IF;
  UPDATE version_regle_delai
     SET statut = CASE p_decision WHEN 'refuser' THEN 'refusee' ELSE 'retiree' END,
         motif_refus = CASE p_decision WHEN 'refuser' THEN btrim(p_motif) END,
         decidee_par = fn_current_user_id(), decidee_le = now()
   WHERE version = r.version;
  RETURN NULL;
END $$;
REVOKE ALL ON FUNCTION fn_decider_regle_delai(integer, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_decider_regle_delai(integer, text, text) TO emine_app;

-- Une demande garde la règle en vigueur à son dépôt.
ALTER TABLE demande ADD COLUMN regle_delai_version integer REFERENCES version_regle_delai(version);
COMMENT ON COLUMN demande.regle_delai_version IS
  'Version de la règle des délais en vigueur au dépôt (V27), figée ensuite';
UPDATE demande SET regle_delai_version = 1 WHERE statut <> 'brouillon';

CREATE FUNCTION fn_demande_regle_delai() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF NEW.statut <> 'brouillon' AND (TG_OP = 'INSERT' OR OLD.statut = 'brouillon') THEN
    NEW.regle_delai_version := fn_regle_delai_version_en_vigueur();
  ELSIF TG_OP = 'UPDATE' AND OLD.statut <> 'brouillon'
        AND NEW.regle_delai_version IS DISTINCT FROM OLD.regle_delai_version THEN
    RAISE EXCEPTION 'Demande % : la règle des délais du dépôt ne se modifie pas', OLD.id
      USING ERRCODE = 'P0001', HINT = 'DEM-001';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_demande_regle_delai BEFORE INSERT OR UPDATE ON demande
  FOR EACH ROW EXECUTE FUNCTION fn_demande_regle_delai();

-- Règle applicable à une demande : celle de son dépôt (celle en vigueur pour un brouillon).
CREATE FUNCTION fn_regle_delai(p_demande bigint) RETURNS version_regle_delai
LANGUAGE sql STABLE AS
$$
  SELECT r.* FROM version_regle_delai r
   WHERE r.version = COALESCE((SELECT d.regle_delai_version FROM demande d WHERE d.id = p_demande),
                              fn_regle_delai_version_en_vigueur())
$$;

-- Calculs des délais (V17) : la règle de la demande au lieu de la règle unique.
CREATE OR REPLACE FUNCTION fn_depart_delai(p_demande bigint) RETURNS timestamptz
LANGUAGE sql STABLE AS
$$
  SELECT CASE
           WHEN d.date_depot IS NULL THEN NULL
           WHEN p.depart = 'depot' THEN d.date_depot
           WHEN fn_tarif_en_vigueur(d.type_document_id, 'FRAIS_DOSSIER') IS NULL
                AND fn_tarif_en_vigueur(d.type_document_id, 'ATTESTATION') IS NULL THEN d.date_depot
           ELSE (SELECT min(pa.date_paiement) FROM paiement pa
                  WHERE pa.demande_id = d.id AND pa.objet IN ('FRAIS_DOSSIER', 'ATTESTATION')
                    AND pa.statut IN ('paye', 'remboursement_demande', 'rembourse'))
         END
    FROM demande d CROSS JOIN LATERAL fn_regle_delai(d.id) p
   WHERE d.id = p_demande;
$$;

CREATE OR REPLACE FUNCTION fn_jours_pause(p_demande bigint, p_depuis timestamptz, p_jusqua timestamptz)
RETURNS integer LANGUAGE sql STABLE AS
$$
  WITH p AS (SELECT * FROM fn_regle_delai(p_demande)),
  periodes AS (
    SELECT c.demande_le AS debut, COALESCE(c.repondu_le, c.clos_le, p_jusqua) AS fin
      FROM complement c, p
     WHERE p.pause_complement AND c.demande_id = p_demande
    UNION ALL
    SELECT h.created_at,
           COALESCE((SELECT h2.created_at FROM historique_statut_demande h2
                      WHERE h2.demande_id = p_demande AND h2.id > h.id
                      ORDER BY h2.id LIMIT 1),
                    p_jusqua)
      FROM historique_statut_demande h, p
     WHERE p.pause_droits AND h.demande_id = p_demande AND h.nouveau_statut = 'decision')
  SELECT COALESCE(sum(greatest(0, fn_jours_conakry(greatest(debut, p_depuis), least(fin, p_jusqua)))), 0)::integer
    FROM periodes
   WHERE debut < p_jusqua;
$$;

CREATE OR REPLACE FUNCTION fn_motif_pause(p_demande bigint) RETURNS text
LANGUAGE sql STABLE AS
$$
  SELECT CASE
           WHEN d.statut = 'complement_requis' AND p.pause_complement THEN 'complement'
           WHEN d.statut = 'decision' AND p.pause_droits THEN 'droits'
         END
    FROM demande d CROSS JOIN LATERAL fn_regle_delai(d.id) p
   WHERE d.id = p_demande;
$$;

-- Seuil « proche du dépassement » : celui de la règle de la demande.
DROP FUNCTION fn_seuil_proche(integer);
CREATE FUNCTION fn_seuil_proche(p_demande bigint) RETURNS integer
LANGUAGE sql STABLE AS
$$
  SELECT greatest(p.seuil_proche_min_jours, ceil(d.delai_jours * p.seuil_proche_pourcent / 100.0))::integer
    FROM demande d CROSS JOIN LATERAL fn_regle_delai(d.id) p
   WHERE d.id = p_demande;
$$;
