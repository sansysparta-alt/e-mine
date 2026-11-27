-- US-04.18, décision V1-13 du Ministère : les remboursements sont **proposés par le Responsable
-- fiscal et validés par le Directeur**. La base garantit la séparation des tâches : celui qui a
-- proposé un remboursement ne le valide ni ne le refuse ; l'ordre du Trésor reste enregistré par
-- le Responsable fiscal (droit ENCAISSER), après validation.

-- 1. Droit de valider (ou refuser) un remboursement : le Directeur (matrice des droits, D2).
INSERT INTO permission (code, libelle)
VALUES ('VALIDER_REMBOURSEMENT', 'Valider ou refuser un remboursement');
INSERT INTO profil_permission (profil_id, permission_id)
SELECT p.id, pm.id FROM profil p, permission pm
 WHERE p.code = 'DIRECTEUR' AND pm.code = 'VALIDER_REMBOURSEMENT';

-- 2. Contrôles de la décision et de l'exécution, sous la session de l'appelant.
CREATE FUNCTION fn_remboursement_decision() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF OLD.statut = 'demande' AND NEW.statut IN ('valide', 'refuse') THEN
    IF NOT fn_a_permission('VALIDER_REMBOURSEMENT') THEN
      RAISE EXCEPTION 'Remboursement % : seul le Directeur le valide ou le refuse', OLD.numero
        USING ERRCODE = 'P0001', HINT = 'PERM-001';
    END IF;
    -- Séparation des tâches, sauf pour une proposition faite par la règle elle-même (frais d'une
    -- demande rejetée, V32) : son auteur enregistré est celui qui a rejeté la demande.
    IF OLD.motif <> 'rejet_demande'
       AND (NEW.valide_par IS NOT DISTINCT FROM OLD.demande_par
            OR fn_current_user_id() IS NOT DISTINCT FROM OLD.demande_par) THEN
      RAISE EXCEPTION 'Remboursement % : celui qui le propose ne le décide pas', OLD.numero
        USING ERRCODE = 'P0001', HINT = 'PAY-018';
    END IF;
  ELSIF OLD.statut = 'valide' AND NEW.statut = 'execute' AND NOT fn_a_permission('ENCAISSER') THEN
    RAISE EXCEPTION 'Remboursement % : seul le Responsable fiscal enregistre l''ordre du Trésor',
      OLD.numero USING ERRCODE = 'P0001', HINT = 'PERM-001';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER trg_remboursement_decision BEFORE UPDATE OF statut ON remboursement
  FOR EACH ROW WHEN (OLD.statut IS DISTINCT FROM NEW.statut)
  EXECUTE FUNCTION fn_remboursement_decision();
