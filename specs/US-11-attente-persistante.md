# US-11 – Attendre 5 minutes de façon persistante
**Statut** : implémentée
**Priorité** : MVP
**Dépend de** : US-09 (suppression confirmée) ; prépare US-12 (republication)

## Contexte
Après la suppression d'une annonce, il faut attendre 5 minutes avant de la republier (README). Ce délai ne doit être ni remis à zéro ni sauté par un arrêt ou un plantage de l'outil. C'est un délai minimal : une annonce supprimée depuis plus longtemps se republie sans attendre.

## Objectif
En tant que membre, je veux que l'outil ne me propose la republication d'une annonce qu'au moins 5 minutes après sa suppression confirmée, même après un redémarrage, afin de ne pas republier juste après avoir supprimé.

## Comportement attendu
1. Une annonce dont la dernière suppression est confirmée (US-09) devient republiable à la date de preuve de cette suppression (`settledAt`) plus le délai minimal (`republication.minimum-delay`, 5 minutes).
2. Sans suppression, ou si la dernière est non confirmée ou non faite, l'annonce n'a aucune date de republication.
3. Cette date est calculée à chaque lecture depuis la suppression en base : rien n'est stocké en plus, rien n'est gardé en mémoire ; un redémarrage donne la même date.
4. L'aperçu de la sauvegarde (`listing-backup.html`) affiche, sous la suppression confirmée :
   - avant la date : « Republication possible dans 3 min 12 s (à 14:37) », temps restant mis à jour chaque seconde par la page, avec l'heure du navigateur (même machine que le serveur) ;
   - à la date ou après, sans recharger : « Republication possible ».
5. Rien n'est demandé à Vinted ; aucune republication n'est lancée (US-12).

## Contrat d'API
Contract first : `src/main/resources/static/openapi/vinted-republisher.yaml`.
- Le schéma `ListingDeletion` (champ `deletion` de `ListingBackupView`) gagne `republishableAt` (date-time) : présent seulement si `outcome` vaut `CONFIRMED`, égal à `settledAt` + délai minimal.
- Aucun nouveau chemin ; les réponses de `GET /api/listings/{listingId}/backup`, `deleteListing` et `recheckListingDeletion` portent ce champ.

## Critères d'acceptation
- [x] Une suppression confirmée donne une date de republication égale à sa date de preuve plus le délai minimal configuré.
- [x] Une suppression non confirmée ou non faite, ou l'absence de suppression, ne donne aucune date de republication.
- [x] Une suppression confirmée par une revérification compte depuis la date de cette revérification.
- [x] La date de republication est la même après un redémarrage de l'outil.
- [x] Le délai minimal vient de `republication.minimum-delay`, 5 minutes dans `application.yml` ; absent, nul, négatif ou illisible, l'outil refuse de démarrer.
- [x] L'aperçu affiche le temps restant tant que la date n'est pas atteinte, puis « Republication possible » sans recharger ; une annonce supprimée depuis plus longtemps affiche directement « Republication possible ».

## Tests d'acceptation
### AT-11-01 – Délai après une suppression confirmée
```gherkin
Étant donné une annonce dont la suppression a été prouvée à 14:32
Et un délai minimal de 5 minutes
Quand le membre ouvre l'aperçu de sa sauvegarde à 14:34
Alors la suppression indique « republiable à partir de 14:37 »
```
Test : `RepublicationDelayAcceptanceTest.confirmedDeletionIsRepublishableAfterMinimumDelay()`

### AT-11-02 – Délai déjà écoulé
```gherkin
Étant donné une annonce dont la suppression a été prouvée à 14:32
Quand le membre ouvre l'aperçu de sa sauvegarde à 14:45
Alors la suppression indique « republiable à partir de 14:37 », date déjà passée
```
Test : `RepublicationDelayAcceptanceTest.elapsedDelayLeavesListingRepublishable()`

### AT-11-03 – Pas de date sans suppression confirmée
```gherkin
Étant donné une annonce sans suppression, une autre dont la suppression est non confirmée, une troisième dont la suppression est non faite
Quand le membre ouvre l'aperçu de leur sauvegarde
Alors aucune n'a de date de republication
```
Test : `RepublicationDelayAcceptanceTest.noRepublicationDateWithoutConfirmedDeletion()`

### AT-11-04 – Suppression prouvée par une revérification
```gherkin
Étant donné une suppression cliquée à 14:00, restée non confirmée
Quand le membre la revérifie à 16:00 et Vinted ne montre plus l'annonce
Alors l'annonce est republiable à partir de 16:05
```
Test : `RepublicationDelayAcceptanceTest.recheckProofStartsTheDelay()`

### AT-11-05 – Redémarrage pendant le délai
```gherkin
Étant donné une suppression prouvée à 14:32, enregistrée en base
Quand l'outil redémarre à 14:34
Alors l'aperçu indique toujours « republiable à partir de 14:37 »
```
Test : `JpaListingDeletionRepositoryTest.republicationDateSurvivesRestart()` (`@Tag("docker")`)

### AT-11-06 – Délai réglable
```gherkin
Étant donné la configuration par défaut
Alors le délai minimal vaut 5 minutes
Et quand `republication.minimum-delay` vaut PT10M, une suppression prouvée à 14:32 est republiable à partir de 14:42
```
Test : `RepublicationConfigurationTest.defaultMinimumDelayIsFiveMinutes()` et `RepublicationConfigurationTest.minimumDelayIsConfigurable()`

### AT-11-07 – Affichage du temps restant dans l'aperçu
```gherkin
Étant donné un aperçu dont la suppression est republiable dans 2 secondes
Quand la page est ouverte
Alors elle affiche « Republication possible dans 0 min 2 s » avec l'heure de republication
Et après 2 secondes, sans recharger, elle affiche « Republication possible »
Et une suppression republiable depuis 10 minutes affiche directement « Republication possible »
```
Test : `ListingBackupPageTest.Deletion.showsRemainingTimeUntilRepublication()` et `ListingBackupPageTest.Deletion.showsRepublicationPossibleOnceDelayElapsed()` (`@Tag("browser")`, page locale, API simulée)

### AT-11-08 – Délai invalide dans la configuration
```gherkin
Étant donné `republication.minimum-delay` absent, à PT0S, à -PT5M ou à « cinq »
Quand l'outil démarre
Alors il refuse de démarrer avec un message qui nomme `republication.minimum-delay`
```
Test : `RepublicationConfigurationTest.missingMinimumDelayPreventsStartup()` et `RepublicationConfigurationTest.invalidMinimumDelayPreventsStartup()`

## Cas limites et erreurs
- Délai minimal absent, nul, négatif ou illisible dans la configuration : l'outil refuse de démarrer (AT-11-08).
- Horloge du navigateur différente de celle du serveur : sans objet, l'application n'écoute que sur `127.0.0.1`.
- Dry-run (US-10) : aucune suppression enregistrée, donc aucune date.
- Annonce supprimée puis revue par un scan : hors sujet ici, la date ne dépend que de la suppression.

## Hors périmètre
- Republication elle-même, et son refus avant la date (US-12) ; vérification de la republication (US-13).
- Enchaînement automatique suppression → attente → republication (US-15, US-16, US-19).
- Affichage dans la liste des annonces (dashboard, US-18).

## Décisions
- Nature de l'attente, décidé par l'utilisateur (2026-11-11) : délai qui bloque la republication manuelle (US-12) jusqu'à la fin des 5 minutes, temps restant affiché dans l'aperçu ; aucun déclenchement automatique de la republication (orchestration : US-15, US-16, US-19).
- Point de départ, décidé par l'utilisateur (2026-11-11) : la date de preuve de la suppression confirmée (`settledAt` de `ListingDeletion`), y compris quand la preuve vient d'une revérification (choix prudent : l'instant exact de la disparition est inconnu). L'attente se calcule depuis cette date déjà en base : rien de nouveau à stocker, elle survit à tout redémarrage.
- Délai minimal, décidé par l'utilisateur (2026-11-11) : les 5 minutes servent seulement à ne pas proposer la republication juste après la suppression. Une annonce supprimée depuis 5 minutes ou plus (par exemple 10 minutes, ou après un arrêt de l'outil) est republiable tout de suite, sans aucune attente.
- Durée réglable, décidé par l'utilisateur (2026-11-11) : le délai minimal est une propriété de configuration, `republication.minimum-delay: 5m`, écrite dans `application.yml` (même valeur partout), sans variable d'environnement obligatoire ; surchargeable comme toute propriété Spring. Nom de la propriété aligné à l'implémentation sur `deletion.dry-run` (préfixe court, sans `republisher.`).
- Livrable, décidé par l'utilisateur (2026-11-11) : la règle du domaine (une annonce dont la suppression est confirmée est republiable à `settledAt` + délai minimal ; sans suppression confirmée, aucune date, pas republiable), la date `republishableAt` dans l'aperçu de la suppression (contrat OpenAPI d'abord) et son affichage dans l'aperçu. Le refus d'une republication trop tôt est appliqué par US-12, qui s'appuie sur cette règle.
- Affichage, décidé par l'utilisateur (2026-11-11) : seulement dans l'aperçu de la sauvegarde (`listing-backup.html`) ; temps restant mis à jour chaque seconde par la page avec l'heure du navigateur, passage à « Republication possible » sans recharger.

- Configuration invalide, décidé par l'utilisateur (2026-11-11) : délai minimal absent, nul, négatif ou illisible → l'outil refuse de démarrer, avec un message qui nomme la propriété (un délai nul republierait sans attente).

## Questions ouvertes
- Aucune.

## Modèle de domaine
- Nouveau terme : `RepublicationDelay` | Délai avant republication | Durée minimale entre la preuve d'une suppression et sa republication, fixée par la configuration (5 minutes) ; strictement positive.
- `RepublicationDelay.republishableAt(ListingDeletion)` : `Optional<Instant>`, présent seulement pour une suppression `CONFIRMED`, égal à `settledAt` + délai. La règle vit dans `domain.republication`, qui dépend de `domain.deletion` et pas l'inverse (US-12 y ajoutera la republication sans cycle de paquets).
- `InvalidRepublicationDelay` : délai nul ou négatif refusé.
- `DeletionReport` gagne `republishableAt`, calculé par `ShowListingDeletion` : l'adaptateur web n'a pas à connaître le délai.
- Aucun nouvel agrégat, aucun nouveau port, aucun changement de schéma (Liquibase).

## Tests unitaires et d'intégration attendus
- Domaine : `RepublicationDelayTest` (date pour `CONFIRMED`, aucune pour `UNCONFIRMED` et `NOT_DONE`, refuse une durée nulle ou négative).
- Application : `ShowListingBackup` (ou le cas d'usage qui construit `DeletionReport`) transmet la date.
- Adapter web : mapping de `republishableAt` dans `ListingBackupView` ; `ListingBackupPageTest` pour l'affichage.
- Configuration : `RepublicationConfigurationTest` (défaut 5 minutes, surcharge, valeur invalide).
- Persistance : relecture d'une suppression confirmée après redémarrage (`@Tag("docker")`).
