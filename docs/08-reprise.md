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

## Reprise au 24/11/2026 — lot ergonomie (langue, gabarit, listes, profils)

PR ouvertes, à fusionner dans l'ordre : **#87** (porte d'entrée) → **#88** (langue complète) → **#89** (gabarit commun : action principale, compteurs, cloche « Voir toutes », fil d'Ariane, recherche Ctrl+K, aide, mentions légales, pied de page).

**Fait ensuite (PR « Listes et recherches », empilée sur #89)** : avancement « Étape N sur M », colonne Actions, statut après le numéro, bandeau, recherche (croix, suggestions, surlignage, aucun résultat). **Historique de la branche `fix/listes-recherches`** (empilée sur `fix/gabarit-commun`, commit WIP, pas encore de PR) :
- fait : V23 `fn_normaliser` + recherche tolérante de l'API (`DemandeRepository.conditionRecherche` : accents, ordre des mots, libellé EN, nom du demandeur et organisation, fautes sur les mots de 5 lettres ou plus via pg_trgm, numéros recherchés exactement) ; test ajouté dans `MesDemandesTest.laListeSeTrieEtSeCherche` (passé : 11 tests verts).
- reste à faire : ajouter à `DemandeDto` l'étape en cours (« Étape 3 sur 6 », calculée par `RegleSuivi`, une seule définition côté API) ; colonne Actions du demandeur (Continuer, Payer les frais/droits, Compléter, Télécharger) ; statut juste après le numéro ; bandeau « N demandes attendent votre action » (vue « à traiter ») ; `ChampRecherche` : croix pour vider, suggestions (recherches récentes + noms de documents, `datalist`), surlignage (`TexteSurligne`) dans les tableaux, écran « aucun résultat » utile ; appliquer aux autres listes (À traiter, catalogue, workflows, journal).
- ensuite : PR4 tableaux de bord et menus par profil (admin technique : comptes et accès, santé des services, journal des connexions ; investisseur : « Paiements & reçus », « Documents délivrés », qui demandent de nouveaux points d'API), puis le compte rendu complet du lot (ce que dit le cahier, ce qui a été fait, initiatives, vérifications navigateur).
- À rappeler au porteur : redémarrer `pnpm dev` (API comprise, pour appliquer V22/V23) et `pnpm infra:keycloak:reset`.
