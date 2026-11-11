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
3. **Contrôles**, avant d'ouvrir Chrome, l'un en échec = refus sans rien toucher sur Vinted : sauvegarde vue = sauvegarde actuelle ; dernière suppression confirmée, de cette version ; date de republication atteinte ; sauvegarde toujours validée ; photos gardées présentes, intactes et au moins une ; aucune republication réussie ni en erreur pour l'annonce.
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

- `ListingBackupView` gagne `republication` (absent = aucune tentative) : `outcome` (`PUBLISHED`, `FAILED`), `attemptedAt` (clic sur « Publier »), `publishedAt` et `newListingId` (présents si `PUBLISHED`), `backupSavedAt` ; `republicationDryRun` (vrai tant que le dry-run est actif) ; `republicationDryRunPassed` en réponse d'un dry-run.
- `BrowserAction` gagne `REPUBLISHING`.

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
Test : `RepublishListingAcceptanceTest.refusesSecondRepublication()`

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
Test : `RepublicationPropertiesTest.dryRunSetting()`

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
Alors chaque champ reçoit la valeur sauvegardée, les photos sont envoyées dans l'ordre
Et la nouvelle annonce est reconnue sur la page du dressing
```
Test : `PlaywrightListingPublisherTest` (`@Tag("browser")`, pages locales)

### AT-12-15 – Conservation en base
```gherkin
Étant donné une republication réussie et une autre en erreur
Quand l'outil redémarre
Alors les deux sont relues à l'identique, avec le lien entre ancienne et nouvelle annonce
```
Test : `JpaListingRepublicationRepositoryTest` (`@Tag("docker")`)

### AT-12-16 – Page de l'aperçu
```gherkin
Étant donné une annonce supprimée, republiable
Quand le membre clique « Republier sur Vinted »
Alors la confirmation montre titre, prix, nombre de photos et date de suppression
Et « Republier maintenant » envoie la demande avec la version vue, puis affiche l'issue
Et l'état du dry-run est affiché en permanence
```
Test : `ListingBackupPageTest` (`@Tag("browser")`, API simulée)

### AT-12-17 – Demande venue d'un autre site
```gherkin
Étant donné une demande de republication dont l'origine n'est pas l'outil
Alors elle est refusée (403)
```
Test : `RepublishListingWebTest.refusesCrossSiteRequest()`

### AT-12-18 – Observation du formulaire réel
```gherkin
Étant donné une annonce choisie par le membre, sur son compte, en dry-run
Quand l'outil remplit le formulaire de mise en vente sans publier
Alors le membre et Claude relèvent les sélecteurs, le choix d'une option par identifiant, l'envoi des photos et l'éventuel brouillon gardé par Vinted
```
Test : manuel (vrai compte Vinted, avec l'accord de l'utilisateur ; sert de base aux pages locales d'AT-12-14)

## Cas limites et erreurs
- Libellé vide dans la sauvegarde (taille 588 d'US-05) : choix par identifiant si l'observation le permet, sinon FIELD_NOT_FILLABLE.
- Brouillon gardé par Vinted quand on quitte le formulaire (erreur avant publication, dry-run) : à relever pendant l'observation (AT-12-18) ; s'il existe, son traitement revient à l'utilisateur avant l'adaptateur.
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

## Questions ouvertes
- Aucune bloquant la spec. L'observation du formulaire réel (AT-12-18) précède l'adaptateur ; un brouillon gardé par Vinted, s'il existe, sera soumis à l'utilisateur.

## Modèle de domaine
Termes à ajouter au glossaire :
- `ListingRepublication` (`RepublishListing`) | Republication d'une annonce | Tentative de remettre en vente une annonce supprimée, arrivée au clic sur « Publier » : annonce, version de la sauvegarde, date du clic, issue, nouvel identifiant et date de preuve. Enregistrée en erreur juste avant le clic, réussie une fois la nouvelle annonce retrouvée ; jamais supprimée.
- `RepublicationOutcome` (`PUBLISHED` / `FAILED`) | Issue de la republication (réussie / erreur de republication) | Réussie : nouvelle annonce retrouvée seule dans le dressing. Erreur : tout le reste après le clic ; bloque une nouvelle republication jusqu'à US-13.
- `RepublicationMode` (`DRY_RUN` / `REAL`) | Mode de republication | Fixé au démarrage par `REPUBLICATION_DRY_RUN`.
- `ListingPublisher` | Publieur d'annonce | Port sortant : remplit le formulaire de mise en vente et publie comme le membre, dans le Chrome piloté.
- `ListingForm` | Formulaire de mise en vente | Page de Vinted pour créer une annonce.
- `FieldNotFillable` | Champ impossible à remplir | Option introuvable par identifiant et libellé, ou champ absent ; rien publié.
- `ListingNotDeleted`, `RepublicationTooEarly`, `ListingAlreadyRepublished`, `RepublicationInError` | Refus | Annonce sans suppression confirmée, date de republication non atteinte, déjà republiée, erreur de republication en cours.
- `Listing.republishedFrom` | Republiée depuis | Lien d'une annonce vers celle qu'elle remplace.
- `BrowserAction` gagne `REPUBLISHING`.
- Persistance (Liquibase) : table des republications ; colonne « republiée depuis » sur les annonces.

## Tests unitaires et d'intégration attendus
- Domaine : `ListingRepublicationTest` (erreur à la création, réussite une seule fois, refus d'une seconde tentative) ; contrôles avant republication (`RepublicationVisit` ou équivalent) ; reconnaissance de la nouvelle annonce (une, aucune, plusieurs, identifiants connus ignorés) ; `PhotosVerification` partielle.
- Application : `RepublishListing` avec ports simulés (ordre : enregistrement avant le clic, verrou libéré, dry-run sans enregistrement).
- Adapters : `PlaywrightListingPublisherTest` (pages locales), `JpaListingRepublicationRepositoryTest` (`@Tag("docker")`), contrôleur `republishListing` (codes et refus), `ListingBackupPageTest`, `RepublicationPropertiesTest`.
