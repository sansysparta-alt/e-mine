# CLAUDE.md — e-mine (MINE-GUINÉE 360°)

Plateforme du Ministère des Mines et de la Géologie de Guinée : un **site public** (9 menus) et une **application de gestion** (16 profils, 13 modules) sur **une seule base PostgreSQL**. Cœur : le module **E-DEMANDES** — demander en ligne l'un des 15 documents du Ministère, payer (TrésorPay ou virement), suivre, recevoir un PDF signé avec QR vérifiable publiquement.

Langue du projet : **français** (documents, interface, commits, échanges). Interface bilingue FR/EN.

## État du projet

- **Priorité : la V1 de démonstration en une semaine — `docs/07-plan-v1-une-semaine.md`** (parcours E-DEMANDES de bout en bout avec simulateurs, un mini-sprint par jour). Pour une story de la V1, c'est sa **version allégée** dans `docs/07` qui fait foi. Le plan complet (`docs/03`, `docs/04`) reprend ensuite.
- **Git est initialisé** et relié à `https://github.com/sansysparta-alt/e-mine` (dépôt public). Le **premier commit sera fait depuis VS Code** par le porteur du projet.
- Démarrer chaque sprint avec **`/sprint J1`** (`.claude/commands/sprint.md`) ; voir « Méthode de travail ». Permissions : `.claude/settings.json` (commit, push de branche de story et PR autorisés ; fusion et suppressions sur accord ; push sur `main` et réécriture d'historique interdits).
- Là où `docs/07` (§4, §5) parle de `/clear` par story, de worktrees créés par le porteur du projet ou de fusion en fin de journée, **la méthode de ce fichier prévaut**.
- Socle créé au sprint J1 : les commandes ci-dessous existent ; l'arborescence est la cible (dossiers ajoutés au fil des stories). Mettre ce fichier à jour dès qu'elles changent.

## Documents de référence

| Fichier | Contenu |
|---|---|
| `docs/01-analyse-cahier-des-charges.md` | Périmètre, exigences chiffrées, incohérences, décisions D1–D5 |
| `docs/02-stack-technique.md` | Stack et justifications, décisions D6–D10 |
| `docs/03-product-backlog.md` | Epics, stories `US-xx.y`, critères d'acceptation |
| `docs/04-plan-des-sprints.md` | Sprints, DoR, **DoD**, jalons |
| `docs/05-prerequis-avant-de-coder.md` | GitHub, fichiers interdits, `.env.example`, ports, hooks (§9) |
| `docs/06-glossaire.md` | Sigles métier (CPDM, DNM, BNE, ITIE…) et codes des 15 documents |
| **`docs/07-plan-v1-une-semaine.md`** | **Priorité** : périmètre V1, jours 1 à 5, worktrees, DoD allégée |
| `docs/adr/` | Décisions d'architecture (à créer, liste dans `docs/05` §6) |
| `_client/` | Originaux du Ministère (2 `.pptx`, 2 `.sql`) — **exclus de Git, lecture seule** |

`_client/` : ne jamais modifier, renommer, déplacer ni versionner. Le schéma est copié en `api/src/main/resources/db/migration/V1__schema_initial.sql` au Sprint 0 ; on adapte **cette copie** (US-00.2), jamais l'original.

## Décisions à respecter (demander avant de les remettre en cause)

- **D1** Le schéma fourni est la base du socle et d'E-DEMANDES ; les autres modules ajoutent leurs tables lot par lot.
- **D2** Les **16 profils** et les **libellés de documents de l'application** font foi (pas ceux du site).
- **D3** Numéros `DEM-AAAA-NNNNNN`, `PAY-AAAA-NNNNNN`, `REC-AAAA-NNNNNN`.
- **D4** Le signataire de chaque document est **paramétré dans le catalogue**, jamais codé en dur.
- **D5** ArcGIS, TrésorPay, HSM et SMS sont **simulés** derrière des ports remplaçables.
- **D6** Stack ci-dessous · **D7** Dépôt **public** : aucun secret, aucune vraie donnée · **D8** Keycloak gère les comptes (`utilisateur.keycloak_id`) · **D9** Docker + MinIO par défaut · **D10** Scrum, sprints d'une semaine.

## Stack (détail et justifications : `docs/02`)

| Couche | Technologie |
|---|---|
| Back-end | Java 25, Spring Boot 4, Spring Modulith, **jOOQ (pas de JPA)**, Flyway, springdoc-openapi, db-scheduler + outbox, Maven (`mvnw`) |
| Base | PostgreSQL 16, schéma `emine`, RLS, triggers ; PostGIS seulement dans le simulateur cadastre |
| Front-end | Next.js (App Router), TypeScript, pnpm + Turborepo, Tailwind + shadcn/ui, next-intl, TanStack Query, React Hook Form + Zod, orval, `@arcgis/core`, Recharts, Serwist ; BFF : openid-client, iron-session |
| Auth | Keycloak (OIDC, SAML 2.0 vers ArcGIS, 2FA SMS par SPI) |
| Documents | Thymeleaf → OpenHTMLtoPDF (PDF/A), ZXing, EU DSS (PAdES), SoftHSM2 / PKCS#11, MinIO (Object Lock), ClamAV |
| Qualité | Prettier, ESLint, Spotless (google-java-format), JUnit 5, Testcontainers, ArchUnit, JaCoCo, Vitest, Playwright, axe-core, Lighthouse CI, k6, ZAP, Trivy, CodeQL, Gitleaks |
| Exploitation | Docker Compose, OpenTelemetry, Prometheus, Grafana, Loki, pgBackRest, OpenBao |

N'ajoute **aucune dépendance hors de cette liste sans demander**.

## Organisation du code (cible)

```
/            CLAUDE.md · README.md · .env.example · compose.yaml · package.json · pom.xml
_client/     originaux du Ministère (hors Git)       docs/   planification, adr/
db/bootstrap/  rôles, base, extensions (hors Flyway)
api/         Spring Boot — gn.gov.mines.emine.{shared, auth, admin, audit, catalogue, demande,
             paiement, instruction, delivrance, cadastre, notification}
             + src/main/resources/db/migration/ (V1__schema_initial.sql, V2__…)
apps/web/    site public (SSR/SSG)          apps/app/   application de gestion (BFF)
packages/ui/ design system « Bleu Souverain »   packages/api-client/  généré, ne pas éditer
simulateurs/{tresorpay,cadastre,sms}/       infra/{keycloak,softhsm,minio,observabilite}/
tests/{e2e,perf}/
```

Module Java = `api/` (REST, DTO) · `application/` (cas d'usage, transactions) · `domaine/` (règles, ports) · `infrastructure/` (jOOQ, adaptateurs). Un module n'utilise que l'API publique d'un autre (`ApplicationModules.verify()`).

## Commandes

```bash
pnpm infra:up / infra:down   # Docker Compose : PostgreSQL, Keycloak, MinIO, Mailpit (limites mémoire, ~4 Go Docker)
pnpm dev                     # API (8080) + web (3000) + app (3001) + simulateur TrésorPay (8090) via Turborepo
pnpm db:seed                 # personas de démo (après le 1er démarrage de l'API) · pnpm infra:keycloak:reset
pnpm signature:certificat    # certificat PKCS#12 de démonstration (infra/signature/, exclu de Git)
pnpm db:codegen              # jOOQ forcé (Testcontainers : infra ARRÊTÉE) · pnpm api:client (orval)
pnpm lint && pnpm typecheck && pnpm test && pnpm format:check
./mvnw -pl api verify        # mvnw.cmd sous Windows ; ne pas lancer en même temps que infra:up
./mvnw -pl simulateurs/tresorpay verify   # simulateur TrésorPay (contrat : docs/contrats/tresorpay.md)
```

Poste de dev sous **Windows 11** : scripts multiplateformes (scripts `package.json`, pas de `make` ni de Bash obligatoire). API sur l'hôte : `SIGNATURE_MODE=pkcs12` ; SoftHSM2 tourne dans l'image Docker de l'API et en CI. Ports : `docs/05` §5.

## Conventions

- **Nommage** : termes métier en français sans accents, alignés sur la base (`demande`, `paiement`, `titre`, `avisDecision`) ; termes techniques en anglais (`Controller`, `Repository`, `Gateway`). Ports : `CadastreGateway`, `PaiementGateway`, `SignatureService`, `SmsGateway`.
- **API** : `/api/v1/<ressources>` en kebab-case, JSON UTF-8, dates ISO 8601 UTC ; erreurs `ProblemDetail` `{code, message, details}` avec le catalogue AUTH, PERM, VAL, DEM, PAY, GEO, DOC, SYS (`docs/01` et `docs/03` US-00.8), messages FR/EN.
- **Montants** : dans E-DEMANDES (frais, droits, formations, attestations), **`long` en GNF entiers**, jamais `double` ni `BigDecimal`. Les **redevances multi-devises (USD/GNF)** de FISCA-MINE seront traitées au **lot 5** (E13), avec leur propre modèle.
- **Dates** : `Instant` / `timestamptz` en UTC ; affichage `JJ/MM/AAAA`, heure de Conakry.
- **Numéros** générés **par la base** (`fn_next_numero` via triggers), jamais en Java.
- **Statuts** : transitions contrôlées par `transition_statut` et triggers, jamais contournées (`DEM-001`).
- **Base** : ne jamais modifier une migration fusionnée (nouvelle `V<n>__…`) ; connexion `emine_app` ; `SET LOCAL emine.user_id / emine.interne / emine.ip` à chaque transaction (posé par `TransactionManagerAvecContexte`) : **tout accès aux données se fait dans une transaction**, sinon la RLS ne montre rien ; vues sur `demande`/`paiement` en `security_invoker` ; tests SQL avec Testcontainers (jamais H2) ; pas de suppression physique (`deleted_at`) ; code jOOQ généré non modifié.
- **Front** : Server Components par défaut, `"use client"` seulement si nécessaire ; aucun jeton dans le navigateur (BFF, cookie httpOnly) ; chaque texte dans `messages/fr.json` et `messages/en.json` ; couleurs et polices via `packages/ui` ; WCAG AA.
- **Socle commun (obligatoire pour tout nouvel écran)** : l'écran est placé sous `apps/app/src/app/(espace)/` (gabarit `GabaritApplication`) et ajouté à la navigation par **une ligne** dans `apps/app/src/navigation/config.ts` (module, adresse, icône, droit requis). Il utilise les composants de `packages/ui` : `EnTetePage`, `CarteChiffre`, `TableauDonnees`, `FiltresPastilles`, `ChampRecherche`, `BadgeStatut`, états `EtatVide` / `EtatChargement` / `EtatErreur` ; messages `Alerte` (jamais de barre colorée épaisse) ; consultation d'un document par le **visualiseur** (`useVisualiseurDocument`) ; listes **paginées** (`Pagination` à l'écran, `shared.pagination` dans l'API, jamais de limite fixe) ; **formats** de `packages/ui/src/format.ts` (aucun `Intl` ni `toLocale…` dans un écran) ; cases à cocher `CaseACocher` et cibles de 44 px ; icônes par `@emine/ui/icones`.
- **Règles générales du socle (tous les écrans futurs)** :
  - *Lignes cliquables* : dans toute liste ou tableau, la ligne entière ouvre l'élément (`TableauDonnees` avec `lienLigne`, ou `classesLienEtire` + `classesLigneCliquable`) ; c'est un vrai lien (Tab, Entrée, Ctrl+clic, clic du milieu, lecteurs d'écran) ; les boutons d'action d'une ligne passent au-dessus (`interactive`, `classesAuDessusDuLien`) et gardent leur comportement.
  - *Badges de statut* : toujours `BadgeStatut` avec le code couleur commun `TONS_STATUT` (gris = rien à faire pour vous ou en attente de l'autre partie, bleu = en cours, orange = action attendue **de vous**, vert = abouti, rouge = rejeté), icône + libellé (jamais la couleur seule), contraste AA vérifié par test ; **un seul badge par ligne**.
  - *Libellés de statut par profil* : le statut technique ne change pas, le libellé dit à chacun ce qui le concerne (demandeur : « Frais à payer », « En cours de vérification », « Droits à payer »… ; agent : « À examiner », « Au guichet », « Frais attendus », « À décider »…). Table unique `presentationStatut` (`apps/app/src/composants/demande/statut.ts`), libellés FR/EN `statutsAffiches.demandeur|agent.*`, réutilisée partout (listes, fiches, tableaux de bord, notifications) ; l'orange suit `aTraiterParMoi`, calculé par l'API.
  - *Filtres par profil* : 4 ou 5 filtres métier au plus, définis selon le profil (le demandeur et l'agent n'ont pas les mêmes besoins), chacun avec son nombre d'éléments ; le détail par statut va dans « Plus de filtres » ; un agent ne voit pas les brouillons d'un usager.
  - *Recherche* : `RechercheListe` (résultats pendant la saisie, court délai, adresse mise à jour).
  - *Règle « À traiter »* : « À traiter » = ce que **le profil connecté** doit faire maintenant (demandeur : payer les frais, payer les droits, compléter ; guichet : examiner les demandes dont les frais sont payés ; signataire : décider puis signer **ses** documents au catalogue). Une seule définition, **côté API** (`RegleATraiter`, `GET /demandes/a-traiter`, filtres `aTraiter`/`groupe`) ; écrans et tableaux de bord ne la recodent jamais. Les listes utilisent les **vues exclusives** de l'API (`GET /demandes/vues`, paramètre `vue`) : chaque demande est dans une seule vue et « Toutes » = la somme. L'API n'envoie jamais un brouillon à un agent.
  - *Prochaine étape* : ce qui est proposé dépend du profil qui consulte (action attendue pour l'agent, démarche pour l'usager).
  - *Dates et temps d'attente* : jamais un « aujourd'hui » seul sous une date. On affiche la date (« Déposée le JJ/MM/AAAA ») et, **séparément**, le temps d'attente **à l'étape actuelle** (« En attente depuis 1 jour »), en jours calendaires à l'heure de Conakry (`joursCalendairesDepuis` de `packages/ui`, `etapeDepuis` fourni par l'API), signalé à partir de 5 jours ; composant `DateAttente`.
- **Git** : branches `us-04.3-paiement-tresorpay`, `fix/…`, `tech/…` ; Conventional Commits en français avec l'ID de story (`feat(paiement): redirection TrésorPay (US-04.3)`) ; une PR par story.

## Règles impératives

1. **Dépôt public** : aucun secret, clé, certificat ou mot de passe réel dans le code, les tests, les commits ou les messages. Secrets dans `.env` (exclu) ; toute nouvelle variable va dans `.env.example` avec une valeur fictive.
2. **Rien de `_client/` dans Git** (ni fichier, ni copie, ni long extrait). Seule la migration issue du schéma est versionnée.
3. **Aucune vraie donnée** : noms fictifs, e-mails `.test`, `+224` fictifs, IBAN `GN00 0000 …`, géométries inventées, pas de logo officiel.
4. **Git** : branche, commit, push de la branche de story et PR sont faits par Claude (voir Méthode). **Jamais** : push sur `main`, fusion dans `main`, réécriture d'historique (`--force`, `rebase`, `reset --hard`, `--amend` sur une branche poussée), création de dépôt ou de release.
5. Toujours passer par les **ports** pour ArcGIS, TrésorPay, HSM, SMS et e-mail.
6. Respecter la **Definition of Done** (`docs/04` §4). Couverture **≥ 80 %** sur `paiement` et `cadastre`.
7. Écart au cahier des charges ou règle métier floue : voir « Quand demander » ci-dessous ; noter la décision dans le document concerné.

## Méthode de travail (sprint autonome)

**Feu vert par sprint.** Le porteur du projet lance un sprint (`/sprint J1`) : ce feu vert couvre **toutes les stories du sprint**. Une conversation par sprint ; `/clear` seulement en fin de sprint.

**Début de sprint (une seule interruption)**
1. Relire toutes les stories du sprint (`docs/07` pour la V1, sinon `docs/03` + `docs/04`), les décisions et le code existant.
2. Poser **en une seule fois** les questions qui relèvent du porteur du projet (voir « Quand demander »), et proposer l'**ordre des stories** (et les pistes parallèles en worktrees si utile).
3. Après sa réponse, **enchaîner les stories sans attendre de validation story par story**.

**Autonomie**
- Trancher seul les questions techniques courantes. Noter les **décisions structurantes** dans `docs/02-stack-technique.md` §6 (journal des décisions).
- **Quand demander** : écart au cahier des charges, changement de périmètre, dépense, sécurité ou données, décision difficile à défaire.
- **Jamais seul** : fusionner dans `main`, toucher un secret (créer, lire, modifier une vraie valeur), supprimer des données, engager une dépense, ajouter un service externe ou une dépendance hors stack.

**Git, une branche et une PR par story**
- Branche `us-xx.y-<sujet>` (ex. `us-04.3-paiement-tresorpay`) partant de `main`, ou de la branche de la story précédente si elle en dépend (PR empilée, base indiquée dans la PR).
- Claude commite (Conventional Commits en français, ID de story), **pousse sa branche et ouvre la PR** (`gh pr create`, critères d'acceptation dans la description), puis **enchaîne sans attendre la fusion**. Jamais de push direct sur `main`.

**Contrôles**
- À chaque story : lint et tests **des modules touchés**, écrits et lancés par Claude, corrigés jusqu'au vert.
- Une fois avant fusion : **pipeline complet** (CI de la PR). Le reste (E2E, contrôles longs) en fin de sprint.
- Le pipeline **ne bloque jamais le travail** : la story suivante démarre pendant que la CI tourne ; un échec est corrigé sur la branche concernée.
- **Avant de rendre la main** : relire son diff ; faire une **contre-épreuve** (introduire une erreur volontaire, vérifier qu'un test échoue, puis l'annuler).
- Hooks (`docs/05` §9) : corriger les erreurs signalées, ne jamais désactiver un hook.

**Compte rendu unique en fin de sprint**
- **Fait et prouvé** : chaque critère d'acceptation ↔ test qui le prouve, avec le lien de la PR.
- **Non fait** ou partiel, et pourquoi.
- **Décisions prises seul** (renvoi au journal `docs/02` §6).
- **Vérifications à faire dans le navigateur** : tableau adresse · compte de test · action · résultat attendu.
- Puis rappeler : fusionner les PR dans l'ordre, puis `/clear` avant le sprint suivant.

**Modèle et abonnement**
- Sonnet pour le travail courant ; **Opus pour le code difficile** (signature, paiement, RLS, règles spatiales) : le signaler au moment de basculer (`/model`).
- Prévenir quand la fenêtre d'utilisation de 5 h approche de sa fin (dès qu'un avertissement de limite apparaît ou après environ 4 h de travail dans la conversation), en indiquant où reprendre.
- Une erreur récurrente corrigée par le porteur du projet : proposer d'ajouter la règle dans ce fichier.
