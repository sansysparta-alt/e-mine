-- e-mine · V10 — numéro du document délivré attribué par la base (US-06.1, D3 et CLAUDE.md)
-- Le schéma fourni rend document_delivre.numero obligatoire et unique sans le calculer.
-- Format du cahier des charges (US-06.3) : <CODE>-AAAA-NNNN, compteur par code de document et par
-- année (fn_next_numero), par exemple PEX-2026-0001.
-- Adaptation du schéma à signaler au Ministère.

CREATE FUNCTION fn_document_delivre_numero() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF NEW.numero IS NULL THEN
    NEW.numero := fn_next_numero((SELECT code FROM type_document WHERE id = NEW.type_document_id), 4);
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER trg_document_delivre_numero BEFORE INSERT ON document_delivre
  FOR EACH ROW EXECUTE FUNCTION fn_document_delivre_numero();

ALTER TABLE document_delivre
  ADD CONSTRAINT ck_document_delivre_numero CHECK (numero ~ '^[A-Z]{3}-[0-9]{4}-[0-9]{4,}$');
