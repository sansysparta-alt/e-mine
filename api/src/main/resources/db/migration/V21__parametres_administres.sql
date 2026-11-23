-- e-mine · V21 — Paramètres des délais et canaux des notifications administrés (US-02.6, US-02.7).
-- Choix du porteur du projet (22 et 23/11/2026) : règles des délais (V17) et canaux par événement
-- (V16) paramétrables, à faire valider par le Ministère (docs/01 §3, points 17 à 22) ; modification
-- par l'administrateur, immédiate et tracée (journal d'audit). Un événement garde au moins un canal :
-- l'usager sait toujours où en est son dossier. Ajouts au schéma fourni, à signaler au Ministère.

CREATE FUNCTION fn_parametre_controle() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF current_user <> 'emine_owner' AND NOT fn_a_permission('PARAMETRER') THEN
    RAISE EXCEPTION 'Seul l''administrateur modifie ce paramètre' USING ERRCODE = 'P0001', HINT = 'PERM-001';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER trg_parametre_delai_controle BEFORE UPDATE ON parametre_delai
  FOR EACH ROW EXECUTE FUNCTION fn_parametre_controle();
CREATE TRIGGER trg_notification_evenement_controle BEFORE UPDATE ON notification_evenement
  FOR EACH ROW EXECUTE FUNCTION fn_parametre_controle();

-- Les libellés et les modèles ne changent pas ici : seuls les canaux.
CREATE FUNCTION fn_notification_evenement_fige() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF (NEW.id, NEW.modele, NEW.libelle_fr, NEW.libelle_en)
     IS DISTINCT FROM (OLD.id, OLD.modele, OLD.libelle_fr, OLD.libelle_en) THEN
    RAISE EXCEPTION 'Événement % : seuls les canaux se modifient', OLD.modele
      USING ERRCODE = 'P0001', HINT = 'VAL-001';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_notification_evenement_fige BEFORE UPDATE ON notification_evenement
  FOR EACH ROW EXECUTE FUNCTION fn_notification_evenement_fige();

ALTER TABLE notification_evenement
  ADD CONSTRAINT notification_evenement_un_canal CHECK (sms OR email OR interne);
ALTER TABLE parametre_delai
  ADD CONSTRAINT parametre_delai_seuil_jours CHECK (seuil_proche_min_jours <= 30);

-- Les lignes sont fixes : l'application ne fait que les mettre à jour.
REVOKE INSERT ON parametre_delai, notification_evenement FROM emine_app;
