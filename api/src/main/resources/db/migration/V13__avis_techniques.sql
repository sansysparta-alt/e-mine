-- e-mine · V13 — Avis techniques motivés (US-05.3, choix du porteur du projet du 22/11/2026) :
-- les avis sont requis selon le catalogue. Chaque document liste les profils qui rendent un avis
-- (déduits de « instruit par » : DNM, Géologue, Inspection) ; le signataire ne décide qu'une fois
-- tous ces avis rendus. Un document sans avis requis reste décidé dès la recevabilité (V9).
-- Ajouts au schéma fourni, à signaler au Ministère.

-- 1. Avis requis par document : une donnée du catalogue, jamais codée en dur (même esprit que D4).
CREATE TABLE type_document_avis (
  type_document_id smallint NOT NULL REFERENCES type_document(id),
  profil_id        smallint NOT NULL REFERENCES profil(id),
  ordre            smallint NOT NULL CHECK (ordre > 0),
  PRIMARY KEY (type_document_id, profil_id)
);

COMMENT ON TABLE type_document_avis IS
  'Profils qui rendent un avis technique motivé avant la décision, par document du catalogue';

INSERT INTO type_document_avis (type_document_id, profil_id, ordre)
SELECT td.id, p.id, v.ordre
  FROM type_document td
  JOIN (VALUES ('DNM', 'DNM', 1), ('Géologue', 'GEOLOGUE', 2), ('Inspection', 'INSPECTEUR', 3))
       AS v(mot, profil, ordre) ON position(v.mot IN td.instruit_par) > 0
  JOIN profil p ON p.code = v.profil;

CREATE TRIGGER trg_type_document_avis_audit AFTER INSERT OR UPDATE OR DELETE ON type_document_avis
  FOR EACH ROW EXECUTE FUNCTION fn_audit();
GRANT SELECT ON type_document_avis TO emine_readonly;

-- 2. Avis : profil de l'auteur conservé (il peut changer de poste), un avis par profil et par
--    demande, motif obligatoire.
ALTER TABLE avis_decision ADD COLUMN profil_id smallint REFERENCES profil(id);
UPDATE avis_decision a SET profil_id = u.profil_id FROM utilisateur u WHERE u.id = a.auteur_id;

CREATE FUNCTION fn_avis_profil() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  SELECT profil_id INTO NEW.profil_id FROM utilisateur WHERE id = NEW.auteur_id;
  RETURN NEW;
END $$;

CREATE TRIGGER trg_avis_profil BEFORE INSERT ON avis_decision
  FOR EACH ROW EXECUTE FUNCTION fn_avis_profil();

ALTER TABLE avis_decision ALTER COLUMN profil_id SET NOT NULL;
ALTER TABLE avis_decision ADD CONSTRAINT avis_motive CHECK (nature <> 'avis' OR motif IS NOT NULL);
CREATE UNIQUE INDEX avis_un_par_profil ON avis_decision (demande_id, profil_id) WHERE nature = 'avis';

-- 3. Transitions : rejet motivé après les avis (le schéma fourni ne prévoyait que la décision).
INSERT INTO transition_statut (entite, de, vers) VALUES
  ('demande', 'avis_rendu', 'rejetee')
ON CONFLICT DO NOTHING;

-- 4. Garde : pas de décision sans les avis requis ; « avis rendu » seulement quand ils y sont tous.
CREATE FUNCTION fn_avis_manquants(p_demande bigint) RETURNS integer LANGUAGE sql STABLE AS
$$
  SELECT count(*)::integer
    FROM demande d
    JOIN type_document_avis tda ON tda.type_document_id = d.type_document_id
   WHERE d.id = p_demande
     AND NOT EXISTS (SELECT 1 FROM avis_decision a
                      WHERE a.demande_id = d.id AND a.nature = 'avis'
                        AND a.profil_id = tda.profil_id)
$$;

CREATE FUNCTION fn_demande_condition_decision() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF OLD.statut = 'recevable' AND NEW.statut IN ('decision', 'rejetee')
     AND EXISTS (SELECT 1 FROM type_document_avis WHERE type_document_id = NEW.type_document_id) THEN
    RAISE EXCEPTION 'Demande % : avis techniques requis avant la décision', NEW.id
      USING ERRCODE = 'P0001', HINT = 'DEM-011';
  END IF;
  IF OLD.statut = 'en_instruction' AND NEW.statut = 'avis_rendu'
     AND fn_avis_manquants(NEW.id) > 0 THEN
    RAISE EXCEPTION 'Demande % : avis techniques manquants', NEW.id
      USING ERRCODE = 'P0001', HINT = 'DEM-011';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER trg_demande_condition_decision BEFORE UPDATE OF statut ON demande
  FOR EACH ROW EXECUTE FUNCTION fn_demande_condition_decision();
