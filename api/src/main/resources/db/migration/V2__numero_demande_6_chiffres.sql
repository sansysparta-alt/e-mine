-- e-mine · V2 — numéros de demande sur 6 chiffres (décision D3 : DEM-AAAA-NNNNNN)
-- Le schéma fourni générait DEM-AAAA-NNN ; PAY et REC sont déjà sur 6 chiffres.

CREATE OR REPLACE FUNCTION fn_demande_avant() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF TG_OP = 'INSERT' AND NEW.numero IS NULL THEN
    NEW.numero := fn_next_numero('DEM', 6);
  END IF;
  IF NEW.statut = 'deposee' AND NEW.date_depot IS NULL THEN
    NEW.date_depot := now();
  END IF;
  RETURN NEW;
END $$;

ALTER TABLE demande DROP CONSTRAINT demande_numero_check;
ALTER TABLE demande ADD CONSTRAINT demande_numero_check CHECK (numero ~ '^DEM-[0-9]{4}-[0-9]{6}$');
