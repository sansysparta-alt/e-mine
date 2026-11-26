# Contrat · Relevé bancaire CSV (US-04.8)

> Format **fictif**, défini pour la V1 de démonstration. Le format réel (CSV de la banque, ou MT940) sera fixé avec la banque du Trésor ; MT940 viendra plus tard (décision du porteur du projet, lot 5).

Le Responsable fiscal importe le relevé du compte qui reçoit les virements. e-mine rapproche chaque ligne d'un paiement par sa **référence** (`PAY-AAAA-NNNNNN`, lue dans le libellé) et son **montant**. Aucun paiement n'est validé à la main pendant l'import : tout ce qui ne correspond pas exactement part dans la file « À vérifier » du Responsable fiscal (US-04.9).

## Fichier

| Règle | Valeur |
|---|---|
| Encodage | UTF-8 (marque d'ordre des octets admise) |
| Séparateur | point-virgule `;` |
| Première ligne | en-tête obligatoire : `date_operation;libelle;montant_gnf;sens` (casse et accents ignorés) |
| Taille | 2 Mo au plus, 5 000 lignes au plus |
| Unicité | un même fichier (empreinte SHA-256) ne s'importe qu'une fois (`PAY-011`) |
| Erreur de format | rien n'est importé ; les lignes fautives sont citées (10 au plus, `VAL-001`) |

## Colonnes

| Colonne | Format | Exemple |
|---|---|---|
| `date_operation` | `JJ/MM/AAAA` ou `AAAA-MM-JJ` (date de valeur, heure de Conakry) | `26/11/2026` |
| `libelle` | texte libre de la banque ; doit contenir la référence pour être rapproché | `VIR SARL HORIZON PAY-2026-000123` |
| `montant_gnf` | entier positif en GNF, espaces de milliers admis, sans décimale | `5 000 000` |
| `sens` | `C` (crédit, argent reçu) ou `D` (débit, argent sorti) | `C` |

La référence est reconnue même écrite en minuscules ou sans tirets (`pay2026000123`).

## Résultat de chaque ligne

| Cas | Condition | Ligne | Paiement |
|---|---|---|---|
| **Conforme** | crédit, référence d'un virement en attente, montant exact | rapprochée | **payé** (reçu `REC-…`, date de l'opération) |
| **Inférieur** | crédit, virement en attente, montant plus petit | écart | à vérifier |
| **Supérieur** | crédit, virement en attente, montant plus grand | écart | à vérifier |
| **Référence inconnue** | aucune référence, ou référence d'aucun virement | inconnue | — |
| **Doublon** | crédit pour un paiement déjà payé (ou déjà à vérifier) | écart | inchangé |
| **Tardif** | crédit pour une référence expirée ou annulée | écart | inchangé |
| **Retour bancaire** | débit (`D`) : virement retourné, frais | écart | inchangé |

Chaque cas est couvert par `RapprochementTest`. Les écarts et les lignes inconnues sont traités par le Responsable fiscal (US-04.9) ; un remboursement éventuel suit US-04.10.

## Relevé d'exemple (démonstration)

`GET /api/v1/releves/exemple` (droit « encaisser ») produit un relevé conforme à ce contrat avec une ligne **conforme** par virement déclaré en attente, et une ligne inconnue. Il est réservé à la démonstration : `RELEVE_EXEMPLE_ACTIF=false` le désactive (à faire en recette et en production).

```csv
date_operation;libelle;montant_gnf;sens
26/11/2026;VIR SARL HORIZON MINING PAY-2026-000123;5 000 000;C
26/11/2026;VIR SANS REFERENCE;250 000;C
27/11/2026;RETOUR VIR PAY-2026-000099;200 000;D
```
