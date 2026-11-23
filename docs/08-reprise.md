# Reprise — sprint « Catalogue administrable » (23/11/2026, interrompu : fenêtre de 5 h)

## PR ouvertes (à fusionner dans l'ordre)

| PR | Branche | Story | Base |
|---|---|---|---|
| #80 | `docs/migrations-v11-v14` | docs/01 §7 : V11 à V14 | `main` |
| #81 | `us-02.2-fiche-document` | US-02.2 / 02.4 : fiches versionnées approuvées par le Ministre (V18), écrans « Catalogue et tarifs » | `main` |
| #82 | `us-02.3-tarifs-versionnes` | US-02.3 / 02.4 : tarifs versionnés (V19) | #81 |

## Branche en cours : `us-02.5-workflows` (WIP, poussée, pas de PR)

Fait : V20 (`version_workflow`, étapes versionnées, `fn_modifier_workflow`, `demande.workflow_version` figée au dépôt), API `/api/v1/workflows` (liste, détail, modification), `WorkflowsTest` vert, erreurs jOOQ de routine traduites par le gestionnaire du socle, tables des workflows dans le journal d'audit.

Reste à faire :
1. Écrans `/workflows`, `/workflows/[code]`, `/workflows/[code]/modifier` (étapes ordonnées : ajouter, retirer, monter, descendre), entrée de navigation « Workflows » (administration), traductions FR/EN, tests Vitest.
2. `pnpm api:client` après `OpenApiTest`, `./mvnw -pl api verify`, contre-épreuve, docs/01 §7 (V20) et journal docs/02 §6, puis PR empilée sur #82.
3. US-02.6 « Paramètres des délais » (`parametre_delai`) et US-02.7 « Canaux des notifications » (`notification_evenement`) : écrans d'administration, immédiats et tracés (ajouter les stories au backlog docs/03).
4. E2E Playwright du parcours catalogue (proposition → approbation → catalogue public).
5. Compte rendu de fin de sprint.

## À signaler au porteur dans le compte rendu

- **Écart au cahier des charges** (sa réponse 2) : le cahier (diapo 14) fait approuver « catalogue, pièces, tarifs et délais » par le Ministère ; **toute la fiche** passe donc par l'approbation du Ministre, pas seulement tarifs et signataire. Point 23 ajouté à docs/01 §3.
- Réponses 1, 3, 4 conformes (cahier muet) et appliquées ; la fiche (délai, avis requis, signataire) est figée au dépôt.

## Rappels techniques

- Changer de branche : `./mvnw -pl api clean …` ; `pnpm db:codegen` avec l'infra arrêtée (`pnpm infra:down` a été fait : relancer `pnpm infra:up`).
- Tests catalogue : documents KIM, CCC, AEX et workflow EXPORT réservés à ces tests (restaurés en fin de classe).
