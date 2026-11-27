-- =====================================================================
-- V30 — Import des relevés bancaires et rapprochement automatique (US-04.8)
-- =====================================================================
-- Le Responsable fiscal importe le relevé CSV du compte qui reçoit les virements (contrat :
-- docs/contrats/releve-csv.md). Chaque ligne est rapprochée d'un paiement par la référence
-- PAY-AAAA-NNNNNN lue dans son libellé et par son montant. Seul le cas conforme confirme un
-- paiement ; tout le reste part en « écart » ou « inconnue », traité par le Responsable fiscal
-- (US-04.9). Aucune décision n'est prise à sa place.

-- 1. Lignes : sens (crédit ou débit), numéro dans le fichier, référence lue, nature du résultat.
ALTER TABLE ligne_releve
  ADD COLUMN numero_ligne  integer,
  ADD COLUMN sens          char(1) NOT NULL DEFAULT 'C' CHECK (sens IN ('C', 'D')),
  ADD COLUMN reference_lue text,
  ADD COLUMN nature        text CHECK (nature IN ('conforme', 'inferieur', 'superieur', 'inconnue',
                                                  'doublon', 'tardif', 'retour'));

COMMENT ON COLUMN ligne_releve.sens IS 'C : crédit (argent reçu) ; D : débit (retour, frais)';
COMMENT ON COLUMN ligne_releve.nature IS
  'Résultat du rapprochement : conforme, inférieur, supérieur, inconnue, doublon, tardif, retour';

CREATE UNIQUE INDEX ux_ligne_releve_numero ON ligne_releve (releve_id, numero_ligne);
CREATE INDEX ix_ligne_releve_statut ON ligne_releve (statut) WHERE statut IN ('ecart', 'inconnue');

-- 2. Traçabilité : relevés et lignes au journal d'audit.
CREATE TRIGGER trg_releve_bancaire_audit AFTER INSERT OR UPDATE OR DELETE ON releve_bancaire
  FOR EACH ROW EXECUTE FUNCTION fn_audit();
CREATE TRIGGER trg_ligne_releve_audit AFTER INSERT OR UPDATE OR DELETE ON ligne_releve
  FOR EACH ROW EXECUTE FUNCTION fn_audit();
CREATE TRIGGER trg_releve_bancaire_nodelete BEFORE DELETE ON releve_bancaire
  FOR EACH ROW EXECUTE FUNCTION fn_interdire_modification();
CREATE TRIGGER trg_ligne_releve_nodelete BEFORE DELETE ON ligne_releve
  FOR EACH ROW EXECUTE FUNCTION fn_interdire_modification();

-- 3. Référence lue dans un libellé : PAY-AAAA-NNNNNN, en majuscules ou minuscules, avec ou sans
--    tirets ni espaces (« pay 2026 000123 »). Renvoie la référence normalisée, ou NULL.
CREATE FUNCTION fn_reference_paiement_lue(p_libelle text) RETURNS text
LANGUAGE sql IMMUTABLE AS
$$
  SELECT 'PAY-' || m[1] || '-' || m[2]
    FROM regexp_match(upper(p_libelle), 'PAY[\s-]?([0-9]{4})[\s-]?([0-9]{6})') AS m
$$;

-- 4. Rapprochement (fonction du schéma fourni, complétée) : les 7 cas du contrat.
CREATE OR REPLACE FUNCTION fn_rapprocher_releve(p_releve_id bigint)
RETURNS TABLE (rapprochees integer, ecarts integer, inconnues integer) LANGUAGE plpgsql AS
$$
DECLARE
  l     ligne_releve%ROWTYPE;
  p     paiement%ROWTYPE;
  v_ref text;
BEGIN
  rapprochees := 0; ecarts := 0; inconnues := 0;
  FOR l IN SELECT * FROM ligne_releve
            WHERE releve_id = p_releve_id AND statut = 'non_traitee'
            ORDER BY numero_ligne NULLS LAST, id
  LOOP
    v_ref := fn_reference_paiement_lue(l.libelle);
    p := NULL;
    IF v_ref IS NOT NULL THEN
      SELECT * INTO p FROM paiement WHERE reference = v_ref AND moyen = 'VIREMENT' FOR UPDATE;
    END IF;

    IF l.sens = 'D' THEN
      -- Retour bancaire ou frais : rien n'est changé, le Responsable fiscal décide.
      UPDATE ligne_releve SET statut = 'ecart', nature = 'retour', reference_lue = v_ref,
                              paiement_id = p.id, ecart_gnf = -l.montant_gnf
       WHERE id = l.id;
      ecarts := ecarts + 1;
    ELSIF p.id IS NULL THEN
      UPDATE ligne_releve SET statut = 'inconnue', nature = 'inconnue', reference_lue = v_ref
       WHERE id = l.id;
      inconnues := inconnues + 1;
    ELSIF p.statut IN ('initie', 'en_attente') AND p.montant_gnf = l.montant_gnf THEN
      IF p.statut = 'initie' THEN UPDATE paiement SET statut = 'en_attente' WHERE id = p.id; END IF;
      UPDATE paiement SET statut = 'paye', montant_recu_gnf = l.montant_gnf,
                          date_paiement = l.date_operation::timestamptz
       WHERE id = p.id;
      UPDATE ligne_releve SET statut = 'rapprochee', nature = 'conforme', reference_lue = v_ref,
                              paiement_id = p.id, ecart_gnf = 0
       WHERE id = l.id;
      rapprochees := rapprochees + 1;
    ELSIF p.statut IN ('initie', 'en_attente') THEN
      -- Montant différent : le paiement attend la décision du Responsable fiscal.
      IF p.statut = 'initie' THEN UPDATE paiement SET statut = 'en_attente' WHERE id = p.id; END IF;
      UPDATE paiement SET statut = 'a_verifier', montant_recu_gnf = l.montant_gnf WHERE id = p.id;
      UPDATE ligne_releve
         SET statut = 'ecart', reference_lue = v_ref, paiement_id = p.id,
             nature = CASE WHEN l.montant_gnf < p.montant_gnf THEN 'inferieur' ELSE 'superieur' END,
             ecart_gnf = l.montant_gnf - p.montant_gnf
       WHERE id = l.id;
      ecarts := ecarts + 1;
    ELSIF p.statut IN ('paye', 'a_verifier', 'remboursement_demande', 'rembourse') THEN
      -- Deuxième versement pour un paiement déjà reçu.
      UPDATE ligne_releve SET statut = 'ecart', nature = 'doublon', reference_lue = v_ref,
                              paiement_id = p.id, ecart_gnf = l.montant_gnf
       WHERE id = l.id;
      ecarts := ecarts + 1;
    ELSE
      -- Référence expirée, annulée, refusée ou rejetée : versement tardif.
      UPDATE ligne_releve SET statut = 'ecart', nature = 'tardif', reference_lue = v_ref,
                              paiement_id = p.id, ecart_gnf = l.montant_gnf
       WHERE id = l.id;
      ecarts := ecarts + 1;
    END IF;
  END LOOP;
  RETURN NEXT;
END $$;
