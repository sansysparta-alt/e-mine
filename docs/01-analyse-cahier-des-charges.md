# e-mine · MINE-GUINÉE 360° — Analyse du cahier des charges

> Date : 20/11/2026 · Sources (dossier `_client/`, exclu de Git) : `e-mine_Application_de_gestion.pptx` (122 diapositives), `e-mine_web.pptx` (51 diapositives), `e-mine_schema.sql` (PostgreSQL 16, 1 238 lignes), `e-mine_requetes_metier.sql` (10 requêtes).

## 1. Le projet en une phrase

Une plateforme du Ministère des Mines et de la Géologie de Guinée en **deux faces sur une seule base de données** :

| Face | Public | Contenu |
|---|---|---|
| **e-mine web** (site public) | Investisseurs, citoyens, ONG, demandeurs | 9 menus : Accueil, Cadastre, Investir, e-Services, Appels d'offres, Actualités, Transparence, Academy, Contact + espace connecté |
| **Application de gestion** | 16 profils (agents, directions, Ministre, opérateurs, PME…) | 13 modules, dont le module pivot **E-DEMANDES** |

Le cœur : **demander en ligne l'un des 15 documents du Ministère, payer (TrésorPay ou virement), suivre, recevoir un PDF signé (HSM) avec QR vérifiable publiquement**, le cadastre étant tenu dans **ArcGIS Enterprise**.

## 2. Périmètre fonctionnel

### 2.1 Les 13 modules

| # | Module | Rôle | Couvert par le schéma SQL ? |
|---|---|---|---|
| 01 | TITRA-LINK | Titres, octroi, registre | ✅ `titre`, `titre_substance` |
| 02 | GEO-DATA | Couches ArcGIS, échantillons, campagnes | ⚠️ partiel (`controle_spatial`, réf. `arcgis_globalid`) |
| 03 | FISCA-MINE | Redevances, recouvrement, rapprochement | ⚠️ paiements e-Services oui ; déclarations de volumes / redevances non |
| 04 | LOGI-TRACK | Production, exportations | ❌ |
| 05 | INSPECT | Inspections, non-conformités, mode hors ligne | ❌ |
| 06 | ENVIRO-RSE | Plaintes, FODEL, RSE | ❌ |
| 07 | IMPACT | Contenu local, PME, appels d'offres | ❌ |
| 08 | GUICHET UNIQUE | Réception, recevabilité | ✅ via `demande`, `piece_jointe` |
| 09 | TUTELLE | SOGUIPAMI, ANAIM, BNE | ❌ |
| 10 | BRIGADE | Signalements, saisies, révocation | ⚠️ révocation seulement (`document_delivre.statut`) |
| 11 | DECISION | Validation, signature | ✅ `avis_decision`, `document_delivre` |
| 12 | ADMIN & ACADEMY | Utilisateurs, workflows, catalogue, site public | ⚠️ admin oui ; Academy et CMS non |
| 13 | **E-DEMANDES** | Demande, paiement, renouvellement, délivrance | ✅ **complet** |

Transverse (✅ dans le schéma) : `profil`/`permission`, `notification`, `message`, `annonce`, `journal_audit` (partitionné, non modifiable), compteurs de numérotation, transitions de statut contrôlées par trigger, RLS sur `demande` et `paiement`.

**Constat : le schéma fourni couvre très bien E-DEMANDES et le socle transverse, mais ~7 modules sur 13 n'ont aucune table.** Ce n'est pas un défaut : c'est le bon point de départ pour un MVP.

### 2.2 E-DEMANDES — le cœur, très bien spécifié

- **Catalogue de 15 documents** en 5 familles / 5 workflows paramétrables (titres miniers ; artisanal & comptoirs ; export & expertise ; attestations ; contenu local), délais 2 à 60 j, tarifs versionnés validés par le Ministre.
- **Cycle de vie** : brouillon → déposée → recevable → en instruction → avis rendu → décision → droits payés → délivrée → archivée (+ complément requis, rejetée). Transitions imposées par `transition_statut` + trigger.
- **Deux paiements par dossier** : frais de dossier (condition de recevabilité), droits (condition de délivrance). Référence `PAY-AAAA-NNNNNN`, reçu `REC-AAAA-NNNNNN` avec QR.
- **TrésorPay** : initiation → redirection → notification signée HMAC-SHA256 → confirmation serveur à serveur, idempotence, polling 30 s × 15 min, 10 statuts de paiement, 3 essais/jour.
- **Virement** : coordonnées + justificatif (5 Mo), import relevé CSV/MT940, rapprochement automatique avec 7 cas (conforme, inférieur, supérieur, inconnu, doublon, tardif, retour bancaire).
- **Contrôles spatiaux** : géométrie valide, superficie max par type, non-chevauchement (tolérance 0,01 ha), zones réservées → codes GEO-OK / GEO-001…005, 5 s max, file de reprise 1/5/15 min.
- **Renouvellement** : alertes J-180 / J-90 / J-30, dossier pré-rempli, suspension automatique à l'échéance.
- **Délivrance** : signature HSM, QR, registre, révocation → « NON VALIDE » immédiat.
- **API** documentée (9 endpoints paiement + endpoints publics titres/vérification), erreurs `{code, message, details}`, catalogue d'erreurs AUTH/PERM/VAL/DEM/PAY/GEO/DOC/SYS.

### 2.3 Exigences non fonctionnelles (chiffrées)

| Domaine | Exigence |
|---|---|
| Disponibilité | 99,5 % / mois |
| Performance | Pages ≤ 3 s (p95, 4G) ; API ≤ 800 ms ; carte ≤ 3 s ; Lighthouse ≥ 90 |
| Capacité | 2 000 utilisateurs simultanés ; 500 dépôts / jour |
| Sécurité | TLS 1.2+, AES-256 au repos, 2FA SMS (code 6 chiffres, 5 min, 3 essais), mdp 12 car., blocage 15 min après 5 échecs, OWASP Top 10, secrets en coffre |
| Sauvegarde | Quotidienne, 30 j, RPO 24 h, RTO 4 h, test trimestriel |
| Conservation | Actes et paiements 10 ans ; journaux techniques 12 mois |
| Accessibilité / i18n | WCAG AA, FR/EN sur toutes les pages, mobile-first 320→1920 px, hors-ligne partiel |
| Recette | Couverture ≥ 80 % sur règles paiement et spatiales ; E2E dépôt → paiement → décision → délivrance |

### 2.4 Intégrations externes

| Système | État annoncé | Impact développement |
|---|---|---|
| ArcGIS Enterprise (licence propre, SAML) | À déployer | **Bloquant** pour cadastre et non-chevauchement → prévoir un **simulateur** (PostGIS) en dev |
| TrésorPay | À raccorder ; format fixé par convention avec le Trésor | **Bloquant** → simulateur obligatoire (prévu par la spec) |
| Banques (relevés CSV/MT940) | À raccorder | Parseur + jeux de test |
| HSM (signature) | Non précisé | Signature logicielle en dev, interface remplaçable |
| SMS / e-mail | À raccorder | Passerelle abstraite, Mailpit/console en dev |
| Alphorm (SSO Academy) | À raccorder | Hors MVP |

## 3. Incohérences et points à clarifier avec le Ministère

Classées par impact sur le développement.

### 🔴 Bloquants pour la modélisation

1. **Les profils ne correspondent pas entre le site et l'application.**
   Site (diapo 38) : 15 profils — Ministre, SG, Dir. Cadastre, Dir. Titres, Instructeur, Agent cadastre, **Comptable, Juriste**, Demandeur, Titulaire, Investisseur, Société civile, Webmaster, Administrateur, **Auditeur**.
   Application (diapo 11) : 16 profils — Admin technique, Guichet unique, Agent DNM, Inspecteur, Géologue, Resp. fiscal, Directeur, Ministre/SG, Investisseur, Société minière, Communauté locale, PME, Tutelle, Brigade, Apprenant, Webmaster.
   La diapo « Cohérence » affirme pourtant qu'ils sont identiques. → **Retenir la liste de l'application (16)**, qui est celle du schéma et de la matrice des droits ; le rôle `emine_auditeur` du SQL reste à rattacher.

2. **6 codes documents sur 15 ont un libellé différent** entre site (diapo 19) et application (diapo 14) :

   | Code | Site web | Application (à retenir) |
   |---|---|---|
   | COR | Correction d'un titre | Carte d'orpailleur |
   | CEX | Certificat d'exportation | Certificat d'expertise (BNE) |
   | KIM | Certificat Kimberley | Certificat de conformité export (diamant) |
   | EXC | Attestation d'exclusivité | Extrait de cadastre / non-chevauchement |
   | CCC | Certificat de conformité | Copie certifiée conforme de titre |
   | ARF | Attestation de régularité fiscale | Attestation de situation des redevances |

3. **Format du numéro de demande `DEM-AAAA-NNN` (3 chiffres, « extensible à 4 »)** incompatible avec 500 dépôts/jour (~180 000/an). → Proposer `DEM-AAAA-NNNNNN` comme les paiements.

### 🟠 Règles métier contradictoires

4. **Signataire Ministre** : le catalogue (diapo 14) fait signer PRC, PRE, PEX, CES par le Ministre ; la diapo 33 dit « seuls PEX et cessions remontent au Ministre ».
5. **Ordre contrôle / paiement** : le site place « Contrôle de recevabilité » *avant* le paiement des frais ; l'application fait des frais payés la *condition* de recevabilité (et le schéma suit l'application).
6. **Nombre de statuts** : « 9 statuts » partout, le schéma en a 11 (`complement_requis`, `rejetee` en plus), la frise de suivi du site en montre 6. → Clarifier ce qu'on affiche à l'usager (6 étapes) vs les statuts techniques (11).
7. **Formulaire de demande** : 4 sections (diapo 20) vs 6 sections (diapo 49).
8. **Devise** : « montants entiers en GNF, devise fixe » pour les paiements, mais les redevances FISCA-MINE sont en **USD** (diapo 47). → Redevances = multi-devise, hors E-DEMANDES. **Retenu** : GNF entiers pour E-DEMANDES ; redevances multi-devises (USD/GNF) traitées au lot 5 (E13).
9. **Statut titre** : le site affiche « Valide », le schéma utilise `actif`.
10. **2FA** : « double authentification SMS » pour tous (site) vs « des agents » (application).
11. **Academy** : réservée aux cadres du Ministère (profil Apprenant) ou ouverte aux titulaires et jeunes talents (site) ?

### 🟡 Mineurs

12. Domaine : `mines.gov.gn` (site) vs `mines-guinee360.gov.gn` (application).
13. Numérotations hétérogènes dans les maquettes : `DOS-2026-001`, `DEM-2026-046`, `VAL-2026-012`.
14. Taille des pièces : 10 Mo (pièces), 5 Mo (justificatifs et CSV de périmètre) — à garder tel quel mais documenter.
15. Chiffres affichés (1 245 titres, 450 Mds GNF, 38 500 emplois) : données d'exemple à remplacer par des requêtes.

### 🔐 Sécurité du schéma fourni (à remonter au Ministère)

16. **Les vues du schéma contournent la RLS.** `v_demande_suivi`, `v_paiement_a_verifier`, `v_encaissement_mensuel` et `v_demande_par_statut` appartiennent à `emine_owner` et sont créées sans `security_invoker` : PostgreSQL y applique donc les droits du propriétaire, pas ceux de l'appelant. Conséquence : le rôle applicatif `emine_app` voit par ces vues **les demandes et paiements de tous les demandeurs**, alors que la RLS les masque sur les tables. Constaté sur `v_demande_suivi` par `RlsTest.lesVuesAppliquentLaRlsDeLAppelant` (sprint J1, US-01.5) ; les trois autres vues ont la même définition de droits. → **Corrigé dans notre copie** par la migration `V4__vues_rls_appelant.sql` (`security_invoker = true`, PostgreSQL 15+). **À signaler au Ministère** : toute autre installation du schéma d'origine (`_client/`) porte la même faille ; toute nouvelle vue sur `demande` ou `paiement` doit être créée en `security_invoker`.

### Décisions retenues (provisoires, à valider par le Ministère)

| # | Décision | Conséquence |
|---|---|---|
| D1 | La base actuelle est conservée pour le socle et E-DEMANDES ; les tables des autres modules sont créées lot par lot | Le schéma fourni devient la migration initiale |
| D2 | Les 16 profils et les libellés de documents de l'**application** font foi | Règle les points 1 et 2 |
| D3 | Numéro de demande au format **DEM-AAAA-NNNNNN** | Trigger `fn_demande_avant` : `fn_next_numero('DEM', 6)` (la contrainte `^DEM-[0-9]{4}-[0-9]{3,}$` l'accepte déjà) |
| D4 | Les documents signés par le Ministre sont **paramétrables dans le catalogue** | Règle le point 4 : signataire porté par le type de document / l'étape du workflow |
| D5 | ArcGIS, TrésorPay, HSM et SMS sont **simulés** au début, derrière des interfaces remplaçables | Voir [02-stack-technique.md](02-stack-technique.md) |

Restent ouverts : points 5 à 15. Point 16 : corrigé de notre côté (V4), à porter à la connaissance du Ministère. Décisions D6 à D10 (stack, forge, Keycloak, hébergement, méthode) : voir [02-stack-technique.md §5](02-stack-technique.md).

## 4. Risques

| Risque | Probabilité | Parade |
|---|---|---|
| Taille du périmètre (13 modules × 16 profils × 2 faces) | Certaine | Livrer par **tranches verticales**, E-DEMANDES d'abord |
| ArcGIS non disponible au démarrage | Élevée | Port `CadastreGateway` + implémentation PostGIS en dev/test |
| Convention TrésorPay non signée | Élevée | Simulateur TrésorPay (HMAC, notifications, échecs) dès le sprint 1 |
| HSM non fourni | Moyenne | Port `SignatureService`, signature logicielle PAdES en dev |
| Données métier contradictoires (§3) | Certaine | Atelier de validation avant de figer le catalogue |
| Connectivité terrain (inspecteurs, mobile 3G) | Moyenne | PWA + hors-ligne limité aux écrans prévus |
| **Licence jOOQ** : l'édition open source (gratuite) ne prend officiellement en charge que la dernière version de PostgreSQL ; avec PostgreSQL 16 (imposé), jOOQ émet un avertissement de dialecte | Moyenne | **V1 : édition gratuite acceptée** par le porteur du projet (20/11/2026), fonctionnement vérifié par les tests Testcontainers. **Décision à prendre avant la production** : licence commerciale jOOQ (dépense) ou montée de version de PostgreSQL (à faire valider par le Ministère, le cahier des charges impose la 16) |
| Faille RLS des vues du schéma fourni (§3, point 16) | Certaine (constatée) | Corrigée par `V4` ; `RlsTest` empêche toute régression ; vues futures en `security_invoker` ; signalement au Ministère |

## 5. Recommandation : découpage en lots

| Lot | Contenu | Valeur |
|---|---|---|
| **Lot 0 — Socle** | Monorepo, Docker (PostgreSQL 16 + schéma fourni), auth (JWT + 2FA SMS simulé), profils/permissions, journal d'audit, i18n FR/EN, design system « Bleu Souverain », CI | Fondations |
| **Lot 1 — E-DEMANDES MVP** | Catalogue, dépôt + pièces, workflow & transitions, paiement TrésorPay **simulé** + virement + rapprochement, avis/décision, délivrance PDF + QR, vérification publique `/verifier/{code}`, notifications | **Le parcours complet de bout en bout** |
| Lot 2 — Cadastre | Passerelle ArcGIS (simulateur PostGIS → vrai ArcGIS), contrôles GEO-001…005, carte publique, fiche titre, open data | Transparence |
| Lot 3 — Renouvellement & site public | Alertes J-180/90/30, suspension auto, menus publics (Accueil, Transparence, Actualités, Contact), CMS Webmaster | Site institutionnel |
| Lot 4 — Espaces métier | 16 tableaux de bord, messagerie, annonces, rapports hiérarchiques | Pilotage |
| Lot 5+ — Modules restants | FISCA-MINE (redevances), INSPECT (hors ligne), IMPACT/AO, ENVIRO-RSE, BRIGADE, TUTELLE, LOGI-TRACK, Academy | Couverture complète |

Le schéma SQL fourni est directement exploitable pour les lots 0 et 1 ; les modules des lots 5+ demanderont de nouvelles tables.

## 6. Choix techniques à arrêter

Recherche faite dans les deux présentations : **aucun langage ni framework de développement n'est cité** (ni React, Angular, Vue, Java/Spring, Node, Python, PHP, .NET…). Ce qui est imposé concerne les données, les interfaces et les intégrations :

| Couche | Imposé par le cahier des charges | Laissé libre |
|---|---|---|
| Base de données | **PostgreSQL 16** (schéma fourni : citext, pg_trgm, pgcrypto, RLS, partitions) | — |
| SIG | **ArcGIS Enterprise** Standard (Portal, Server, Data Store, Web Adaptor), géodatabase sur PostgreSQL, WGS 84 / UTM 28N-29N, **API JavaScript ArcGIS** côté carte | Extension PostGIS en dev (non prévue, à proposer) |
| API | **REST JSON**, documentée **OpenAPI 3**, dates ISO 8601 UTC, erreurs `{code, message, details}` | Langage et framework back-end |
| Authentification | **JWT** (usagers), **SAML 2.0** vers ArcGIS, jeton ArcGIS 60 min jamais exposé au navigateur, 2FA SMS | Fournisseur d'identité (maison ou Keycloak…) |
| Paiement | TrésorPay avec **HMAC-SHA256**, relevés **CSV / MT940** | Implémentation du connecteur |
| Cartographie publique | **WMS 1.3, WFS 2.0, GeoJSON** | — |
| Signature | **HSM** + horodatage qualifié, QR, **pas de blockchain** | Format de signature PDF (PAdES conseillé) |
| Front | Responsive 320→1920 px, WCAG AA, Lighthouse ≥ 90, FR/EN, hors-ligne partiel (→ PWA), polices Cambria / Calibri | Framework front-end |
| Environnements | dev, recette, pré-production, production ; simulateur TrésorPay en dev | Hébergement, conteneurs, CI/CD |

→ Le choix du langage back-end, du framework front-end et de l'outillage (Docker, CI) est **à proposer par l'équipe** et à faire valider ; il sera traité après le product backlog.

## 7. Adaptations du schéma fourni (à signaler au Ministère)

Le schéma d'origine (`_client/`) n'est jamais modifié : chaque adaptation est une migration Flyway de notre copie, prouvée par un test.

| Migration | Adaptation | Raison | Test |
|---|---|---|---|
| V2 | Numéro de demande sur 6 chiffres (`DEM-AAAA-NNNNNN`) | Décision D3 | `MigrationsTest` |
| V3 | `utilisateur.keycloak_id` ; mot de passe géré par Keycloak | Décision D8 | `MigrationsTest` |
| V4 | Vues sur `demande`/`paiement` en `security_invoker` | Faille : ces vues contournaient la RLS (§3, point 16) | `RlsTest` |
| V5 | Numéro de demande attribué au dépôt, plus à la création du brouillon | Pas de trou dans la numérotation officielle | `MigrationsTest`, `DepotTest` |
| V6 | RLS sur `piece_jointe` | Faille : les pièces d'un usager étaient lisibles par un autre | `RlsTest` |
| V7 | `demande.declaration_honneur_le` | La déclaration sur l'honneur du dépôt n'était pas conservée | `DepotTest` |
| V8 | Le paiement des frais ne rend plus la demande recevable ; la recevabilité est déclarée par l'agent du guichet, et refusée par la base tant que les frais ne sont pas payés (DEM-006) | La recevabilité exige l'examen des pièces : le paiement est une condition, pas un déclencheur | `RecevabiliteTest` |
| V9 | Transitions recevable → décision et recevable → rejetée (sans avis technique obligatoire en V1) ; `type_document.signataire_profil_id` : profil qui décide et signe chaque document | Le signataire (D4) devient une donnée exploitable ; démonstration sans avis technique | `DecisionTest` |
| V10 | Numéro du document délivré `<CODE>-AAAA-NNNN` attribué par la base (trigger) et contrôlé | Le schéma fourni rend le numéro obligatoire sans le calculer | `MigrationsTest` |
