-- e-mine · V26 — Catalogue de la V1 : CEX et KIM désactivés (lot 4, correction 9, décision du porteur
-- du projet du 25/11/2026, docs/01 §3). Ces deux certificats sont instruits par le BNE, dont le profil
-- arrive en V2 : ils ne se demandent plus en V1. Les demandes déjà déposées vont à leur terme (V18 :
-- une désactivation n'arrête pas les demandes en cours).
--
-- Même circuit qu'une modification du catalogue (V18) : une nouvelle version de chaque fiche, motif
-- écrit, publiée et tracée au journal d'audit. Faute d'utilisateur connecté dans une migration, les
-- contrôles de droits de l'administrateur et du Ministre sont suspendus le temps de l'écriture ; les
-- barèmes sont repris tels quels (aucun nouveau tarif).

ALTER TABLE version_document DISABLE TRIGGER trg_version_document_controle;
ALTER TABLE version_document DISABLE TRIGGER trg_version_document_tarifs;
ALTER TABLE version_document DISABLE TRIGGER trg_version_document_publier_tarifs;

CREATE TEMPORARY TABLE v1_retraits ON COMMIT DROP AS
SELECT v.*, (SELECT max(x.numero) FROM version_document x WHERE x.type_document_id = v.type_document_id)
            + 1 AS nouveau_numero
  FROM version_document v
  JOIN type_document td ON td.id = v.type_document_id
 WHERE td.code IN ('CEX', 'KIM') AND v.statut = 'publiee' AND v.actif;

-- 1. L'ancienne version est remplacée (une seule version publiée par document) ; une proposition
--    éventuellement en attente reste à la décision du Ministre.
UPDATE version_document v SET statut = 'remplacee'
  FROM v1_retraits r WHERE v.id = r.id;

-- 2. Nouvelle version publiée, identique sauf « disponible à la demande ».
INSERT INTO version_document (type_document_id, numero, statut, libelle_fr, libelle_en, pieces_cles,
                              delai_jours, instruit_par, signe_par, signataire_profil_id,
                              avis_profils, actif, motif, proposee_le, decidee_le,
                              frais_dossier_gnf, droits_gnf, attestation_gnf, tarifs_date_effet)
SELECT type_document_id, nouveau_numero, 'publiee', libelle_fr, libelle_en, pieces_cles,
       delai_jours, instruit_par, signe_par, signataire_profil_id, avis_profils, false,
       'Périmètre de la V1 : certificat instruit par le BNE, dont le profil arrive en V2. '
         || 'Décision du porteur du projet du 25/11/2026.',
       now(), now(), frais_dossier_gnf, droits_gnf, attestation_gnf, tarifs_date_effet
  FROM v1_retraits;

-- 3. La fiche en vigueur suit la version publiée (comme fn_version_document_publier, V18).
UPDATE type_document td SET actif = false
  FROM v1_retraits r WHERE td.id = r.type_document_id;
UPDATE workflow w
   SET delai_min_jours = s.mini, delai_max_jours = s.maxi
  FROM (SELECT workflow_id, min(delai_jours) AS mini, max(delai_jours) AS maxi
          FROM type_document WHERE actif AND deleted_at IS NULL GROUP BY workflow_id) s
 WHERE s.workflow_id = w.id
   AND (w.delai_min_jours, w.delai_max_jours) IS DISTINCT FROM (s.mini, s.maxi);

ALTER TABLE version_document ENABLE TRIGGER trg_version_document_controle;
ALTER TABLE version_document ENABLE TRIGGER trg_version_document_tarifs;
ALTER TABLE version_document ENABLE TRIGGER trg_version_document_publier_tarifs;
