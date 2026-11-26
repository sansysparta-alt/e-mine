-- e-mine · seed 1 — personas de démo (données FICTIVES), liés aux comptes du realm Keycloak
-- (infra/keycloak/realm-emine.json : mêmes identifiants « id » = utilisateur.keycloak_id).
-- Idempotent. Lancé par : pnpm db:seed (après pnpm infra:up et une première migration de l'API).
-- Seed minimal de la V1 (US-00.9) : voir aussi 02-organisations.sql. Titres et demandes : plan complet.

SET search_path = emine, public;

INSERT INTO organisation (raison_sociale, nif, type_org, adresse, prefecture_id, telephone, email)
SELECT v.rs, v.nif, 'investisseur', v.adr, p.id, v.tel, v.mail
  FROM (VALUES
   ('Horizon Invest SARL (fictive)', 'NIF-TEST-0901', 'Kaloum, Conakry', 'Conakry', '+224620000901', 'contact@horizon-invest.test'),
   ('Sahel Ressources SA (fictive)', 'NIF-TEST-0902', 'Siguiri centre',  'Siguiri', '+224620000902', 'contact@sahel-ressources.test')
  ) AS v(rs, nif, adr, pref, tel, mail)
  JOIN prefecture p ON p.nom = v.pref
ON CONFLICT (nif) DO NOTHING;

INSERT INTO utilisateur (profil_id, organisation_id, nom, prenom, email, telephone, keycloak_id, double_auth_actif)
SELECT pr.id, o.id, v.nom, v.prenom, v.email, v.tel, v.kc::uuid, false
  FROM (VALUES
   ('00000000-0000-4000-8000-000000000009', 'INVESTISSEUR', 'NIF-TEST-0901', 'Bangoura', 'Fodé',     'f.bangoura@horizon-invest.test', '+224620000909'),
   ('00000000-0000-4000-8000-000000000109', 'INVESTISSEUR', 'NIF-TEST-0902', 'Sylla',    'Kadiatou', 'k.sylla@sahel-ressources.test',  '+224620000919'),
   ('00000000-0000-4000-8000-000000000002', 'GUICHET',      NULL,            'Camara',   'Aïcha',    'a.camara@e-mine.test',           '+224620000902'),
   ('00000000-0000-4000-8000-000000000003', 'DNM',          NULL,            'Sow',      'Nènè',     'n.sow@e-mine.test',              '+224620000903'),
   ('00000000-0000-4000-8000-000000000005', 'GEOLOGUE',     NULL,            'Keïta',    'Moussa',   'm.keita@e-mine.test',            '+224620000905'),
   ('00000000-0000-4000-8000-000000000004', 'INSPECTEUR',   NULL,            'Barry',    'Fatoumata', 'f.barry@e-mine.test',            '+224620000904'),
   ('00000000-0000-4000-8000-000000000007', 'DIRECTEUR',    NULL,            'Kouyaté',  'Sékou',    's.kouyate@e-mine.test',          '+224620000907'),
   ('00000000-0000-4000-8000-000000000008', 'MINISTRE',     NULL,            'Soumah',   'Ibrahima', 'i.soumah@e-mine.test',           '+224620000908'),
   ('00000000-0000-4000-8000-000000000006', 'FISCAL',       NULL,            'Diallo',   'Mariama',  'm.diallo@e-mine.test',           '+224620000906'),
   ('00000000-0000-4000-8000-000000000001', 'ADMIN_TECH',   NULL,            'Bah',      'Ousmane',  'o.bah@e-mine.test',              '+224620000901')
  ) AS v(kc, profil, nif, nom, prenom, email, tel)
  JOIN profil pr ON pr.code = v.profil
  LEFT JOIN organisation o ON o.nif = v.nif
ON CONFLICT (keycloak_id) DO NOTHING;

-- Titres officiels (V25) : ce qu'affichent une révocation ou une signature.
UPDATE utilisateur u SET titre = v.titre
  FROM (VALUES ('00000000-0000-4000-8000-000000000007', 'Directeur national'),
               ('00000000-0000-4000-8000-000000000008', 'Ministre')) AS v(kc, titre)
 WHERE u.keycloak_id = v.kc::uuid AND u.titre IS NULL;
