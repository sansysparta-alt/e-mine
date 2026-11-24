# Reprise — lot « Porte d'entrée de l'application » (24/11/2026, interrompu : limite de session)

État : **analyse terminée, aucun code écrit.** Branche `fix/porte-entree` (partie de `main` après #86). Reprendre ici, sans refaire l'analyse.

## Demande du porteur (résumé)

1. Menu déroulant sous l'avatar (barre du haut) à la place du bouton « Se déconnecter » : en-tête nom + profil, « Mon profil », « Langue », séparateur, « Se déconnecter » (sans couleur d'alerte) ; clavier, Échap, clic extérieur.
2. Une seule porte d'entrée : supprimer la page intermédiaire « / » ; page Keycloak en écran partagé (panneau Bleu Souverain : République de Guinée, devise, Ministère des Mines et de la Géologie, e-mine, phrase, emplacement d'emblème provisoire ; formulaire à droite, œil du mot de passe aligné, « Mot de passe oublié ? », mention de connexion sécurisée, lien d'aide, mention « données fictives ») ; sur mobile, bandeau en haut.
3. Bilingue : vérifier le cahier ; garder et compléter s'il est exigé.
4. Finitions : message « Vous avez été déconnecté » sur la page de connexion ; session expirée : message clair et retour à la page d'origine ; erreurs de connexion en français sans dire si l'identifiant ou le mot de passe est faux ; e-mails Keycloak à la même identité ; titre et icône d'onglet officiels.
Comme d'habitude : branche, PR, tests, contre-épreuve, WCAG AA, liste de vérifications dans le navigateur. Le porteur décidera de la suite après le compte rendu.

## Ce que dit le cahier des charges (vérifié)

- **Bilingue exigé** : site, diapo 40 « Bilingue FR / EN : chaque page et chaque e-Service existent dans les deux langues » ; diapo 4 « Connexion FR / EN » ; diapo 6 « FR / EN sur chaque page ». Les e-Services (dépôt des demandes) sont dans l'application → **on garde l'anglais et on le complète** (aujourd'hui la langue est figée en français : `apps/app/src/i18n/request.ts`, aucun sélecteur).
- **Écran de connexion** (site, diapo 38) : titre « Se connecter », champs « Identifiant ou e-mail », « Mot de passe », « Code SMS à 6 chiffres », bouton **« Accéder à mon espace »** (le cahier l'emporte sur « Se connecter » proposé pour le bouton), mention « Double authentification par SMS · Session sécurisée · Journal des connexions ». La 2FA SMS (SPI Keycloak) n'est pas encore en place : **ne pas l'afficher** ; mention retenue « Connexion sécurisée · Journal des connexions » ; signaler l'écart (2FA à venir).
- **Session expirée** (application, états d'erreur) : « Fenêtre de reconnexion · Brouillon conservé · Se reconnecter » → garder la **fenêtre de reconnexion** dans l'application (le dialogue existe déjà dans `composants/session/surveillance-session.tsx`, mais `deconnecter()` quitte aussitôt la page vers `/?expiree=1` : l'afficher sans quitter la page, puis « Se reconnecter » → `/api/auth/login?retour=<page>&motif=expiree`).

## Plan technique arrêté

**Keycloak (thème `infra/keycloak/themes/emine`, Keycloak 26.8.2)**
- Surcharger seulement `login/template.ftl` (disposition écran partagé ; base copiée depuis le conteneur : garder `<head>`, scripts, macros, messages, sélecteur de langue Keycloak) ; **ne pas surcharger `login.ftl`** (formulaire de base : groupe mot de passe + œil, erreurs, accessibilité).
- `messages_fr/en.properties` : `loginAccountTitle=Se connecter`, `doLogIn=Accéder à mon espace`, `usernameOrEmail=Identifiant ou e-mail`, `doForgotPassword=Mot de passe oublié ?`, `invalidUserMessage` / `invalidPasswordMessage` / `invalidUsernameOrPasswordMessage` = « Identifiant ou mot de passe incorrect » (sans préciser lequel), messages de blocage, `loginTitle` (titre d'onglet).
- CSS : œil aligné (même hauteur, même bordure que le champ : `.pf-c-input-group`), panneau gauche, bandeau mobile, focus visible, contrastes AA.
- Message de motif (déconnecté, session expirée) : l'application ajoute `motif=` à l'URL d'autorisation (Keycloak ignore les paramètres inconnus ; l'URL reste dans la barre à l'affichage initial) ; message rendu caché par le gabarit, révélé par un petit `js/motif.js` (déclaré par `scripts=` dans `theme.properties`).
- Emblème : **visuel provisoire neutre** (SVG, pas d'armoiries officielles, règle « pas de logo officiel ») + fin liseré tricolore ; devise « Travail – Justice – Solidarité ».
- Icône d'onglet : SVG e-mine dans le gabarit (et `app/icon.svg` côté Next) ; `realm.displayName` explicite.
- Realm (`realm-emine.json`) : `resetPasswordAllowed: true`, `smtpServer` vers Mailpit (`mailpit:1025`, expéditeur fictif `.test`), `emailTheme: "emine"`. **Réimport nécessaire : `pnpm infra:keycloak:reset`.**
- Thème e-mail `emine/email` : `theme.properties` (`parent=base`, `locales=fr,en`), `html/template.ftl` à l'identité visuelle (bandeau Bleu Souverain, mentions, « message automatique », « données fictives »), `messages_fr/en` pour la réinitialisation du mot de passe.

**Application (`apps/app`)**
- `/` (page intermédiaire) → redirection : session → tableau de bord ; sinon → `/api/auth/login` (en relayant `motif`). Garder une page de secours **uniquement** si Keycloak est injoignable (`erreur=indisponible`).
- `api/auth/login` : accepter `motif` (deconnecte | expiree | erreur) et `ui_locales` (langue choisie) → ajoutés à l'URL Keycloak (`preparerConnexion` dans `serveur/oidc.ts`). Callback en erreur → `/api/auth/login?motif=erreur`.
- Déconnexion (`api/auth/logout`) → `/api/auth/login?motif=deconnecte`. Proxy (`proxy.ts`, échec de renouvellement) → `/api/auth/login?retour=<page>&motif=expiree` au lieu de `/?expiree=1`.
- **Menu utilisateur** dans le socle `packages/ui` (`MenuUtilisateur`) : bouton avatar (initiales, nom/profil dès `sm`, chevron), `aria-haspopup="menu"`, `aria-expanded`, `role="menu"`/`menuitem`, flèches, Échap (focus rendu au bouton), clic extérieur, Tab ferme ; « Mon profil » (lien), « Langue » (Français / English, `menuitemradio`), séparateur, « Se déconnecter » (formulaire POST existant `FormulaireDeconnexion`, sans couleur d'alerte). Branché dans `navigation-application.tsx` à la place de `actionUtilisateur` / bloc avatar de `gabarit.tsx`.
- **Langue** : cookie `NEXT_LOCALE` lu dans `i18n/request.ts` (fr par défaut, en accepté), action serveur pour le changer + `router.refresh()` ; `ui_locales` transmis à Keycloak ; `<html lang>` déjà dynamique. Vérifier la parité FR/EN (`messages.test.ts`).
- **Page « Mon profil »** (`/profil`, hors menu, titre dans la barre) : identité (nom, e-mail, profil, organisation) en lecture, langue, « Modifier mon mot de passe » → `/api/auth/login?action=mot-de-passe` → `kc_action=UPDATE_PASSWORD` (page Keycloak à l'identité e-mine), mention « pour modifier vos informations, contactez l'administrateur ».
- Titre d'onglet et icône (`app/icon.svg`, `metadata` cohérents avec la page de connexion).

**Tests et preuves** : Vitest (menu clavier/Échap/clic extérieur, langue, redirections `/`, login avec `motif`/`ui_locales`, logout), E2E à adapter (`connecter()` des specs : plus de page intermédiaire, bouton « Accéder à mon espace »), contre-épreuve, axe/WCAG AA, puis compte rendu avec la liste des vérifications dans le navigateur.

## Rappels

- Après une modification du realm : `pnpm infra:keycloak:reset` (le realm n'est importé qu'à la création).
- Thème Keycloak monté en lecture seule (`compose.yaml`) : recharger la page suffit (start-dev, cache des thèmes désactivé à vérifier).
- Base de départ des gabarits : extraire `org.keycloak.keycloak-themes-26.8.2.jar` depuis le conteneur (`/opt/keycloak/lib/lib/main/`).
