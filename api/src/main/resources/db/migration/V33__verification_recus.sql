-- US-04.14 : chaque reçu porte un QR de vérification publique, comme les documents délivrés.
--
-- Le QR encode un code aléatoire de 128 bits (impossible à deviner, contrairement au numéro
-- REC-AAAA-NNNNNN, séquentiel). La vérification se fait sans connexion : la RLS du paiement ne
-- montrerait rien, d'où une fonction SECURITY DEFINER qui ne renvoie que des données non
-- personnelles (ni payeur, ni organisation, ni référence de transaction).

ALTER TABLE paiement
  ADD COLUMN code_verification text NOT NULL UNIQUE
    DEFAULT replace(gen_random_uuid()::text, '-', '')
    CHECK (code_verification ~ '^[0-9a-f]{32}$');

COMMENT ON COLUMN paiement.code_verification IS
  'Code du QR du reçu (128 bits aléatoires) : vérification publique sans donnée personnelle (V33)';

-- Reçu authentique : paiement payé, reçu émis. « rembourse » : la somme a été rendue par le Trésor
-- (remboursement exécuté) ; le reçu reste authentique, mais celui qui le présente doit le savoir.
CREATE FUNCTION fn_verifier_recu(p_code text)
RETURNS TABLE (recu_numero text, objet objet_paiement, moyen moyen_paiement, montant_gnf bigint,
               date_paiement timestamptz, demande_numero text, rembourse boolean)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = emine, pg_temp AS
$$
  SELECT p.recu_numero, p.objet, p.moyen, COALESCE(p.montant_recu_gnf, p.montant_gnf),
         p.date_paiement, d.numero,
         EXISTS (SELECT 1 FROM remboursement r WHERE r.paiement_id = p.id AND r.statut = 'execute')
    FROM paiement p
    JOIN demande d ON d.id = p.demande_id
   WHERE p.code_verification = p_code
     AND p.statut = 'paye'
     AND p.recu_numero IS NOT NULL
$$;

REVOKE ALL ON FUNCTION fn_verifier_recu(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_verifier_recu(text) TO emine_app;
