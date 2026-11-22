# Reprise — correctifs d'ergonomie (22/11/2026)

## Où on en est

- Tour de **correctifs d'ergonomie et de cohérence** (remarques A1 à A10 et revue B), lancé après le sprint J5 et avant « J3 suite ».
- Méthode : une PR par correctif, empilées.
- Branche en cours : **`tech/passer-dossier`** (correctif 7/8). PR en brouillon, empilée sur `tech/tableaux-de-bord` (#65).
- Commit WIP `fc7216d` : l'API, la migration V11 et le front sont écrits.
  - Les **tests API passent** : `PassageTest`, `IndicateursTest`, `MigrationsTest`, `MesDemandesTest`, `OpenApiTest`.
  - Le **typecheck du front échoue dans les tests**, à cause de fixtures incomplètes (voir « Reste à faire »).

| PR | Branche | Correctif | État |
|---|---|---|---|
| #60 | `tech/regle-a-traiter` | 1/8 Règle « À traiter » unique (A3) | CI verte |
| #61 | `tech/libelles-statut-profil` | 2/8 Libellés par profil, un badge (A2) | CI verte |
| #62 | `tech/dates-attente` | 3/8 Date de dépôt et attente à l'étape (A4) | CI verte |
| #63 | `tech/listes-par-profil` | 4/8 Listes par profil, actions directes (A6, A9) | CI verte |
| #64 | `tech/formulaire-etapes` | 5/8 Formulaire en 3 étapes (A7) | CI à vérifier |
| #65 | `tech/tableaux-de-bord` | 6/8 Tableaux de bord par profil (A5, A8, A10, cartes datées et cliquables) | CI à vérifier |
| brouillon | `tech/passer-dossier` | 7/8 « Passer ce dossier » (A1) | WIP |

## Fait

- **A2, A3, A4, A6, A7, A9** : terminés (PR #60 à #64).
- **A5, A8, A10** et la règle « chaque carte a sa période et ouvre sa liste » : terminés (#65).
  - API : `GET /demandes/indicateurs` ; filtres `enRetard`, `traiteParMoi`, `periode`.
  - Règle ajoutée à `CLAUDE.md`.
- **A1** (#7, WIP) :
  - migration `V11__passage_dossier.sql` : table en ajout seul, RLS réservée aux agents, journal d'audit ;
  - API : `POST` et `GET /demandes/{id}/passages`, champs `passages` et `bloque` du `DemandeDto`, filtre `bloque`, indicateur `bloques` ;
  - front : dialogue du motif dans la barre de série, historique sur la fiche, mention « Passé N fois » ou « Bloqué », alerte « dossiers bloqués » pour les décideurs, filtre de liste.
- Règles déjà dans `CLAUDE.md` : libellés par profil, « À traiter », dates et attente, tableaux de bord.

## Reste à faire, dans l'ordre

1. **Finir le correctif 7** (`tech/passer-dossier`) :
   - compléter les fixtures de test front : `passages: 0, bloque: false` dans les `DemandeDto` de `composants.test.tsx`, `formulaire-demande.test.tsx`, `listes.test.tsx` et `travail.test.tsx` ; `bloques: 0` dans les `IndicateursDto` ; `demandeId` dans le `BarreSerie` de `travail.test.tsx` ;
   - ajouter les tests : `PasserDossier` (motif obligatoire, puis ouverture du suivant), `HistoriquePassages` (alerte « Bloqué ») et l'alerte des dossiers bloqués du tableau de bord ;
   - lancer typecheck, lint, `vitest --maxWorkers=2` et la contre-épreuve ;
   - mettre à jour l'E2E si la barre de série y est utilisée ;
   - ajouter l'entrée « Correctifs ergonomie · 7 » au journal `docs/02` §6 ;
   - commit `feat(ergonomie): passer ce dossier avec motif tracé (correctifs ergonomie)`, puis sortir la PR du brouillon.
2. **Correctif 8 : revue générale B** de tous les écrans, pour chaque profil (investisseur, guichet, Directeur, Ministre) :
   - libellés, alignements, espacements, hiérarchie, états vides, messages, accessibilité, mobile ;
   - corriger ce qui est trouvé et le consigner au journal.
3. Vérifier la CI de #64, #65 et #7 ; corriger un échec sur sa branche.
4. Compte rendu final : rapport court, liste des points trouvés par Claude, ordre de fusion, vérifications dans le navigateur pour chaque profil.

## Décisions prises (porteur du projet)

- Délais comptés **depuis le dépôt, en jours calendaires** (heure de Conakry). Un dossier est en retard au-delà de `type_document.delai_jours`.
- Passages **tracés et visibles** de tous les agents. Au **3e passage à la même étape**, le dossier est « Bloqué » pour les décideurs ; le compteur repart à zéro quand le dossier change d'étape.
- Période des indicateurs : **mois en cours** par défaut, modifiable (30 derniers jours, année en cours). Les retards et les listes « à traiter » sont toujours calculés **à ce jour**.
- Formulaire : l'étape « Demandeur » est **supprimée** (3 étapes ; l'identité est rappelée au récapitulatif).
- Les autres choix techniques sont dans `docs/02` §6 (entrées « Correctifs ergonomie · 1 à 6 »).

Aucune question n'est en attente.

## Ordre de fusion

#60 → #61 → #62 → #63 → #64 → #65 → PR du correctif 7 → PR du correctif 8.

Les PR sont empilées : fusionner dans cet ordre. Après chaque fusion, GitHub rebascule la PR suivante sur `main`.

## Remarques du porteur du projet (texte de la demande, résumé fidèle)

Exigence générale : l'ergonomie est une **exigence**. Elle doit être sobre, institutionnelle, cohérente d'un écran à l'autre et adaptée à chaque profil. Les parcours doivent être **fluides** : chaque utilisateur doit savoir quoi faire et à quel moment.

- **A1** « Passer ce dossier » : tracer le nombre de dossiers passés et demander un motif court.
- **A2** Libellés de statut par profil : un seul badge par ligne, une table unique par profil, en FR et en EN, réutilisée partout.
- **A3** Une seule règle « À traiter », calculée par l'API et identique pour `/demandes` et `/a-traiter` ; un test par profil prouve que les nombres sont les mêmes.
- **A4** Dates : « Déposée le JJ/MM/AAAA » et, séparément, « En attente depuis N jour(s) » pour l'étape actuelle.
- **A5** Tableau de bord des décideurs (Ministre, Directeur), orienté pilotage :
  - les dossiers en retard sur les délais du catalogue, avec une alerte bien visible ;
  - le délai moyen, les encaissements et la répartition par document ;
  - une courte liste « à traiter » ;
  - aucune bordure sans signification sur une carte.
- **A6** Listes par profil :
  - un texte d'introduction adapté au profil ;
  - une colonne Demandeur pour les agents ;
  - les mêmes colonnes de base que `/a-traiter` ;
  - « Plus de filtres » intégré à la rangée de pastilles.
- **A7** Formulaire :
  - supprimer l'étape Demandeur ;
  - un vrai indicateur de progression (coche, ligne de liaison, une étape non atteinte n'a pas l'air cliquable) ;
  - moins d'espace vide ;
  - un lien pour revenir ou abandonner le brouillon.
- **A8** Tableau de bord investisseur : un bloc « Ce que je dois faire » en tête, avec des boutons directs ; des totaux qui s'additionnent (les rejetées manquaient).
- **A9** Mes demandes (investisseur) :
  - un brouillon s'affiche « Brouillon », avec Reprendre et Supprimer ;
  - « Télécharger » donne directement le PDF signé d'une demande délivrée ;
  - les mêmes nombres partout.
- **A10** Tableau de bord du guichet : des indicateurs de rôle (compléments attendus, frais attendus, traités aujourd'hui, en retard) à la place de « Documents délivrés » ; une grille équilibrée.
- **Règle générale** : chaque carte de tableau de bord affiche sa période et ouvre la liste filtrée correspondante.
- **B** : Claude fait lui-même la revue de tous les écrans pour chaque profil (investisseur, guichet, Directeur, Ministre) :
  - points à vérifier : libellés, alignements, espacements, hiérarchie, cohérence, états vides, messages, accessibilité, mobile ;
  - il corrige tout ce qu'il trouve et tranche selon les règles de l'art.
- **Livrable final attendu** :
  - un compte rendu court ;
  - la liste de ce que Claude a trouvé par lui-même ;
  - l'ordre de fusion ;
  - les vérifications dans le navigateur, profil par profil.

Remarques plus anciennes (sprints J4 et J5), toujours valables :

- Ouverture d'un fichier : on choisit d'abord de **voir** (visualiseur) ou de **télécharger**, et ce partout.
- L'export de dossiers doit être compact et professionnel.
- Ministre : ses dossiers doivent être **groupés** pour faciliter le traitement. Le traitement se fait **en série** : on ouvre un dossier, on agit, on enchaîne sur le suivant.
- La page de signature doit être pratique.

## Préférences et règles de travail apprises en cours de route

- Le porteur veut une **ergonomie soignée dès la première version**, sans avoir à la rappeler : appliquer le socle de `CLAUDE.md` sur chaque écran.
- Il teste lui-même dans le navigateur : toujours lui fournir le tableau des vérifications (adresse · compte · action · résultat attendu).
- Il fusionne lui-même les PR dans l'ordre, puis vérifie. Ne jamais attendre une fusion pour enchaîner.
- **Une PR par correctif**, empilée sur la précédente, avec le titre « (correctifs ergonomie n/8) ». La description contient les critères et les preuves (le test qui prouve chaque point) et la contre-épreuve. Ajouter une entrée « Correctifs ergonomie · n » au journal `docs/02` §6.
- Commits : Conventional Commits en français, avec la ligne `Co-Authored-By` ; les corps de PR se terminent par la ligne « Generated with Claude Code ».
- **Jamais** lire `.env` ni contourner un refus de permission (un script qui lisait `DEMO_PASSWORD` a été refusé, à juste titre).
- Le poste est sous Windows :
  - lancer Vitest avec `--maxWorkers=2` (sinon « spawn UNKNOWN ») ;
  - lancer les tests Maven par petits lots (`-Dtest=… -Djacoco.skip=true`), à cause de la mémoire ;
  - les heredocs Bash contenant des apostrophes échouent : écrire les scripts d'édition Python dans le scratchpad, avec `newline='\n'`.
- Après une modification de l'API :
  - lancer `OpenApiTest`, qui exporte `packages/api-client/openapi.json`, puis `pnpm api:client` ;
  - le jOOQ est régénéré par `mvnw compile` quand une migration change ;
  - `ModularityTest` doit rester vert.
- `pnpm dev` tourne peut-être en arrière-plan. L'API ne recharge pas le Java à chaud : la redémarrer pour tester dans le navigateur.

## Reprendre

```bash
git fetch && git checkout tech/passer-dossier
cd apps/app && pnpm exec tsc --noEmit   # liste des fixtures à compléter
```

Puis dans Claude Code : « Reprends les correctifs d'ergonomie à partir de `docs/08-reprise.md`. »
