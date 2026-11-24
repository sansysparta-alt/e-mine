-- e-mine · V22 — Libellés bilingues des référentiels (lot « Langue »). Cahier des charges : « Bilingue
-- FR / EN : chaque page et chaque e-Service existent dans les deux langues ». Données de référence
-- seulement (aucune donnée d'usager) :
-- 1. libellé anglais des 15 documents du catalogue (colonne prévue par le schéma fourni, restée vide),
--    reporté sur la version publiée de chaque fiche ;
-- 2. noms des 16 profils en typographie française (majuscule au premier mot seulement :
--    « Administrateur technique ») ; les codes et les droits ne changent pas (D2).

-- 1. Documents
CREATE TEMPORARY TABLE libelle_anglais (code char(3) PRIMARY KEY, libelle text NOT NULL) ON COMMIT DROP;
INSERT INTO libelle_anglais (code, libelle) VALUES
  ('PRC', 'Reconnaissance permit'),
  ('PRE', 'Exploration permit'),
  ('PEX', 'Mining licence'),
  ('CAR', 'Quarry authorisation'),
  ('CES', 'Title assignment / transfer'),
  ('AEA', 'Artisanal mining authorisation'),
  ('COR', 'Artisanal gold miner card'),
  ('AGC', 'Trading house / dealer approval'),
  ('CEX', 'Expert appraisal certificate (BNE)'),
  ('AEX', 'Export authorisation'),
  ('KIM', 'Export compliance certificate (diamonds)'),
  ('EXC', 'Cadastre extract / non-overlap certificate'),
  ('CCC', 'Certified true copy of title'),
  ('ARF', 'Royalty status certificate'),
  ('PME', 'SME / local content approval');

-- Contenu des versions figé par trigger (V18) : simple complément de traduction, sans nouvelle
-- version ni trace (une nouvelle version exigerait l'approbation du Ministre pour une traduction).
ALTER TABLE version_document DISABLE TRIGGER USER;
UPDATE version_document v SET libelle_en = a.libelle
  FROM type_document td JOIN libelle_anglais a ON a.code = td.code
 WHERE v.type_document_id = td.id AND v.statut = 'publiee' AND v.libelle_en IS NULL;
ALTER TABLE version_document ENABLE TRIGGER USER;

UPDATE type_document td SET libelle_en = a.libelle
  FROM libelle_anglais a WHERE a.code = td.code AND td.libelle_en IS NULL;

-- 2. Profils
UPDATE profil p SET libelle = v.libelle
  FROM (VALUES
    ('ADMIN_TECH', 'Administrateur technique'),
    ('GUICHET', 'Agent du guichet unique (CPDM)'),
    ('DNM', 'Agent DNM'),
    ('INSPECTEUR', 'Inspecteur des mines'),
    ('GEOLOGUE', 'Géologue (DNG)'),
    ('FISCAL', 'Responsable fiscal'),
    ('DIRECTEUR', 'Directeur général / national'),
    ('MINISTRE', 'Ministre / Secrétaire général'),
    ('INVESTISSEUR', 'Investisseur extérieur'),
    ('SOCIETE_MINIERE', 'Société minière'),
    ('COMMUNAUTE', 'Communauté locale'),
    ('PME', 'PME guinéenne'),
    ('TUTELLE', 'Organismes sous tutelle'),
    ('BRIGADE', 'Brigade anti-fraude'),
    ('APPRENANT', 'Apprenant (cadre du Ministère)'),
    ('WEBMASTER', 'Webmaster')
  ) AS v(code, libelle)
 WHERE p.code = v.code;

-- 3. Événements de notification : en anglais, une demande est une « request » (et non « application »,
--    qui désigne d'abord un logiciel). Libellés figés par trigger (V21) : reprise ponctuelle.
ALTER TABLE notification_evenement DISABLE TRIGGER USER;
UPDATE notification_evenement
   SET libelle_en = regexp_replace(regexp_replace(libelle_en, '\mApplications\M', 'Requests', 'g'),
                                   '\mApplication\M', 'Request', 'g')
 WHERE libelle_en ~ '\mApplications?\M';
ALTER TABLE notification_evenement ENABLE TRIGGER USER;
