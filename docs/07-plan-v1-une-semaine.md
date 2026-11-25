# e-mine · Plan V1 de démonstration en une semaine

> Date : 20/11/2026, mis à jour le 25/11/2026 (lot 4, voir §9) · **Priorité actuelle du projet.** Le plan complet ([03](03-product-backlog.md), [04](04-plan-des-sprints.md)) reste valable pour la suite : la V1 en réalise une partie, en version allégée.
> Développement avec Claude dans VS Code, une conversation par sprint, lancée avec `/sprint J1` … `/sprint J5` (méthode : `CLAUDE.md`).

## 1. Objectif

Montrer **de bout en bout**, sur un poste de démonstration, le parcours E-DEMANDES avec simulateurs :

> Un investisseur se connecte, dépose une demande de permis, paie les frais par TrésorPay (simulé) ; l'agent du guichet la déclare recevable ; le directeur prend une décision favorable ; l'investisseur paie les droits ; il télécharge un PDF signé avec QR ; n'importe qui vérifie ce QR sur une page publique.

## 2. Périmètre V1

| Domaine | Inclus en V1 | Stories (version allégée) |
|---|---|---|
| Socle | Docker Compose (PostgreSQL, Keycloak, MinIO, Mailpit), migrations Flyway V1–V3, jOOQ, squelette Spring Modulith, monorepo Next.js, erreurs `ProblemDetail` | US-00.1 à 00.5, 00.8, 00.9 (seed minimal) |
| Auth et profils | Keycloak (realm versionné, 16 rôles, 5 personas de démo), connexion OIDC par BFF, `SET LOCAL` + RLS, droits par profil sur les actions de la V1 | US-01.1, 01.2, 01.5, 01.6 (partiel) |
| Audit | Journal alimenté par les triggers existants ; page de consultation simple pour l'administrateur | US-11.7 (partiel) |
| Catalogue | Lecture des 15 documents et des tarifs du seed (pas d'édition) | US-02.1 |
| Dépôt | Formulaire guidé sans périmètre cartographique, pièces PDF/JPG/PNG dans MinIO, numéro `DEM-AAAA-NNNNNN`, liste « Mes demandes » | US-03.1, 03.2 (sans ClamAV), 03.4, 03.5 |
| Paiement | Simulateur TrésorPay (succès, refus, signature invalide), création du paiement, redirection et retour, notification HMAC idempotente, reçu PDF simple | US-04.1, 04.2, 04.3, 04.4, 04.6 |
| Instruction | File du guichet et recevabilité, décision du directeur (favorable ou rejet motivé), paiement des droits | US-05.1, 05.4, 05.5 |
| Délivrance | PDF/A générique avec QR, signature PAdES en mode `pkcs12` (certificat de test), archivage MinIO, page publique `/verifier/{code}`, révocation « NON VALIDE » | US-06.1, 06.2, 06.3, 06.4, 06.5, 06.6 |
| Qualité | Tests JUnit + Testcontainers sur les règles, Vitest sur les composants clés, **1 test Playwright** du parcours complet, hooks de formatage/lint, CI GitHub minimale (build + tests) | US-00.7 (minimal), 07.4 |

**Personas de démo** (données fictives) : Investisseur (09), Agent guichet unique (02), Directeur (07), Responsable fiscal (06, consultation des paiements), Administrateur technique (01, journal d'audit).

**Simplifications assumées en V1** (à l'origine ; plusieurs ont été levées depuis, voir §9) : interface en **français seulement** (clés de traduction prêtes pour l'anglais) ; un seul gabarit PDF générique ; pas de 2FA SMS ; pas de complément de pièces ; avis technique non obligatoire avant la décision ; design system réduit aux composants utilisés.

## 3. Hors V1 (repris dans le plan complet)

| Reporté | Où dans le plan |
|---|---|
| 2FA SMS, création de compte en ligne, gestion des utilisateurs | US-01.3, 01.4, 01.7 |
| Édition du catalogue, tarifs versionnés, workflows paramétrables | US-02.2 à 02.5 |
| Périmètre et contrôles spatiaux, carte, cadastre, open data | US-03.3, E08 |
| ClamAV sur les pièces | US-03.2 complète |
| Virement, rapprochement, remboursement, journal fiscal, polling TrésorPay | US-04.5, 04.7 à 04.11 |
| Compléments, avis techniques, délais, contrôles automatiques | US-05.2, 05.3, 05.6, 05.7 |
| SoftHSM2 / PKCS#11, Object Lock WORM, gabarits spécifiques | US-06.2 et 06.3 complètes |
| Notifications SMS et e-mail, frise de suivi | E07 |
| Renouvellement, site public complet, espaces métier, messagerie, rapports | E09, E10, E11 |
| k6, OWASP ZAP, Lighthouse CI, Trivy, observabilité (OpenTelemetry, Grafana, Loki), pgBackRest, OpenBao | E12 |
| Anglais (EN), PWA hors ligne | DoD complète, US-07.3 |
| Modules du lot 5 et intégrations réelles | E13 à E21 |

## 4. Un mini-sprint par jour

Chaque jour : **planning de 15 min le matin** (stories du jour, worktrees), **démo le soir** (le résultat ci-dessous doit marcher). Une branche et une PR par story, enchaînées sans `/clear` ; `/clear` seulement en fin de sprint (méthode de `CLAUDE.md`).

### Jour 1 — Socle et connexion

| Story | Contenu V1 |
|---|---|
| US-00.1 | `compose.yaml` (PostgreSQL 16, Keycloak, MinIO, Mailpit), scripts racine `pnpm infra:up`, `pnpm dev`, `.env.example` |
| US-00.2 | Adapter la copie `V1__schema_initial.sql`, `db/bootstrap/`, `V2` (DEM à 6 chiffres), `V3` (`keycloak_id`) |
| US-00.3 / 00.4 | Génération jOOQ, squelette Spring Boot + Modulith, `/actuator/health` |
| US-00.5 | Monorepo pnpm + Turborepo, `apps/app`, `apps/web`, `packages/ui`, `packages/api-client` |
| US-01.1 / 01.2 | Realm Keycloak (16 rôles, 5 personas), connexion OIDC via BFF |
| US-01.5 | `SET LOCAL` par transaction, test RLS |

**Démo du soir** : `pnpm infra:up` puis `pnpm dev` ; l'investisseur et l'agent se connectent et voient chacun un tableau de bord vide à leur nom.

**Parallèle (worktrees)** : `v1-back` (00.1–00.4, 01.5) ‖ `v1-front` (00.5, page de connexion) ; le realm Keycloak (01.1) va avec `v1-back`. La connexion (01.2) vient ensuite, en PR empilée sur les stories dont elle dépend (voir §5).

### Jour 2 — Catalogue et dépôt

| Story | Contenu V1 |
|---|---|
| US-00.8 / 00.9 | Erreurs `ProblemDetail` + seed minimal (5 personas, 15 documents, tarifs, organisations) |
| US-02.1 | `GET /api/v1/types-documents` + page catalogue |
| US-03.1 / 03.4 | Formulaire de demande (Demandeur, Document, Pièces, Récapitulatif), brouillon, dépôt |
| US-03.2 | Pièces jointes vers MinIO, contrôle format et taille, empreinte SHA-256 |
| US-03.5 | « Mes demandes » (RLS) |
| US-01.6 | Droits « déposer » appliqués |

**Démo du soir** : l'investisseur dépose une demande PEX avec deux pièces et obtient `DEM-2026-000001` ; un autre investisseur ne la voit pas.

**Parallèle** : dès le matin, fixer le contrat OpenAPI (DTO) des demandes ; puis `v1-back-demande` (endpoints, MinIO) ‖ `v1-front-demande` (formulaire, liste) avec le client généré.

### Jour 3 — Paiement des frais

| Story | Contenu V1 |
|---|---|
| US-04.1 | Simulateur TrésorPay : initiation, page de paiement, retour, notification HMAC ; scénarios succès, refus, signature invalide |
| US-04.2 / 04.3 | Choix du moyen (TrésorPay seul actif, virement affiché « bientôt »), référence `PAY-…`, redirection, retour |
| US-04.4 | Notification signée, idempotence, transitions de statut |
| US-04.6 | Reçu PDF `REC-…` simple, sans signature |

**Démo du soir** : l'investisseur paie les frais de sa demande ; le statut passe à Payé, le reçu se télécharge ; un refus laisse une nouvelle tentative possible.

**Parallèle** : `v1-simulateur-tresorpay` (application indépendante dans `simulateurs/tresorpay/`) ‖ `v1-paiement` (module `paiement` de l'API + écrans), en partant du contrat d'échange défini le matin.

### Jour 4 — Recevabilité, décision, droits

| Story | Contenu V1 |
|---|---|
| US-05.1 | File du guichet ; recevable si pièces présentes **et** frais payés |
| US-05.4 | Décision du directeur : favorable, ou rejet avec motif obligatoire ; signataire lu dans le catalogue (D4) |
| US-05.5 | Paiement des droits (réutilise le jour 3) |
| US-11.7 (partiel) | Page « Journal d'audit » pour l'administrateur |

**Démo du soir** : l'agent déclare la demande recevable, le directeur décide, l'investisseur paie les droits ; l'administrateur voit toutes ces actions dans le journal.

**Parallèle** : `v1-instruction` (guichet + décision) ‖ `v1-audit` (page du journal), fichiers distincts.

### Jour 5 — Délivrance, vérification, démonstration complète

| Story | Contenu V1 |
|---|---|
| US-06.1 | Gabarit Thymeleaf générique → PDF/A |
| US-06.2 | Signature PAdES avec EU DSS en mode `pkcs12` (certificat de test généré localement, exclu de Git) |
| US-06.3 / 06.4 | QR (ZXing), registre `document_delivre`, archivage MinIO, téléchargement |
| US-06.5 | Page publique `apps/web` `/verifier/{code}` |
| US-06.6 | Révocation par le directeur → « NON VALIDE » |
| US-07.4 | Test Playwright du parcours complet, en CI |

**Démo du soir = démo V1** : le parcours complet du §1, puis révocation et nouvelle vérification « NON VALIDE ».

**Parallèle** : `v1-delivrance` (PDF, signature, QR, API) ‖ `v1-verification` (page publique `apps/web`) ; le test Playwright se fait après fusion.

> **Risque principal** : la signature PAdES (EU DSS). Faire un essai d'une heure dès le jour 1 au soir ou le jour 2 ; si cela bloque, la V1 livre un PDF avec QR et empreinte SHA-256, la signature passant au premier sprint suivant.

## 5. Worktrees : règles

- Les pistes `v1-<piste>` du §4 désignent des **worktrees**, pas des branches : dans chaque piste, les branches restent **une par story** (`us-xx.y-<sujet>`), avec **une PR par story** (méthode de `CLAUDE.md`).
- Pistes parallèles seulement si elles ne touchent **pas les mêmes fichiers** ; le contrat (OpenAPI ou échange TrésorPay) est fixé **avant** de séparer.
- Claude propose les pistes au début du sprint (`/sprint J<n>`), dans le message unique de questions ; après le feu vert du porteur du projet, **Claude crée les worktrees** et y crée ses branches, commite, pousse et ouvre les PR. La suppression d'un worktree reste soumise à accord.
- Une story qui dépend d'une autre part de la branche de celle-ci (**PR empilée**, base indiquée dans la PR) ; on n'attend pas de fusion pour avancer.
- **Seul le porteur du projet fusionne dans `main`**, dans l'ordre donné par le compte rendu de fin de sprint ; la CI de chaque PR passe avant fusion, et les contrôles longs (E2E) tournent en fin de sprint.

## 6. Outillage réduit V1

| Garder | Reporter après la V1 |
|---|---|
| Docker Compose, Flyway, jOOQ, Spring Modulith | k6, OWASP ZAP, Lighthouse CI |
| JUnit 5, Testcontainers, Vitest, 1 test Playwright | Trivy, CodeQL en workflow (le « default setup » GitHub suffit) |
| Prettier, ESLint, Spotless + hooks Claude Code | OpenTelemetry, Prometheus, Grafana, Loki |
| Gitleaks (pré-commit) + push protection GitHub | pgBackRest, OpenBao |
| CI GitHub minimale : build + tests back et front | ClamAV, SoftHSM2, GeoServer, PostGIS |

## 7. Definition of Done V1 (allégée)

- Le résultat démontrable du jour fonctionne sur l'environnement Docker.
- Tests écrits et **verts** pour les règles touchées (statuts, droits, RLS, paiement, signature), lancés par Claude.
- Lint et formatage propres (hooks), CI verte.
- Aucun secret, aucune vraie donnée, rien de `_client/`.
- OpenAPI et client régénérés si l'API change.
- Écarts par rapport à la DoD complète notés dans la PR (ils sont rattrapés dans le plan complet).

## 8. Après la V1

Reprendre le plan des sprints ([04](04-plan-des-sprints.md)) en marquant les stories déjà livrées en version allégée, puis compléter ce qui a été simplifié (§2) avant d'avancer sur les lots suivants.

## 9. Avancement et périmètre V1 au 25/11/2026 (lot 4)

La V1 a dépassé la version allégée de la semaine initiale. Ce tableau fait foi pour la démonstration ; les décisions de périmètre sont dans [01 §3](01-analyse-cahier-des-charges.md) (« Décisions du porteur du projet pour la V1 »).

| Domaine | Livré en V1 | Reporté en V2 |
|---|---|---|
| Langues | Interface FR / EN complète ; référentiels et **textes saisis par l'administrateur** en FR / EN (US-02.9) | — |
| Catalogue | Fiches versionnées, tarifs à date d'effet, **validation par le Ministre** ; CEX et KIM désactivés (profil BNE en V2) | CEX, KIM, profil BNE |
| Workflows et délais | Versions **proposées par l'administrateur, validées par le Ministre, appliquées à leur date d'effet** ; une demande garde la version de son dépôt (US-02.8) | — |
| Notifications | Canaux par événement (réglage immédiat), e-mails et SMS simulés reçus dans Mailpit | Passerelle SMS réelle |
| Paiement | TrésorPay simulé et **virement** ; frais non remboursés sur rejet, sauf double paiement ou erreur | Réglage administrable du remboursement |
| Instruction | Recevabilité, compléments, **avis du Géologue et de l'Inspecteur** (avis seulement), décision, délais | Rubriques métier cadastre et inspection |
| Délivrance | PDF signé, QR, vérification publique, révocation avec **autorité et motif** ; lecteur PDF paginé (pdf.js) | — |
| Comptes | Gérés dans Keycloak (D8) ; titre officiel de l'agent (`utilisateur.titre`) | Écran d'administration des comptes |
| Espaces | Tableaux de bord par profil, « À traiter », journal d'audit | **Renouvellement**, **Messagerie**, **Annonces**, **Rapports** hiérarchiques |
