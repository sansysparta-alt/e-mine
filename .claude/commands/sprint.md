---
description: Lance un sprint e-mine en autonomie (ex. /sprint J1) selon la méthode de CLAUDE.md
argument-hint: J1 | J2 | … | S1 | S2 | …
---

Le porteur du projet donne le **feu vert pour le sprint $ARGUMENTS** : il couvre **toutes ses stories**. Suis la section « Méthode de travail (sprint autonome) » de `CLAUDE.md`.

## 1. Préparer (sans modifier de fichier)

1. Si `$ARGUMENTS` est vide, demande quel sprint lancer et arrête-toi.
2. Trouve les stories du sprint :
   - `J1` à `J5` : `docs/07-plan-v1-une-semaine.md` §4 (version allégée V1, qui fait foi) ;
   - `S1`, `S2`… : `docs/04-plan-des-sprints.md` §5 et `docs/03-product-backlog.md`.
3. Relis chaque story et ses critères d'acceptation, les décisions D1–D10, le journal `docs/02` §6 et le code existant. Vérifie l'état Git (branche courante, branches `us-*` existantes, PR ouvertes avec `gh pr list`).

## 2. Une seule interruption

Présente en un seul message :
- l'**objectif du sprint** et le résultat démontrable attendu ;
- l'**ordre proposé des stories**, les dépendances (PR empilées) et les pistes parallèles possibles en worktrees ;
- **toutes les questions qui relèvent du porteur du projet**, et seulement celles-là : écart au cahier des charges, périmètre, dépense, sécurité ou données, décision difficile à défaire. S'il n'y en a pas, dis-le.

Puis attends sa réponse. Ensuite, **n'attends plus de validation story par story**.

## 3. Boucle par story

Pour chaque story, dans l'ordre :
1. Crée la branche `us-xx.y-<sujet>` depuis `main` (ou depuis la branche de la story dont elle dépend).
2. Code selon les conventions de `CLAUDE.md` ; tranche seul les questions techniques courantes ; note les décisions structurantes dans `docs/02` §6.
3. Écris et lance les **tests et le lint des modules touchés** ; corrige jusqu'au vert.
4. **Relis ton diff** ; fais une **contre-épreuve** : une erreur volontaire doit faire échouer un test, puis annule-la.
5. Mets à jour OpenAPI et client généré, `.env.example`, traductions, documentation touchée.
6. Commite (Conventional Commits en français, ID de story), **pousse la branche**, **ouvre la PR** avec `gh pr create` (critères d'acceptation cochés, base indiquée si PR empilée).
7. Passe à la story suivante **sans attendre la CI ni la fusion** ; si la CI d'une PR échoue, corrige sur sa branche.

**Jamais seul** : fusionner dans `main`, pousser sur `main`, réécrire l'historique, toucher un secret, supprimer des données, engager une dépense, ajouter un service externe ou une dépendance hors stack. Si l'un de ces points devient nécessaire, mets la story de côté, continue les autres, et garde la question pour le compte rendu (ou interromps si c'est bloquant pour tout le sprint).

Pour le code difficile (signature, paiement, RLS, règles spatiales), signale qu'il vaut mieux passer sur Opus (`/model`). Préviens si la fenêtre d'utilisation de 5 h approche de sa fin, en indiquant où reprendre.

## 4. Fin de sprint

1. Lance les contrôles de fin de sprint (pipeline complet sur la dernière branche, E2E du sprint s'il existe).
2. Rends **un seul compte rendu** :
   - **Fait et prouvé** : tableau critère d'acceptation ↔ test qui le prouve ↔ PR ;
   - **Non fait** ou partiel, et pourquoi ;
   - **Décisions prises seul** (renvoi au journal `docs/02` §6) ;
   - **Questions en attente** du porteur du projet ;
   - **Vérifications dans le navigateur** : tableau adresse · compte de test · action · résultat attendu ;
   - **Ordre de fusion** des PR.
3. Termine par : « Fusionne les PR dans cet ordre, puis tape `/clear` avant le sprint suivant. »
