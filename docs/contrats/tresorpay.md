# Contrat d'échange e-mine ↔ TrésorPay (simulé, D5)

> Fixé au sprint J3 (21/11/2026) avant de séparer les pistes `v1-simulateur-tresorpay` (US-04.1) et `v1-paiement` (US-04.2 à 04.4). Le simulateur (`simulateurs/tresorpay`, port 8090) et l'adaptateur de l'API (`paiement.infrastructure`, port `PaiementGateway`) respectent ce contrat. Le vrai connecteur TrésorPay remplacera l'adaptateur selon la convention du Trésor (lot 5), sans toucher au reste du module.

## 1. Principes

- **Le serveur fixe montant et référence** : le navigateur ne transmet jamais de montant.
- **Seule la notification signée confirme un paiement.** Le retour du navigateur n'est qu'une invitation à relire le statut en base.
- Montants en **GNF entiers** (`long`), dates en **UTC** (ISO 8601 ou secondes Unix pour l'horodatage de signature).
- Secret partagé `TRESORPAY_HMAC_SECRET` (fictif en dev, dans `.env`, jamais versionné) ; identifiant marchand `TRESORPAY_MERCHANT_ID`.

## 2. Signature (les deux sens)

| En-tête | Contenu |
|---|---|
| `X-TresorPay-Marchand` | Identifiant marchand (`MARCHAND-TEST-0001` en dev) |
| `X-TresorPay-Horodatage` | Secondes Unix (UTC) au moment de l'envoi |
| `X-TresorPay-Signature` | `HMAC-SHA256(secret, horodatage + "." + corps brut)` en hexadécimal minuscule |

Le destinataire :
1. recalcule la signature sur le **corps brut reçu** (octets UTF-8, avant tout décodage JSON) et la compare en **temps constant** ;
2. refuse un horodatage écarté de plus de **5 minutes** de son horloge (anti-rejeu) ;
3. côté e-mine, refuse une deuxième notification portant le **même `transactionId`** sans la retraiter (idempotence + anti-rejeu, contrainte `UNIQUE (transaction_id, type_evenement)` de `paiement_evenement`).

## 3. Initiation d'une session de paiement (e-mine → TrésorPay)

`POST {TRESORPAY_BASE_URL}/api/v1/sessions` · `Content-Type: application/json` · en-têtes signés (§2)

```json
{
  "reference": "PAY-2026-000001",
  "montantGnf": 500000,
  "libelle": "Frais de dossier · DEM-2026-000001",
  "urlRetour": "http://localhost:3001/demandes/42/paiement/retour"
}
```

| Réponse | Sens |
|---|---|
| `201` `{ "sessionId": "…", "urlPaiement": "http://localhost:8090/payer/…", "expireLe": "2026-11-21T10:15:00Z" }` | Session ouverte pour **15 minutes** ; e-mine redirige le navigateur vers `urlPaiement` |
| `401` `{ "code": "SIGNATURE_INVALIDE" }` | Signature, marchand ou horodatage refusé |
| `400` `{ "code": "REQUETE_INVALIDE", "message": "…" }` | Champ manquant, montant ≤ 0, `urlRetour` non autorisée |
| `503` `{ "code": "INDISPONIBLE" }` | TrésorPay indisponible : e-mine affiche PAY-003 et propose le virement |

L'adresse de **notification** n'est pas transmise : elle est enregistrée chez TrésorPay pour le marchand (`TRESORPAY_URL_NOTIFICATION` côté simulateur). Les adresses de retour admises commencent par `TRESORPAY_URL_RETOUR_AUTORISEE` (pas de redirection ouverte).

## 4. Notification de résultat (TrésorPay → e-mine)

`POST {URL de notification du marchand}` = `http://localhost:8080/api/v1/paiements/notifications/tresorpay` · en-têtes signés (§2)

```json
{
  "transactionId": "TP-3f1c9a6e-…",
  "reference": "PAY-2026-000001",
  "statut": "PAYE",
  "montantGnf": 500000,
  "date": "2026-11-21T10:03:12Z"
}
```

`statut` : `PAYE`, `REFUSE` (refus de la banque), `ANNULE` (abandon par l'usager).

| Réponse d'e-mine | Cas |
|---|---|
| `200` `{ "recu": true, "issue": "TRAITEE" }` | Notification appliquée au paiement |
| `200` `{ "recu": true, "issue": "DEJA_TRAITEE" }` | Même `transactionId` déjà reçu : aucun retraitement |
| `200` `{ "recu": true, "issue": "IGNOREE" }` | Paiement déjà clos (annulé après un changement de moyen, expiré…) : notification journalisée, non appliquée, à examiner par le service fiscal |
| `401` (`PAY-004`) | Marchand, signature ou horodatage invalide : rien n'est modifié, l'événement est journalisé (`signature_valide = false`) et une alerte est écrite dans les journaux |
| `404` (`PAY-005`) | Référence inconnue : notification journalisée |

Effet côté e-mine (transitions de `transition_statut`, jamais contournées) :

| `statut` reçu | Paiement e-mine |
|---|---|
| `PAYE`, montant égal | `en_attente → paye` (reçu `REC-…` attribué par la base) |
| `PAYE`, montant différent | `en_attente → a_verifier` (contrôle par le responsable fiscal) |
| `REFUSE` | `en_attente → echoue` (nouvelle tentative possible) |
| `ANNULE` | `en_attente → annule` |

## 5. Interrogation d'une transaction (e-mine → TrésorPay, US-04.5)

Quand la notification n'est pas arrivée, e-mine interroge TrésorPay **toutes les 30 s**, à partir de 30 s après l'ouverture de la session et jusqu'à sa fin (15 min).

`GET {TRESORPAY_BASE_URL}/api/v1/transactions/{reference}` · en-têtes signés (§2) sur un **corps vide**

| Réponse | Sens |
|---|---|
| `200` `{ "reference", "statut": "EN_COURS" }` | Session ouverte, pas encore payée |
| `200` `{ "reference", "statut": "PAYE", "transactionId", "montantGnf", "date" }` | Payée |
| `200` `{ "reference", "statut": "REFUSE" \| "ANNULE", "transactionId", "date" }` | Refusée par la banque, ou abandonnée |
| `200` `{ "reference", "statut": "EXPIREE" }` | Session close sans paiement |
| `404` `{ "code": "INCONNUE" }` | Aucune session pour cette référence |
| `401`, `503` | Comme pour l'initiation |

Effet côté e-mine : la réponse s'applique comme une notification (§4), et chaque interrogation est journalisée (`paiement_evenement`, type `INTERROGATION`). **Après les 15 minutes**, le paiement devient :
- `paye`, ou `a_verifier` si le montant diffère, quand TrésorPay confirme le paiement ;
- `expire` quand la session s'est close sans paiement (`EN_COURS` ou `EXPIREE`) ;
- `a_verifier` sans réponse, ou si la référence est inconnue : le Responsable fiscal tranche.

La même tâche fait expirer les références dont la date limite est passée sans paiement : virement non déclaré au bout de 7 jours, session jamais ouverte.

## 6. Ordre des échanges

1. L'usager choisit TrésorPay ; e-mine crée le paiement (`initie`, référence `PAY-…` par la base), appelle `POST /api/v1/sessions`, passe le paiement à `en_attente` et redirige le navigateur.
2. Sur la page du simulateur, l'usager paie (ou choisit un scénario de test).
3. Le simulateur **envoie d'abord la notification signée**, puis redirige le navigateur vers `urlRetour?reference=PAY-…`.
4. La page de retour d'e-mine relit le statut en base (rafraîchissement automatique pendant quelques secondes s'il est encore `en_attente`).

## 7. Scénarios du simulateur (US-04.1)

Choisis sur la page de paiement (bloc « Scénarios de test ») ou par l'adresse `…/payer/{sessionId}?scenario=<code>` :

| Code | Effet |
|---|---|
| `succes` (défaut) | Notification `PAYE`, montant exact |
| `refus` | Notification `REFUSE` |
| `abandon` | Bouton « Annuler et revenir » : notification `ANNULE` |
| `signature-invalide` | Notification `PAYE` avec une signature fausse (e-mine doit répondre 401) |
| `double-notification` | La même notification `PAYE` envoyée deux fois (e-mine ne la traite qu'une fois) |
| `montant-divergent` | Notification `PAYE` avec un montant inférieur de 1 000 GNF |
| `sans-notification` | Paiement accepté mais notification perdue : retour du navigateur sans notification ; e-mine le confirme par interrogation (§5) |

Indisponibilité : interrupteur sur la page d'accueil du simulateur (`http://localhost:8090/`) ; toute initiation reçoit alors `503`.
