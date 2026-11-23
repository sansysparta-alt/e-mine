-- e-mine · V20 — Workflows paramétrables et versionnés (US-02.5). Cahier des charges (diapo 15) :
-- « Les étapes, délais et paiements sont paramétrables par workflow ». Choix du porteur du projet du
-- 23/11/2026 : dans ce sprint, les étapes servent à l'affichage (le circuit réel reste conduit par
-- les statuts, les avis requis et le signataire de chaque fiche) ; une modification est immédiate et
-- tracée, sans approbation. Chaque modification crée une version (auteur, date, motif) ; une
-- demande garde la version du workflow de son dépôt (demande.workflow_version, schéma fourni).
-- La fourchette de délais d'un workflow est déduite des délais de ses documents (V18), approuvés par
-- le Ministre. Ajouts au schéma fourni, à signaler au Ministère.

-- 1. Versions : libellé, règle de paiement affichée, motif, auteur.
CREATE TABLE version_workflow (
  id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  workflow_id    smallint NOT NULL REFERENCES workflow(id),
  version        integer NOT NULL CHECK (version > 0),
  libelle        text NOT NULL CHECK (char_length(btrim(libelle)) BETWEEN 3 AND 150),
  regle_paiement text NOT NULL CHECK (char_length(btrim(regle_paiement)) BETWEEN 3 AND 300),
  motif          text NOT NULL CHECK (char_length(btrim(motif)) BETWEEN 10 AND 2000),
  modifiee_par   bigint REFERENCES utilisateur(id),
  modifiee_le    timestamptz NOT NULL DEFAULT now(),
  UNIQUE (workflow_id, version)
);
COMMENT ON TABLE version_workflow IS
  'Versions d''un workflow : libellé, règle de paiement affichée, motif et auteur de la modification';

INSERT INTO version_workflow (workflow_id, version, libelle, regle_paiement, motif, modifiee_le)
SELECT id, version, libelle, regle_paiement, 'Workflow initial (schéma fourni par le Ministère)', created_at
  FROM workflow;

-- 2. Étapes par version (les étapes d'une version ne changent plus).
ALTER TABLE workflow_etape ADD COLUMN version integer;
UPDATE workflow_etape e SET version = w.version FROM workflow w WHERE w.id = e.workflow_id;
ALTER TABLE workflow_etape ALTER COLUMN version SET NOT NULL;
ALTER TABLE workflow_etape DROP CONSTRAINT workflow_etape_workflow_id_ordre_key;
ALTER TABLE workflow_etape ADD CONSTRAINT workflow_etape_version_ordre UNIQUE (workflow_id, version, ordre);
ALTER TABLE workflow_etape ADD CONSTRAINT workflow_etape_version_fk
  FOREIGN KEY (workflow_id, version) REFERENCES version_workflow (workflow_id, version);
ALTER TABLE workflow_etape
  ADD CONSTRAINT workflow_etape_acteur_longueur CHECK (char_length(btrim(acteur)) BETWEEN 2 AND 100),
  ADD CONSTRAINT workflow_etape_libelle_longueur CHECK (char_length(btrim(libelle)) BETWEEN 2 AND 150);

-- 3. Modification : une seule fonction, droits contrôlés en base (administrateur), nouvelle version
--    complète (libellé, règle de paiement, étapes dans l'ordre), motif obligatoire.
CREATE FUNCTION fn_modifier_workflow(p_code text, p_libelle text, p_regle_paiement text,
                                     p_etapes jsonb, p_motif text) RETURNS integer
LANGUAGE plpgsql SECURITY DEFINER SET search_path = emine, pg_temp AS
$$
DECLARE
  w workflow%ROWTYPE;
  v integer;
  n integer;
  e jsonb;
  i integer := 0;
  identiques boolean;
BEGIN
  IF NOT fn_a_permission('PARAMETRER') OR fn_current_user_id() IS NULL THEN
    RAISE EXCEPTION 'Seul l''administrateur modifie un workflow' USING ERRCODE = 'P0001', HINT = 'PERM-001';
  END IF;
  SELECT * INTO w FROM workflow WHERE code = p_code FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Workflow % inconnu', p_code USING ERRCODE = 'P0001', HINT = 'SYS-002';
  END IF;
  n := jsonb_array_length(COALESCE(p_etapes, '[]'::jsonb));
  IF n NOT BETWEEN 1 AND 12 THEN
    RAISE EXCEPTION 'Un workflow a de 1 à 12 étapes' USING ERRCODE = 'P0001', HINT = 'VAL-001';
  END IF;
  -- Rien ne change ? (libellé, règle, étapes dans l'ordre)
  SELECT w.libelle = btrim(p_libelle) AND w.regle_paiement = btrim(p_regle_paiement)
         AND (SELECT jsonb_agg(jsonb_build_object('acteur', acteur, 'libelle', libelle) ORDER BY ordre)
                FROM workflow_etape WHERE workflow_id = w.id AND version = w.version)
             = (SELECT jsonb_agg(jsonb_build_object('acteur', btrim(x->>'acteur'), 'libelle', btrim(x->>'libelle'))
                                 ORDER BY o)
                  FROM jsonb_array_elements(p_etapes) WITH ORDINALITY AS t(x, o))
    INTO identiques;
  IF identiques THEN
    RAISE EXCEPTION 'Workflow % : aucune modification', p_code USING ERRCODE = 'P0001', HINT = 'DOC-010';
  END IF;
  v := w.version + 1;
  INSERT INTO version_workflow (workflow_id, version, libelle, regle_paiement, motif, modifiee_par)
  VALUES (w.id, v, btrim(p_libelle), btrim(p_regle_paiement), btrim(p_motif), fn_current_user_id());
  FOR e IN SELECT x FROM jsonb_array_elements(p_etapes) AS t(x) LOOP
    i := i + 1;
    INSERT INTO workflow_etape (workflow_id, ordre, acteur, libelle, version)
    VALUES (w.id, i, btrim(e->>'acteur'), btrim(e->>'libelle'), v);
  END LOOP;
  UPDATE workflow SET libelle = btrim(p_libelle), regle_paiement = btrim(p_regle_paiement), version = v
   WHERE id = w.id;
  RETURN v;
END $$;
REVOKE ALL ON FUNCTION fn_modifier_workflow(text, text, text, jsonb, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_modifier_workflow(text, text, text, jsonb, text) TO emine_app;

-- L'application ne modifie les workflows que par cette fonction ; tout est tracé.
REVOKE INSERT, UPDATE ON workflow, workflow_etape, version_workflow FROM emine_app;
CREATE TRIGGER trg_workflow_audit AFTER INSERT OR UPDATE OR DELETE ON workflow
  FOR EACH ROW EXECUTE FUNCTION fn_audit();
CREATE TRIGGER trg_version_workflow_audit AFTER INSERT ON version_workflow
  FOR EACH ROW EXECUTE FUNCTION fn_audit();
CREATE TRIGGER trg_workflow_etape_audit AFTER INSERT ON workflow_etape
  FOR EACH ROW EXECUTE FUNCTION fn_audit();
CREATE TRIGGER trg_version_workflow_nodelete BEFORE UPDATE OR DELETE ON version_workflow
  FOR EACH ROW EXECUTE FUNCTION fn_interdire_modification();
CREATE TRIGGER trg_workflow_etape_nodelete BEFORE UPDATE OR DELETE ON workflow_etape
  FOR EACH ROW EXECUTE FUNCTION fn_interdire_modification();
GRANT SELECT ON version_workflow TO emine_readonly;

-- 4. Une demande garde la version du workflow de son dépôt.
CREATE FUNCTION fn_demande_workflow_version() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF NEW.statut <> 'brouillon' AND (TG_OP = 'INSERT' OR OLD.statut = 'brouillon') THEN
    SELECT w.version INTO NEW.workflow_version
      FROM type_document td JOIN workflow w ON w.id = td.workflow_id
     WHERE td.id = NEW.type_document_id;
  ELSIF TG_OP = 'UPDATE' AND OLD.statut <> 'brouillon'
        AND NEW.workflow_version IS DISTINCT FROM OLD.workflow_version THEN
    RAISE EXCEPTION 'Demande % : la version du workflow du dépôt ne se modifie pas', OLD.id
      USING ERRCODE = 'P0001', HINT = 'DEM-001';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_demande_workflow_version BEFORE INSERT OR UPDATE ON demande
  FOR EACH ROW EXECUTE FUNCTION fn_demande_workflow_version();
