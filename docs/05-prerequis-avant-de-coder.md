# e-mine · Prérequis avant de coder (Sprint 0)

> Date : 20/11/2026 · À terminer pendant le Sprint 0 (23/11 → 27/11/2026) · Responsable : porteur du projet, sauf mention contraire.
> Rappel : pendant la planification, rien n'est installé, codé ni créé sur GitHub par Claude.

## 1. Checklist du Sprint 0

| # | Tâche | Qui | Bloque |
|---|---|---|---|
| 1 | ✅ Git initialisé et relié à `https://github.com/sansysparta-alt/e-mine`. Reste : vérifier les réglages du §2 (secret scanning, push protection, ruleset sur `main`) | Porteur | S1 |
| 2 | ✅ Originaux du Ministère rangés dans `_client/` (fait le 20/11/2026, §3) | — | — |
| 3 | ✅ `.gitignore` créé à la racine (20/11/2026). Reste : `.gitattributes`, `.env.example`, `README.md` **avant** le premier commit (§4) | Porteur, ou Claude sur demande | 1er commit |
| 3b | Copier `_client/e-mine_schema.sql` en `api/src/main/resources/db/migration/V1__schema_initial.sql` (copie, l'original reste intact) | Porteur, ou Claude sur demande | US-00.2 |
| 4 | Préparer le poste de travail (§5) | Porteur | S1 |
| 5 | Créer le tableau GitHub Projects, les milestones S01–S23, les labels (voir plan des sprints §2) | Porteur | S1 |
| 6 | Créer les issues des **epics E00–E12** et des stories de **S1 et S2** depuis le backlog | Porteur | S1 |
| 7 | Rédiger les ADR (décisions D1–D10) dans `docs/adr/` (§6) | Porteur + Claude en S1 | — |
| 8 | Envoyer au Ministère la liste des demandes du §7 | Porteur | Lot 5, M2 |
| 9 | Choisir la licence du code avec le Ministère | Porteur | Publication |
| 10 | Relire et valider `CLAUDE.md` | Porteur | S1 |
| 11 | Installer les **hooks Claude Code** de formatage et lint, et le hook Git Gitleaks (§9) | Porteur + Claude | S1 |

## 2. Réglages du dépôt GitHub (public)

**Sécurité (Settings → Code security)**
- Secret scanning : **activé** · Push protection : **activée**
- Dependabot alerts et security updates : activés ; `dependabot.yml` pour Maven, npm, GitHub Actions et Docker (ajouté en S1)
- Code scanning **CodeQL** : Java et JavaScript/TypeScript (workflow ajouté en S2, US-00.7)
- Private vulnerability reporting : activé ; fichier `SECURITY.md`

**Branches (ruleset sur `main`)**
- Pull request obligatoire, 1 approbation (le porteur relit les PR préparées avec Claude)
- Checks obligatoires : build back, build front, tests, CodeQL, Gitleaks
- Historique linéaire (squash merge), suppression de branche après fusion, force push interdit

**Actions** : permissions du `GITHUB_TOKEN` en lecture par défaut ; aucun secret de production dans GitHub (la CI n'utilise que des valeurs fictives générées).

**Fichiers de gouvernance** (S1) : `README.md`, `SECURITY.md`, `CONTRIBUTING.md`, `.github/pull_request_template.md`, `.github/ISSUE_TEMPLATE/story.yml` (ID, valeur, critères d'acceptation, points, epic, lot), `CODEOWNERS`.

## 3. Ce qui ne va jamais dans le dépôt public

| Interdit | Pourquoi | Où le garder |
|---|---|---|
| Tout le dossier **`_client/`** : `e-mine_Application_de_gestion.pptx`, `e-mine_web.pptx`, `e-mine_schema.sql`, `e-mine_requetes_metier.sql` | Originaux du client, pas d'accord de publication | Restent dans `_client/`, exclu par `.gitignore` ; les `docs/` en contiennent l'analyse |
| Fichiers `.env*` (sauf `.env.example`) | Secrets | Poste local |
| Clés, certificats, jetons SoftHSM2, keystores `.p12`, `.jks`, `.pem` | Secrets | Générés localement par script, exclus |
| Vraies données : noms, e-mails, téléphones, NIF, IBAN, coordonnées de titres réels, montants réels | « Aucune vraie donnée » (D7) | — |
| Logo officiel, armoiries, charte du Ministère | Pas d'autorisation | Logo de substitution |
| Dumps de base, exports de relevés, fichiers clients | Données | — |

Seule la **copie** du schéma devenue `V1__schema_initial.sql` est versionnée (ses données de test sont fictives, et elles passent dans le seed). Le mot de passe `CHANGER_CE_MOT_DE_PASSE` y sera remplacé par une variable (US-00.2).

**Arborescence actuelle**

```
D:\DEV\projet-mine\
├── .git\           initialisé, relié à github.com/sansysparta-alt/e-mine
├── .gitignore      exclut notamment _client\ et les .env
├── CLAUDE.md
├── _client\        originaux du Ministère (exclu de Git, lecture seule)
│   ├── e-mine_Application_de_gestion.pptx
│   ├── e-mine_web.pptx
│   ├── e-mine_schema.sql
│   └── e-mine_requetes_metier.sql
└── docs\           01 à 06 (planification, glossaire)
```

## 4. Fichiers à créer avant le premier commit

### `.gitignore` (✅ créé à la racine ; contenu de référence)

```gitignore
# Secrets et configuration locale
.env
.env.*
!.env.example
*.p12
*.jks
*.pem
*.key
*.crt
infra/softhsm/tokens/
# Originaux du Ministère (présentations et scripts SQL)
_client/
# Documents bureautiques égarés ailleurs
*.pptx
*.docx
*.xlsx
# Java / Maven
target/
*.class
.mvn/wrapper/maven-wrapper.jar
# Node / Next.js
node_modules/
.next/
out/
.turbo/
coverage/
playwright-report/
test-results/
# Généré
packages/api-client/src/generated/
api/src/main/generated/
# IDE / OS
.idea/
*.iml
.vscode/*
!.vscode/extensions.json
!.vscode/settings.json
.DS_Store
Thumbs.db
# Claude Code (réglages personnels)
.claude/settings.local.json
```

### `.gitattributes`

```gitattributes
* text=auto eol=lf
*.cmd text eol=crlf
*.bat text eol=crlf
*.ps1 text eol=crlf
*.png binary
*.jpg binary
*.pdf binary
```

Indispensable sous Windows : les scripts exécutés dans les conteneurs Linux doivent rester en LF.

### `.env.example` (variables prévues, valeurs fictives uniquement)

| Groupe | Variables |
|---|---|
| Base | `POSTGRES_PASSWORD`, `EMINE_OWNER_PASSWORD`, `EMINE_APP_PASSWORD`, `DB_URL` |
| Keycloak | `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD`, `OIDC_ISSUER`, `OIDC_CLIENT_ID_APP`, `OIDC_CLIENT_SECRET_APP`, `OIDC_CLIENT_ID_WEB` |
| BFF Next.js | `SESSION_SECRET`, `API_BASE_URL` |
| MinIO | `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`, `S3_ENDPOINT`, `S3_BUCKET_PIECES`, `S3_BUCKET_DOCUMENTS` |
| TrésorPay (simulateur) | `TRESORPAY_BASE_URL`, `TRESORPAY_HMAC_SECRET`, `TRESORPAY_MERCHANT_ID` |
| Signature | `SIGNATURE_MODE` (`pkcs12` ou `pkcs11`), `SIGNATURE_PKCS11_LIB`, `SIGNATURE_PKCS11_PIN`, `SIGNATURE_PKCS12_PATH`, `SIGNATURE_PKCS12_PASSWORD`, `TSA_URL` |
| Cadastre (simulateur) | `CADASTRE_MODE` (`simulateur` ou `arcgis`), `CADASTRE_BASE_URL` |
| SMS / e-mail | `SMS_MODE` (`simulateur`), `SMTP_HOST`, `SMTP_PORT` |
| Virement | `VIREMENT_BENEFICIAIRE`, `VIREMENT_BANQUE`, `VIREMENT_IBAN` (`GN00 0000 …` fictif) |

## 5. Poste de travail (Windows 11)

| Outil | Version | Remarque |
|---|---|---|
| Git | récente | `git config --global core.autocrlf false` (le `.gitattributes` gère les fins de ligne) |
| JDK | **Temurin 25 LTS** | Maven fourni par le wrapper `mvnw` du projet |
| Node.js | **26 LTS** (déjà installé : v26.13) | `corepack enable` pour pnpm |
| pnpm | via corepack | version figée dans `package.json` (`packageManager`) |
| Docker Desktop | récent, moteur WSL 2 | **≥ 8 Go de RAM** alloués (ClamAV, Keycloak, PostgreSQL, MinIO) |
| VS Code | récent | Extensions : Claude Code, Extension Pack for Java, Spring Boot Extension Pack, ESLint, Prettier, Tailwind CSS IntelliSense, Docker, GitHub Pull Requests, EditorConfig, Playwright Test |
| Gitleaks | récent | Hook de pré-commit (en plus de la push protection GitHub) |
| **GitHub CLI (`gh`)** | récent | `gh auth login` une fois ; permet à Claude de lire issues, PR et résultats de CI (lecture autorisée d'avance, création soumise à accord) |
| Claude Code (VS Code) | récent | Voir §10 : réglages du projet, commande `/sprint`, acceptation automatique |

**Ports locaux réservés**

| Service | Port |
|---|---|
| Site public `web` | 3000 |
| Application `app` | 3001 |
| API Spring Boot | 8080 |
| Keycloak | 8081 |
| Simulateur TrésorPay | 8090 |
| Simulateur cadastre | 8091 |
| Simulateur SMS | 8092 |
| PostgreSQL e-mine | 5432 |
| PostGIS (simulateur cadastre) | 5433 |
| MinIO API / console | 9000 / 9001 |
| Mailpit SMTP / interface | 1025 / 8025 |
| ClamAV | 3310 |
| GeoServer (facultatif) | 8600 |
| Grafana (à partir de S9) | 3100 |

## 6. ADR à rédiger (`docs/adr/`)

Format court : contexte, décision, conséquences, statut.

| ADR | Sujet | Décision |
|---|---|---|
| 001 | Base de données pilotée par le schéma fourni | D1, jOOQ + Flyway |
| 002 | Référentiel profils et documents | D2 |
| 003 | Format des numéros | D3 (`DEM-AAAA-NNNNNN`) |
| 004 | Signataire paramétrable | D4 |
| 005 | Ports et simulateurs | D5 |
| 006 | Stack technique | D6 |
| 007 | Dépôt public et gestion des secrets | D7 |
| 008 | Keycloak et table `utilisateur` | D8 |
| 009 | Hébergement par défaut (Docker, MinIO) | D9 |
| 010 | Scrum, sprints d'une semaine | D10 |
| 011 | Monolithe modulaire (Spring Modulith) plutôt que microservices | §3.1 de la stack |
| 012 | BFF Next.js : aucun jeton dans le navigateur | §3.2 de la stack |

## 7. À demander au Ministère (non bloquant pour S1 à S13)

| Demande | Nécessaire pour |
|---|---|
| Validation des points ouverts 5 à 15 de l'analyse | M2 (démo MVP) |
| Gabarits officiels des 15 documents (textes, mentions légales) et autorisation d'utiliser le logo | Délivrance réelle |
| Barèmes officiels (frais et droits par document) | Recette |
| Convention d'interconnexion TrésorPay et accès à l'environnement d'essai du Trésor | E21 |
| Format des relevés bancaires (CSV ou MT940) et banques partenaires | E21 |
| HSM : modèle, PKCS#11, autorité d'horodatage qualifiée | E21 |
| Licence ArcGIS Enterprise, portail de développement, données cadastrales de test | E21 |
| Opérateur SMS et conditions | E21 |
| Hébergement de production (et orchestration) | Mise en production |
| Licence du code publié | Dépôt public |

## 8. Données de test fictives à préparer

- **16 personas**, un par profil, avec nom fictif, e-mail `@<organisation>.test`, téléphone `+224 6xx xxx xxx` fictif.
- **5 organisations** fictives (société minière, investisseur, PME, organisme sous tutelle, communauté).
- **Préfectures et régions** : référentiel public (données ouvertes, pas de donnée personnelle).
- **Substances** : bauxite, or, fer, diamant, autres.
- **Géométries** : ~20 polygones fictifs plausibles (Boké, Kindia, Siguiri, N'Zérékoré) dont 2 qui se chevauchent et 1 zone réservée, pour tester GEO-002 et GEO-003.
- **Relevés bancaires** CSV et MT940 couvrant les 7 cas de rapprochement.

## 9. Hooks de formatage et lint (à installer au Sprint 0)

Objectif : chaque fichier modifié par Claude est **formaté et linté automatiquement**, et Claude voit les erreurs restantes pour les corriger aussitôt.

### 9.1 Outils

| Fichiers | Formatage | Lint |
|---|---|---|
| `.ts`, `.tsx`, `.js`, `.mjs` | Prettier | ESLint `--fix` (configuration « flat » à la racine) |
| `.json`, `.css`, `.md`, `.yml`, `.yaml` | Prettier | — |
| `.java` | Spotless + google-java-format, limité au fichier modifié | Compilation et tests en CI (ArchUnit, Modulith) |
| `.sql` | — (migrations relues en PR) | Test Testcontainers des migrations |

Prettier, ESLint et Spotless sont ajoutés à la stack (rubrique Qualité) ; leur configuration est créée par US-00.4 (Spotless) et US-00.5 (Prettier, ESLint).

### 9.2 Hook Claude Code — `.claude/settings.json` (versionné)

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Edit|MultiEdit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "node \"$CLAUDE_PROJECT_DIR/scripts/hooks/format-lint.mjs\"",
            "timeout": 60
          }
        ]
      }
    ]
  }
}
```

### 9.3 Script `scripts/hooks/format-lint.mjs` (Node, multiplateforme)

Comportement attendu :
1. Lit le JSON reçu sur l'entrée standard et récupère `tool_input.file_path`.
2. **Ignore** : `_client/`, `node_modules/`, `.next/`, `target/`, code généré (`packages/api-client/src/generated/`, `api/src/main/generated/`), fichiers binaires.
3. Choisit l'outil selon l'extension (§9.1) : `pnpm exec prettier --write`, `pnpm exec eslint --fix`, `mvnw spotless:apply -DspotlessFiles=<fichier>` (`mvnw.cmd` sous Windows).
4. Si un outil n'est pas encore installé (avant S1/S2) : sortie silencieuse, code 0.
5. S'il reste des erreurs de lint : les écrit sur la sortie d'erreur et sort avec le **code 2**, pour que Claude les reçoive et les corrige.
6. Doit rester rapide (< 5 s par fichier) ; si Spotless via Maven est trop lent, appeler directement le jar google-java-format.

### 9.4 Hook Git de pré-commit (Git est initialisé : à activer)

- `.githooks/pre-commit` : `gitleaks protect --staged` (bloque un secret avant qu'il parte), puis Prettier et ESLint sur les fichiers indexés.
- Activation par le porteur du projet : `git config core.hooksPath .githooks`.
- Complète la push protection de GitHub, ne la remplace pas.

### 9.5 Vérification

- Modifier un fichier `.tsx` mal formaté via Claude → il est reformaté.
- Introduire une erreur ESLint → Claude reçoit le message et la corrige.
- Modifier un fichier dans `_client/` → le hook ne fait rien (et Claude ne doit de toute façon pas y écrire).
- Commit d'un faux secret de test → bloqué par Gitleaks.

## 10. Réglages Claude Code (créés le 20/11/2026)

| Fichier | Rôle |
|---|---|
| `.claude/settings.json` (versionné) | **Autorisé d'avance** : `pnpm`, `mvnw`, `docker compose up/ps/logs/build/config/stop/restart`, lecture Git, `git switch/add/commit`, push d'une branche `us-*`, `gh pr create/edit/comment`, lecture `gh`. **Sur accord** : `gh pr merge`, `git merge/reset/clean/restore`, suppressions (`rm`, branches, worktrees, volumes, conteneurs, `docker compose down`), `pnpm add/remove`, `gh issue create`. **Interdit** : push sur `main`/`master`, `--force`, `rebase`, `reset --hard`, `commit --amend`, `filter-branch/filter-repo`, `gh repo`/`gh release`, modifier `_client/`, lire les `.env`. **Hook** : tout `git push` lancé depuis `main` ou `master` est bloqué (filet de sécurité en plus des règles). Les hooks de formatage et lint (§9) seront ajoutés au premier sprint. |
| `.claude/commands/sprint.md` (versionné) | Commande **`/sprint J1`** (ou `S1`…) : relit toutes les stories du sprint, pose les questions en une fois, propose l'ordre, puis enchaîne les stories en autonomie (branche + PR par story) et rend un compte rendu unique. |
| `.claude/settings.local.json` (exclu de Git) | Réglages personnels éventuels. |

**Mode d'acceptation automatique** : après la réponse aux questions de début de sprint, appuyer sur **Shift+Tab** pour accepter automatiquement les modifications de fichiers pendant tout le sprint ; les commandes « sur accord » (fusion, suppressions…) restent soumises à confirmation et les commandes interdites restent bloquées.

**Indispensable côté GitHub** : la règle de protection (ruleset) de `main` du §2 — PR obligatoire, push direct et force push interdits. C'est la vraie barrière ; les règles de `.claude/settings.json` et le hook ne sont qu'un filet local.
