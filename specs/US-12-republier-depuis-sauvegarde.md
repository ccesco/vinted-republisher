# US-12 – Republier depuis la sauvegarde
**Statut** : validée
**Priorité** : MVP
**Dépend de** : US-05 et US-06 (contenu et photos sauvegardés), US-08 (validation), US-09 (suppression confirmée), US-11 (délai minimal) ; suivie d'US-13 (vérification)

## Contexte
Une annonce supprimée (US-09) doit être remise en vente à l'identique, sans rien ressaisir, à partir de sa sauvegarde : contenu du formulaire de mise en vente avec les identifiants Vinted (US-05) et photos gardées telles que Vinted les a servies (US-06). La republication n'est possible qu'à partir de la date de republication d'US-11.

Contraintes déjà posées par d'autres specs :
- Aucune requête directe vers l'API de Vinted : l'outil agit comme le membre, dans le Chrome piloté (US-04, US-09).
- Les photos gardées ne sont jamais modifiées ; la version recadrée est hors périmètre (US-31).
- Le lien entre l'annonce et sa republication, sous un nouvel identifiant Vinted, est attendu par US-04 et US-07 (cumul des vues et favoris).

## Objectif
En tant que membre, je veux republier une annonce supprimée à partir de sa sauvegarde, afin de la remettre en vente à l'identique sans rien ressaisir.

## Comportement attendu
1. **Aperçu** : une annonce dont la suppression est confirmée montre « Republier sur Vinted », actif à partir de sa date de republication (US-11), et l'état du mode : « Dry-run actif : aucune republication réelle » ou « Republications réelles activées ».
2. **Confirmation** : le bouton ouvre une confirmation dans l'outil (titre, prix, nombre de photos gardées, date de suppression) ; « Republier maintenant » envoie la demande avec la version de la sauvegarde vue.
3. **Contrôles**, avant d'ouvrir Chrome, dans cet ordre, l'un en échec = refus sans rien toucher sur Vinted : sauvegarde vue = sauvegarde actuelle ; aucune republication réussie ni en erreur pour l'annonce ; dernière suppression confirmée, de cette version ; date de republication atteinte ; sauvegarde toujours validée ; photos gardées présentes, intactes et au moins une.
4. **Remplissage** : action navigateur `REPUBLISHING` (verrou d'US-03) ; formulaire de mise en vente ouvert dans le Chrome piloté ; photos originales dans l'ordre, puis titre, description, prix, catégorie, options et champs propres à la catégorie. Champ impossible à remplir → abandon, rien publié.
5. **Dry-run** : arrêt ici, sans clic sur « Publier » ni enregistrement ; la réponse dit que tout a pu être rempli.
6. **Publication** : la tentative est enregistrée « erreur de republication » juste avant le clic sur « Publier », puis l'outil clique.
7. **Preuve** : une lecture du dressing ; parmi les annonces dont l'identifiant Vinted est inconnu de l'outil, ouverture de leur page pour lire titre et prix (comme au scan). Exactement une avec le titre et le prix de la sauvegarde → republication réussie : nouvel identifiant et date de preuve enregistrés, nouvelle annonce créée présente et reliée à l'ancienne. Aucune ou plusieurs, ou toute erreur après le clic → le statut « erreur de republication » reste.
8. **Après** : l'aperçu montre l'issue (réussie avec le nouvel identifiant et son lien, ou « erreur de republication, vérifier sur Vinted ») ; l'ancienne annonce reste absente avec sa sauvegarde, ses photos, sa validation et sa suppression.

## Contrat d'API
Contract first : `src/main/resources/static/openapi/vinted-republisher.yaml`. Demande synchrone : la réponse arrive une fois l'action finie.

| Méthode et chemin | operationId | Réponse |
| --- | --- | --- |
| `POST /api/listings/{listingId}/republication` | `republishListing` | corps `RepublicationRequest` {`savedAt`} ; 200 `ListingBackupView` à jour (avec `republication`), y compris en erreur de republication, ou dry-run réussi (`republicationDryRunPassed`) ; 400 sans `savedAt` ; 404 sans sauvegarde ; 409 `RepublicationRefusal` (`BACKUP_REPLACED`, `NOT_VALIDATED`, `NOT_DELETED`, `TOO_EARLY` avec `republishableAt`, `ALREADY_REPUBLISHED`, `REPUBLICATION_IN_ERROR`, `BROWSER_BUSY`) ; 422 `RepublicationRefusal` (`PHOTOS_NOT_VERIFIED` avec `verification`, `FIELD_NOT_FILLABLE` avec `field`) ; 502 `RepublicationRefusal` (`BROWSER_UNAVAILABLE`, `VINTED_UNREACHABLE`, `SESSION_DISCONNECTED`, `FORM_UNREADABLE`) ; 500 `RepublicationRefusal` (`REPUBLICATION_FAILED`) ; 403 si la demande vient d'un autre site |

- `ListingBackupView` gagne `republication` (absent = aucune tentative) : `outcome` (`PUBLISHED`, `FAILED`), `attemptedAt` (clic sur « Publier »), `publishedAt`, `newListingId` et `newListingUrl` (présents si `PUBLISHED`), `backupSavedAt` ; `republicationDryRun` (vrai tant que le dry-run est actif) ; `republicationDryRunPassed` en réponse d'un dry-run.
- `BrowserAction` gagne `REPUBLISHING`.
- `FIELD_NOT_FILLABLE` porte `field` (`ListingFormField` : `PHOTOS`, `TITLE`, … `SIZE`, …) et, s'il y en a une, `optionId` (identifiant Vinted de l'option introuvable).

## Critères d'acceptation
- [ ] Une annonce ne se republie que si sa dernière suppression est confirmée, sur la version de la sauvegarde vue, après sa date de republication, avec une sauvegarde toujours validée et des photos présentes et intactes ; sinon Chrome n'est pas ouvert.
- [ ] Une annonce déjà republiée, ou en erreur de republication, ne se republie pas.
- [ ] Le formulaire est rempli avec tout le contenu sauvegardé et les photos originales dans l'ordre ; un champ impossible à remplir abandonne avant publication.
- [ ] Toute erreur avant le clic sur « Publier » refuse sans rien publier ni enregistrer.
- [ ] Une republication n'est réussie que si la nouvelle annonce est retrouvée, seule, dans le dressing, avec le titre et le prix de la sauvegarde ; elle est alors enregistrée, présente et reliée à l'ancienne.
- [ ] Après le clic sur « Publier », tout échec, plantage compris, laisse « erreur de republication », sans nouvel essai.
- [ ] En dry-run (par défaut), rien n'est publié ni enregistré.
- [ ] Sauvegarde, photos, validation et suppression de l'ancienne annonce restent intactes.
- [ ] Une seule action navigateur à la fois ; le verrou est toujours libéré.
- [ ] Les republications restent en base après un redémarrage.
- [ ] Un autre site ouvert dans le navigateur ne peut pas demander une republication.
- [ ] Aucun test automatisé ne contacte le vrai site Vinted.

## Tests d'acceptation
### AT-12-01 – Republication réussie
```gherkin
Étant donné une annonce « Robe bleue » à 12,00 €, sauvegarde validée, suppression confirmée il y a 6 minutes
Et les republications réelles activées
Quand le membre confirme « Republier maintenant »
Alors le formulaire est rempli avec tout le contenu et les photos dans l'ordre, puis publié
Et le dressing montre une seule nouvelle annonce « Robe bleue » à 12,00 €
Et la republication est réussie, avec son nouvel identifiant
Et la nouvelle annonce est présente, reliée à l'ancienne, sans sauvegarde
```
Test : `RepublishListingAcceptanceTest.republishesConfirmedDeletion()`

### AT-12-02 – Annonce non supprimée
```gherkin
Étant donné une annonce sans suppression, une autre dont la suppression est non confirmée, une troisième non faite
Quand le membre demande leur republication
Alors chacune est refusée (NOT_DELETED) et Chrome n'est pas ouvert
```
Test : `RepublishListingAcceptanceTest.refusesWithoutConfirmedDeletion()`

### AT-12-03 – Trop tôt
```gherkin
Étant donné une suppression prouvée il y a 2 minutes
Quand le membre demande la republication
Alors elle est refusée (TOO_EARLY) avec la date de republication, et Chrome n'est pas ouvert
```
Test : `RepublishListingAcceptanceTest.refusesBeforeRepublicationDate()`

### AT-12-04 – Sauvegarde remplacée ou non validée
```gherkin
Étant donné une sauvegarde remplacée depuis l'aperçu, ou dont la validation ne compte plus
Quand le membre demande la republication
Alors elle est refusée (BACKUP_REPLACED ou NOT_VALIDATED) et Chrome n'est pas ouvert
```
Test : `RepublishListingAcceptanceTest.refusesReplacedOrUnvalidatedBackup()`

### AT-12-05 – Photos manquantes ou modifiées
```gherkin
Étant donné une sauvegarde dont une photo manque sur disque, ou dont l'empreinte a changé
Quand le membre demande la republication
Alors elle est refusée (PHOTOS_NOT_VERIFIED) avec la photo en cause, et Chrome n'est pas ouvert
Et l'absence de l'annonce dans le dressing n'est pas un motif de refus
```
Test : `RepublishListingAcceptanceTest.refusesMissingOrAlteredPhotos()`

### AT-12-06 – Déjà republiée ou en erreur
```gherkin
Étant donné une annonce déjà republiée, une autre en erreur de republication
Quand le membre demande leur republication
Alors elles sont refusées (ALREADY_REPUBLISHED, REPUBLICATION_IN_ERROR) et Chrome n'est pas ouvert
```
Test : `RepublishListingAcceptanceTest.refusesSecondRepublication()`, `RepublishListingAcceptanceTest.refusesRepublicationInError()`

### AT-12-07 – Champ impossible à remplir
```gherkin
Étant donné une sauvegarde dont la taille 588 est introuvable dans le formulaire
Quand le membre demande la republication
Alors elle est refusée (FIELD_NOT_FILLABLE, « Taille »), « Publier » n'est pas cliqué
Et aucune republication n'est enregistrée ; le membre peut relancer
```
Test : `RepublishListingAcceptanceTest.abandonsWhenFieldNotFillable()`

### AT-12-08 – Erreur avant le clic sur « Publier »
```gherkin
Étant donné <situation>
Quand le membre demande la republication
Alors elle est refusée (<code>), rien n'est publié ni enregistré, et le verrou est libéré

Exemples :
| situation                          | code                 |
| session non connectée              | SESSION_DISCONNECTED |
| navigateur indisponible            | BROWSER_UNAVAILABLE  |
| Vinted inaccessible                | VINTED_UNREACHABLE   |
| formulaire illisible               | FORM_UNREADABLE      |
| erreur inattendue                  | REPUBLICATION_FAILED |
```
Test : `RepublishListingAcceptanceTest.refusesOnErrorBeforePublishClick()`

### AT-12-09 – Nouvelle annonce introuvable ou ambiguë
```gherkin
Étant donné un clic sur « Publier » fait
Quand le dressing ne montre aucune nouvelle annonce avec ce titre et ce prix, ou en montre deux
Alors la republication reste « erreur de republication », sans nouvel essai
Et l'aperçu affiche « erreur de republication, vérifier sur Vinted »
```
Test : `RepublishListingAcceptanceTest.leavesErrorWhenNewListingNotFoundOrAmbiguous()`

### AT-12-10 – Échec ou plantage après le clic
```gherkin
Étant donné un clic sur « Publier » fait
Quand la lecture du dressing échoue, ou l'outil s'arrête avant de la faire
Alors la republication est « erreur de republication » en base, et bloque toute relance
```
Test : `RepublishListingAcceptanceTest.leavesErrorOnFailureOrCrashAfterPublishClick()`

### AT-12-11 – Dry-run
```gherkin
Étant donné le dry-run actif (REPUBLICATION_DRY_RUN absente ou true)
Quand le membre demande la republication d'une annonce qui passe tous les contrôles
Alors le formulaire est entièrement rempli, « Publier » n'est pas cliqué, rien n'est enregistré
Et la réponse indique un dry-run réussi
```
Test : `RepublishListingAcceptanceTest.dryRunFillsFormWithoutPublishing()`

### AT-12-12 – Réglage du dry-run
```gherkin
Étant donné REPUBLICATION_DRY_RUN <valeur>
Quand l'application démarre
Alors <résultat>

Exemples :
| valeur  | résultat                                  |
| absente | dry-run actif                             |
| true    | dry-run actif                             |
| false   | republications réelles                    |
| oui     | refus de démarrer (true ou false attendu) |
| (vide)  | refus de démarrer (true ou false attendu) |
```
Test : `RepublicationModeConfigurationTest` (`dryRunSetting()` et suivants)

### AT-12-13 – Une seule action navigateur à la fois
```gherkin
Étant donné un scan en cours
Quand le membre demande une republication
Alors elle est refusée (BROWSER_BUSY)
```
Test : `RepublishListingAcceptanceTest.refusesWhileBrowserBusy()`

### AT-12-14 – Adaptateur sur une fausse page locale
```gherkin
Étant donné une copie locale du formulaire de mise en vente et du dressing relevés pendant l'observation
Quand l'adaptateur remplit et publie
Alors chaque champ reçoit la valeur sauvegardée, même là où Vinted a suggéré autre chose, les photos sont envoyées dans l'ordre
Et seul « Ajouter » est cliqué, jamais « Sauvegarder le brouillon » ni le Boost
Et la nouvelle annonce est reconnue sur la page du dressing
```
Test : `PlaywrightListingPublisherTest` (`@Tag("browser")`, pages locales), dont `Publication.fills_every_field_with_the_saved_values_over_vinteds_suggestions_then_publishes()` ; la reconnaissance dans le dressing passe par `PlaywrightWardrobeReader`, déjà couvert par ses propres tests.

### AT-12-15 – Conservation en base
```gherkin
Étant donné une republication réussie et une autre en erreur
Quand l'outil redémarre
Alors les deux sont relues à l'identique, avec le lien entre ancienne et nouvelle annonce
```
Test : `JpaListingRepublicationRepositoryTest.republicationsSurviveRestart()` (`@Tag("docker")`)

### AT-12-16 – Page de l'aperçu
```gherkin
Étant donné une annonce supprimée, republiable
Quand le membre clique « Republier sur Vinted »
Alors la confirmation montre titre, prix, nombre de photos et date de suppression
Et « Republier maintenant » envoie la demande avec la version vue, puis affiche l'issue
Et l'état du dry-run est affiché en permanence
```
Test : `ListingBackupPageTest.Republication.pageShowsRepublicationFlow()` (`@Tag("browser")`, API simulée)

### AT-12-17 – Demande venue d'un autre site
```gherkin
Étant donné une demande de republication dont l'origine n'est pas l'outil
Alors elle est refusée (403)
```
Test : `ListingRepublicationApiControllerTest.refusesCrossSiteRequest()`

### AT-12-18 – Observation du formulaire réel
```gherkin
Étant donné une annonce choisie par le membre, sur son compte, en dry-run
Quand l'outil remplit le formulaire de mise en vente sans publier
Alors le membre et Claude relèvent les sélecteurs, le choix d'une option par identifiant, l'envoi des photos et l'éventuel brouillon gardé par Vinted
```
Test : manuel (vrai compte Vinted, avec l'accord de l'utilisateur ; sert de base aux pages locales d'AT-12-14)

## Cas limites et erreurs
- Brouillon gardé par Vinted quand on quitte le formulaire (erreur avant publication, dry-run) : aucun, d'après l'observation (AT-12-18) ; on ne clique jamais sur « Sauvegarder le brouillon ».
- Libellé vide d'une taille : l'option se choisit par son identifiant (`size-group-<groupe>-grid-option-<id>`), le libellé ne sert pas.
- Une annonce sans rapport mais inconnue de l'outil (mise en ligne par le membre hors de l'outil) avec un autre titre ou prix : ignorée ; avec le même titre et le même prix : ambiguïté → erreur de republication.
- Plantage entre l'enregistrement de la tentative et le clic : « erreur de republication » alors que rien n'est publié ; c'est le prix de l'absence de doublon, US-13 le règle.

## Hors périmètre
- Vérification de la republication (US-13).
- Republication avec la photo recadrée (US-31).
- Enchaînement automatique et traitement en série (US-15, US-16, US-19).

## Décisions
- Périmètre, décidé par l'utilisateur (2026-11-11) : une annonce à la fois, à la demande du membre, par un bouton « Republier » dans l'aperçu de sa sauvegarde, actif seulement à partir de la date de republication d'US-11 ; aucune republication en série (US-19).

- Contrôles avant republication, décidé par l'utilisateur (2026-11-11), tous avant d'ouvrir Chrome, l'un en échec = refus sans rien toucher sur Vinted : (1) dernière suppression de l'annonce confirmée ; (2) date de republication d'US-11 atteinte (sinon refus avec le temps restant) ; (3) sauvegarde actuelle = version supprimée (même date de sauvegarde) et toujours validée ; (4) photos gardées présentes, intactes (empreinte SHA-256) et au moins une — sans les contrôles « annonce présente » et « titre et prix du dernier scan » d'US-08, forcément faux après une suppression ; (5) annonce pas déjà republiée (pas de doublon sur Vinted).

- Confirmation par le membre, décidé par l'utilisateur (2026-11-11) : « Republier sur Vinted » dans l'aperçu ouvre une confirmation dans l'outil (titre, prix, nombre de photos gardées, date de suppression) ; second clic « Republier maintenant ». La demande porte la version de la sauvegarde vue (date de sauvegarde) : sauvegarde remplacée depuis → refus, comme `BackupReplaced`. Pas de saisie de l'identifiant.

- Remplissage, décidé par l'utilisateur (2026-11-11) : comme le membre, dans le Chrome piloté, formulaire de mise en vente champ par champ : photos originales par le sélecteur de fichiers, dans l'ordre gardé (couverture d'abord) ; titre, description, prix saisis ; catégorie par l'arbre ; puis marque, état, taille, couleurs, matières, format du colis et champs propres à la catégorie. Prérequis à l'adaptateur : observer une fois avec l'utilisateur le formulaire réel, sur une annonce qu'il choisit, rempli sans publier ; l'observation dit aussi comment choisir une option par son identifiant quand son libellé est vide (cas de la taille 588 d'US-05).

- Champ impossible à remplir, décidé par l'utilisateur (2026-11-11) : option introuvable par son identifiant comme par son libellé, ou champ absent du formulaire → abandon avant le clic de publication, rien publié, l'annonce reste supprimée ; problème nommant le champ dans l'aperçu (ex. « Taille impossible à remplir (identifiant 588) »). À voir pendant l'observation : brouillon éventuellement gardé par Vinted quand on quitte le formulaire.

- Échec de la republication, décidé par l'utilisateur (2026-11-11) : une republication qui n'a pas réussi est enregistrée avec un statut « erreur de republication ».

- Moment de l'erreur, décidé par l'utilisateur (2026-11-11) : une erreur avant le clic de publication ne laisse aucun statut (rien publié) : problème affiché, relance libre. Une erreur après le clic laisse le statut « erreur de republication », qui bloque toute nouvelle republication de l'annonce jusqu'à la vérification sur Vinted (US-13), pour éviter un doublon. Pour qu'un plantage juste après le clic laisse ce statut, la tentative est enregistrée juste avant le clic.

- Preuve de la réussite, décidé par l'utilisateur (2026-11-11) : la page affichée juste après le clic ne suffit pas ; la republication est réussie quand la nouvelle annonce est retrouvée dans le dressing. La comparaison complète avec la sauvegarde reste en US-13.
- Délai d'apparition, décidé par l'utilisateur (2026-11-11) : la nouvelle annonce apparaît dans le dressing quasi instantanément ; une seule lecture du dressing juste après la publication, sans attente ni relecture (hors le nouvel essai sur délai dépassé d'AT-04-19).

- Reconnaissance dans le dressing, décidé par l'utilisateur (2026-11-11) : la nouvelle annonce est celle du dressing dont l'identifiant Vinted est inconnu de l'outil, avec le même titre et le même prix que la sauvegarde. Exactement une → republication réussie : nouvel identifiant gardé, relié à l'ancienne annonce, avec la date. Aucune ou plusieurs → « erreur de republication » (bloque la relance jusqu'à US-13).

- Dry-run, décidé par l'utilisateur (2026-11-11) : la republication a son propre dry-run, réglé par `REPUBLICATION_DRY_RUN` sur le modèle de `DELETION_DRY_RUN` (US-10) : absente ou `true` → dry-run, `false` → republications réelles, autre valeur → l'application ne démarre pas ; `true` dans `.env.example` ; non modifiable depuis l'interface ou l'API ; état affiché en permanence dans l'aperçu. En dry-run : tous les contrôles, formulaire entièrement rempli (photos comprises), aucun clic sur « Publier », rien enregistré ; la réponse dit si tout a pu être rempli. Indépendant du dry-run de la suppression.

- Erreurs avant le clic de publication, décidé par l'utilisateur (2026-11-11), règles d'US-09 ; refus sans rien publier ni enregistrer, problème affiché dans l'aperçu, relance libre : nouvelle action navigateur `REPUBLISHING` sur le verrou d'US-03 (autre action en cours → 409) ; session non connectée ; navigateur indisponible ; Vinted inaccessible (HTTP, ou délai dépassé après le nouvel essai d'AT-04-19), sans nouvel essai ; formulaire illisible ; champ impossible à remplir ; erreur inattendue ou base indisponible → « échec de la republication » (logs), verrou toujours libéré ; demande d'un autre site → 403.

- Nouvelle annonce, décidé par l'utilisateur (2026-11-11) : après une republication réussie, la nouvelle annonce est une annonce à part dans l'outil, présente, sous son nouvel identifiant Vinted, reliée à l'ancienne (« republiée depuis », pour le cumul des statistiques d'US-07), sans sauvegarde : nouvelle sauvegarde et validation avant toute future suppression. L'ancienne reste absente avec sa sauvegarde, ses photos, sa validation et sa suppression.

- Lien entre ancienne et nouvelle annonce, décidé par l'utilisateur (2026-11-14) : porté par la republication réussie (ancien et nouvel identifiant), pas par une colonne sur les annonces, que chaque scan réécrit. La nouvelle annonce est une annonce ordinaire de l'outil.
- Choix de la catégorie, décidé par l'utilisateur (2026-11-15) après l'observation : le libellé sauvegardé est saisi dans la recherche de catégorie, puis l'outil clique sur le résultat qui porte l'identifiant sauvegardé ; aucun résultat à cet identifiant → FIELD_NOT_FILLABLE (catégorie). Cette décision remplace « catégorie par l'arbre » (2026-11-11), le chemin dans l'arbre n'étant pas sauvegardé. Toutes les options (marque, taille, état, couleurs, matières, format du colis) se choisissent par leur identifiant.
- Valeurs pré-remplies par Vinted, décidé par l'utilisateur (2026-11-15) : la sauvegarde s'impose. L'outil sélectionne toujours la valeur sauvegardée, décoche toute couleur ou matière cochée en trop et choisit « Sans marque » si la sauvegarde n'a pas de marque. Le formulaire final correspond exactement à la sauvegarde. La case payante « Boost » n'est jamais touchée.
- Ordre de travail, décidé par l'utilisateur (2026-11-14) : comme pour US-09, tout sauf l'adaptateur navigateur d'abord, avec un publieur « non observé » qui n'ouvre jamais Chrome ; l'observation AT-12-18 et l'adaptateur Playwright (AT-12-14) viennent ensuite.

- Messages de l'aperçu, proposés à l'implémentation (2026-11-14) : un refus dit « Republication refusée, rien n'a été publié : … » (ex. « Taille impossible à remplir (identifiant 588). ») ; une erreur inattendue (500) dit « Échec de la republication : vérifiez sur Vinted avant de relancer », sans affirmer que rien n'a été publié (elle peut survenir après le clic, si la base tombe pendant la réponse) ; un dry-run réussi dit « Dry-run réussi : contrôles réussis, formulaire de mise en vente entièrement rempli. Rien n'a été publié. » ; une republication réussie montre sa date et le lien vers la nouvelle annonce. Le compte à rebours d'US-11 active le bouton à la date de republication et disparaît dès qu'une republication existe.

## Avancement (2026-11-14)
- Fait : domaine, cas d'usage (`RepublishListing`, découpé en `RepublicationChecks` et `NewListingSearch`), base (`009-create-listing-republication`), API, réglage du dry-run, aperçu ; AT-12-01 à AT-12-13, AT-12-15 à AT-12-17 verts avec un faux navigateur.
- 2026-11-15 : observation AT-12-18 faite ; adaptateur `PlaywrightListingPublisher` écrit d'après elle, AT-12-14 vert sur pages locales. Il remplace `UnobservedListingPublisher`. Les champs propres aux livres et objets, jamais observés, rendent l'annonce non remplissable (`FIELD_NOT_FILLABLE`) sans ouvrir Chrome.
- 2026-11-15 : dry-run de republication sur le vrai compte, avec l'accord de l'utilisateur, sur l'annonce supprimée 10208799832 (« Bonnet 1-3 mois du pareil au même ») : contrôles réussis, formulaire entièrement rempli et relu, `republicationDryRunPassed: true`, aucune republication enregistrée, rien publié.
- 2026-11-15 : première republication réelle du bonnet, avec l'accord de l'utilisateur : clic sur « Ajouter », mais Vinted est resté sur le formulaire 10 s, republication enregistrée FAILED, annonce absente du dressing. Cause non prouvée : l'onglet a été fermé sans rien garder. Le même formulaire, rempli à la main au rythme d'un membre et envoyé d'un seul clic observé (accord de l'utilisateur), a été publié en moins de 3 s ; Vinted affiche ensuite la page du membre (`/member/<id>?promo_shown=true`), pas la nouvelle annonce. La ligne FAILED a été supprimée de la base à la demande de l'utilisateur.
- Observation de l'envoi d'une photo (2026-11-15) : `POST /api/v2/photos`, pendant lequel `media-upload` contient `upload-button-loader` et une `progressbar`, disparues à l'apparition de la vignette.
- Correctif : l'adaptateur attend la fin de l'envoi des photos (plus de barre de progression) avant de continuer, et, si Vinted garde le formulaire après le clic, l'erreur journalisée cite les messages affichés (alertes, validations sous les champs).
- 2026-11-15 : la copie publiée à la main a été supprimée par l'utilisateur, puis 2e republication réelle du bonnet avec le correctif : même symptôme, formulaire toujours affiché 10 s après le clic, aucun message visible, annonce absente, republication enregistrée FAILED (laissée en base). Piste du clic sur la case du colis écartée en dry-run : aucune fenêtre ouverte. Pistes restantes : publication plus lente que 10 s quand l'outil envoie, ou refus silencieux (anti-robot, rythme de remplissage).
- Reste : relever la réponse de Vinted à l'envoi du formulaire et attendre plus longtemps avant de conclure, puis refaire un test réel plus tard, sur validation explicite de l'utilisateur. L'US-12 reste « validée » tant qu'une republication réelle n'a pas réussi.

## Observation du formulaire réel (AT-12-18, 2026-11-15)
Faite avec l'accord de l'utilisateur sur l'annonce 10208513136 (« Pyjama petit bateau 3 mois »), dans le Chrome piloté, formulaire entièrement rempli puis onglet fermé : aucun clic sur « Ajouter » ni sur « Sauvegarder le brouillon ».
- Page `https://www.vinted.fr/items/new`, titre « Vends ton article ». Pas de `<main>`. Au départ, seuls les champs photos, titre, description, catégorie et prix sont affichés. Les autres apparaissent une fois la catégorie choisie.
- Photos : `input[data-testid=add-photos-input]` (`type=file`, `multiple`, jpeg/gif/png/webp). Les fichiers s'envoient d'un coup, sans boîte de dialogue, dans l'ordre donné ; la première devient « Principale ». Vignettes `media-select-photo-<rang>`.
- Titre `#title`, description `textarea#description`, prix `#price` : saisie « 2,00 », affiché « 2,00 € ».
- Catégorie `#category` : la liste déroulante propose des suggestions (`catalog-suggestion-<id>`), la recherche `#catalog-search-input` et l'arbre (`catalog-<id>`). Avec le libellé sauvegardé dans la recherche, chaque résultat porte l'identifiant (`#catalog-search-<id>-result`) et le chemin complet. Vinted **pré-sélectionne** une catégorie suggérée d'après le titre et les photos.
- Marque `#brand` : recherche `#brand-search-input`, résultat `brand-<id>`, suggestion `suggested-brand-<id>`, « Sans marque » `empty-brand`, listé dès l'ouverture, sans recherche. Vinted **pré-remplit** une marque suggérée.
- Taille `#size` : grille `size-group-<groupe>-grid-option-<id>`. L'identifiant suffit ; le libellé vide (taille 588 d'US-05) n'empêche pas le choix.
- État `#condition` : `condition-<id>`.
- Couleurs `#color` : 2 au maximum, cases `filter-grid-option-<id>` (`role=checkbox`, état dans `aria-checked`, comme les tailles et les matières). Une couleur suggérée peut apparaître deux fois (suggestion et liste complète).
- Matériaux `#material` : cases `material-<id>` (liste) et `material-suggestion-<id>` (suggestions).
- Format du colis : cellule `#package-size-<id>`, radio `package_type_selector_<id>--input`. Vinted **pré-coche** le format recommandé.
- Chaque champ choisi affiche sa valeur dans son `input` (`#category`, `#brand`, `#size`, `#condition`, `#color`, `#material`). L'outil relit ces valeurs juste avant de publier : une suggestion de Vinted arrivée après coup rend le champ non remplissable, et rien n'est publié.
- Une fois le prix saisi, un encart « Services supplémentaires » apparaît avec une case « Boost » (payante, décochée) : l'outil ne la touche jamais.
- Boutons : « Ajouter » = publication (`upload-form-save-button`), « Sauvegarder le brouillon » (`upload-form-save-draft-button`).
- Champs propres aux livres et aux objets (ISBN, auteur, fabricant, dimensions) : non vus, l'annonce observée n'en a pas.
- Après fermeture de l'onglet, `/items/new` rouvert est vide : le navigateur ne garde aucun brouillon.
- Titre : le titre lu au scan du dressing et le titre sauvegardé sont identiques pour l'annonce observée.

- Brouillon : l'utilisateur confirme qu'aucun brouillon n'apparaît dans son compte Vinted après l'observation (2026-11-15).

## Questions ouvertes
- Aucune.

## Modèle de domaine
Termes à ajouter au glossaire :
- `ListingRepublication` (`RepublishListing`) | Republication d'une annonce | Tentative de remettre en vente une annonce supprimée, arrivée au clic sur « Publier » : annonce, version de la sauvegarde, date du clic, issue, nouvel identifiant et date de preuve. Enregistrée en erreur juste avant le clic, réussie une fois la nouvelle annonce retrouvée ; jamais supprimée.
- `RepublicationOutcome` (`PUBLISHED` / `FAILED`) | Issue de la republication (réussie / erreur de republication) | Réussie : nouvelle annonce retrouvée seule dans le dressing. Erreur : tout le reste après le clic ; bloque une nouvelle republication jusqu'à US-13.
- `RepublicationMode` (`DRY_RUN` / `REAL`) | Mode de republication | Fixé au démarrage par `REPUBLICATION_DRY_RUN`.
- `ListingPublisher` | Publieur d'annonce | Port sortant : remplit le formulaire de mise en vente et publie comme le membre, dans le Chrome piloté.
- `ListingForm` | Formulaire de mise en vente | Page de Vinted pour créer une annonce.
- `FieldNotFillable` | Champ impossible à remplir | Option introuvable par identifiant et libellé, ou champ absent ; rien publié.
- `ListingNotDeleted`, `RepublicationTooEarly`, `ListingAlreadyRepublished`, `RepublicationInError` | Refus | Annonce sans suppression confirmée, date de republication non atteinte, déjà republiée, erreur de republication en cours.
- `ListingRepublication.newListingId` | Republiée depuis | Lien de l'ancienne annonce vers celle qui la remplace, porté par la republication réussie (voir Décisions).
- `ListingFormUnreadable` | Formulaire de mise en vente illisible | Formulaire introuvable ou pas celui attendu ; rien publié.
- `BrowserAction` gagne `REPUBLISHING`.
- Persistance (Liquibase) : table des republications, avec le nouvel identifiant unique et lié à la table des annonces.

## Tests unitaires et d'intégration attendus
- Domaine : `ListingRepublicationTest` (erreur à la création, réussite une seule fois, refus d'une seconde tentative) ; contrôles avant republication (`RepublicationVisit` ou équivalent) ; reconnaissance de la nouvelle annonce (une, aucune, plusieurs, identifiants connus ignorés) ; `PhotosVerification` partielle.
- Application : `RepublishListing` avec ports simulés (ordre : enregistrement avant le clic, verrou libéré, dry-run sans enregistrement).
- Adapters : `PlaywrightListingPublisherTest` (pages locales), `JpaListingRepublicationRepositoryTest` (`@Tag("docker")`), contrôleur `republishListing` (codes et refus), `ListingBackupPageTest`, `RepublicationModeConfigurationTest`.
