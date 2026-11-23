-- e-mine · V18 — Fiches des documents versionnées, approuvées par le Ministre (US-02.2, US-02.4).
-- Cahier des charges (application, diapo 14) : « Catalogue, pièces, tarifs et délais sont paramétrés
-- par l'Administrateur et approuvés par le Ministère (arrêté) » ; écran de l'administrateur (diapo
-- 32) : version de chaque fiche, « Publié » ou « À valider », toute modification tracée.
-- 1. L'administrateur (droit PARAMETRER) propose une nouvelle version de la fiche : libellés,
--    pièces, délai, instruit par, signataire, avis requis, document actif ou non. Motif obligatoire.
-- 2. Le Ministre (droit VALIDER_CATALOGUE) l'approuve ou la refuse (motif). L'administrateur peut
--    retirer sa proposition tant qu'elle n'est pas décidée. Une seule proposition ouverte à la fois.
-- 3. À l'approbation, la base applique la version à la fiche (type_document, avis requis) ; plus
--    aucune modification directe de la fiche par l'application.
-- 4. Choix du porteur du projet du 23/11/2026 : une demande garde la fiche en vigueur à son dépôt
--    (délai, avis requis, signataire) ; une désactivation n'arrête pas les demandes en cours.
-- Ajouts au schéma fourni, à signaler au Ministère.

-- 1. Droit d'approuver le catalogue : le Ministre (matrice des droits, D2).
INSERT INTO permission (code, libelle) VALUES ('VALIDER_CATALOGUE', 'Approuver le catalogue et les tarifs');
INSERT INTO profil_permission (profil_id, permission_id)
SELECT p.id, pm.id FROM profil p, permission pm
 WHERE p.code = 'MINISTRE' AND pm.code = 'VALIDER_CATALOGUE';

-- Le profil connecté a-t-il ce droit ? (matrice des droits, lue sous la session de l'appelant)
CREATE FUNCTION fn_a_permission(p_code text) RETURNS boolean LANGUAGE sql STABLE AS
$$
  SELECT EXISTS (SELECT 1 FROM utilisateur u
                   JOIN profil_permission pp ON pp.profil_id = u.profil_id
                   JOIN permission pm ON pm.id = pp.permission_id
                  WHERE u.id = fn_current_user_id() AND pm.code = p_code)
$$;

-- 2. Titres des signataires (D4) : libellé affiché sur le document et profil qui décide et signe.
CREATE TABLE titre_signataire (
  libelle    text PRIMARY KEY,
  profil_id  smallint NOT NULL REFERENCES profil(id),
  ordre      smallint NOT NULL UNIQUE
);
INSERT INTO titre_signataire (libelle, profil_id, ordre)
SELECT v.libelle, p.id, v.ordre
  FROM (VALUES ('Ministre', 'MINISTRE', 1), ('Directeur général', 'DIRECTEUR', 2),
               ('Directeur national', 'DIRECTEUR', 3), ('Chef du BNE', 'DIRECTEUR', 4))
       AS v(libelle, profil, ordre)
  JOIN profil p ON p.code = v.profil;
COMMENT ON TABLE titre_signataire IS 'Signataires possibles d''un document du catalogue (D4)';
GRANT SELECT ON titre_signataire TO emine_readonly;

-- 3. Versions des fiches.
CREATE TABLE version_document (
  id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  type_document_id     smallint NOT NULL REFERENCES type_document(id),
  numero               smallint NOT NULL CHECK (numero > 0),
  statut               text NOT NULL DEFAULT 'a_valider'
                         CHECK (statut IN ('a_valider', 'publiee', 'remplacee', 'refusee', 'retiree')),
  libelle_fr           text NOT NULL CHECK (char_length(btrim(libelle_fr)) BETWEEN 3 AND 150),
  libelle_en           text CHECK (libelle_en IS NULL OR char_length(btrim(libelle_en)) BETWEEN 3 AND 150),
  pieces_cles          text NOT NULL CHECK (char_length(btrim(pieces_cles)) BETWEEN 3 AND 2000),
  delai_jours          smallint NOT NULL CHECK (delai_jours BETWEEN 1 AND 365),
  instruit_par         text NOT NULL CHECK (char_length(btrim(instruit_par)) BETWEEN 2 AND 150),
  signe_par            text NOT NULL REFERENCES titre_signataire(libelle),
  signataire_profil_id smallint NOT NULL REFERENCES profil(id),
  avis_profils         smallint[] NOT NULL DEFAULT '{}',
  actif                boolean NOT NULL,
  motif                text NOT NULL CHECK (char_length(btrim(motif)) BETWEEN 10 AND 2000),
  proposee_par         bigint REFERENCES utilisateur(id),
  proposee_le          timestamptz NOT NULL DEFAULT now(),
  decidee_par          bigint REFERENCES utilisateur(id),
  decidee_le           timestamptz,
  motif_refus          text CHECK (motif_refus IS NULL OR char_length(btrim(motif_refus)) BETWEEN 10 AND 2000),
  UNIQUE (type_document_id, numero),
  CHECK ((statut = 'refusee') = (motif_refus IS NOT NULL)),
  CHECK (statut = 'a_valider' OR decidee_le IS NOT NULL)
);

CREATE UNIQUE INDEX version_document_une_ouverte ON version_document (type_document_id)
  WHERE statut = 'a_valider';
CREATE UNIQUE INDEX version_document_une_publiee ON version_document (type_document_id)
  WHERE statut = 'publiee';

COMMENT ON TABLE version_document IS
  'Versions de la fiche d''un document : proposées par l''administrateur, approuvées par le Ministre';
COMMENT ON COLUMN version_document.motif IS
  'Objet de la modification (motif de la désactivation le cas échéant), obligatoire';
COMMENT ON COLUMN version_document.avis_profils IS
  'Profils qui rendent un avis technique avant la décision, dans l''ordre';

-- Version 1 de chaque fiche : le catalogue actuel, publié (données de test du schéma fourni).
INSERT INTO version_document (type_document_id, numero, statut, libelle_fr, libelle_en, pieces_cles,
                              delai_jours, instruit_par, signe_par, signataire_profil_id,
                              avis_profils, actif, motif, proposee_le, decidee_le)
SELECT td.id, 1, 'publiee', td.libelle_fr, td.libelle_en, td.pieces_cles, td.delai_jours,
       td.instruit_par, td.signe_par, td.signataire_profil_id,
       COALESCE((SELECT array_agg(a.profil_id ORDER BY a.ordre) FROM type_document_avis a
                  WHERE a.type_document_id = td.id), '{}'),
       td.actif, 'Catalogue initial (schéma fourni par le Ministère)', td.created_at, td.created_at
  FROM type_document td;

-- 4. Contrôles : contenu figé, transitions réservées aux droits de la matrice, numéro et signataire
--    posés par la base.
CREATE FUNCTION fn_version_document_controle() RETURNS trigger LANGUAGE plpgsql AS
$$
DECLARE
  p smallint;
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NOT fn_a_permission('PARAMETRER') OR NEW.proposee_par IS DISTINCT FROM fn_current_user_id() THEN
      RAISE EXCEPTION 'Seul l''administrateur propose une version du catalogue'
        USING ERRCODE = 'P0001', HINT = 'PERM-001';
    END IF;
    IF NEW.statut <> 'a_valider' OR NEW.decidee_par IS NOT NULL OR NEW.decidee_le IS NOT NULL THEN
      RAISE EXCEPTION 'Une version proposée attend l''approbation du Ministre'
        USING ERRCODE = 'P0001', HINT = 'DOC-009';
    END IF;
    -- Concurrence : les index uniques (une proposition ouverte, numéro) refusent le second insert.
    SELECT COALESCE(max(numero), 0) + 1 INTO NEW.numero
      FROM version_document WHERE type_document_id = NEW.type_document_id;
    NEW.proposee_le := now();
    SELECT profil_id INTO NEW.signataire_profil_id FROM titre_signataire WHERE libelle = NEW.signe_par;
    -- Avis : profils internes qui instruisent (hors guichet), sans doublon.
    FOREACH p IN ARRAY NEW.avis_profils LOOP
      IF NOT EXISTS (SELECT 1 FROM profil pr
                       JOIN profil_permission pp ON pp.profil_id = pr.id
                       JOIN permission pm ON pm.id = pp.permission_id
                      WHERE pr.id = p AND pr.est_interne AND pr.code <> 'GUICHET'
                        AND pm.code = 'INSTRUIRE') THEN
        RAISE EXCEPTION 'Profil % : ne rend pas d''avis technique', p
          USING ERRCODE = 'P0001', HINT = 'VAL-001';
      END IF;
    END LOOP;
    IF cardinality(NEW.avis_profils) <> (SELECT count(DISTINCT x) FROM unnest(NEW.avis_profils) x) THEN
      RAISE EXCEPTION 'Un profil ne rend qu''un avis' USING ERRCODE = 'P0001', HINT = 'VAL-001';
    END IF;
    RETURN NEW;
  END IF;

  IF (NEW.type_document_id, NEW.numero, NEW.libelle_fr, NEW.libelle_en, NEW.pieces_cles,
      NEW.delai_jours, NEW.instruit_par, NEW.signe_par, NEW.signataire_profil_id, NEW.avis_profils,
      NEW.actif, NEW.motif, NEW.proposee_par, NEW.proposee_le)
     IS DISTINCT FROM
     (OLD.type_document_id, OLD.numero, OLD.libelle_fr, OLD.libelle_en, OLD.pieces_cles,
      OLD.delai_jours, OLD.instruit_par, OLD.signe_par, OLD.signataire_profil_id, OLD.avis_profils,
      OLD.actif, OLD.motif, OLD.proposee_par, OLD.proposee_le) THEN
    RAISE EXCEPTION 'Version % : une version proposée ne se modifie pas', OLD.id
      USING ERRCODE = 'P0001', HINT = 'DOC-009';
  END IF;
  IF NEW.statut = OLD.statut THEN
    RAISE EXCEPTION 'Version % : statut inchangé', OLD.id USING ERRCODE = 'P0001', HINT = 'DOC-009';
  END IF;
  -- Remplacement d'une version publiée : seulement par la publication de la suivante (point 5).
  IF OLD.statut = 'publiee' AND NEW.statut = 'remplacee' AND pg_trigger_depth() > 1 THEN
    RETURN NEW;
  END IF;
  IF OLD.statut <> 'a_valider' THEN
    RAISE EXCEPTION 'Version % : déjà décidée', OLD.id USING ERRCODE = 'P0001', HINT = 'DOC-009';
  END IF;
  IF NEW.statut IN ('publiee', 'refusee') THEN
    IF NOT fn_a_permission('VALIDER_CATALOGUE') THEN
      RAISE EXCEPTION 'Seul le Ministre approuve le catalogue' USING ERRCODE = 'P0001', HINT = 'PERM-001';
    END IF;
  ELSIF NEW.statut = 'retiree' THEN
    IF NOT fn_a_permission('PARAMETRER') THEN
      RAISE EXCEPTION 'Seul l''administrateur retire sa proposition'
        USING ERRCODE = 'P0001', HINT = 'PERM-001';
    END IF;
  ELSE
    RAISE EXCEPTION 'Version % : % -> % interdit', OLD.id, OLD.statut, NEW.statut
      USING ERRCODE = 'P0001', HINT = 'DOC-009';
  END IF;
  NEW.decidee_par := fn_current_user_id();
  NEW.decidee_le := now();
  -- La version publiée jusque-là est remplacée avant (une seule version publiée par document).
  IF NEW.statut = 'publiee' THEN
    UPDATE version_document SET statut = 'remplacee'
     WHERE type_document_id = NEW.type_document_id AND statut = 'publiee';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER trg_version_document_controle BEFORE INSERT OR UPDATE ON version_document
  FOR EACH ROW EXECUTE FUNCTION fn_version_document_controle();
CREATE TRIGGER trg_version_document_audit AFTER INSERT OR UPDATE ON version_document
  FOR EACH ROW EXECUTE FUNCTION fn_audit();
CREATE TRIGGER trg_version_document_nodelete BEFORE DELETE ON version_document
  FOR EACH ROW EXECUTE FUNCTION fn_interdire_modification();

-- 5. Publication : la version approuvée devient la fiche en vigueur. Exécutée en propriétaire du
--    schéma (l'application n'écrit plus la fiche directement) ; le journal d'audit garde l'auteur de
--    la session et les anciennes et nouvelles valeurs.
CREATE FUNCTION fn_version_document_publier() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = emine, pg_temp AS
$$
BEGIN
  UPDATE type_document
     SET libelle_fr = NEW.libelle_fr, libelle_en = NEW.libelle_en, pieces_cles = NEW.pieces_cles,
         delai_jours = NEW.delai_jours, instruit_par = NEW.instruit_par, signe_par = NEW.signe_par,
         signataire_profil_id = NEW.signataire_profil_id, actif = NEW.actif
   WHERE id = NEW.type_document_id;

  DELETE FROM type_document_avis
   WHERE type_document_id = NEW.type_document_id AND NOT (profil_id = ANY (NEW.avis_profils));
  INSERT INTO type_document_avis (type_document_id, profil_id, ordre)
  SELECT NEW.type_document_id, a.profil_id, a.ordre::smallint
    FROM unnest(NEW.avis_profils) WITH ORDINALITY AS a(profil_id, ordre)
  ON CONFLICT (type_document_id, profil_id) DO UPDATE SET ordre = EXCLUDED.ordre
   WHERE type_document_avis.ordre IS DISTINCT FROM EXCLUDED.ordre;

  -- Fourchette indicative du workflow : déduite des délais des documents actifs de la famille.
  UPDATE workflow w
     SET delai_min_jours = s.mini, delai_max_jours = s.maxi
    FROM (SELECT workflow_id, min(delai_jours) AS mini, max(delai_jours) AS maxi
            FROM type_document WHERE actif AND deleted_at IS NULL GROUP BY workflow_id) s
   WHERE s.workflow_id = w.id
     AND w.id = (SELECT workflow_id FROM type_document WHERE id = NEW.type_document_id)
     AND (w.delai_min_jours, w.delai_max_jours) IS DISTINCT FROM (s.mini, s.maxi);
  RETURN NULL;
END $$;
REVOKE ALL ON FUNCTION fn_version_document_publier() FROM PUBLIC;

CREATE TRIGGER trg_version_document_publier AFTER UPDATE OF statut ON version_document
  FOR EACH ROW WHEN (NEW.statut = 'publiee' AND OLD.statut = 'a_valider')
  EXECUTE FUNCTION fn_version_document_publier();

-- La fiche ne se modifie plus que par une version approuvée.
REVOKE INSERT, UPDATE ON type_document, type_document_avis, titre_signataire FROM emine_app;

-- RLS : les versions publiées (ou remplacées) sont publiques comme le catalogue ; les propositions
-- ne sont visibles que des profils internes.
ALTER TABLE version_document ENABLE ROW LEVEL SECURITY;
CREATE POLICY version_document_lecture_app ON version_document FOR SELECT TO emine_app
  USING (statut IN ('publiee', 'remplacee') OR fn_est_interne());
CREATE POLICY version_document_creation ON version_document FOR INSERT TO emine_app
  WITH CHECK (fn_est_interne());
CREATE POLICY version_document_maj ON version_document FOR UPDATE TO emine_app
  USING (fn_est_interne()) WITH CHECK (fn_est_interne());
CREATE POLICY version_document_lecture ON version_document FOR SELECT TO emine_readonly USING (true);
GRANT SELECT ON version_document TO emine_readonly;

-- 6. Une demande garde la fiche en vigueur à son dépôt : version, délai et signataire figés.
ALTER TABLE demande
  ADD COLUMN version_document_id  bigint REFERENCES version_document(id),
  ADD COLUMN delai_jours          smallint,
  ADD COLUMN signataire_profil_id smallint REFERENCES profil(id);

COMMENT ON COLUMN demande.version_document_id IS
  'Version de la fiche en vigueur au dépôt : délai, avis requis et signataire de cette demande';

UPDATE demande d
   SET version_document_id = v.id, delai_jours = v.delai_jours,
       signataire_profil_id = v.signataire_profil_id
  FROM version_document v
 WHERE v.type_document_id = d.type_document_id AND v.statut = 'publiee' AND d.statut <> 'brouillon';

ALTER TABLE demande ADD CONSTRAINT demande_fiche_au_depot CHECK (
  statut = 'brouillon'
  OR (version_document_id IS NOT NULL AND delai_jours IS NOT NULL AND signataire_profil_id IS NOT NULL));

CREATE OR REPLACE FUNCTION fn_demande_avant() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF NEW.statut <> 'brouillon' AND NEW.numero IS NULL THEN
    NEW.numero := fn_next_numero('DEM', 6);
  END IF;
  IF NEW.statut = 'deposee' AND NEW.date_depot IS NULL THEN
    NEW.date_depot := now();
  END IF;
  -- Fiche en vigueur au dépôt (V18), figée ensuite ; un document désactivé ne se dépose plus.
  IF NEW.statut <> 'brouillon' AND NEW.version_document_id IS NULL THEN
    SELECT v.id, v.delai_jours, v.signataire_profil_id
      INTO NEW.version_document_id, NEW.delai_jours, NEW.signataire_profil_id
      FROM version_document v
     WHERE v.type_document_id = NEW.type_document_id AND v.statut = 'publiee' AND v.actif;
    IF NEW.version_document_id IS NULL THEN
      RAISE EXCEPTION 'Document % retiré du catalogue', NEW.type_document_id
        USING ERRCODE = 'P0001', HINT = 'DEM-005';
    END IF;
  ELSIF TG_OP = 'UPDATE' AND (NEW.version_document_id, NEW.delai_jours, NEW.signataire_profil_id)
        IS DISTINCT FROM (OLD.version_document_id, OLD.delai_jours, OLD.signataire_profil_id)
        AND OLD.version_document_id IS NOT NULL THEN
    RAISE EXCEPTION 'Demande % : la fiche du dépôt ne se modifie pas', OLD.id
      USING ERRCODE = 'P0001', HINT = 'DEM-001';
  END IF;
  RETURN NEW;
END $$;

-- 7. Avis requis d'une demande : ceux de la fiche de son dépôt (ceux du catalogue pour un brouillon).
CREATE FUNCTION fn_avis_requis(p_demande bigint) RETURNS smallint[] LANGUAGE sql STABLE AS
$$
  SELECT COALESCE(v.avis_profils,
                  (SELECT COALESCE(array_agg(a.profil_id ORDER BY a.ordre), '{}')
                     FROM type_document_avis a WHERE a.type_document_id = d.type_document_id))
    FROM demande d LEFT JOIN version_document v ON v.id = d.version_document_id
   WHERE d.id = p_demande
$$;

CREATE OR REPLACE FUNCTION fn_avis_manquants(p_demande bigint) RETURNS integer LANGUAGE sql STABLE AS
$$
  SELECT count(*)::integer
    FROM unnest(fn_avis_requis(p_demande)) AS r(profil_id)
   WHERE NOT EXISTS (SELECT 1 FROM avis_decision a
                      WHERE a.demande_id = p_demande AND a.nature = 'avis'
                        AND a.profil_id = r.profil_id)
$$;
