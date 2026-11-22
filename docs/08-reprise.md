# Reprise — sprint « Notifications et suivi » (23/11/2026)

## Où on en est

Sprint terminé côté code : 4 PR empilées, à fusionner dans l'ordre. Prochains sprints décidés par le porteur : **Catalogue administrable**, puis **Paiement complet**.

| PR | Branche | Story | Base |
|---|---|---|---|
| #76 | `us-07.1-outbox-notifications` | US-07.1 Envoi fiable SMS / e-mail (outbox, db-scheduler) | `main` |
| #77 | `us-07.2-evenements-notifications` | US-07.2 Notifications à chaque étape, canaux paramétrables, centre de notifications | #76 |
| #78 | `us-05.6-respect-des-delais` | US-05.6 Délais paramétrables, part délivrée dans le délai, file par échéance | #77 |
| #79 | `us-07.4-e2e-notifications` | US-07.4 E2E : notifications vérifiées dans Mailpit et l'application | #78 |

US-07.3 : la frise existait déjà ; l'affichage **hors ligne** est reporté au chantier PWA (raisons dans le compte rendu du sprint et `docs/02` §6).

## Décisions du porteur (22/11/2026)

- Le cahier des charges fait foi ; ce qu'il ne fixe pas est appliqué selon les recommandations, **paramétrable**, et listé pour validation du Ministère (`docs/01` §3, points 17 à 22).
- Délais : jours calendaires, jour de départ inclus, départ au paiement des frais, pauses pendant un complément et pendant l'attente des droits, fin à la délivrance, rejets exclus du pourcentage et affichés à part, seuil 20 % / 2 jours (`parametre_delai`).
- Notifications : SMS + e-mail + application à chaque changement d'étape ; canaux paramétrables par événement (`notification_evenement`) car chaque SMS a un coût. Les 3 événements de paiement (à vérifier, remboursé, référence J-2) sont branchés sur les statuts et s'activeront avec « Paiement complet ».

## Pour le sprint « Catalogue administrable »

Écrans d'administration attendus par ce sprint :
- délais et tarifs du catalogue (existant au backlog E02) ;
- **paramètres des délais** (`parametre_delai`) ;
- **canaux des notifications** (`notification_evenement`).

## Rappels techniques

- Changer de branche : `./mvnw -pl api clean …` (sinon des migrations d'une autre branche restent dans `target/classes`).
- Démonstration des notifications : Mailpit sur http://localhost:8025 (e-mails et SMS simulés `<numéro>@sms.test`).
- Les tests désactivent le planificateur (`emine.planification.active=false`) et appellent les tâches eux-mêmes.
