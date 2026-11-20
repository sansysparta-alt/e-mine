# e-mine · Product backlog

> Date : 20/11/2026 · Méthode : Scrum, sprints d'une semaine · Références : [01-analyse](01-analyse-cahier-des-charges.md), [02-stack](02-stack-technique.md), [04-plan-des-sprints](04-plan-des-sprints.md)

## 0. Conventions

- **ID** : `US-<epic>.<n>` (ex. `US-04.3`). Une story = une issue GitHub, créée par le porteur du projet.
- **Points** : suite de Fibonacci (1, 2, 3, 5, 8, 13). Une story à 13 doit être découpée avant d'entrer en sprint.
- **Priorité (MoSCoW)** : **M** = indispensable au lot · **S** = devrait · **C** = pourrait · **W** = pas maintenant.
- **Lot** : L0 socle · L1 E-DEMANDES MVP · L2 cadastre · L3 renouvellement & site public · L4 espaces métier · L5 autres modules.
- **Critères d'acceptation** : détaillés pour les lots 0 à 2 (prêts à développer) ; les lots 3 à 5 seront affinés au backlog refinement.
- Le **PO** est le porteur du projet ; il ordonne le backlog. Les décisions **D1 à D10** s'appliquent à toutes les stories.

## 1. Vue des epics

| Epic | Titre | Lot | Points | Sprints prévus |
|---|---|---|---|---|
| E00 | Socle technique et outillage | L0 | 42 | S1–S2 |
| E01 | Authentification, profils, droits, audit | L0 | 34 | S3–S4 |
| E02 | Catalogue des documents et tarifs | L1 | 21 | S4–S5 |
| E03 | Dépôt d'une demande | L1 | 29 | S5–S6 |
| E04 | Paiement : TrésorPay, virement, rapprochement, remboursement | L1 | 60 | S7–S9 |
| E05 | Instruction et décision | L1 | 29 | S10–S11 |
| E06 | Délivrance sécurisée et vérification QR | L1 | 34 | S11–S12 |
| E07 | Notifications et suivi du demandeur | L1 | 20 | S13 |
| E08 | Cadastre (simulateur, contrôles spatiaux, carte, open data) | L2 | 47 | S14–S16 |
| E09 | Renouvellement des titres | L3 | 21 | S16–S17 |
| E10 | Site public e-mine web | L3 | 52 | S17–S19 |
| E11 | Espaces métier et menus communs | L4 | 55 | S20–S22 |
| E12 | Exploitation, performance, sécurité | L0→L4 | 31 | S9, S19, S21–S23 |
| E13–E21 | Modules complémentaires et intégrations réelles | L5 | à estimer | S24+ |

**Total lots 0 à 4 : 475 points**, soit environ 23 sprints à 21 points (voir le plan des sprints).

## 2. Lot 0 — Socle

### E00 · Socle technique et outillage

| ID | Story | Pts | Prio |
|---|---|---|---|
| US-00.1 | En tant que développeur, je lance tout l'environnement local avec une commande | 5 | M |
| US-00.2 | En tant que développeur, je dispose du schéma de base fourni sous forme de migrations Flyway | 5 | M |
| US-00.3 | En tant que développeur, j'utilise un code d'accès aux données généré depuis le schéma (jOOQ) | 3 | M |
| US-00.4 | En tant que développeur, je pars d'un squelette Spring Boot modulaire dont les frontières sont testées | 5 | M |
| US-00.5 | En tant que développeur front, je pars d'un monorepo avec `web`, `app`, `ui`, `api-client` | 5 | M |
| US-00.6 | En tant qu'utilisateur, je vois une interface au design « Bleu Souverain », accessible et bilingue | 8 | M |
| US-00.7 | En tant qu'équipe, chaque pull request passe une CI complète | 5 | M |
| US-00.8 | En tant qu'équipe, les erreurs de l'API suivent un format et un catalogue uniques | 3 | M |
| US-00.9 | En tant qu'équipe, un script injecte un jeu de données fictif cohérent | 3 | M |

**Critères d'acceptation**

- **US-00.1** — `pnpm infra:up` (Docker Compose) démarre PostgreSQL 16, Keycloak, MinIO, ClamAV, Mailpit et les simulateurs ; `pnpm dev` lance l'API et les deux fronts (scripts multiplateformes, le poste de dev est sous Windows) ; SoftHSM2 est une bibliothèque PKCS#11 installée dans l'image Docker de l'API (profil Compose `full` et CI) — l'API lancée sur l'hôte Windows utilise le profil de signature `pkcs12` ; aucune valeur secrète dans les fichiers versionnés ; `.env.example` documente chaque variable ; le README décrit le démarrage en moins de 10 lignes.
- **US-00.2** — Point de départ : la copie `api/src/main/resources/db/migration/V1__schema_initial.sql` faite au Sprint 0 depuis `_client/e-mine_schema.sql` (l'original n'est jamais modifié). Cette copie est adaptée à Flyway : les commandes `psql` (`\set`, `\connect`), la création des rôles, de la base et des extensions passent dans `db/bootstrap/` (mot de passe par variable) ; `V1` garde le schéma, les types, tables, fonctions, triggers, index, vues et la RLS ; les données de test passent dans le seed (US-00.9) ; `V2__numero_demande_6_chiffres.sql` applique D3 ; `V3__utilisateur_keycloak.sql` applique D8 ; un test Testcontainers vérifie que toutes les migrations passent sur une base vide.
- **US-00.3** — La génération jOOQ lit le schéma migré (Testcontainers) ; les enums PostgreSQL deviennent des enums Java ; le code généré n'est pas modifié à la main.
- **US-00.4** — Paquet racine `gn.gov.mines.emine` ; modules Spring Modulith `shared`, `auth`, `catalogue`, `demande`, `paiement`, `instruction`, `delivrance`, `cadastre`, `notification`, `admin`, `audit` ; test `ApplicationModules.verify()` vert ; `/actuator/health` répond.
- **US-00.5** — pnpm + Turborepo ; `apps/web` et `apps/app` en Next.js ; `packages/api-client` généré par orval depuis l'OpenAPI de l'API ; `pnpm build`, `pnpm lint`, `pnpm test` passent.
- **US-00.6** — Jetons de couleur (Navy #0A1F44, Royal #0B3A8F, Azure #1E6FD9, Sky #5AA9F0, Ice #EAF2FC, Or #C9A227), polices Cambria/Calibri ; composants bouton, champ, pastille de statut, tableau, carte, stepper ; FR/EN avec next-intl et sélecteur de langue ; axe-core sans violation sérieuse ; grille mobile-first 320→1920 px.
- **US-00.7** — GitHub Actions : build et tests back (Testcontainers), lint/typecheck/tests front, contrôle que l'OpenAPI et le client généré sont à jour, CodeQL, Trivy, Gitleaks ; checks obligatoires sur `main`.
- **US-00.8** — Réponse `{code, message, details}` (ProblemDetail) ; codes AUTH-001…SYS-001 de l'analyse ; messages FR/EN ; `SYS-001` affiche un identifiant d'incident.
- **US-00.9** — Commande `seed` : 16 utilisateurs (un par profil), 5 organisations, 15 types de documents, tarifs, ~20 titres, demandes à tous les statuts ; uniquement des données fictives (`.test`, +224 fictifs, IBAN `GN00…`).

### E01 · Authentification, profils, droits, audit

| ID | Story | Pts | Prio |
|---|---|---|---|
| US-01.1 | En tant qu'équipe, Keycloak est configuré par fichier (realm, clients, rôles des 16 profils, politique de mot de passe, verrouillage) | 5 | M |
| US-01.2 | En tant qu'usager, je me connecte via Keycloak et l'application garde mes jetons côté serveur (BFF) | 5 | M |
| US-01.3 | En tant qu'agent, je confirme ma connexion par un code SMS à 6 chiffres | 8 | M |
| US-01.4 | En tant qu'investisseur, je crée mon compte et celui de mon organisation | 5 | M |
| US-01.5 | En tant que système, chaque transaction renseigne `emine.user_id`, `emine.interne`, `emine.ip` pour la RLS et l'audit | 3 | M |
| US-01.6 | En tant qu'administrateur, les droits par profil de la matrice sont appliqués à chaque action | 5 | M |
| US-01.7 | En tant qu'administrateur, je crée, suspends et réaffecte des utilisateurs | 3 | S |

**Critères d'acceptation**

- **US-01.1** — Export du realm versionné (`infra/keycloak/`), importé au démarrage ; mot de passe ≥ 12 car. avec majuscule, chiffre, symbole ; blocage 15 min après 5 échecs (AUTH-002) ; rôles = codes des 16 profils.
- **US-01.2** — Flux OIDC Authorization Code + PKCE ; cookie httpOnly, Secure, SameSite ; jeton jamais lisible en JavaScript ; déconnexion et expiration de session (fenêtre de reconnexion, brouillon conservé).
- **US-01.3** — Extension Keycloak (SPI) branchée sur le port SMS ; code valable 5 min, 3 essais ; obligatoire pour les profils internes ; le simulateur SMS affiche le code reçu.
- **US-01.4** — Contrôles : téléphone `+224` + 9 chiffres, e-mail unique, NIF/RCCM 8–20 car. ; ligne `utilisateur` liée par `keycloak_id` ; profil externe attribué (09, 10 ou 12).
- **US-01.5** — `SET LOCAL` exécuté à l'ouverture de chaque transaction ; test : un investisseur ne voit que ses demandes et paiements, un agent voit tout.
- **US-01.6** — Matrice (déposer, payer, instruire, signer, encaisser, vérifier, paramétrer, cadastre L/É, publier) stockée dans `profil_permission` ; refus = PERM-001 (403) ; tests pour chaque cellule de la matrice.
- **US-01.7** — Actions tracées dans `journal_audit` ; suspension synchronisée avec Keycloak.

## 3. Lot 1 — E-DEMANDES MVP

### E02 · Catalogue des documents et tarifs

| ID | Story | Pts | Prio |
|---|---|---|---|
| US-02.1 | En tant que visiteur, je consulte le catalogue des 15 documents avec délais et pièces requises | 3 | M |
| US-02.2 | En tant qu'administrateur, je modifie la fiche d'un document (pièces, délai, workflow, signataire) | 5 | M |
| US-02.3 | En tant qu'administrateur, je crée une nouvelle version de tarif soumise à validation | 5 | M |
| US-02.4 | En tant que Ministre, je valide une version de tarif avant sa publication | 3 | M |
| US-02.5 | En tant qu'administrateur, je paramètre les 5 workflows (étapes, intervenants, délais) | 5 | S |

**Critères d'acceptation**

- **US-02.1** — Libellés de l'application (D2) ; FR/EN ; délai et pièces affichés ; page publique servie en SSR.
- **US-02.2** — Le signataire (Directeur, Directeur général, Ministre, Chef du BNE) est un paramètre du document (D4) ; modification tracée (ancienne et nouvelle valeur).
- **US-02.3 / 02.4** — Un tarif a une date d'effet et une version ; seule une version validée s'applique ; un paiement garde le tarif en vigueur à sa création.
- **US-02.5** — Un changement de workflow n'affecte pas les demandes en cours (`workflow_version`).

### E03 · Dépôt d'une demande

| ID | Story | Pts | Prio |
|---|---|---|---|
| US-03.1 | En tant que demandeur, je remplis le formulaire guidé et mon brouillon est enregistré automatiquement | 8 | M |
| US-03.2 | En tant que demandeur, je joins mes pièces, contrôlées en format, taille et antivirus | 5 | M |
| US-03.3 | En tant que demandeur, je saisis le périmètre (dessin, GeoJSON, Shapefile zippé, CSV) | 8 | M |
| US-03.4 | En tant que demandeur, je relis le récapitulatif, signe la déclaration sur l'honneur et dépose | 3 | M |
| US-03.5 | En tant que demandeur, je consulte la liste de mes demandes et leur statut | 5 | M |

**Critères d'acceptation**

- **US-03.1** — Sections Demandeur, Document, Périmètre, Pièces, Récapitulatif (le cas « 4 ou 6 sections » de l'analyse est tranché ici : 5 sections + dépôt) ; brouillon toutes les 30 s ; règles de saisie et messages de l'analyse ; pièces requises et délai adaptés au document choisi.
- **US-03.2** — PDF, JPG, PNG ; ≤ 10 Mo (DOC-001/002) ; analyse ClamAV avant acceptation ; stockage MinIO ; empreinte SHA-256 enregistrée.
- **US-03.3** — Polygone fermé, ≥ 4 sommets, sans auto-intersection (JTS) → GEO-001 ; fichier ≤ 5 Mo ; envoi au port `CadastreGateway` (contrôle complet en E08 ; ici le simulateur renvoie GEO-OK).
- **US-03.4** — Le dépôt crée le numéro `DEM-AAAA-NNNNNN` (D3), passe au statut `deposee` et crée le paiement des frais (E04).
- **US-03.5** — Liste filtrable ; RLS : uniquement mes demandes.

### E04 · Paiement

| ID | Story | Pts | Prio |
|---|---|---|---|
| US-04.1 | En tant qu'équipe, je dispose d'un simulateur TrésorPay rejouant tous les cas | 8 | M |
| US-04.2 | En tant que demandeur, je choisis mon moyen de paiement ; montant et référence sont fixés par le serveur | 5 | M |
| US-04.3 | En tant que demandeur, je paie par TrésorPay et je reviens sur e-mine avec mon reçu | 8 | M |
| US-04.4 | En tant que système, je traite la notification TrésorPay signée de façon idempotente | 5 | M |
| US-04.5 | En tant que système, sans notification, j'interroge TrésorPay toutes les 30 s pendant 15 min | 3 | M |
| US-04.6 | En tant que demandeur, je reçois un reçu PDF numéroté `REC-AAAA-NNNNNN` avec QR | 5 | M |
| US-04.7 | En tant que demandeur, je paie par virement : coordonnées, référence, justificatif | 5 | M |
| US-04.8 | En tant que responsable fiscal, j'importe un relevé CSV ou MT940 et le rapprochement est automatique | 8 | M |
| US-04.9 | En tant que responsable fiscal, je traite les écarts (À vérifier) | 5 | M |
| US-04.10 | En tant que responsable fiscal, je valide un remboursement | 3 | S |
| US-04.11 | En tant que responsable fiscal, je consulte et exporte le journal des encaissements | 5 | S |

**Critères d'acceptation**

- **US-04.1** — Initiation, page de paiement, retour navigateur, notification HMAC-SHA256 ; scénarios : succès, abandon, refus, absence de retour, double notification, signature invalide, montant divergent, indisponibilité ; scénario choisi par l'URL ou par la référence de test.
- **US-04.2** — Aucune présélection ; montant entier en GNF selon le barème ; référence `PAY-AAAA-NNNNNN` ; changement de moyen possible tant que le paiement n'est pas Payé (l'ancien passe à Annulé, une nouvelle référence est créée) ; PAY-001 si l'objet est déjà payé.
- **US-04.3** — Session de 15 min ; confirmation affichée en 10 s après le retour ; 3 tentatives par objet et par jour ; bandeau « choisissez le virement » si TrésorPay est indisponible (PAY-003).
- **US-04.4** — Signature invalide → 401 (PAY-004) + alerte + journal ; même identifiant de transaction → 200 sans retraitement ; montant divergent → `a_verifier` ; transitions contrôlées par `transition_statut`.
- **US-04.5** — Tâche db-scheduler ; au-delà de 15 min → `expire` ou `paye` selon la réponse, sinon `a_verifier`.
- **US-04.7** — Bénéficiaire, banque, IBAN fictif, montant, référence, date limite à 7 jours ; justificatif PDF/JPG/PNG ≤ 5 Mo obligatoire avant « J'ai effectué le virement ».
- **US-04.8** — Les 7 cas de rapprochement (conforme, inférieur, supérieur, référence inconnue, doublon, tardif, retour bancaire) couverts par des tests ; `fn_rapprocher_releve` réutilisée ; générateur de relevés fictifs fourni.
- **US-04.9 / 04.10** — Transitions À vérifier → Payé / Rejeté / Remboursement demandé → Remboursé ; tout est tracé.
- Couverture de tests ≥ 80 % sur le module `paiement` (exigence de recette).

### E05 · Instruction et décision

| ID | Story | Pts | Prio |
|---|---|---|---|
| US-05.1 | En tant qu'agent du guichet unique, je traite la file des demandes et je prononce la recevabilité | 5 | M |
| US-05.2 | En tant qu'agent, je demande un complément ; le demandeur répond depuis son suivi | 5 | M |
| US-05.3 | En tant qu'agent DNM, géologue ou inspecteur, je rends un avis motivé | 5 | M |
| US-05.4 | En tant que directeur ou Ministre, je prends la décision (favorable ou rejet motivé) | 5 | M |
| US-05.5 | En tant que demandeur, après décision favorable je paie les droits | 3 | M |
| US-05.6 | En tant qu'agent, je vois les délais par dossier et les dossiers proches du dépassement | 3 | S |
| US-05.7 | En tant que système, je bloque la délivrance si un contrôle automatique échoue | 3 | M |

**Critères d'acceptation**

- **US-05.1** — Recevable seulement si pièces complètes **et** frais payés ; vue pipeline (Reçues, Recevables, En instruction, À signer, Délivrées).
- **US-05.2** — Délai de réponse de 5 jours ; le délai de traitement est suspendu pendant le complément ; relance automatique.
- **US-05.3 / 05.4** — `avis_decision` (nature, sens, motif) ; rejet = `motif_rejet` obligatoire ; le signataire attendu est celui du catalogue (D4).
- **US-05.7** — Contrôles : redevances à jour, aucune non-conformité ouverte (stub jusqu'au lot INSPECT), droits payés.

### E06 · Délivrance sécurisée et vérification

| ID | Story | Pts | Prio |
|---|---|---|---|
| US-06.1 | En tant que système, je génère le PDF/A officiel du document à partir d'un gabarit FR/EN | 5 | M |
| US-06.2 | En tant que signataire, je signe le document (PAdES, horodatage) via le port de signature | 8 | M |
| US-06.3 | En tant que système, j'appose le QR, j'inscris le document au registre et je l'archive en WORM | 5 | M |
| US-06.4 | En tant que demandeur, je télécharge mes documents délivrés | 3 | M |
| US-06.5 | En tant que public, je vérifie un document par QR ou par numéro | 5 | M |
| US-06.6 | En tant que directeur ou brigade, je révoque un document ; la vérification affiche « NON VALIDE » | 5 | M |
| US-06.7 | En tant que système, l'octroi d'un titre crée le titre et le transmet au cadastre | 3 | M |

**Critères d'acceptation**

- **US-06.1** — Thymeleaf → OpenHTMLtoPDF, PDF/A ; logo de substitution ; 15 gabarits (un gabarit générique paramétré en L1, gabarits spécifiques au fil de l'eau).
- **US-06.2** — EU DSS, PAdES B-LT, PKCS#11 vers SoftHSM2 ; la signature est vérifiable par un lecteur PDF standard (certificat de test).
- **US-06.3** — Object Lock MinIO ; empreinte SHA-256 en base ; numéro unique `<CODE>-AAAA-NNNN`.
- **US-06.5** — Page publique `/verifier/{code}` en < 3 s ; aucune donnée personnelle ; « Document authentique » ou « NON VALIDE » (modifié, expiré, révoqué).
- **US-06.6** — Motif obligatoire ; effet immédiat ; titulaire et brigade notifiés.

### E07 · Notifications et suivi

| ID | Story | Pts | Prio |
|---|---|---|---|
| US-07.1 | En tant que système, j'envoie SMS et e-mails via une outbox fiable | 5 | M |
| US-07.2 | En tant que demandeur, je reçois les 9 notifications du catalogue | 5 | M |
| US-07.3 | En tant que demandeur, je suis ma demande sur une frise de 6 étapes | 5 | M |
| US-07.4 | En tant qu'équipe, un test E2E couvre dépôt → paiement → décision → délivrance → vérification | 5 | M |

**Critères d'acceptation**

- **US-07.1** — Table outbox + db-scheduler ; ports `SmsGateway` (simulateur) et e-mail (Mailpit) ; reprise sur échec ; statut dans `notification`.
- **US-07.2** — Événements : déposée, pièce manquante, paiement confirmé, paiement à vérifier, référence proche de l'expiration (J-2), décision, document délivré, remboursement ; modèles FR/EN selon `utilisateur.langue`.
- **US-07.3** — 11 statuts techniques regroupés en 6 étapes visibles (Dépôt, Contrôle, Paiement 1, Instruction, Paiement 2, Signé) : réponse au point 6 de l'analyse ; page disponible hors ligne (dernier statut consulté).
- **US-07.4** — Playwright, sur l'environnement Docker avec simulateurs ; exécuté en CI.

## 4. Lot 2 — Cadastre

### E08 · Cadastre

| ID | Story | Pts | Prio |
|---|---|---|---|
| US-08.1 | En tant qu'équipe, je dispose d'un simulateur cadastre PostGIS derrière `CadastreGateway` | 8 | M |
| US-08.2 | En tant que système, je contrôle superficie maximale, non-chevauchement et zones réservées (GEO-002/003/005) | 8 | M |
| US-08.3 | En tant que système, un contrôle > 5 s ou > 500 sommets passe en asynchrone avec notification | 3 | S |
| US-08.4 | En tant que système, les écritures vers le cadastre passent par une file avec reprise 1/5/15 min | 5 | M |
| US-08.5 | En tant que visiteur, je consulte la carte interactive (couches, filtres, bulle titre) | 8 | M |
| US-08.6 | En tant que visiteur, je consulte la fiche d'un titre avec périmètre et historique | 5 | M |
| US-08.7 | En tant que partenaire, j'utilise l'API ouverte (`/api/v1/titres`, GeoJSON) avec une clé et des quotas | 5 | S |
| US-08.8 | En tant qu'agent du guichet, je vérifie et géoréférence le périmètre d'un dossier | 5 | S |

**Critères d'acceptation**

- **US-08.1** — Base PostGIS séparée ; couches TITRES, DEMANDES_PERIMETRES, ZONES_RESERVEES, LIMITES_ADMIN (données fictives) ; stockage WGS 84, calculs en UTM 28N/29N selon le centroïde ; même contrat que l'adaptateur ArcGIS futur.
- **US-08.2** — Tolérance 0,01 ha (au-dessous : avertissement) ; zone réservée = blocage + avis du géologue ; ordre d'arrivée = date de recevabilité ; couverture de tests ≥ 80 % (exigence).
- **US-08.5** — `@arcgis/core` lisant le GeoJSON (et WMS via GeoServer si activé) ; ≤ 3 s jusqu'à 500 titres ; si carte indisponible, liste en tableau.
- **US-08.6** — `/cadastre/{numero}` en SSR ; statut affiché « Valide » pour `actif` (point 9 de l'analyse).

## 5. Lot 3 — Renouvellement et site public

### E09 · Renouvellement

| ID | Story | Pts | Prio |
|---|---|---|---|
| US-09.1 | En tant que titulaire et agent DNM, je reçois les alertes J-180, J-90, J-30 | 5 | M |
| US-09.2 | En tant que système, je suspends un titre à l'échéance et je l'affiche au cadastre | 3 | M |
| US-09.3 | En tant que titulaire, je lance un renouvellement avec un dossier pré-rempli | 5 | M |
| US-09.4 | En tant que système, je vérifie les conditions du renouvellement | 5 | M |
| US-09.5 | En tant que titulaire, je renouvelle tardivement avec pénalité | 3 | S |

### E10 · Site public e-mine web

| ID | Story | Pts | Prio |
|---|---|---|---|
| US-10.1 | Accueil : hero, recherche unique, accès rapides, chiffres clés calculés | 8 | M |
| US-10.2 | e-Services : pages publiques (catalogue, parcours, paiement, vérifier) | 3 | M |
| US-10.3 | Transparence : registre exportable CSV, recettes par trimestre, textes | 8 | M |
| US-10.4 | Investir : pourquoi la Guinée, blocs ouverts, guide, cadre légal | 5 | S |
| US-10.5 | Actualités : fil, article, communiqués, agenda, RSS | 5 | S |
| US-10.6 | Contact : formulaire avec numéro de suivi, annuaire, FAQ | 5 | S |
| US-10.7a | Webmaster : gestion des pages, menus, actualités et témoignages (brouillon, publication) | 8 | M |
| US-10.7b | Webmaster : validation avant publication, versions et restauration | 5 | M |
| US-10.8 | Pages d'erreur, maintenance, états vides, squelettes de chargement | 2 | M |
| US-10.9 | Référencement : sitemap XML, métadonnées, Lighthouse ≥ 90 | 3 | M |

## 6. Lot 4 — Espaces métier et menus communs

### E11 · Espaces métier

| ID | Story | Pts | Prio |
|---|---|---|---|
| US-11.1 | Coquille commune : barre latérale par profil, menus entièrement déroulés, fil d'Ariane | 5 | M |
| US-11.2a | Tableaux de bord des profils internes E-DEMANDES (01 Admin, 02 Guichet, 03 DNM, 06 Fiscal) | 8 | M |
| US-11.2b | Tableaux de bord de pilotage et externes (07 Directeur, 08 Ministre, 09 Investisseur) | 5 | M |
| US-11.3 | Tableaux de bord des autres profils (vues minimales en attendant leurs modules) | 8 | S |
| US-11.4 | Messagerie : canaux par métier, messages directs, pièces jointes, accusé de lecture | 8 | M |
| US-11.5 | Annonces ciblées par profil ou région (app, e-mail, SMS) | 5 | S |
| US-11.6 | Rapports hiérarchiques : génération, validation, transmission, archivage, PDF/Excel/CSV | 8 | M |
| US-11.7 | Recherche et filtres sur chaque tableau, historique des actions, consultation du journal d'audit | 8 | M |

## 7. Transverse — Exploitation et qualité

### E12 · Exploitation, performance, sécurité

| ID | Story | Pts | Prio |
|---|---|---|---|
| US-12.1 | Supervision : métriques, traces, journaux (OpenTelemetry, Prometheus, Grafana, Loki) | 5 | M |
| US-12.2 | Sauvegarde pgBackRest et test de restauration documenté | 5 | M |
| US-12.3 | Tests de charge k6 : 2 000 utilisateurs, 500 dépôts/jour, API ≤ 800 ms | 5 | M |
| US-12.4 | Scan OWASP ZAP et correction des alertes hautes et moyennes | 5 | M |
| US-12.5 | Configuration recette et production via OpenBao (aucun `.env` hors dev) | 3 | M |
| US-12.6 | Guide d'exploitation et documentation OpenAPI publiée | 3 | M |
| US-12.7 | Partitions mensuelles du journal d'audit créées automatiquement ; purge des journaux techniques à 12 mois | 5 | S |

## 8. Lot 5 — Modules complémentaires (epics à affiner)

| Epic | Module | Contenu principal | Dépendances externes |
|---|---|---|---|
| E13 | FISCA-MINE | Déclarations de volumes, calcul des redevances (multi-devise USD/GNF), relances, rapprochement ITIE, ARF | Barèmes fiscaux |
| E14 | INSPECT | Planning, rapports hors ligne (PWA ou Field Maps), non-conformités, synchronisation | Décision PWA / Field Maps |
| E15 | IMPACT | Appels d'offres (liste, fiche, dépôt horodaté, résultats), agrément PME, contenu local | — |
| E16 | ENVIRO-RSE | Plaintes et signalements confidentiels, FODEL, réunions | — |
| E17 | BRIGADE | Signalements, opérations, saisies, sites autorisés | — |
| E18 | TUTELLE | SOGUIPAMI, ANAIM, BNE (certificats d'expertise) | — |
| E19 | LOGI-TRACK | Production et exportations | Douanes |
| E20 | ACADEMY | Catalogue, inscription et paiement, certifications, SSO | Alphorm |
| E21 | Intégrations réelles | Adaptateurs ArcGIS Enterprise, TrésorPay, HSM, passerelle SMS | Conventions et licences |

## 9. Hors périmètre actuel (W)

Application mobile native · blockchain (exclue par le cahier des charges) · microservices · paiement par carte bancaire.
