# Reprise — sprint « Catalogue administrable » (23/11/2026)

## Où on en est

Sprint terminé côté code : 5 PR, dont 4 empilées, à fusionner dans l'ordre. Prochain sprint décidé par le porteur : **Paiement complet**.

| PR | Branche | Story | Base |
|---|---|---|---|
| #80 | `docs/migrations-v11-v14` | docs/01 §7 : V11 à V14 | `main` |
| #81 | `us-02.2-fiche-document` | US-02.2 / 02.4 : fiches versionnées approuvées par le Ministre (V18), écrans « Catalogue et tarifs » | `main` |
| #82 | `us-02.3-tarifs-versionnes` | US-02.3 / 02.4 : tarifs versionnés (V19) | #81 |
| #83 | `us-02.5-workflows` | US-02.5 : workflows versionnés, affichage (V20) | #82 |
| #84 | `us-02.6-parametres-delais-notifications` | US-02.6 / 02.7 : règle des délais et canaux des notifications (V21) ; E2E du catalogue | #83 |

## Décisions du porteur (23/11/2026) et écart au cahier des charges

- Le cahier des charges fait foi. **Écart à la réponse 2** : le cahier (diapo 14) fait approuver « catalogue, pièces, tarifs et délais » par le Ministère ; **toute la fiche** passe donc par l'approbation du Ministre, pas seulement tarifs et signataire (docs/01 §3 point 23).
- Délai **figé au dépôt** ; par cohérence, avis requis et signataire aussi (fiche du dépôt, `demande.version_document_id`).
- Workflows : **affichage seulement**, modification immédiate et tracée ; fourchette de délais déduite des fiches approuvées.
- Désactivation d'un document : motif obligatoire, aucune suppression ; les demandes en cours vont à leur terme.
- Règle des délais et canaux : modification immédiate et tracée par l'administrateur ; au moins un canal par événement.

## Pistes proposées (hors sprint)

- Afficher sur la fiche d'une demande le **circuit de sa version de workflow** (la version est déjà figée au dépôt).
- Notifier le Ministre (application) quand une modification du catalogue l'attend : aujourd'hui, seul son tableau de bord l'annonce (les agents ne reçoivent pas de notification, US-07.2).

## Rappels techniques

- Changer de branche : `./mvnw -pl api clean …` (sinon des migrations d'une autre branche restent dans `target/classes`).
- `pnpm db:codegen` avec l'infra arrêtée ; l'infra a été arrêtée pendant le sprint : `pnpm infra:up` avant `pnpm dev`.
- Tests : documents KIM, CCC, AEX et workflow EXPORT réservés aux tests du catalogue (restaurés en fin de classe) ; l'E2E modifie puis remet CCC.
- Personas : administrateur `o.bah@e-mine.test`, Ministre `i.soumah@e-mine.test` (mot de passe `DEMO_PASSWORD` du `.env`).
