-- e-mine · V5 — numéro de demande attribué au dépôt (US-03.4, D3)
-- Le schéma fourni attribuait DEM-AAAA-NNNNNN dès la création du brouillon : chaque brouillon
-- abandonné aurait laissé un trou dans la numérotation officielle. Le numéro est désormais
-- attribué par la base quand la demande quitte le statut brouillon (dépôt), toujours via
-- fn_next_numero. Un brouillon n'a pas de numéro.

ALTER TABLE demande ALTER COLUMN numero DROP NOT NULL;
ALTER TABLE demande ADD CONSTRAINT demande_numero_hors_brouillon_check
  CHECK (statut = 'brouillon' OR numero IS NOT NULL);

CREATE OR REPLACE FUNCTION fn_demande_avant() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF NEW.statut <> 'brouillon' AND NEW.numero IS NULL THEN
    NEW.numero := fn_next_numero('DEM', 6);
  END IF;
  IF NEW.statut = 'deposee' AND NEW.date_depot IS NULL THEN
    NEW.date_depot := now();
  END IF;
  RETURN NEW;
END $$;

COMMENT ON COLUMN demande.numero IS 'DEM-AAAA-NNNNNN, attribué par la base au dépôt (V5) ; NULL pour un brouillon';
