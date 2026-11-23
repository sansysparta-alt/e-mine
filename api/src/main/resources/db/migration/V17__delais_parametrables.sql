-- US-05.6 (amélioration des tableaux de bord) : délai de traitement paramétrable, et une seule règle
-- de calcul pour les jours restants, les retards, la part des dossiers délivrés dans le délai et le
-- délai moyen.
--
-- Le cahier des charges ne fixe que des « délais cibles » (2 à 60 jours par document) ; il ne dit
-- ni jours calendaires ou ouvrés, ni point de départ, ni suspension. Choix du porteur du projet du
-- 22/11/2026, à faire valider par le Ministère (docs/01 §3) et donc paramétrables :
--   * jours calendaires, à l'heure de Conakry, jour de départ inclus (comme le délai moyen) ;
--   * départ au paiement des frais de dossier (ou du paiement unique d'une attestation) : avant, le
--     Ministère ne peut pas instruire ; pour un document sans frais au barème, départ au dépôt ;
--   * pause pendant un complément et pendant l'attente du paiement des droits (l'usager doit agir) ;
--   * fin à la délivrance ; « proche du dépassement » à 20 % du délai restant, 2 jours au moins.

-- 1. Paramètres (une seule ligne), modifiables par l'administration du catalogue.
CREATE TABLE parametre_delai (
  id                     smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  depart                 text NOT NULL DEFAULT 'paiement_frais'
                           CHECK (depart IN ('depot', 'paiement_frais')),
  pause_complement       boolean NOT NULL DEFAULT true,
  pause_droits           boolean NOT NULL DEFAULT true,
  seuil_proche_pourcent  smallint NOT NULL DEFAULT 20 CHECK (seuil_proche_pourcent BETWEEN 1 AND 100),
  seuil_proche_min_jours smallint NOT NULL DEFAULT 2 CHECK (seuil_proche_min_jours >= 0),
  updated_at             timestamptz NOT NULL DEFAULT now()
);
INSERT INTO parametre_delai DEFAULT VALUES;
CREATE TRIGGER trg_parametre_delai_maj BEFORE UPDATE ON parametre_delai
  FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
CREATE TRIGGER trg_parametre_delai_audit AFTER INSERT OR UPDATE OR DELETE ON parametre_delai
  FOR EACH ROW EXECUTE FUNCTION fn_audit();
GRANT SELECT ON parametre_delai TO emine_readonly;

-- Jours calendaires (heure de Conakry) entre deux instants.
CREATE FUNCTION fn_jours_conakry(p_depuis timestamptz, p_jusqua timestamptz) RETURNS integer
LANGUAGE sql IMMUTABLE AS
$$ SELECT (p_jusqua AT TIME ZONE 'Africa/Conakry')::date - (p_depuis AT TIME ZONE 'Africa/Conakry')::date $$;

-- 2. Départ du délai : NULL tant qu'il n'a pas commencé (frais non payés).
CREATE FUNCTION fn_depart_delai(p_demande bigint) RETURNS timestamptz
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
    FROM demande d CROSS JOIN parametre_delai p
   WHERE d.id = p_demande;
$$;

-- 3. Jours de pause entre p_depuis et p_jusqua : compléments, puis attente du paiement des droits
--    (de la décision favorable au statut suivant). Chaque période est bornée à [depuis, jusqua].
CREATE FUNCTION fn_jours_pause(p_demande bigint, p_depuis timestamptz, p_jusqua timestamptz)
RETURNS integer LANGUAGE sql STABLE AS
$$
  WITH p AS (SELECT * FROM parametre_delai),
  periodes AS (
    SELECT c.demande_le AS debut, COALESCE(c.repondu_le, c.clos_le, p_jusqua) AS fin
      FROM complement c, p
     WHERE p.pause_complement AND c.demande_id = p_demande
    UNION ALL
    -- La pause s'arrête au statut suivant (droits payés, ou rejet) ; sans droits au barème, la
    -- décision mène directement à la signature : pause nulle.
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

-- 4. Jours de traitement comptés sur le délai, à un instant (ou à la délivrance, si elle est
--    antérieure) : jour de départ inclus, pauses déduites, 1 au moins. NULL si le délai n'a pas
--    commencé à cet instant.
CREATE FUNCTION fn_jours_traitement(p_demande bigint, p_jusqua timestamptz) RETURNS integer
LANGUAGE plpgsql STABLE AS
$$
DECLARE
  v_depart timestamptz := fn_depart_delai(p_demande);
  v_fin    timestamptz;
BEGIN
  IF v_depart IS NULL OR v_depart > p_jusqua THEN
    RETURN NULL;
  END IF;
  SELECT least(p_jusqua, COALESCE(min(h.created_at), p_jusqua)) INTO v_fin
    FROM historique_statut_demande h
   WHERE h.demande_id = p_demande AND h.nouveau_statut = 'delivree';
  RETURN greatest(1, fn_jours_conakry(v_depart, v_fin) + 1 - fn_jours_pause(p_demande, v_depart, v_fin));
END $$;

-- 5. Seuil « proche du dépassement » d'un délai du catalogue, en jours.
CREATE FUNCTION fn_seuil_proche(p_delai integer) RETURNS integer
LANGUAGE sql STABLE AS
$$
  SELECT greatest(seuil_proche_min_jours, ceil(p_delai * seuil_proche_pourcent / 100.0))::integer
    FROM parametre_delai;
$$;

-- 6. Le délai est-il en pause à cet instant (complément en cours, ou droits attendus) ?
CREATE FUNCTION fn_motif_pause(p_demande bigint) RETURNS text
LANGUAGE sql STABLE AS
$$
  SELECT CASE
           WHEN d.statut = 'complement_requis' AND p.pause_complement THEN 'complement'
           WHEN d.statut = 'decision' AND p.pause_droits THEN 'droits'
         END
    FROM demande d CROSS JOIN parametre_delai p
   WHERE d.id = p_demande;
$$;
