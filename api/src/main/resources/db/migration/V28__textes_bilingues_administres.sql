-- e-mine · V28 — Saisie bilingue FR/EN des textes de l'administrateur (lot 4, US-02.9). Cahier des
-- charges : « chaque page et chaque e-Service existent dans les deux langues ». Les textes saisis
-- par l'administrateur ont désormais leur version anglaise : pièces requises et service instructeur
-- d'une fiche, nom, paiements affichés et étapes d'un workflow. L'anglais est facultatif (repli sur
-- le français) ; il fait partie de la version proposée, validée par le Ministre et figée ensuite.
-- Ajouts au schéma fourni, à signaler au Ministère.

-- =================================================================================================
-- 1. Fiches des documents
-- =================================================================================================

ALTER TABLE version_document
  ADD COLUMN pieces_cles_en  text
    CHECK (pieces_cles_en IS NULL OR char_length(btrim(pieces_cles_en)) BETWEEN 3 AND 2000),
  ADD COLUMN instruit_par_en text
    CHECK (instruit_par_en IS NULL OR char_length(btrim(instruit_par_en)) BETWEEN 2 AND 150);
ALTER TABLE type_document
  ADD COLUMN pieces_cles_en  text,
  ADD COLUMN instruit_par_en text;
COMMENT ON COLUMN version_document.pieces_cles_en IS
  'Pièces requises en anglais, dans le même ordre que pieces_cles (facultatif : repli sur le français)';

-- Le texte anglais d'une version ne change plus après sa proposition (comme le reste, V18).
CREATE FUNCTION fn_version_document_anglais_fige() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF (NEW.pieces_cles_en, NEW.instruit_par_en) IS DISTINCT FROM (OLD.pieces_cles_en, OLD.instruit_par_en) THEN
    RAISE EXCEPTION 'Version % : une version proposée ne se modifie pas', OLD.id
      USING ERRCODE = 'P0001', HINT = 'DOC-009';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_version_document_anglais_fige BEFORE UPDATE ON version_document
  FOR EACH ROW EXECUTE FUNCTION fn_version_document_anglais_fige();

-- À la publication, la fiche en vigueur reçoit aussi les textes anglais : même mise à jour que V18
-- (une seule trace au journal d'audit, avec l'ancienne et la nouvelle valeur).
CREATE OR REPLACE FUNCTION fn_version_document_publier() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = emine, pg_temp AS
$$
BEGIN
  UPDATE type_document
     SET libelle_fr = NEW.libelle_fr, libelle_en = NEW.libelle_en, pieces_cles = NEW.pieces_cles,
         delai_jours = NEW.delai_jours, instruit_par = NEW.instruit_par, signe_par = NEW.signe_par,
         signataire_profil_id = NEW.signataire_profil_id, actif = NEW.actif,
         pieces_cles_en = NEW.pieces_cles_en, instruit_par_en = NEW.instruit_par_en
   WHERE id = NEW.type_document_id;

  DELETE FROM type_document_avis
   WHERE type_document_id = NEW.type_document_id AND NOT (profil_id = ANY (NEW.avis_profils));
  INSERT INTO type_document_avis (type_document_id, profil_id, ordre)
  SELECT NEW.type_document_id, a.profil_id, a.ordre::smallint
    FROM unnest(NEW.avis_profils) WITH ORDINALITY AS a(profil_id, ordre)
  ON CONFLICT (type_document_id, profil_id) DO UPDATE SET ordre = EXCLUDED.ordre
   WHERE type_document_avis.ordre IS DISTINCT FROM EXCLUDED.ordre;

  UPDATE workflow w
     SET delai_min_jours = s.mini, delai_max_jours = s.maxi
    FROM (SELECT workflow_id, min(delai_jours) AS mini, max(delai_jours) AS maxi
            FROM type_document WHERE actif AND deleted_at IS NULL GROUP BY workflow_id) s
   WHERE s.workflow_id = w.id
     AND w.id = (SELECT workflow_id FROM type_document WHERE id = NEW.type_document_id)
     AND (w.delai_min_jours, w.delai_max_jours) IS DISTINCT FROM (s.mini, s.maxi);
  RETURN NULL;
END $$;

-- Traduction des fiches initiales (données de référence) : seulement si le texte français est encore
-- celui du schéma fourni. Simple complément de traduction, sans nouvelle version (comme V22).
CREATE TEMPORARY TABLE fiche_anglais (code char(3) PRIMARY KEY, pieces_fr text, pieces_en text,
                                      instruit_fr text, instruit_en text) ON COMMIT DROP;
INSERT INTO fiche_anglais VALUES
  ('PRC', 'Statuts, capacités, carte de la zone', 'Articles of association, capabilities, map of the area', 'DNM + Géologue', 'DNM + Geologist'),
  ('PRE', 'Programme de travaux, capacités financières, périmètre', 'Work programme, financial capacity, perimeter', 'DNM + Géologue', 'DNM + Geologist'),
  ('PEX', 'Étude de faisabilité, EIES, plan de restauration', 'Feasibility study, ESIA, rehabilitation plan', 'DNM + Inspection', 'DNM + Inspection'),
  ('CAR', 'Plan de carrière, accord foncier', 'Quarry plan, land agreement', 'DNM', 'DNM'),
  ('CES', 'Acte de cession, attestation des redevances', 'Deed of transfer, royalty certificate', 'DNM + Fiscalité', 'DNM + Tax'),
  ('AEA', 'Identité, accord communautaire, plan du site', 'Identity, community agreement, site plan', 'DNM régionale', 'Regional DNM'),
  ('COR', 'Pièce d''identité, photo, site de rattachement', 'Identity document, photo, assigned site', 'Guichet unique', 'One-stop shop'),
  ('AGC', 'RCCM, attestation fiscale, local', 'Trade register (RCCM), tax certificate, premises', 'DNM + Brigade', 'DNM + Brigade'),
  ('CEX', 'Déclaration du lot, provenance licite', 'Lot declaration, lawful origin', 'BNE', 'BNE'),
  ('AEX', 'Certificat d''expertise, déclaration en douane', 'Appraisal certificate, customs declaration', 'DNM + Fiscalité', 'DNM + Tax'),
  ('KIM', 'Certificat d''expertise, preuve d''origine licite', 'Appraisal certificate, proof of lawful origin', 'BNE + Brigade', 'BNE + Brigade'),
  ('EXC', 'Référence de la zone ou du titre', 'Reference of the area or title', 'Agent DNM', 'DNM officer'),
  ('CCC', 'Numéro du titre, identité du titulaire', 'Title number, holder''s identity', 'Guichet unique', 'One-stop shop'),
  ('ARF', 'Numéro du titre, période', 'Title number, period', 'Responsable fiscal', 'Tax officer'),
  ('PME', 'RCCM, attestation fiscale, effectifs locaux', 'Trade register (RCCM), tax certificate, local workforce', 'Contenu local', 'Local content');

ALTER TABLE version_document DISABLE TRIGGER USER;
UPDATE version_document v
   SET pieces_cles_en = CASE WHEN lower(v.pieces_cles) = lower(a.pieces_fr) THEN a.pieces_en END,
       instruit_par_en = CASE WHEN lower(v.instruit_par) = lower(a.instruit_fr) THEN a.instruit_en END
  FROM type_document td JOIN fiche_anglais a ON a.code = td.code
 WHERE v.type_document_id = td.id;
ALTER TABLE version_document ENABLE TRIGGER USER;

UPDATE type_document td
   SET pieces_cles_en = v.pieces_cles_en, instruit_par_en = v.instruit_par_en
  FROM version_document v
 WHERE v.type_document_id = td.id AND v.statut = 'publiee';

-- =================================================================================================
-- 2. Workflows
-- =================================================================================================

ALTER TABLE version_workflow
  ADD COLUMN libelle_en        text
    CHECK (libelle_en IS NULL OR char_length(btrim(libelle_en)) BETWEEN 3 AND 150),
  ADD COLUMN regle_paiement_en text
    CHECK (regle_paiement_en IS NULL OR char_length(btrim(regle_paiement_en)) BETWEEN 3 AND 300);
ALTER TABLE workflow_etape
  ADD COLUMN acteur_en  text CHECK (acteur_en IS NULL OR char_length(btrim(acteur_en)) BETWEEN 2 AND 100),
  ADD COLUMN libelle_en text CHECK (libelle_en IS NULL OR char_length(btrim(libelle_en)) BETWEEN 2 AND 150);

-- Contenu figé, anglais compris (V27).
CREATE OR REPLACE FUNCTION fn_version_workflow_fige() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF (NEW.workflow_id, NEW.version, NEW.libelle, NEW.regle_paiement, NEW.motif, NEW.modifiee_par,
      NEW.modifiee_le, NEW.libelle_en, NEW.regle_paiement_en)
     IS DISTINCT FROM
     (OLD.workflow_id, OLD.version, OLD.libelle, OLD.regle_paiement, OLD.motif, OLD.modifiee_par,
      OLD.modifiee_le, OLD.libelle_en, OLD.regle_paiement_en) THEN
    RAISE EXCEPTION 'Version % : une version proposée ne se modifie pas', OLD.version
      USING ERRCODE = 'P0001', HINT = 'DOC-009';
  END IF;
  IF OLD.statut <> 'a_valider' THEN
    RAISE EXCEPTION 'Version % : déjà décidée', OLD.version USING ERRCODE = 'P0001', HINT = 'DOC-009';
  END IF;
  RETURN NEW;
END $$;

-- Traduction des workflows initiaux (version 1, si le texte français est inchangé).
CREATE TEMPORARY TABLE workflow_anglais (code text PRIMARY KEY, libelle_fr text, libelle_en text,
                                         regle_fr text, regle_en text) ON COMMIT DROP;
INSERT INTO workflow_anglais VALUES
  ('TITRES', 'Titres miniers', 'Mining titles', 'Frais au dépôt, droits avant délivrance', 'Fees on submission, duties before issue'),
  ('ARTISANAL', 'Artisanal et comptoirs', 'Artisanal mining and trading houses', 'Frais et droits regroupés, TrésorPay ou virement', 'Fees and duties combined, TrésorPay or bank transfer'),
  ('EXPORT', 'Export et expertise', 'Export and appraisal', 'Frais d''expertise puis droits d''export', 'Appraisal fees, then export duties'),
  ('ATTESTATIONS', 'Attestations et copies', 'Certificates and copies', 'Paiement unique avant délivrance', 'Single payment before issue'),
  ('CONTENU_LOCAL', 'Contenu local', 'Local content', 'Paiement unique, renouvellement avec alerte', 'Single payment, renewal with reminder');

CREATE TEMPORARY TABLE etape_anglais (fr text PRIMARY KEY, en text) ON COMMIT DROP;
INSERT INTO etape_anglais VALUES
  ('Guichet unique', 'One-stop shop'), ('Réception et recevabilité', 'Receipt and admissibility'),
  ('DNM', 'DNM'), ('Instruction technique', 'Technical review'), ('Géologue', 'Geologist'),
  ('Avis géologique', 'Geological opinion'), ('Inspection', 'Inspection'),
  ('Inspection de terrain', 'Field inspection'), ('Directeur', 'Director'),
  ('Avis de direction', 'Management opinion'), ('Ministre', 'Minister'),
  ('Décision et signature', 'Decision and signature'), ('DNM régionale ou Brigade', 'Regional DNM or Brigade'),
  ('Instruction', 'Review'), ('BNE', 'BNE'), ('Expertise du lot', 'Lot appraisal'),
  ('DNM, Fiscalité ou Brigade', 'DNM, Tax or Brigade'), ('Contrôle', 'Inspection'),
  ('Signataire', 'Signatory'), ('Signature', 'Signature'),
  ('Guichet ou agent compétent', 'One-stop shop or competent officer'), ('Contenu local', 'Local content');

ALTER TABLE version_workflow DISABLE TRIGGER USER;
UPDATE version_workflow vw
   SET libelle_en = CASE WHEN vw.libelle = a.libelle_fr THEN a.libelle_en END,
       regle_paiement_en = CASE WHEN vw.regle_paiement = a.regle_fr THEN a.regle_en END
  FROM workflow w JOIN workflow_anglais a ON a.code = w.code
 WHERE vw.workflow_id = w.id AND vw.version = 1;
ALTER TABLE version_workflow ENABLE TRIGGER USER;

ALTER TABLE workflow_etape DISABLE TRIGGER USER;
UPDATE workflow_etape e
   SET acteur_en = (SELECT en FROM etape_anglais WHERE fr = e.acteur),
       libelle_en = (SELECT en FROM etape_anglais WHERE fr = e.libelle)
 WHERE e.version = 1;
ALTER TABLE workflow_etape ENABLE TRIGGER USER;

-- Proposition avec les textes anglais (remplace la fonction de V27).
DROP FUNCTION fn_proposer_workflow(text, text, text, jsonb, text, date);

CREATE FUNCTION fn_proposer_workflow(p_code text, p_libelle text, p_regle_paiement text,
                                     p_etapes jsonb, p_motif text, p_date_effet date,
                                     p_libelle_en text, p_regle_paiement_en text)
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
  -- Rien ne change (anglais compris) par rapport à la dernière version validée ?
  SELECT max(version) INTO derniere FROM version_workflow WHERE workflow_id = w.id AND statut = 'publiee';
  SELECT vw.libelle = btrim(p_libelle) AND vw.regle_paiement = btrim(p_regle_paiement)
         AND vw.libelle_en IS NOT DISTINCT FROM NULLIF(btrim(p_libelle_en), '')
         AND vw.regle_paiement_en IS NOT DISTINCT FROM NULLIF(btrim(p_regle_paiement_en), '')
         AND (SELECT jsonb_agg(jsonb_build_object('acteur', acteur, 'libelle', libelle,
                                                  'acteur_en', acteur_en, 'libelle_en', libelle_en)
                               ORDER BY ordre)
                FROM workflow_etape WHERE workflow_id = w.id AND version = derniere)
             = (SELECT jsonb_agg(jsonb_build_object(
                         'acteur', btrim(x->>'acteur'), 'libelle', btrim(x->>'libelle'),
                         'acteur_en', NULLIF(btrim(x->>'acteur_en'), ''),
                         'libelle_en', NULLIF(btrim(x->>'libelle_en'), '')) ORDER BY o)
                  FROM jsonb_array_elements(p_etapes) WITH ORDINALITY AS t(x, o))
    INTO identiques
    FROM version_workflow vw WHERE vw.workflow_id = w.id AND vw.version = derniere;
  IF identiques THEN
    RAISE EXCEPTION 'Workflow % : aucune modification', p_code USING ERRCODE = 'P0001', HINT = 'DOC-010';
  END IF;
  SELECT max(version) + 1 INTO v FROM version_workflow WHERE workflow_id = w.id;
  INSERT INTO version_workflow (workflow_id, version, libelle, regle_paiement, motif, modifiee_par,
                                statut, date_effet, libelle_en, regle_paiement_en)
  VALUES (w.id, v, btrim(p_libelle), btrim(p_regle_paiement), btrim(p_motif), fn_current_user_id(),
          'a_valider', p_date_effet, NULLIF(btrim(p_libelle_en), ''),
          NULLIF(btrim(p_regle_paiement_en), ''));
  FOR e IN SELECT x FROM jsonb_array_elements(p_etapes) AS t(x) LOOP
    i := i + 1;
    INSERT INTO workflow_etape (workflow_id, ordre, acteur, libelle, version, acteur_en, libelle_en)
    VALUES (w.id, i, btrim(e->>'acteur'), btrim(e->>'libelle'), v,
            NULLIF(btrim(e->>'acteur_en'), ''), NULLIF(btrim(e->>'libelle_en'), ''));
  END LOOP;
  RETURN v;
END $$;
REVOKE ALL ON FUNCTION fn_proposer_workflow(text, text, text, jsonb, text, date, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_proposer_workflow(text, text, text, jsonb, text, date, text, text) TO emine_app;
