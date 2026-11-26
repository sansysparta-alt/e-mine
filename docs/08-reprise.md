# Reprise — lot 5 V1 « paiement complet » (26/11/2026)

PR à fusionner **dans l'ordre**, chacune empilée sur la précédente :

1. **#99** — « Motif : » des propositions, texte sélectionnable dans le lecteur PDF (base `main`, indépendante)
2. **#100** — US-04.7 virement
3. **#101** — US-04.8 relevés bancaires et rapprochement
4. **#102** — US-04.9 écarts « À vérifier »
5. **#103** — US-04.10 remboursements et règle V1-4
6. **#104** — US-04.11 journal des encaissements et tableau de bord fiscal
7. **#105** — US-04.5 interrogation TrésorPay (empilée sur #104 pour un conflit résolu)
8. **#106** — cette documentation

## Après la fusion, sur le poste

- `pnpm install`, puis redémarrer l'API (`pnpm dev`) : migrations **V29 à V32**.
- `.env` : ajouter les variables nouvelles de `.env.example` (`VIREMENT_*`, `RELEVE_EXEMPLE_ACTIF=true` en démonstration).
- Redémarrer le simulateur TrésorPay (point d'accès d'interrogation).

## Non fait dans ce lot

- **Date d'effet propre des fiches du catalogue** (point ouvert du lot 4) : reportée. Plan proposé : `version_document.date_effet` ; approbation avec une date future → état « programmée » (un seul « à valider » ou « programmée » par fiche) ; application à la date par une fonction de la base appelée au dépôt et par une tâche planifiée ; une seule date d'effet pour la fiche et ses tarifs (`ChampDateEffet` commun) ; états affichés communs (`EtatVersion`).

## Points ouverts

- **Séparation des tâches** sur les remboursements : le même Responsable fiscal propose et valide (un seul compte fiscal de démonstration). À trancher : validation par un second profil (Directeur, ou second compte fiscal).
- **Format réel du relevé** et **convention TrésorPay** (interrogation comprise) à obtenir de la banque et du Trésor (questionnaire de mise en service, question 12).
- **`RELEVE_EXEMPLE_ACTIF=false`** en recette et production.
- Noms de DTO en double, antérieurs au lot (`ApprobationDto`, `MotifRefusDto`, `ParametresDelaiDto`) : formes identiques aujourd'hui, à renommer pour éviter une collision future dans l'OpenAPI.
- Restent du lot 4 : double authentification SMS (agents, à l'arrivée de l'opérateur SMS), emblème et canal d'assistance réels.
