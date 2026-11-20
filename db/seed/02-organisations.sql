-- e-mine · seed 2 — organisations complémentaires (données FICTIVES), seed minimal de la V1 (US-00.9).
-- Avec le seed 1 : 5 organisations de types différents. Aucune demande ni aucun paiement :
-- la première demande déposée pendant la démo reçoit DEM-AAAA-000001.
-- Le catalogue (15 documents), les tarifs, les profils et la matrice des droits sont dans la migration V1.
-- Idempotent. Lancé par : pnpm db:seed.

SET search_path = emine, public;

INSERT INTO organisation (raison_sociale, nif, rccm, type_org, adresse, prefecture_id, telephone, email)
SELECT v.rs, v.nif, v.rccm, v.type_org, v.adr, p.id, v.tel, v.mail
  FROM (VALUES
   ('Bauxite du Rio Fictif SA',          'NIF-TEST-1001', 'RCCM-TEST-1001', 'societe_miniere', 'Kamsar',        'Boké',   '+224620001001', 'contact@bauxite-fictif.test'),
   ('Carrières Kindia Test SARL',        'NIF-TEST-1201', 'RCCM-TEST-1201', 'pme',             'Kindia centre', 'Kindia', '+224620001201', 'contact@carrieres-kindia.test'),
   ('Union des orpailleurs (fictive)',   'NIF-TEST-1101', NULL,             'communaute',      'Kouroussa',     'Kouroussa', '+224620001101', 'contact@orpailleurs.test')
  ) AS v(rs, nif, rccm, type_org, adr, pref, tel, mail)
  JOIN prefecture p ON p.nom = v.pref
ON CONFLICT (nif) DO NOTHING;
