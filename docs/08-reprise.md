# Reprise — lot 4 (25/11/2026)

PR à fusionner dans l'ordre : **#95** (correctifs 4 à 10) → **#96** (US-02.8 gouvernance des workflows et des délais) → **#97** (US-02.9 saisie bilingue) → **#98** (documentation). Chacune est empilée sur la précédente.

## Après la fusion, sur le poste

- Redémarrer l'API (`pnpm dev`) : migrations V25 à V28.
- `pnpm db:seed` : titres officiels des personas (Directeur national, Ministre).
- `pnpm install` : nouvelle dépendance `pdfjs-dist` (lecteur PDF du socle).

## Ce qui a été livré

- Correctifs : journal d'audit lisible (nom de l'objet, jamais « true ») ; révocation avec autorité exacte et motif ; document révoqué « Voir » d'abord ; lecteur PDF pdf.js paginé ; exemples de recherche courts ; CEX et KIM désactivés ; codes des documents expliqués dans les workflows.
- Gouvernance (US-02.8) : workflows et règle des délais proposés, validés par le Ministre, appliqués à leur date d'effet ; règle des délais figée par demande.
- Saisie bilingue (US-02.9) : pièces, service instructeur, workflows en FR / EN ; textes initiaux traduits.
- Décisions V1 du porteur du projet : `docs/01` §3 ; périmètre V1 : `docs/07` §9.

## Points ouverts

- Réglage administrable de la règle de remboursement (décision V1-4) : à outiller avec les remboursements.
- Double authentification SMS : pas encore en place (docs/01 §3 point 24).
- Emblème officiel et canal d'assistance réels à fournir par le Ministère.
- Fiche du catalogue : date d'effet propre (aujourd'hui, seuls les tarifs en ont une) — proposée pour aligner les trois circuits.
