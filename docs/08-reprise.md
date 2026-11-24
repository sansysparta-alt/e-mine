# Reprise — lot « Porte d'entrée de l'application » (24/11/2026)

Lot terminé côté code, sur la branche `fix/porte-entree` (une PR vers `main`). Le porteur décide de la suite après le compte rendu.

## Après la fusion, sur le poste

- `pnpm infra:keycloak:reset` : réimporte le realm (mot de passe oublié activé, thème des e-mails, serveur d'envoi Mailpit, nom affiché). Les sessions en cours sont fermées.
- Redémarrer `pnpm dev` si des pages récentes répondent 404 (cache Turbopack après des changements de branche).

## Ce qui a été livré

- Page de connexion Keycloak à l'identité de l'État (écran partagé, bandeau sur mobile), libellés du cahier des charges, erreurs neutres, « Mot de passe oublié ? », mention de connexion sécurisée, lien d'aide, mention « données fictives », titre et icône d'onglet.
- Plus de page intermédiaire : « / » mène à la connexion (ou au tableau de bord).
- Menu sous l'avatar (Mon profil, Langue, Se déconnecter), page « Mon profil » (identité, langue, changement de mot de passe).
- Bilingue FR / EN (cookie `NEXT_LOCALE`, transmis à Keycloak).
- Messages « Vous avez été déconnecté », « Votre session a expiré » sur la page de connexion ; fenêtre de reconnexion dans l'application.
- E-mails Keycloak à la même identité.

## Écarts et points ouverts

- Double authentification SMS affichée par le cahier : pas encore en place, non annoncée (docs/01 §3 point 24).
- Emblème : visuel provisoire, à remplacer par le fichier officiel (`infra/keycloak/themes/emine/login/resources/img/embleme-provisoire.svg`).
- Lien d'aide : adresse fictive `assistance@e-mine.test` (à remplacer par le vrai canal d'assistance).
