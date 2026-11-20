-- =====================================================================
--  e-mine · V1 — schéma initial (Flyway)
--  Adapté de la copie du schéma fourni par le Ministère (US-00.2). Différences :
--   · rôles, base, extensions et schéma : déplacés dans db/bootstrap/ (exécutés en super-utilisateur) ;
--   · commandes psql (\set, \connect), SET ROLE / RESET ROLE retirés : Flyway se connecte en emine_owner ;
--   · données fictives (organisations, utilisateurs, titres, demandes, paiements…) et compteurs amorcés
--     retirés : ils passent dans le seed (US-00.9) ;
--   · référentiels conservés (profils, permissions, préfectures, substances, workflows, 15 documents,
--     tarifs de test, transitions de statut) : les triggers et le catalogue en dépendent.
--  Ne jamais modifier cette migration une fois fusionnée : ajouter une V<n>.
-- =====================================================================
SET search_path = emine, public;

-- ---------------------------------------------------------------------
-- 2. TYPES ÉNUMÉRÉS
-- ---------------------------------------------------------------------
CREATE TYPE statut_demande AS ENUM ('brouillon','deposee','complement_requis','recevable','en_instruction',
                                    'avis_rendu','decision','droits_payes','delivree','archivee','rejetee');
CREATE TYPE statut_paiement AS ENUM ('initie','en_attente','paye','a_verifier','echoue','rejete','expire',
                                     'annule','remboursement_demande','rembourse');
CREATE TYPE moyen_paiement AS ENUM ('TRESORPAY','VIREMENT');
CREATE TYPE objet_paiement AS ENUM ('FRAIS_DOSSIER','DROITS','FORMATION','ATTESTATION');
CREATE TYPE statut_titre AS ENUM ('actif','suspendu','expire','revoque','en_renouvellement');
CREATE TYPE nature_avis AS ENUM ('avis','decision');
CREATE TYPE sens_avis AS ENUM ('favorable','defavorable','complement');
CREATE TYPE canal_notification AS ENUM ('sms','email','interne');
CREATE TYPE statut_notification AS ENUM ('en_attente','envoyee','echec');
CREATE TYPE statut_ligne_releve AS ENUM ('non_traitee','rapprochee','ecart','inconnue');
CREATE TYPE statut_document AS ENUM ('valide','non_valide');

-- ---------------------------------------------------------------------
-- 3. TABLES
-- ---------------------------------------------------------------------
-- 3.1 Référentiels ------------------------------------------------------
CREATE TABLE profil (
  id          smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  numero      smallint NOT NULL UNIQUE CHECK (numero BETWEEN 1 AND 16),
  code        text NOT NULL UNIQUE CHECK (code ~ '^[A-Z_]+$'),
  libelle     text NOT NULL,
  est_interne boolean NOT NULL DEFAULT true,
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE permission (
  id         smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code       text NOT NULL UNIQUE CHECK (code ~ '^[A-Z_]+$'),
  libelle    text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE profil_permission (
  profil_id     smallint NOT NULL REFERENCES profil(id) ON DELETE CASCADE,
  permission_id smallint NOT NULL REFERENCES permission(id) ON DELETE CASCADE,
  created_at    timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (profil_id, permission_id)
);

CREATE TABLE prefecture (
  id         smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  region     text NOT NULL,
  nom        text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (region, nom)
);

CREATE TABLE substance (
  id         smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code       text NOT NULL UNIQUE CHECK (code ~ '^[A-Z_]+$'),
  libelle_fr text NOT NULL,
  libelle_en text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE workflow (
  id            smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code          text NOT NULL UNIQUE CHECK (code ~ '^[A-Z_]+$'),
  libelle       text NOT NULL,
  regle_paiement text NOT NULL,
  delai_min_jours smallint NOT NULL CHECK (delai_min_jours > 0),
  delai_max_jours smallint NOT NULL,
  version       integer NOT NULL DEFAULT 1,
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now(),
  CHECK (delai_max_jours >= delai_min_jours)
);

CREATE TABLE workflow_etape (
  id          integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  workflow_id smallint NOT NULL REFERENCES workflow(id) ON DELETE CASCADE,
  ordre       smallint NOT NULL CHECK (ordre > 0),
  acteur      text NOT NULL,
  libelle     text NOT NULL,
  UNIQUE (workflow_id, ordre)
);

CREATE TABLE type_document (
  id             smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code           char(3) NOT NULL UNIQUE CHECK (code ~ '^[A-Z]{3}$'),
  libelle_fr     text NOT NULL,
  libelle_en     text,
  workflow_id    smallint NOT NULL REFERENCES workflow(id),
  delai_jours    smallint NOT NULL CHECK (delai_jours > 0),
  renouvelable   boolean NOT NULL DEFAULT false,
  genere_titre   boolean NOT NULL DEFAULT false,
  pieces_cles    text NOT NULL,
  instruit_par   text NOT NULL,
  signe_par      text NOT NULL,
  actif          boolean NOT NULL DEFAULT true,
  created_at     timestamptz NOT NULL DEFAULT now(),
  updated_at     timestamptz NOT NULL DEFAULT now(),
  deleted_at     timestamptz
);

CREATE TABLE tarif (
  id               integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  type_document_id smallint NOT NULL REFERENCES type_document(id),
  objet            objet_paiement NOT NULL,
  montant_gnf      bigint NOT NULL CHECK (montant_gnf > 0),
  date_effet       date NOT NULL,
  version          integer NOT NULL DEFAULT 1,
  valide_par       text NOT NULL,
  actif            boolean NOT NULL DEFAULT true,
  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now(),
  UNIQUE (type_document_id, objet, version)
);

CREATE TABLE transition_statut (
  entite text NOT NULL CHECK (entite IN ('demande','paiement')),
  de     text NOT NULL,
  vers   text NOT NULL,
  PRIMARY KEY (entite, de, vers)
);

CREATE TABLE compteur (
  cle    text    NOT NULL,
  annee  integer NOT NULL,
  valeur integer NOT NULL DEFAULT 0 CHECK (valeur >= 0),
  PRIMARY KEY (cle, annee)
);

-- 3.2 Organisations, utilisateurs ---------------------------------------
CREATE TABLE organisation (
  id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  raison_sociale text NOT NULL,
  nif            text UNIQUE CHECK (nif ~ '^[A-Za-z0-9-]{8,20}$'),
  rccm           text,
  type_org       text NOT NULL CHECK (type_org IN ('societe_miniere','pme','investisseur','organisme_tutelle','communaute','autre')),
  adresse        text,
  prefecture_id  smallint REFERENCES prefecture(id),
  telephone      text CHECK (telephone ~ '^\+224[0-9]{9}$'),
  email          citext,
  created_at     timestamptz NOT NULL DEFAULT now(),
  updated_at     timestamptz NOT NULL DEFAULT now(),
  deleted_at     timestamptz
);

CREATE TABLE utilisateur (
  id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  profil_id           smallint NOT NULL REFERENCES profil(id),
  organisation_id     bigint REFERENCES organisation(id),
  nom                 text NOT NULL,
  prenom              text NOT NULL,
  email               citext NOT NULL CHECK (email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'),
  telephone           text NOT NULL CHECK (telephone ~ '^\+224[0-9]{9}$'),
  mot_de_passe_hash   text NOT NULL,
  double_auth_actif   boolean NOT NULL DEFAULT true,
  echecs_connexion    smallint NOT NULL DEFAULT 0 CHECK (echecs_connexion >= 0),
  verrouille_jusqua   timestamptz,
  langue              char(2) NOT NULL DEFAULT 'fr' CHECK (langue IN ('fr','en')),
  statut              text NOT NULL DEFAULT 'actif' CHECK (statut IN ('actif','suspendu','invite')),
  derniere_connexion  timestamptz,
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now(),
  deleted_at          timestamptz
);

-- 3.3 Titres et demandes ------------------------------------------------
CREATE TABLE titre (
  id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  numero           text NOT NULL UNIQUE CHECK (numero ~ '^[A-Z]{3}-[0-9]{4}-[0-9]{4}$'),
  type_document_id smallint NOT NULL REFERENCES type_document(id),
  titulaire_id     bigint NOT NULL REFERENCES organisation(id),
  statut           statut_titre NOT NULL DEFAULT 'actif',
  date_octroi      date NOT NULL,
  date_echeance    date NOT NULL,
  prefecture_id    smallint REFERENCES prefecture(id),
  superficie_ha    numeric(12,2) NOT NULL CHECK (superficie_ha > 0),
  arcgis_globalid  uuid NOT NULL UNIQUE,   -- identifiant de la géométrie dans la géodatabase ArcGIS
  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now(),
  deleted_at       timestamptz,
  CHECK (date_echeance > date_octroi)
);

CREATE TABLE titre_substance (
  titre_id     bigint   NOT NULL REFERENCES titre(id) ON DELETE CASCADE,
  substance_id smallint NOT NULL REFERENCES substance(id),
  principale   boolean  NOT NULL DEFAULT false,
  PRIMARY KEY (titre_id, substance_id)
);

CREATE TABLE demande (
  id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  numero              text NOT NULL UNIQUE CHECK (numero ~ '^DEM-[0-9]{4}-[0-9]{3,}$'),
  type_document_id    smallint NOT NULL REFERENCES type_document(id),
  demandeur_id        bigint NOT NULL REFERENCES utilisateur(id),
  organisation_id     bigint REFERENCES organisation(id),
  titre_id            bigint REFERENCES titre(id),          -- renouvellement, cession, copie, attestation
  statut              statut_demande NOT NULL DEFAULT 'brouillon',
  date_depot          timestamptz,
  date_recevabilite   timestamptz,
  workflow_version    integer NOT NULL DEFAULT 1,
  prefecture_id       smallint REFERENCES prefecture(id),
  substance_id        smallint REFERENCES substance(id),
  perimetre_arcgis_id uuid,
  superficie_ha       numeric(12,2) CHECK (superficie_ha IS NULL OR superficie_ha > 0),
  motif_rejet         text,
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now(),
  deleted_at          timestamptz,
  CHECK (statut = 'brouillon' OR date_depot IS NOT NULL),
  CHECK (statut <> 'rejetee' OR motif_rejet IS NOT NULL)
);

CREATE TABLE historique_statut_demande (
  id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  demande_id     bigint NOT NULL REFERENCES demande(id),
  ancien_statut  statut_demande,
  nouveau_statut statut_demande NOT NULL,
  auteur_id      bigint REFERENCES utilisateur(id),
  created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE controle_spatial (
  id                         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  demande_id                 bigint NOT NULL REFERENCES demande(id),
  tentative                  smallint NOT NULL DEFAULT 1 CHECK (tentative > 0),
  code_resultat              text NOT NULL CHECK (code_resultat ~ '^GEO-(OK|00[1-5])$'),
  bloquant                   boolean NOT NULL DEFAULT false,
  superficie_intersectee_ha  numeric(12,4) CHECK (superficie_intersectee_ha IS NULL OR superficie_intersectee_ha >= 0),
  details                    jsonb NOT NULL DEFAULT '{}'::jsonb,
  execute_le                 timestamptz NOT NULL DEFAULT now(),
  UNIQUE (demande_id, tentative)
);

CREATE TABLE piece_jointe (
  id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  demande_id        bigint NOT NULL REFERENCES demande(id),
  type_piece        text NOT NULL,
  nom_fichier       text NOT NULL,
  mime_type         text NOT NULL CHECK (mime_type IN ('application/pdf','image/jpeg','image/png')),
  taille_octets     bigint NOT NULL CHECK (taille_octets BETWEEN 1 AND 10485760),   -- 10 Mo
  empreinte_sha256  char(64) NOT NULL CHECK (empreinte_sha256 ~ '^[0-9a-f]{64}$'),
  stockage_cle      text NOT NULL UNIQUE,
  antivirus_ok      boolean,
  depose_par        bigint NOT NULL REFERENCES utilisateur(id),
  created_at        timestamptz NOT NULL DEFAULT now(),
  updated_at        timestamptz NOT NULL DEFAULT now(),
  deleted_at        timestamptz
);

CREATE TABLE avis_decision (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  demande_id  bigint NOT NULL REFERENCES demande(id),
  auteur_id   bigint NOT NULL REFERENCES utilisateur(id),
  nature      nature_avis NOT NULL,
  sens        sens_avis NOT NULL,
  motif       text,
  rendu_le    timestamptz NOT NULL DEFAULT now(),
  CHECK (sens <> 'defavorable' OR motif IS NOT NULL)
);

-- 3.4 Paiements et rapprochement -----------------------------------------
CREATE TABLE paiement (
  id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  reference               text NOT NULL UNIQUE CHECK (reference ~ '^PAY-[0-9]{4}-[0-9]{6}$'),
  demande_id              bigint NOT NULL REFERENCES demande(id),
  objet                   objet_paiement NOT NULL,
  moyen                   moyen_paiement NOT NULL,
  montant_gnf             bigint NOT NULL CHECK (montant_gnf > 0),
  devise                  char(3) NOT NULL DEFAULT 'GNF' CHECK (devise = 'GNF'),
  statut                  statut_paiement NOT NULL DEFAULT 'initie',
  date_expiration         timestamptz,
  transaction_externe_id  text,
  montant_recu_gnf        bigint CHECK (montant_recu_gnf IS NULL OR montant_recu_gnf > 0),
  date_paiement           timestamptz,
  justificatif_piece_id   bigint REFERENCES piece_jointe(id),
  recu_numero             text UNIQUE CHECK (recu_numero ~ '^REC-[0-9]{4}-[0-9]{6}$'),
  payeur_id               bigint REFERENCES utilisateur(id),
  version                 integer NOT NULL DEFAULT 1,
  created_at              timestamptz NOT NULL DEFAULT now(),
  updated_at              timestamptz NOT NULL DEFAULT now(),
  CHECK (statut <> 'paye' OR (recu_numero IS NOT NULL AND date_paiement IS NOT NULL
                               AND montant_recu_gnf IS NOT NULL AND montant_recu_gnf >= montant_gnf))
);

CREATE TABLE paiement_evenement (
  id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  paiement_id      uuid REFERENCES paiement(id),
  reference_recue  text,
  transaction_id   text,
  type_evenement   text NOT NULL CHECK (type_evenement IN ('INITIATION','NOTIFICATION','INTERROGATION')),
  payload          jsonb NOT NULL DEFAULT '{}'::jsonb,
  signature_valide boolean NOT NULL,
  traite           boolean NOT NULL DEFAULT false,
  recu_le          timestamptz NOT NULL DEFAULT now(),
  UNIQUE (transaction_id, type_evenement)          -- idempotence des notifications
);

CREATE TABLE releve_bancaire (
  id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  banque            text NOT NULL,
  date_releve       date NOT NULL,
  format_fichier    text NOT NULL CHECK (format_fichier IN ('CSV','MT940')),
  nom_fichier       text NOT NULL,
  empreinte_sha256  char(64) NOT NULL UNIQUE CHECK (empreinte_sha256 ~ '^[0-9a-f]{64}$'),
  importe_par       bigint NOT NULL REFERENCES utilisateur(id),
  created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE ligne_releve (
  id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  releve_id      bigint NOT NULL REFERENCES releve_bancaire(id),
  date_operation date NOT NULL,
  libelle        text NOT NULL,
  montant_gnf    bigint NOT NULL CHECK (montant_gnf > 0),
  statut         statut_ligne_releve NOT NULL DEFAULT 'non_traitee',
  paiement_id    uuid REFERENCES paiement(id),
  ecart_gnf      bigint,
  created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE remboursement (
  id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  paiement_id    uuid NOT NULL REFERENCES paiement(id),
  motif          text NOT NULL CHECK (motif IN ('rejet_demande','double_paiement','excedent','autre')),
  montant_gnf    bigint NOT NULL CHECK (montant_gnf > 0),
  statut         text NOT NULL DEFAULT 'demande' CHECK (statut IN ('demande','valide','execute','refuse')),
  demande_par    bigint NOT NULL REFERENCES utilisateur(id),
  valide_par     bigint REFERENCES utilisateur(id),
  date_validation timestamptz,
  created_at     timestamptz NOT NULL DEFAULT now(),
  updated_at     timestamptz NOT NULL DEFAULT now(),
  CHECK (statut NOT IN ('valide','execute') OR (valide_par IS NOT NULL AND date_validation IS NOT NULL))
);

-- 3.5 Délivrance, alertes, communication -------------------------------
CREATE TABLE document_delivre (
  id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  numero             text NOT NULL UNIQUE,
  type_document_id   smallint NOT NULL REFERENCES type_document(id),
  demande_id         bigint UNIQUE REFERENCES demande(id),      -- NULL pour un titre repris de l'existant
  titre_id           bigint REFERENCES titre(id),
  signataire_id      bigint NOT NULL REFERENCES utilisateur(id),
  signature_hsm      text NOT NULL,
  horodatage         timestamptz NOT NULL,
  empreinte_sha256   char(64) NOT NULL CHECK (empreinte_sha256 ~ '^[0-9a-f]{64}$'),
  code_qr            text NOT NULL UNIQUE DEFAULT replace(gen_random_uuid()::text, '-', ''),
  statut             statut_document NOT NULL DEFAULT 'valide',
  motif_invalidation text,
  delivre_le         timestamptz NOT NULL DEFAULT now(),
  created_at         timestamptz NOT NULL DEFAULT now(),
  updated_at         timestamptz NOT NULL DEFAULT now(),
  CHECK (statut = 'valide' OR motif_invalidation IS NOT NULL)
);

CREATE TABLE alerte_renouvellement (
  id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  titre_id      bigint NOT NULL REFERENCES titre(id),
  seuil_jours   smallint NOT NULL CHECK (seuil_jours IN (180,90,30)),
  echeance_ref  date NOT NULL,
  planifiee_le  date NOT NULL,
  envoyee_le    timestamptz,
  created_at    timestamptz NOT NULL DEFAULT now(),
  UNIQUE (titre_id, seuil_jours, echeance_ref)
);

CREATE TABLE notification (
  id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  destinataire_id bigint NOT NULL REFERENCES utilisateur(id),
  canal           canal_notification NOT NULL,
  modele          text NOT NULL,
  demande_id      bigint REFERENCES demande(id),
  paiement_id     uuid REFERENCES paiement(id),
  contenu         jsonb NOT NULL DEFAULT '{}'::jsonb,
  statut          statut_notification NOT NULL DEFAULT 'en_attente',
  tentatives      smallint NOT NULL DEFAULT 0 CHECK (tentatives >= 0),
  envoyee_le      timestamptz,
  created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE message (
  id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  expediteur_id   bigint NOT NULL REFERENCES utilisateur(id),
  destinataire_id bigint NOT NULL REFERENCES utilisateur(id),
  demande_id      bigint REFERENCES demande(id),
  objet           text NOT NULL,
  corps           text NOT NULL,
  lu_le           timestamptz,
  created_at      timestamptz NOT NULL DEFAULT now(),
  deleted_at      timestamptz,
  CHECK (expediteur_id <> destinataire_id)
);

CREATE TABLE annonce (
  id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  auteur_id       bigint NOT NULL REFERENCES utilisateur(id),
  titre           text NOT NULL,
  contenu         text NOT NULL,
  profil_cible_id smallint REFERENCES profil(id),      -- NULL = tous les profils
  publiee_le      timestamptz NOT NULL DEFAULT now(),
  expire_le       timestamptz,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now(),
  deleted_at      timestamptz,
  CHECK (expire_le IS NULL OR expire_le > publiee_le)
);

-- 3.6 Journal d'audit (partitionné par mois, non modifiable) ------------
CREATE SEQUENCE seq_journal_audit;      -- les colonnes identity ne sont pas gérées sur les tables partitionnées en PostgreSQL 16
CREATE TABLE journal_audit (
  id                bigint NOT NULL DEFAULT nextval('seq_journal_audit'),
  created_at        timestamptz NOT NULL DEFAULT now(),
  utilisateur_id    bigint,
  table_nom         text NOT NULL,
  operation         char(1) NOT NULL CHECK (operation IN ('I','U','D')),
  objet_id          text,
  anciennes_valeurs jsonb,
  nouvelles_valeurs jsonb,
  adresse_ip        inet,
  PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- ---------------------------------------------------------------------
-- 4. FONCTIONS ET TRIGGERS
-- ---------------------------------------------------------------------
-- 4.1 Utilitaires de session : l'application renseigne, après authentification,
--     SET LOCAL emine.user_id = '<id>' ; SET LOCAL emine.interne = 'on'|'off' ; SET LOCAL emine.ip = '<ip>'
CREATE FUNCTION fn_current_user_id() RETURNS bigint LANGUAGE sql STABLE AS
$$ SELECT NULLIF(current_setting('emine.user_id', true), '')::bigint $$;

CREATE FUNCTION fn_est_interne() RETURNS boolean LANGUAGE sql STABLE AS
$$ SELECT COALESCE(current_setting('emine.interne', true), 'off') = 'on' $$;

CREATE FUNCTION fn_set_updated_at() RETURNS trigger LANGUAGE plpgsql AS
$$ BEGIN NEW.updated_at := now(); RETURN NEW; END $$;

-- 4.2 Numérotation : DEM-2026-001, PAY-2026-000001, REC-2026-000001, PEX-2026-0009 ...
CREATE FUNCTION fn_next_numero(p_cle text, p_largeur integer) RETURNS text LANGUAGE plpgsql AS
$$
DECLARE
  v_annee integer := extract(year FROM now())::integer;
  v_val   integer;
BEGIN
  INSERT INTO compteur (cle, annee, valeur) VALUES (p_cle, v_annee, 1)
  ON CONFLICT (cle, annee) DO UPDATE SET valeur = compteur.valeur + 1
  RETURNING valeur INTO v_val;
  RETURN p_cle || '-' || v_annee || '-' || lpad(v_val::text, p_largeur, '0');
END $$;

-- 4.3 Journal d'audit
CREATE FUNCTION fn_creer_partition_audit(p_mois date) RETURNS void LANGUAGE plpgsql AS
$$
DECLARE
  d0 date := date_trunc('month', p_mois)::date;
  d1 date := (date_trunc('month', p_mois) + interval '1 month')::date;
BEGIN
  EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF journal_audit FOR VALUES FROM (%L) TO (%L)',
                 'journal_audit_' || to_char(d0, 'YYYY_MM'), d0, d1);
END $$;

DO $$
DECLARE i integer;
BEGIN
  FOR i IN 0..5 LOOP
    PERFORM fn_creer_partition_audit((date '2026-11-01' + (i || ' month')::interval)::date);
  END LOOP;
END $$;
CREATE TABLE journal_audit_defaut PARTITION OF journal_audit DEFAULT;

CREATE FUNCTION fn_audit() RETURNS trigger LANGUAGE plpgsql AS
$$
DECLARE
  v_old jsonb;
  v_new jsonb;
BEGIN
  IF TG_OP IN ('UPDATE','DELETE') THEN v_old := to_jsonb(OLD) - 'mot_de_passe_hash'; END IF;
  IF TG_OP IN ('INSERT','UPDATE') THEN v_new := to_jsonb(NEW) - 'mot_de_passe_hash'; END IF;
  INSERT INTO journal_audit (utilisateur_id, table_nom, operation, objet_id, anciennes_valeurs, nouvelles_valeurs, adresse_ip)
  VALUES (fn_current_user_id(), TG_TABLE_NAME, left(TG_OP, 1),
          COALESCE(v_new->>'id', v_old->>'id'), v_old, v_new,
          NULLIF(current_setting('emine.ip', true), '')::inet);
  RETURN NULL;
END $$;

CREATE FUNCTION fn_interdire_modification() RETURNS trigger LANGUAGE plpgsql AS
$$ BEGIN RAISE EXCEPTION 'Opération % interdite sur % : données non supprimables (suppression logique uniquement)', TG_OP, TG_TABLE_NAME; END $$;

-- 4.4 Contrôle des transitions de statut (demande et paiement)
CREATE FUNCTION fn_verifier_transition() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF NEW.statut IS DISTINCT FROM OLD.statut
     AND NOT EXISTS (SELECT 1 FROM transition_statut
                     WHERE entite = TG_ARGV[0] AND de = OLD.statut::text AND vers = NEW.statut::text) THEN
    RAISE EXCEPTION 'Transition de statut interdite (% : % -> %)', TG_ARGV[0], OLD.statut, NEW.statut
      USING ERRCODE = 'P0001', HINT = TG_ARGV[1];
  END IF;
  RETURN NEW;
END $$;

-- 4.5 Demande : numéro, date de dépôt, historique des statuts
CREATE FUNCTION fn_demande_avant() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF TG_OP = 'INSERT' AND NEW.numero IS NULL THEN
    NEW.numero := fn_next_numero('DEM', 3);
  END IF;
  IF NEW.statut = 'deposee' AND NEW.date_depot IS NULL THEN
    NEW.date_depot := now();
  END IF;
  RETURN NEW;
END $$;

CREATE FUNCTION fn_demande_historique() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF TG_OP = 'INSERT' THEN
    INSERT INTO historique_statut_demande (demande_id, ancien_statut, nouveau_statut, auteur_id)
    VALUES (NEW.id, NULL, NEW.statut, fn_current_user_id());
  ELSIF NEW.statut IS DISTINCT FROM OLD.statut THEN
    INSERT INTO historique_statut_demande (demande_id, ancien_statut, nouveau_statut, auteur_id)
    VALUES (NEW.id, OLD.statut, NEW.statut, fn_current_user_id());
  END IF;
  RETURN NULL;
END $$;

-- 4.6 Paiement : référence, expiration (TrésorPay 15 min, virement 7 jours), reçu, verrou optimiste
CREATE FUNCTION fn_paiement_avant() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF TG_OP = 'INSERT' THEN
    NEW.reference := COALESCE(NEW.reference, fn_next_numero('PAY', 6));
    NEW.date_expiration := COALESCE(NEW.date_expiration,
        now() + CASE NEW.moyen WHEN 'TRESORPAY' THEN interval '15 minutes' ELSE interval '7 days' END);
  ELSE
    IF NEW.version IS DISTINCT FROM OLD.version THEN
      RAISE EXCEPTION 'Conflit de version sur le paiement %', OLD.reference USING ERRCODE = '40001';
    END IF;
    NEW.version := OLD.version + 1;
  END IF;
  IF NEW.statut = 'paye' AND (TG_OP = 'INSERT' OR OLD.statut <> 'paye') THEN
    NEW.date_paiement    := COALESCE(NEW.date_paiement, now());
    NEW.montant_recu_gnf := COALESCE(NEW.montant_recu_gnf, NEW.montant_gnf);
    NEW.recu_numero      := COALESCE(NEW.recu_numero, fn_next_numero('REC', 6));
  END IF;
  RETURN NEW;
END $$;

-- Paiement confirmé : frais -> demande recevable ; droits ou attestation -> droits payés ; notification
CREATE FUNCTION fn_paiement_confirme() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF NEW.objet = 'FRAIS_DOSSIER' THEN
    UPDATE demande SET statut = 'recevable', date_recevabilite = now()
     WHERE id = NEW.demande_id AND statut = 'deposee';
  ELSIF NEW.objet IN ('DROITS','ATTESTATION') THEN
    UPDATE demande SET statut = 'droits_payes'
     WHERE id = NEW.demande_id AND statut = 'decision';
  END IF;
  IF NEW.payeur_id IS NOT NULL THEN
    INSERT INTO notification (destinataire_id, canal, modele, demande_id, paiement_id, contenu)
    VALUES (NEW.payeur_id, 'sms', 'PAIEMENT_CONFIRME', NEW.demande_id, NEW.id,
            jsonb_build_object('reference', NEW.reference, 'montant_gnf', NEW.montant_gnf,
                               'moyen', NEW.moyen, 'recu', NEW.recu_numero));
  END IF;
  RETURN NULL;
END $$;

-- 4.7 Rapprochement d'un relevé bancaire (virements)
CREATE FUNCTION fn_rapprocher_releve(p_releve_id bigint)
RETURNS TABLE (rapprochees integer, ecarts integer, inconnues integer) LANGUAGE plpgsql AS
$$
DECLARE
  l     ligne_releve%ROWTYPE;
  p     paiement%ROWTYPE;
  v_ref text;
BEGIN
  rapprochees := 0; ecarts := 0; inconnues := 0;
  FOR l IN SELECT * FROM ligne_releve WHERE releve_id = p_releve_id AND statut = 'non_traitee' ORDER BY id LOOP
    v_ref := (regexp_match(l.libelle, 'PAY-[0-9]{4}-[0-9]{6}'))[1];
    SELECT * INTO p FROM paiement WHERE reference = v_ref AND moyen = 'VIREMENT' FOR UPDATE;
    IF NOT FOUND THEN
      UPDATE ligne_releve SET statut = 'inconnue' WHERE id = l.id;
      inconnues := inconnues + 1;
    ELSIF p.statut IN ('initie','en_attente') AND p.montant_gnf = l.montant_gnf THEN
      IF p.statut = 'initie' THEN UPDATE paiement SET statut = 'en_attente' WHERE id = p.id; END IF;
      UPDATE paiement SET statut = 'paye', montant_recu_gnf = l.montant_gnf,
                          date_paiement = l.date_operation::timestamptz
       WHERE id = p.id;
      UPDATE ligne_releve SET statut = 'rapprochee', paiement_id = p.id WHERE id = l.id;
      rapprochees := rapprochees + 1;
    ELSE
      IF p.statut IN ('initie','en_attente') THEN
        IF p.statut = 'initie' THEN UPDATE paiement SET statut = 'en_attente' WHERE id = p.id; END IF;
        UPDATE paiement SET statut = 'a_verifier', montant_recu_gnf = l.montant_gnf WHERE id = p.id;
      END IF;
      UPDATE ligne_releve
         SET statut = 'ecart', paiement_id = p.id,
             ecart_gnf = CASE WHEN p.statut = 'paye' THEN l.montant_gnf ELSE l.montant_gnf - p.montant_gnf END
       WHERE id = l.id;
      ecarts := ecarts + 1;
    END IF;
  END LOOP;
  RETURN NEXT;
END $$;

-- 4.8 Alertes de renouvellement (J-180, J-90, J-30) et suspension à l'expiration
--     À planifier chaque jour (pg_cron ou cron système).
CREATE FUNCTION fn_generer_alertes_renouvellement() RETURNS integer LANGUAGE plpgsql AS
$$
DECLARE n integer;
BEGIN
  WITH ins AS (
    INSERT INTO alerte_renouvellement (titre_id, seuil_jours, echeance_ref, planifiee_le)
    SELECT t.id, s.seuil, t.date_echeance, current_date
      FROM titre t CROSS JOIN (VALUES (180),(90),(30)) AS s(seuil)
     WHERE t.statut = 'actif' AND t.deleted_at IS NULL
       AND t.date_echeance - s.seuil = current_date
    ON CONFLICT (titre_id, seuil_jours, echeance_ref) DO NOTHING
    RETURNING 1)
  SELECT count(*) INTO n FROM ins;
  RETURN n;
END $$;

CREATE FUNCTION fn_suspendre_titres_expires() RETURNS integer LANGUAGE plpgsql AS
$$
DECLARE n integer;
BEGIN
  WITH maj AS (
    UPDATE titre SET statut = 'suspendu'
     WHERE statut = 'actif' AND date_echeance < current_date AND deleted_at IS NULL
    RETURNING 1)
  SELECT count(*) INTO n FROM maj;
  RETURN n;
END $$;

-- 4.9 Branchement des triggers
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['profil','permission','prefecture','substance','workflow','type_document','tarif',
                           'organisation','utilisateur','titre','demande','piece_jointe','paiement',
                           'remboursement','document_delivre','annonce']
  LOOP
    EXECUTE format('CREATE TRIGGER trg_%s_maj BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at()', t, t);
  END LOOP;

  FOREACH t IN ARRAY ARRAY['organisation','utilisateur','titre','demande','piece_jointe','paiement','remboursement',
                           'document_delivre','tarif','avis_decision','type_document']
  LOOP
    EXECUTE format('CREATE TRIGGER trg_%s_audit AFTER INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION fn_audit()', t, t);
  END LOOP;

  FOREACH t IN ARRAY ARRAY['demande','paiement','titre','document_delivre','historique_statut_demande',
                           'paiement_evenement','avis_decision','journal_audit']
  LOOP
    EXECUTE format('CREATE TRIGGER trg_%s_nodelete BEFORE DELETE ON %I FOR EACH ROW EXECUTE FUNCTION fn_interdire_modification()', t, t);
  END LOOP;
END $$;

CREATE TRIGGER trg_journal_audit_noupdate BEFORE UPDATE ON journal_audit
  FOR EACH ROW EXECUTE FUNCTION fn_interdire_modification();

CREATE TRIGGER trg_demande_avant       BEFORE INSERT OR UPDATE ON demande FOR EACH ROW EXECUTE FUNCTION fn_demande_avant();
CREATE TRIGGER trg_demande_transition  BEFORE UPDATE OF statut ON demande FOR EACH ROW EXECUTE FUNCTION fn_verifier_transition('demande','DEM-001');
CREATE TRIGGER trg_demande_historique  AFTER INSERT OR UPDATE OF statut ON demande FOR EACH ROW EXECUTE FUNCTION fn_demande_historique();
CREATE TRIGGER trg_paiement_avant      BEFORE INSERT OR UPDATE ON paiement FOR EACH ROW EXECUTE FUNCTION fn_paiement_avant();
CREATE TRIGGER trg_paiement_transition BEFORE UPDATE OF statut ON paiement FOR EACH ROW EXECUTE FUNCTION fn_verifier_transition('paiement','PAY-006');
CREATE TRIGGER trg_paiement_confirme   AFTER UPDATE OF statut ON paiement FOR EACH ROW
  WHEN (NEW.statut = 'paye' AND OLD.statut IS DISTINCT FROM 'paye') EXECUTE FUNCTION fn_paiement_confirme();

-- ---------------------------------------------------------------------
-- 5. INDEX (hors clés primaires et contraintes UNIQUE déjà indexées)
-- ---------------------------------------------------------------------
-- Unicité de l'e-mail pour les comptes non supprimés
CREATE UNIQUE INDEX ux_utilisateur_email ON utilisateur (email) WHERE deleted_at IS NULL;
-- Un seul paiement confirmé par demande et par objet (erreur PAY-001 : objet déjà payé)
CREATE UNIQUE INDEX ux_paiement_paye ON paiement (demande_id, objet) WHERE statut = 'paye';
-- Unicité de l'identifiant de transaction TrésorPay
CREATE UNIQUE INDEX ux_paiement_transaction ON paiement (transaction_externe_id) WHERE transaction_externe_id IS NOT NULL;
-- Clés étrangères fréquemment jointes
CREATE INDEX ix_utilisateur_profil       ON utilisateur (profil_id);
CREATE INDEX ix_utilisateur_organisation ON utilisateur (organisation_id);
CREATE INDEX ix_titre_titulaire          ON titre (titulaire_id);
CREATE INDEX ix_titre_type               ON titre (type_document_id);
CREATE INDEX ix_demande_demandeur        ON demande (demandeur_id);
CREATE INDEX ix_demande_titre            ON demande (titre_id) WHERE titre_id IS NOT NULL;
CREATE INDEX ix_piece_demande            ON piece_jointe (demande_id);
CREATE INDEX ix_paiement_demande         ON paiement (demande_id);
CREATE INDEX ix_ligne_releve_releve      ON ligne_releve (releve_id);
CREATE INDEX ix_ligne_releve_paiement    ON ligne_releve (paiement_id) WHERE paiement_id IS NOT NULL;
CREATE INDEX ix_historique_demande       ON historique_statut_demande (demande_id, created_at);
CREATE INDEX ix_avis_demande             ON avis_decision (demande_id);
-- File de travail : demandes actives par statut et type (tableaux de bord, files d'instruction)
CREATE INDEX ix_demande_statut_type ON demande (statut, type_document_id) WHERE deleted_at IS NULL;
-- Alertes d'échéance et suspension quotidienne : seuls les titres actifs sont parcourus
CREATE INDEX ix_titre_echeance ON titre (date_echeance) WHERE statut = 'actif' AND deleted_at IS NULL;
-- Rapprochement et contrôle : paiements ouverts
CREATE INDEX ix_paiement_ouverts ON paiement (statut, date_expiration) WHERE statut IN ('initie','en_attente','a_verifier');
-- Encaissements par mois et moyen
CREATE INDEX ix_paiement_date ON paiement (date_paiement) WHERE statut = 'paye';
-- Notifications à envoyer
CREATE INDEX ix_notification_attente ON notification (created_at) WHERE statut = 'en_attente';
-- Messagerie : boîte de réception
CREATE INDEX ix_message_boite ON message (destinataire_id, created_at DESC) WHERE deleted_at IS NULL;
-- Recherche approchée sur les raisons sociales
CREATE INDEX ix_organisation_nom_trgm ON organisation USING gin (raison_sociale gin_trgm_ops);
-- Détails des contrôles spatiaux et événements de paiement (requêtes sur le contenu JSON)
CREATE INDEX ix_controle_details ON controle_spatial USING gin (details);
CREATE INDEX ix_evenement_paiement ON paiement_evenement (paiement_id);
-- Journal d'audit : recherche par objet
CREATE INDEX ix_audit_objet ON journal_audit (table_nom, objet_id, created_at DESC);
CREATE INDEX ix_audit_utilisateur ON journal_audit (utilisateur_id, created_at DESC);

-- ---------------------------------------------------------------------
-- 6. VUES
-- ---------------------------------------------------------------------
-- Cadastre public (site e-mine web) : aucune donnée personnelle
CREATE VIEW v_titre_public WITH (security_barrier = true) AS
SELECT t.numero, td.code AS type_code, td.libelle_fr AS type_libelle, o.raison_sociale AS titulaire,
       t.statut, t.date_octroi, t.date_echeance, p.region, p.nom AS prefecture, t.superficie_ha,
       t.arcgis_globalid,
       (SELECT string_agg(s.libelle_fr, ', ' ORDER BY ts.principale DESC, s.libelle_fr)
          FROM titre_substance ts JOIN substance s ON s.id = ts.substance_id
         WHERE ts.titre_id = t.id) AS substances
  FROM titre t
  JOIN type_document td ON td.id = t.type_document_id
  JOIN organisation o   ON o.id = t.titulaire_id
  LEFT JOIN prefecture p ON p.id = t.prefecture_id
 WHERE t.deleted_at IS NULL;

CREATE VIEW v_titre_echeance AS
SELECT t.id AS titre_id, t.numero, o.raison_sociale AS titulaire, t.date_echeance,
       (t.date_echeance - current_date) AS jours_restants,
       CASE WHEN t.date_echeance - current_date <= 30 THEN 30
            WHEN t.date_echeance - current_date <= 90 THEN 90
            ELSE 180 END AS seuil_atteint
  FROM titre t JOIN organisation o ON o.id = t.titulaire_id
 WHERE t.statut = 'actif' AND t.deleted_at IS NULL
   AND t.date_echeance - current_date <= 180;

CREATE VIEW v_demande_suivi AS
SELECT d.id, d.numero, td.code AS type_code, td.libelle_fr AS type_libelle, d.statut,
       COALESCE(o.raison_sociale, u.nom || ' ' || u.prenom) AS demandeur,
       d.date_depot, td.delai_jours,
       CASE WHEN d.date_depot IS NULL THEN NULL ELSE (current_date - d.date_depot::date) END AS jours_ecoules,
       pf.statut AS statut_frais, pd.statut AS statut_droits
  FROM demande d
  JOIN type_document td ON td.id = d.type_document_id
  JOIN utilisateur u    ON u.id = d.demandeur_id
  LEFT JOIN organisation o ON o.id = d.organisation_id
  LEFT JOIN LATERAL (SELECT statut FROM paiement WHERE demande_id = d.id AND objet IN ('FRAIS_DOSSIER','ATTESTATION')
                      ORDER BY created_at DESC LIMIT 1) pf ON true
  LEFT JOIN LATERAL (SELECT statut FROM paiement WHERE demande_id = d.id AND objet = 'DROITS'
                      ORDER BY created_at DESC LIMIT 1) pd ON true
 WHERE d.deleted_at IS NULL;

CREATE VIEW v_paiement_a_verifier AS
SELECT p.reference, p.demande_id, p.objet, p.moyen, p.statut, p.montant_gnf, p.montant_recu_gnf,
       (p.montant_recu_gnf - p.montant_gnf) AS ecart_gnf, p.date_expiration
  FROM paiement p
 WHERE p.statut = 'a_verifier'
    OR (p.statut IN ('initie','en_attente') AND p.date_expiration < now());

CREATE VIEW v_encaissement_mensuel AS
SELECT date_trunc('month', date_paiement)::date AS mois, moyen, objet,
       count(*) AS nb_paiements, sum(montant_recu_gnf) AS total_gnf
  FROM paiement
 WHERE statut = 'paye'
 GROUP BY 1, 2, 3;

CREATE VIEW v_demande_par_statut AS
SELECT td.code AS type_code, d.statut, count(*) AS nb
  FROM demande d JOIN type_document td ON td.id = d.type_document_id
 WHERE d.deleted_at IS NULL
 GROUP BY td.code, d.statut;

-- ---------------------------------------------------------------------
-- 7. SÉCURITÉ : RLS ET DROITS
-- ---------------------------------------------------------------------
-- Les profils externes ne voient que leurs propres demandes et paiements. Les profils internes
-- (SET LOCAL emine.interne = 'on') voient tout. Le propriétaire des tables n'est pas soumis aux politiques.
ALTER TABLE demande  ENABLE ROW LEVEL SECURITY;
ALTER TABLE paiement ENABLE ROW LEVEL SECURITY;
CREATE POLICY demande_acces ON demande TO emine_app
  USING (fn_est_interne() OR demandeur_id = fn_current_user_id())
  WITH CHECK (fn_est_interne() OR demandeur_id = fn_current_user_id());
CREATE POLICY paiement_acces ON paiement TO emine_app
  USING (fn_est_interne() OR payeur_id = fn_current_user_id()
         OR EXISTS (SELECT 1 FROM demande d WHERE d.id = paiement.demande_id AND d.demandeur_id = fn_current_user_id()))
  WITH CHECK (fn_est_interne() OR payeur_id = fn_current_user_id()
         OR EXISTS (SELECT 1 FROM demande d WHERE d.id = paiement.demande_id AND d.demandeur_id = fn_current_user_id()));

GRANT USAGE ON SCHEMA emine TO emine_app, emine_readonly, emine_auditeur, emine_public;
-- Application : lecture, création, modification ; jamais de suppression physique
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA emine TO emine_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA emine TO emine_app;
REVOKE UPDATE ON journal_audit FROM emine_app;
ALTER DEFAULT PRIVILEGES FOR ROLE emine_owner IN SCHEMA emine GRANT SELECT, INSERT, UPDATE ON TABLES TO emine_app;
ALTER DEFAULT PRIVILEGES FOR ROLE emine_owner IN SCHEMA emine GRANT USAGE, SELECT ON SEQUENCES TO emine_app;
-- Reporting : lecture sans le hachage des mots de passe
GRANT SELECT ON ALL TABLES IN SCHEMA emine TO emine_readonly;
REVOKE SELECT ON utilisateur FROM emine_readonly;
GRANT SELECT (id, profil_id, organisation_id, nom, prenom, statut, langue, created_at) ON utilisateur TO emine_readonly;
-- Auditeur : journal d'audit uniquement
GRANT SELECT ON journal_audit TO emine_auditeur;
-- Site public : vue du cadastre uniquement
GRANT SELECT ON v_titre_public TO emine_public;
REVOKE SELECT ON journal_audit FROM emine_readonly;
-- Le rôle de reporting lit toutes les lignes (les politiques ci-dessus ne visent que emine_app)
CREATE POLICY demande_lecture  ON demande  FOR SELECT TO emine_readonly USING (true);
CREATE POLICY paiement_lecture ON paiement FOR SELECT TO emine_readonly USING (true);

-- ---------------------------------------------------------------------
-- 8. RÉFÉRENTIELS (profils, permissions, catalogue, tarifs de test, transitions)
-- ---------------------------------------------------------------------
-- Les données fictives (organisations, utilisateurs, titres, demandes…) sont dans le seed (US-00.9).
INSERT INTO profil (numero, code, libelle, est_interne) VALUES
 (1,'ADMIN_TECH','Administrateur Technique',true),
 (2,'GUICHET','Agent Guichet Unique (CPDM)',true),
 (3,'DNM','Agent DNM',true),
 (4,'INSPECTEUR','Inspecteur des Mines',true),
 (5,'GEOLOGUE','Géologue (DNG)',true),
 (6,'FISCAL','Responsable Fiscal',true),
 (7,'DIRECTEUR','Directeur Général / National',true),
 (8,'MINISTRE','Ministre / Secrétaire Général',true),
 (9,'INVESTISSEUR','Investisseur Extérieur',false),
 (10,'SOCIETE_MINIERE','Société Minière',false),
 (11,'COMMUNAUTE','Communauté Locale',false),
 (12,'PME','PME Guinéenne',false),
 (13,'TUTELLE','Organismes sous Tutelle',false),
 (14,'BRIGADE','Brigade Anti-Fraude',true),
 (15,'APPRENANT','Apprenant (Cadre du Ministère)',true),
 (16,'WEBMASTER','Webmaster',true);

INSERT INTO permission (code, libelle) VALUES
 ('DEPOSER','Déposer une demande'),('PAYER','Payer les frais et droits'),('INSTRUIRE','Instruire un dossier'),
 ('SIGNER','Signer électroniquement'),('ENCAISSER','Encaisser et rapprocher'),('VERIFIER','Vérifier un document'),
 ('PARAMETRER','Paramétrer la plateforme'),('CADASTRE_LECTURE','Lire le cadastre'),
 ('CADASTRE_ECRITURE','Écrire dans le cadastre'),('PUBLIER','Publier des contenus');

INSERT INTO profil_permission (profil_id, permission_id)
SELECT p.id, pm.id
  FROM (VALUES
   ('ADMIN_TECH','PARAMETRER'),('ADMIN_TECH','CADASTRE_LECTURE'),('ADMIN_TECH','VERIFIER'),
   ('GUICHET','INSTRUIRE'),('GUICHET','CADASTRE_ECRITURE'),('GUICHET','VERIFIER'),
   ('DNM','INSTRUIRE'),('DNM','CADASTRE_ECRITURE'),('DNM','VERIFIER'),
   ('INSPECTEUR','INSTRUIRE'),('INSPECTEUR','CADASTRE_LECTURE'),
   ('GEOLOGUE','INSTRUIRE'),('GEOLOGUE','CADASTRE_ECRITURE'),('GEOLOGUE','PUBLIER'),
   ('FISCAL','ENCAISSER'),('FISCAL','VERIFIER'),('FISCAL','CADASTRE_LECTURE'),
   ('DIRECTEUR','SIGNER'),('DIRECTEUR','CADASTRE_ECRITURE'),('DIRECTEUR','CADASTRE_LECTURE'),('DIRECTEUR','VERIFIER'),
   ('MINISTRE','SIGNER'),('MINISTRE','CADASTRE_ECRITURE'),('MINISTRE','CADASTRE_LECTURE'),
   ('INVESTISSEUR','DEPOSER'),('INVESTISSEUR','PAYER'),('INVESTISSEUR','CADASTRE_LECTURE'),('INVESTISSEUR','VERIFIER'),
   ('SOCIETE_MINIERE','DEPOSER'),('SOCIETE_MINIERE','PAYER'),('SOCIETE_MINIERE','CADASTRE_LECTURE'),('SOCIETE_MINIERE','VERIFIER'),
   ('PME','DEPOSER'),('PME','PAYER'),('PME','CADASTRE_LECTURE'),('PME','VERIFIER'),
   ('COMMUNAUTE','CADASTRE_LECTURE'),('COMMUNAUTE','VERIFIER'),
   ('TUTELLE','CADASTRE_LECTURE'),
   ('BRIGADE','INSTRUIRE'),('BRIGADE','VERIFIER'),('BRIGADE','CADASTRE_LECTURE'),
   ('APPRENANT','PAYER'),('WEBMASTER','PUBLIER')
  ) AS v(profil, perm)
  JOIN profil p ON p.code = v.profil
  JOIN permission pm ON pm.code = v.perm;

INSERT INTO prefecture (region, nom) VALUES
 ('Conakry','Conakry'),('Boké','Boké'),('Boké','Boffa'),('Kindia','Kindia'),('Kankan','Siguiri'),
 ('Kankan','Kouroussa'),('Kankan','Mandiana'),('N''Zérékoré','Beyla'),('Faranah','Dabola');

INSERT INTO substance (code, libelle_fr, libelle_en) VALUES
 ('OR','Or','Gold'),('BAUXITE','Bauxite','Bauxite'),('DIAMANT','Diamant','Diamond'),('FER','Minerai de fer','Iron ore'),
 ('GRANITE','Granite','Granite'),('SABLE','Sable','Sand'),('CALCAIRE','Calcaire','Limestone'),('GRAPHITE','Graphite','Graphite');

INSERT INTO workflow (code, libelle, regle_paiement, delai_min_jours, delai_max_jours) VALUES
 ('TITRES','Titres miniers','Frais au dépôt, droits avant délivrance',30,60),
 ('ARTISANAL','Artisanal et comptoirs','Frais et droits regroupés, TrésorPay ou virement',5,30),
 ('EXPORT','Export et expertise','Frais d''expertise puis droits d''export',2,3),
 ('ATTESTATIONS','Attestations et copies','Paiement unique avant délivrance',2,3),
 ('CONTENU_LOCAL','Contenu local','Paiement unique, renouvellement avec alerte',15,15);

INSERT INTO workflow_etape (workflow_id, ordre, acteur, libelle)
SELECT w.id, v.ordre, v.acteur, v.libelle
  FROM (VALUES
   ('TITRES',1,'Guichet unique','Réception et recevabilité'),('TITRES',2,'DNM','Instruction technique'),
   ('TITRES',3,'Géologue','Avis géologique'),('TITRES',4,'Inspection','Inspection de terrain'),
   ('TITRES',5,'Directeur','Avis de direction'),('TITRES',6,'Ministre','Décision et signature'),
   ('ARTISANAL',1,'Guichet unique','Réception et recevabilité'),('ARTISANAL',2,'DNM régionale ou Brigade','Instruction'),
   ('ARTISANAL',3,'Directeur','Décision et signature'),
   ('EXPORT',1,'BNE','Expertise du lot'),('EXPORT',2,'DNM, Fiscalité ou Brigade','Contrôle'),('EXPORT',3,'Signataire','Signature'),
   ('ATTESTATIONS',1,'Guichet ou agent compétent','Instruction'),('ATTESTATIONS',2,'Directeur','Signature'),
   ('CONTENU_LOCAL',1,'Contenu local','Instruction'),('CONTENU_LOCAL',2,'Directeur','Signature')
  ) AS v(wf, ordre, acteur, libelle)
  JOIN workflow w ON w.code = v.wf;

INSERT INTO type_document (code, libelle_fr, workflow_id, delai_jours, renouvelable, genere_titre, pieces_cles, instruit_par, signe_par)
SELECT v.code, v.libelle, w.id, v.delai, v.renouv, v.titre, v.pieces, v.instruit, v.signe
  FROM (VALUES
   ('PRC','Permis de reconnaissance','TITRES',30,true,true,'Statuts, capacités, carte de la zone','DNM + Géologue','Ministre'),
   ('PRE','Permis de recherche','TITRES',45,true,true,'Programme de travaux, capacités financières, périmètre','DNM + Géologue','Ministre'),
   ('PEX','Permis d''exploitation','TITRES',60,true,true,'Étude de faisabilité, EIES, plan de restauration','DNM + Inspection','Ministre'),
   ('CAR','Autorisation de carrière','TITRES',30,true,true,'Plan de carrière, accord foncier','DNM','Directeur national'),
   ('CES','Cession / transfert de titre','TITRES',45,false,false,'Acte de cession, attestation des redevances','DNM + Fiscalité','Ministre'),
   ('AEA','Autorisation d''exploitation artisanale','ARTISANAL',20,true,true,'Identité, accord communautaire, plan du site','DNM régionale','Directeur national'),
   ('COR','Carte d''orpailleur','ARTISANAL',5,true,false,'Pièce d''identité, photo, site de rattachement','Guichet unique','Directeur national'),
   ('AGC','Agrément de comptoir / négociant','ARTISANAL',30,true,false,'RCCM, attestation fiscale, local','DNM + Brigade','Directeur national'),
   ('CEX','Certificat d''expertise (BNE)','EXPORT',2,false,false,'Déclaration du lot, provenance licite','BNE','Chef du BNE'),
   ('AEX','Autorisation d''exportation','EXPORT',3,false,false,'Certificat d''expertise, déclaration en douane','DNM + Fiscalité','Directeur général'),
   ('KIM','Certificat de conformité export (diamant)','EXPORT',2,false,false,'Certificat d''expertise, preuve d''origine licite','BNE + Brigade','Directeur national'),
   ('EXC','Extrait de cadastre / non-chevauchement','ATTESTATIONS',2,false,false,'Référence de la zone ou du titre','Agent DNM','Directeur national'),
   ('CCC','Copie certifiée conforme de titre','ATTESTATIONS',3,false,false,'Numéro du titre, identité du titulaire','Guichet unique','Directeur national'),
   ('ARF','Attestation de situation des redevances','ATTESTATIONS',3,false,false,'Numéro du titre, période','Responsable fiscal','Directeur général'),
   ('PME','Agrément PME / contenu local','CONTENU_LOCAL',15,true,false,'RCCM, attestation fiscale, effectifs locaux','Contenu local','Directeur national')
  ) AS v(code, libelle, wf, delai, renouv, titre, pieces, instruit, signe)
  JOIN workflow w ON w.code = v.wf;

INSERT INTO tarif (type_document_id, objet, montant_gnf, date_effet, version, valide_par)
SELECT td.id, v.objet::objet_paiement, v.montant, date '2026-01-01', 1, 'Arrêté ministériel (données de test)'
  FROM (VALUES
   ('PRC','FRAIS_DOSSIER',2000000),('PRE','FRAIS_DOSSIER',3000000),('PEX','FRAIS_DOSSIER',5000000),('CAR','FRAIS_DOSSIER',2500000),
   ('CES','FRAIS_DOSSIER',4000000),('AEA','FRAIS_DOSSIER',500000),('COR','FRAIS_DOSSIER',100000),('AGC','FRAIS_DOSSIER',1000000),
   ('CEX','FRAIS_DOSSIER',300000),('AEX','FRAIS_DOSSIER',400000),('KIM','FRAIS_DOSSIER',300000),
   ('PRC','DROITS',3000000),('PRE','DROITS',6000000),('PEX','DROITS',15000000),('CAR','DROITS',5000000),
   ('AEA','DROITS',1000000),('AGC','DROITS',2000000),('AEX','DROITS',800000),
   ('EXC','ATTESTATION',200000),('CCC','ATTESTATION',150000),('ARF','ATTESTATION',150000),('PME','ATTESTATION',250000)
  ) AS v(code, objet, montant)
  JOIN type_document td ON td.code = v.code;

INSERT INTO transition_statut (entite, de, vers) VALUES
 ('demande','brouillon','deposee'),('demande','deposee','recevable'),('demande','deposee','complement_requis'),
 ('demande','deposee','rejetee'),('demande','complement_requis','deposee'),('demande','recevable','en_instruction'),
 ('demande','en_instruction','avis_rendu'),('demande','en_instruction','complement_requis'),('demande','avis_rendu','decision'),
 ('demande','decision','droits_payes'),('demande','decision','rejetee'),('demande','droits_payes','delivree'),
 ('demande','delivree','archivee'),('demande','rejetee','archivee'),
 ('paiement','initie','en_attente'),('paiement','initie','annule'),('paiement','initie','expire'),
 ('paiement','en_attente','paye'),('paiement','en_attente','echoue'),('paiement','en_attente','expire'),
 ('paiement','en_attente','a_verifier'),('paiement','en_attente','annule'),('paiement','a_verifier','paye'),
 ('paiement','a_verifier','rejete'),('paiement','a_verifier','remboursement_demande'),
 ('paiement','paye','remboursement_demande'),('paiement','echoue','initie'),('paiement','remboursement_demande','rembourse');

-- Adaptation Flyway : l'application ne modifie pas l'historique des migrations.
REVOKE INSERT, UPDATE ON flyway_schema_history FROM emine_app;
