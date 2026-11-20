# e-mine · Plan des sprints

> Date : 20/11/2026 · Scrum, **sprints d'une semaine** (lundi → vendredi) · Backlog : [03-product-backlog.md](03-product-backlog.md)
>
> **Priorité actuelle : la V1 de démonstration en une semaine ([07-plan-v1-une-semaine.md](07-plan-v1-une-semaine.md)).** Ce plan reprend ensuite ; les stories livrées en version allégée pendant la V1 y seront complétées, et les dates ci-dessous décalées d'autant.

## 1. Hypothèses

| Hypothèse | Valeur | À revoir |
|---|---|---|
| Équipe | Le porteur du projet (PO + développeur) assisté de Claude Code dans VS Code | Si l'équipe s'agrandit |
| Vélocité de départ | **≈ 21 points / sprint** | Recalée à la revue de S2, puis moyenne glissante sur 3 sprints |
| Périmètre planifié | Lots 0 à 4 + E12 = **475 points → 23 sprints** | Lot 5 planifié après la release v1.0 |
| Calendrier | Sprint 0 du 23/11/2026 ; S1 le 30/11/2026 | Jours fériés et congés non déduits |
| Intégrations réelles | Aucune avant le lot 5 : simulateurs (D5) | Selon conventions et licences du Ministère |

## 2. Rituels adaptés à une semaine

| Quand | Rituel | Durée | Sortie |
|---|---|---|---|
| Lundi matin | **Sprint planning** | 1 h | Objectif du sprint, stories engagées, branches créées |
| Chaque jour | **Daily** (ou point écrit si seul) | 10 min | Blocages identifiés |
| Mercredi | **Backlog refinement** du sprint suivant | 45 min | Stories « Ready » (DoR) pour le lundi |
| Vendredi après-midi | **Sprint review** + démo | 45 min | Incrément démontré, backlog réordonné |
| Vendredi après-midi | **Rétrospective** | 30 min | 1 ou 2 actions d'amélioration notées |

Outils : GitHub Projects (colonnes Backlog · Ready · Sprint · En cours · En revue · Terminé), un **milestone par sprint** (`S01`…), labels `epic:E04`, `lot:L1`, `type:story|bug|tech`, `prio:M|S|C`.

## 3. Definition of Ready (une story entre en sprint si…)

- Elle a un ID, une valeur métier claire et des **critères d'acceptation testables**.
- Elle est estimée et vaut **8 points au plus**.
- Ses dépendances (story, simulateur, donnée de test, décision) sont disponibles.
- Les maquettes concernées (diapositives du cahier des charges) sont identifiées.
- Les codes d'erreur et messages FR/EN attendus sont connus.

## 4. Definition of Done (une story est terminée si…)

- Le code est fusionné dans `main` par pull request, avec **CI verte** (build, tests, lint, CodeQL, Trivy, Gitleaks).
- Tests : unitaires + intégration Testcontainers pour le back ; composant ou E2E pour le front ; **≥ 80 %** sur les modules `paiement` et `cadastre`.
- OpenAPI à jour, client TypeScript régénéré, aucune erreur de type.
- Interface en **FR et EN**, sans violation axe-core sérieuse, utilisable de 320 à 1920 px.
- Toute écriture métier est tracée (journal d'audit) et respecte la RLS.
- Aucune donnée réelle, aucun secret ; nouvelles variables ajoutées à `.env.example`.
- Les critères d'acceptation sont vérifiés et démontrés à la revue.
- La documentation touchée (README, `docs/`, `CLAUDE.md`) est mise à jour.

## 5. Plan sprint par sprint

### Sprint 0 — Préparation (23/11 → 27/11/2026)

Pas de code de production. Objectif : tout ce qui est listé dans [05-prerequis-avant-de-coder.md](05-prerequis-avant-de-coder.md) est prêt (dépôt GitHub, postes de travail, issues des sprints 1 et 2, décisions). Le schéma `_client/e-mine_schema.sql` est copié tel quel en `api/src/main/resources/db/migration/V1__schema_initial.sql` ; son adaptation à Flyway est la story US-00.2 (S1). Les hooks Claude Code de formatage et lint, et le hook Git Gitleaks, sont mis en place (prérequis §9).

### Lot 0 — Socle

| Sprint | Dates | Objectif | Stories | Pts |
|---|---|---|---|---|
| **S1** | 30/11 → 04/12 | « L'environnement démarre et la base est migrée » | US-00.1, 00.2, 00.3, 00.4, 00.8 | 21 |
| **S2** | 07/12 → 11/12 | « Les deux fronts tournent avec le design system et la CI protège `main` » | US-00.5, 00.6, 00.7, 00.9 | 21 |
| **S3** | 14/12 → 18/12 | « On se connecte avec 2FA et la RLS filtre les données » | US-01.1, 01.2, 01.3, 01.5 | 21 |

🏁 **Jalon M1 (fin S3)** : socle complet, connexion des 16 profils de test.

### Lot 1 — E-DEMANDES MVP

| Sprint | Dates | Objectif | Stories | Pts |
|---|---|---|---|---|
| **S4** | 21/12 → 25/12 | « Les droits sont appliqués et le catalogue est consultable » | US-01.4, 01.6, 01.7, 02.1, 02.2 | 21 |
| **S5** | 28/12 → 01/01/2027 | « Tarifs versionnés et formulaire de demande » | US-02.3, 02.4, 02.5, 03.1 | 21 |
| **S6** | 04/01 → 08/01 | « Un demandeur dépose une demande complète » | US-03.2, 03.3, 03.4, 03.5 | 21 |
| **S7** | 11/01 → 15/01 | « Payer les frais par TrésorPay (simulé) » | US-04.1, 04.2, 04.3 | 21 |
| **S8** | 18/01 → 22/01 | « Paiement fiable : notifications, reçu, virement » | US-04.4, 04.5, 04.6, 04.7, 04.10 | 21 |
| **S9** | 25/01 → 29/01 | « Le fiscal rapproche les relevés ; supervision en place » | US-04.8, 04.9, 04.11, 12.1 | 23 |
| **S10** | 01/02 → 05/02 | « Une demande est instruite jusqu'à la décision » | US-05.1, 05.2, 05.3, 05.4, 05.5 | 23 |
| **S11** | 08/02 → 12/02 | « Contrôles de délivrance et premier document signé » | US-05.6, 05.7, 06.1, 06.2 | 19 |
| **S12** | 15/02 → 19/02 | « Document délivré, archivé, vérifiable et révocable » | US-06.3, 06.4, 06.5, 06.6, 06.7 | 21 |
| **S13** | 22/02 → 26/02 | « Le demandeur est notifié et suit son dossier ; parcours E2E vert » | US-07.1, 07.2, 07.3, 07.4 | 20 |

🏁 **Jalon M2 (fin S13) — release `v0.1.0` MVP E-DEMANDES** : démonstration au Ministère du parcours complet dépôt → paiement → instruction → décision → droits → délivrance → vérification QR. C'est le bon moment pour faire valider les points ouverts 5 à 15 de l'analyse.

### Lot 2 — Cadastre, et début du lot 3

| Sprint | Dates | Objectif | Stories | Pts |
|---|---|---|---|---|
| **S14** | 01/03 → 05/03 | « Les règles spatiales protègent le cadastre » | US-08.1, 08.2, 08.4 | 21 |
| **S15** | 08/03 → 12/03 | « Le public voit la carte et les fiches de titres » | US-08.3, 08.5, 08.6, 08.8 | 21 |
| **S16** | 15/03 → 19/03 | « Open data et alertes de renouvellement » | US-08.7, 09.1, 09.2, 09.3, 09.4 | 23 |

🏁 **Jalon M3 (fin S16) — `v0.2.0`** : cadastre public et renouvellement.

### Lot 3 — Site public

| Sprint | Dates | Objectif | Stories | Pts |
|---|---|---|---|---|
| **S17** | 22/03 → 26/03 | « Accueil et e-Services publics, référencés » | US-09.5, 10.1, 10.2, 10.8, 10.9 | 19 |
| **S18** | 29/03 → 02/04 | « Transparence, Investir et CMS Webmaster » | US-10.3, 10.4, 10.7a | 21 |
| **S19** | 05/04 → 09/04 | « Actualités, Contact, publication validée ; sauvegardes » | US-10.5, 10.6, 10.7b, 12.2 | 20 |

🏁 **Jalon M4 (fin S19) — `v0.3.0`** : les 9 menus du site public (Academy en page vitrine).

### Lot 4 — Espaces métier, puis durcissement

| Sprint | Dates | Objectif | Stories | Pts |
|---|---|---|---|---|
| **S20** | 12/04 → 16/04 | « Chaque profil a son espace » | US-11.1, 11.2a, 11.7 | 21 |
| **S21** | 19/04 → 23/04 | « Pilotage et messagerie » | US-11.2b, 11.4, 11.5, 12.7 | 23 |
| **S22** | 26/04 → 30/04 | « Rapports hiérarchiques ; configuration recette » | US-11.3, 11.6, 12.5, 12.6 | 22 |
| **S23** | 03/05 → 07/05 | « Prêt pour la recette du Ministère » | US-12.3, 12.4 + corrections | 10 + marge |

🏁 **Jalon M5 (fin S23) — `v1.0.0-rc`** : livraison en recette (code, scripts de déploiement, OpenAPI, guide d'exploitation).

### Lot 5 — à planifier après M5

Ordre proposé (à confirmer par le Ministère) : E21 intégrations réelles (dès que conventions et licences sont disponibles) → E13 FISCA-MINE → E14 INSPECT → E15 IMPACT/appels d'offres → E17 BRIGADE → E16 ENVIRO-RSE → E18 TUTELLE → E19 LOGI-TRACK → E20 ACADEMY.

## 6. Chemin critique et dépendances

```
S1 base+migrations ─► S3 auth/RLS ─► S4 droits ─► S6 dépôt ─► S7-S9 paiement ─► S10 instruction ─► S11-S12 délivrance ─► S13 MVP
                                         └─► S5 catalogue/tarifs ─┘                                  │
                          S11 SoftHSM2 + EU DSS (risque technique le plus fort : prototyper dès S1) ─┘
S14 simulateur cadastre ─► S15 carte ─► S16 renouvellement ─► S17-S19 site public ─► S20-S22 espaces ─► S23 recette
```

**Spikes à faire tôt** (timebox 1 jour, dans la marge de S1 ou S2) : signature PAdES avec EU DSS + SoftHSM2 · extension SMS Keycloak · `@arcgis/core` dans Next.js (rendu côté client uniquement).

## 7. Suivi

- **Burndown** de sprint dans GitHub Projects (champ « Points »).
- **Burnup** de release par jalon (M1 à M5).
- À chaque revue : vélocité réelle, nouvelle projection des dates M2 à M5.
