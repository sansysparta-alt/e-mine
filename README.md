# e-mine — MINE-GUINÉE 360°

Plateforme de démonstration pour le Ministère des Mines et de la Géologie de Guinée : un **site public** et une **application de gestion** reposant sur une seule base PostgreSQL.

Le cœur du projet est le module **E-DEMANDES** : demander en ligne l'un des 15 documents du Ministère, payer (TrésorPay ou virement), suivre sa demande, puis recevoir un PDF signé portant un QR code vérifiable publiquement.

> **État : V1 de démonstration en cours** ([docs/07](docs/07-plan-v1-une-semaine.md)).

## Documentation

| Document | Contenu |
|---|---|
| [01 · Analyse du cahier des charges](docs/01-analyse-cahier-des-charges.md) | Périmètre, exigences, décisions D1–D5 |
| [02 · Stack technique](docs/02-stack-technique.md) | Technologies, justifications, décisions D6–D10 |
| [03 · Product backlog](docs/03-product-backlog.md) | Epics et stories avec critères d'acceptation |
| [04 · Plan des sprints](docs/04-plan-des-sprints.md) | Sprints, Definition of Done, jalons |
| [05 · Prérequis avant de coder](docs/05-prerequis-avant-de-coder.md) | Poste de travail, ports, règles du dépôt public |
| [06 · Glossaire](docs/06-glossaire.md) | Sigles métier et codes des documents |
| [07 · Plan V1 en une semaine](docs/07-plan-v1-une-semaine.md) | Périmètre et déroulé de la V1 de démonstration |

## Stack (résumé)

- **Back-end** : Java 25, Spring Boot 4, Spring Modulith, jOOQ, Flyway
- **Base** : PostgreSQL 16 (RLS, triggers)
- **Front-end** : Next.js (App Router), TypeScript, pnpm + Turborepo, Tailwind, shadcn/ui
- **Authentification** : Keycloak (OIDC)
- **Documents** : OpenHTMLtoPDF (PDF/A), ZXing, EU DSS (PAdES), MinIO
- **Exploitation** : Docker Compose

Les services externes (TrésorPay, ArcGIS, HSM, SMS) sont **simulés** derrière des interfaces remplaçables.

## Démarrage

Prérequis : Docker Desktop, Node.js 26 et pnpm, JDK 25 (Temurin).

```bash
cp .env.example .env     # valeurs fictives, à adapter localement
pnpm install
pnpm infra:up            # PostgreSQL 5432, Keycloak 8081, MinIO 9000/9001, Mailpit 8025
pnpm dev                 # API 8080 + site public 3000 + application 3001
pnpm infra:down          # arrête les conteneurs (les données sont conservées)
```

Docker dispose d'environ 4 Go sur le poste de référence : chaque conteneur a une limite mémoire, et les tests Testcontainers ne doivent pas tourner en même temps que `pnpm infra:up`.

## Dépôt public : règles

- Aucun secret, clé, certificat ou mot de passe réel ; les secrets restent dans `.env` (non versionné).
- Aucune donnée réelle : noms, e-mails (`.test`), téléphones, IBAN et géométries sont fictifs.
- Aucun logo ni élément de charte officielle.

## Licence

À définir avec le Ministère.
