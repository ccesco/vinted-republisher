# US-09 – Supprimer uniquement après sauvegarde validée
**Statut** : validée
**Priorité** : MVP
**Dépend de** : US-03 (session), US-04 (scan), US-05 et US-06 (sauvegarde), US-08 (validation) ; livrée avec US-10 (dry-run)

## Contexte
Republier une annonce suppose de la supprimer d'abord sur Vinted. La suppression est irréversible : elle ne doit avoir lieu que si la sauvegarde de l'annonce permet de la republier à l'identique, c'est-à-dire si cette sauvegarde est validée par le membre (US-08) et que sa vérification réussit encore au moment de supprimer.

## Objectif
En tant que membre, je veux supprimer une annonce de mon dressing Vinted seulement quand sa sauvegarde est validée, afin de pouvoir la republier sans rien perdre.

## Comportement attendu
1. **Demande** : dans l'aperçu de la sauvegarde d'une annonce (`listing-backup.html`), le bouton « Supprimer de Vinted » est actif seulement si la sauvegarde actuelle est validée, que sa vérification réussit et qu'aucune suppression de l'annonce n'est confirmée ni dans le doute. Il ouvre une confirmation dans l'outil : titre, prix, nombre de photos gardées, date de validation, mention « irréversible ». « Supprimer définitivement » envoie la demande avec la date de la sauvegarde affichée.
2. **Contrôles hors navigateur** : la version demandée est la sauvegarde actuelle, elle est validée, la vérification d'US-08 refaite réussit, aucune suppression de l'annonce n'est `CONFIRMED` ni `UNCONFIRMED`. Sinon : refus, Chrome n'est pas ouvert.
3. **Verrou** : la suppression prend l'action navigateur `DELETING` (verrou d'US-03) ; une autre action en cours → refus.
4. **Contrôle sur Vinted** : dans un onglet du Chrome piloté, l'outil ouvre la page de l'annonce (règles de chargement d'AT-04-19), lit ses données `Product` comme le scan : active (`InStock`), titre et prix identiques à la sauvegarde. Il vérifie que le membre est connecté et que le bouton de suppression est présent. Un écart ou une erreur → refus, aucun clic.
5. **Dry-run** (US-10, actif par défaut) : l'outil s'arrête ici, sans aucun clic, et répond « dry-run : rien supprimé ». Rien n'est enregistré.
6. **Suppression** : l'outil clique sur le bouton de suppression, puis confirme dans la fenêtre de Vinted. Ce clic de confirmation est le point de non-retour : la tentative est enregistrée `UNCONFIRMED` juste avant lui.
7. **Preuve** : l'outil rouvre l'adresse de l'annonce. Vinted ne la montre plus → `CONFIRMED`, date de la preuve, l'annonce passe absente. Sinon, ou échec après le clic → reste `UNCONFIRMED`, problème « suppression non confirmée, vérifier sur Vinted », aucun nouvel essai.
8. **Levée du doute** : « Revérifier sur Vinted » rouvre seulement l'adresse de l'annonce, sans clic (verrou `DELETING`) : plus montrée → `CONFIRMED` ; visible et active → `NOT_DONE`, une nouvelle suppression redevient possible ; impossible de conclure → reste `UNCONFIRMED`.
9. **Fin** : l'onglet est fermé, le verrou libéré dans tous les cas. Sauvegarde, photos et validation ne sont jamais touchées.

## Contrat d'API
Contract first : `src/main/resources/static/openapi/vinted-republisher.yaml`. Les deux demandes sont synchrones : la réponse arrive une fois l'action finie (une à deux pages ouvertes).

| Méthode et chemin | operationId | Réponse |
| --- | --- | --- |
| `POST /api/listings/{listingId}/deletion` | `deleteListing` | corps `DeletionRequest` {`savedAt`} ; 200 `ListingBackupView` à jour (avec `deletion`), y compris quand la suppression reste non confirmée ; 400 sans `savedAt` ; 404 sans sauvegarde ; 409 `DeletionRefusal` (`BACKUP_REPLACED`, `NOT_VALIDATED`, `ALREADY_DELETED`, `DELETION_UNCONFIRMED`, `BROWSER_BUSY`) ; 422 `DeletionRefusal` (`VERIFICATION_FAILED` avec `verification`, `LISTING_CHANGED` avec `differences`) ; 502 `DeletionRefusal` (`BROWSER_UNAVAILABLE`, `VINTED_UNREACHABLE`, `SESSION_DISCONNECTED`, `LISTING_PAGE_UNREADABLE`) ; 500 `DeletionRefusal` (`DELETION_FAILED`) ; 403 si la demande vient d'un autre site |
| `POST /api/listings/{listingId}/deletion/recheck` | `recheckListingDeletion` | 200 `ListingBackupView` à jour ; 404 sans sauvegarde ; 409 `NO_UNCONFIRMED_DELETION` ou `BROWSER_BUSY` ; 502 comme ci-dessus (le doute reste) ; 403 si la demande vient d'un autre site |

- `ListingBackupView` gagne `deletion` (absent = aucune tentative) : `outcome` (`CONFIRMED`, `UNCONFIRMED`, `NOT_DONE`), `attemptedAt` (clic de confirmation), `settledAt` (preuve ou levée du doute, absent si `UNCONFIRMED`), `backupSavedAt`.
- `differences` (absent hors `LISTING_CHANGED`) : liste `ListingDifference` {`kind` : `NOT_ACTIVE`, `TITLE_CHANGED`, `PRICE_CHANGED` ; `savedTitle`, `readTitle` ou `savedPrice`, `readPrice`}.
- 400 aussi pour un corps JSON illisible ou un identifiant d'annonce non numérique.
- `BrowserAction` gagne `DELETING`.
- Le dry-run (réponse et réglage) est défini par US-10.

## Critères d'acceptation
- [ ] Une annonce ne se supprime que si sa sauvegarde actuelle est validée, sur la version vue par le membre, et que la vérification d'US-08 refaite réussit ; sinon Chrome n'est pas ouvert.
- [ ] Juste avant de cliquer, l'annonce est relue sur Vinted : non active, titre ou prix différent de la sauvegarde → refus sans aucun clic.
- [ ] Toute erreur avant le clic de confirmation dans Vinted refuse la suppression sans rien toucher sur Vinted.
- [ ] Une suppression n'est confirmée que si Vinted ne montre plus l'annonce ; elle passe alors absente.
- [ ] Après le clic de confirmation, tout doute est enregistré, sans nouvel essai, et bloque une nouvelle suppression jusqu'à « Revérifier sur Vinted ».
- [ ] Sauvegarde, photos et validation restent intactes après une suppression.
- [ ] Une seule action navigateur à la fois ; le verrou est toujours libéré.
- [ ] Les suppressions restent en base après un redémarrage.
- [ ] Un autre site ouvert dans le navigateur ne peut pas demander une suppression.
- [ ] Aucun test automatisé ne contacte le vrai site Vinted.

## Tests d'acceptation
### AT-09-01 – Suppression d'une annonce validée
```gherkin
Étant donné une annonce présente, active sur Vinted, dont la sauvegarde actuelle est validée et vérifiée
Et le dry-run désactivé
Quand je confirme « Supprimer définitivement » sur la version affichée
Alors l'outil relit l'annonce, clique sur la suppression puis confirme dans Vinted
Et Vinted ne montre plus l'annonce
Et la suppression est confirmée, avec sa date et la version de la sauvegarde
Et l'annonce est absente
Et sa sauvegarde, ses photos et sa validation sont intactes
```
Test : `DeleteListingAcceptanceTest.deletes_a_listing_whose_backup_is_validated()`

### AT-09-02 – Sauvegarde non validée
```gherkin
Étant donné une annonce dont la sauvegarde actuelle n'est pas validée
Quand je demande sa suppression
Alors elle est refusée (« sauvegarde non validée »)
Et Chrome n'est pas ouvert
```
Test : `DeleteListingAcceptanceTest.refuses_a_listing_whose_backup_is_not_validated()`

### AT-09-03 – Sauvegarde remplacée depuis l'aperçu
```gherkin
Étant donné l'aperçu ouvert sur la sauvegarde validée du 10/11
Et une nouvelle sauvegarde de l'annonce enregistrée le 11/11 depuis
Quand je confirme la suppression
Alors elle est refusée (« sauvegarde remplacée ») et Chrome n'est pas ouvert
```
Test : `DeleteListingAcceptanceTest.refuses_a_deletion_from_a_stale_preview()`

### AT-09-04 – Vérification en échec
```gherkin
Étant donné une annonce validée dont un fichier photo a disparu depuis
Quand je demande sa suppression
Alors elle est refusée avec l'échec de vérification « photo N manquante »
Et Chrome n'est pas ouvert
```
Test : `DeleteListingAcceptanceTest.refuses_a_listing_whose_verification_fails()`

### AT-09-05 – Annonce modifiée sur Vinted
```gherkin
Étant donné une annonce validée sauvegardée au prix 12,00 €
Et Vinted l'affiche maintenant à 10,00 €
Quand je demande sa suppression
Alors elle est refusée avec « prix changé : 12,00 € sauvegardé, 10,00 € lu »
Et aucun clic n'a été fait sur Vinted
```
Test : `DeleteListingAcceptanceTest.refuses_a_listing_changed_on_vinted()`

### AT-09-06 – Annonce vendue ou réservée entre-temps
```gherkin
Étant donné une annonce validée que Vinted ne montre plus comme active
Quand je demande sa suppression
Alors elle est refusée avec « annonce plus active sur Vinted »
Et aucun clic n'a été fait sur Vinted
```
Test : `DeleteListingAcceptanceTest.refuses_a_listing_no_longer_active()`

### AT-09-07 – Erreur avant le clic de confirmation
```gherkin
Étant donné une annonce validée
Et <cas>
Quand je demande sa suppression
Alors elle est refusée avec <problème>
Et aucun clic n'a été fait sur Vinted, aucune suppression n'est enregistrée
Et le verrou est libéré

Exemples :
| cas                                         | problème                      |
| la session n'est pas connectée              | session non connectée         |
| Chrome ne répond pas                        | navigateur indisponible       |
| Vinted répond par une erreur HTTP           | Vinted inaccessible           |
| le bouton de suppression est introuvable    | page de l'annonce illisible   |
| la base est indisponible                    | échec de la suppression       |
```
Test : `DeleteListingAcceptanceTest.failure_before_the_confirmation_click_touches_nothing()` (paramétré)

### AT-09-08 – Annonce toujours visible après la suppression
```gherkin
Étant donné une annonce validée
Et Vinted la montre encore après le clic de confirmation
Quand je demande sa suppression
Alors aucun nouvel essai n'est fait
Et la suppression est enregistrée « non confirmée »
Et l'aperçu affiche « suppression non confirmée, vérifier sur Vinted »
Et une nouvelle demande de suppression est refusée
```
Test : `DeleteListingAcceptanceTest.listing_still_shown_leaves_the_deletion_unconfirmed()`

### AT-09-09 – Échec après le clic de confirmation
```gherkin
Étant donné une annonce validée
Et Chrome cesse de répondre juste après le clic de confirmation dans Vinted
Quand je demande sa suppression
Alors la suppression est enregistrée « non confirmée », sans nouvel essai
Et le verrou est libéré
```
Test : `DeleteListingAcceptanceTest.failure_after_the_confirmation_click_leaves_the_deletion_unconfirmed()`

### AT-09-10 – Revérifier : suppression prouvée
```gherkin
Étant donné une suppression non confirmée
Et Vinted ne montre plus l'annonce
Quand je clique sur « Revérifier sur Vinted »
Alors aucun clic n'est fait sur la page de l'annonce
Et la suppression est confirmée et l'annonce est absente
```
Test : `DeleteListingAcceptanceTest.recheck_confirms_a_deletion_vinted_no_longer_shows()`

### AT-09-11 – Revérifier : suppression non faite
```gherkin
Étant donné une suppression non confirmée
Et Vinted montre toujours l'annonce, active
Quand je clique sur « Revérifier sur Vinted »
Alors la suppression est enregistrée « non faite »
Et une nouvelle demande de suppression redevient possible, avec tous les contrôles
```
Test : `DeleteListingAcceptanceTest.recheck_finds_the_listing_still_there()`

### AT-09-12 – Revérifier sans conclure
```gherkin
Étant donné une suppression non confirmée
Et Vinted est inaccessible
Quand je clique sur « Revérifier sur Vinted »
Alors la suppression reste non confirmée et le problème est affiché
```
Test : `DeleteListingAcceptanceTest.recheck_without_answer_keeps_the_doubt()`

### AT-09-13 – Scan après une suppression non confirmée
```gherkin
Étant donné une suppression non confirmée
Quand un scan ne trouve plus l'annonce
Alors l'annonce est marquée absente
Et la suppression reste non confirmée
```
Test : `DeleteListingAcceptanceTest.scan_does_not_settle_an_unconfirmed_deletion()`

### AT-09-14 – Annonce déjà supprimée
```gherkin
Étant donné une annonce dont la suppression est confirmée
Quand je demande de nouveau sa suppression
Alors elle est refusée (« déjà supprimée ») et Chrome n'est pas ouvert
```
Test : `DeleteListingAcceptanceTest.refuses_a_listing_already_deleted()`

### AT-09-15 – Une seule action navigateur à la fois
```gherkin
Étant donné un scan en cours
Quand je demande la suppression d'une annonce validée
Alors elle est refusée (« une action est en cours ») et le scan continue
```
Test : `DeleteListingAcceptanceTest.refuses_while_another_browser_action_runs()`

### AT-09-16 – Adaptateur sur une fausse page locale
```gherkin
Étant donné une fausse page d'annonce locale imitant celle de Vinted, avec son bouton de suppression et sa confirmation
Quand l'adaptateur navigateur supprime l'annonce
Alors il clique sur la suppression puis sur la confirmation, et rien d'autre
Et il rapporte la suppression prouvée quand la page renvoie ensuite 404, non confirmée sinon
Et il n'a rien cliqué si la page n'a pas de bouton de suppression
Et il ferme son onglet
```
Test : `PlaywrightListingDeleterTest` (`@Tag("browser")`, page locale)

### AT-09-17 – Conservation en base
```gherkin
Étant donné une suppression confirmée et une non confirmée
Quand l'application redémarre
Alors elles sont relues à l'identique
```
Test : `JpaListingDeletionRepositoryTest` (Testcontainers)

### AT-09-18 – Page de l'aperçu
```gherkin
Étant donné une annonce validée, une non validée, une suppression non confirmée et une confirmée
Quand j'ouvre leurs aperçus
Alors « Supprimer de Vinted » n'est actif que pour l'annonce validée
Et il ouvre une confirmation avec titre, prix, nombre de photos, date de validation et « irréversible »
Et l'aperçu affiche « suppression non confirmée, vérifier sur Vinted » avec « Revérifier sur Vinted », ou « supprimée de Vinted le … »
```
Test : `ListingBackupPageTest` (suppression)

### AT-09-19 – Demande venue d'un autre site
```gherkin
Étant donné une page d'un autre site ouverte dans le navigateur
Quand elle demande la suppression ou la revérification d'une annonce
Alors la demande est refusée (403), Chrome n'est pas ouvert et rien n'est enregistré
```
Test : `ListingDeletionApiControllerTest.deletion_requested_by_another_site_is_refused()`

### AT-09-20 – Observation de la page réelle
```gherkin
Étant donné ma session connectée et une annonce que je choisis
Quand j'ouvre avec Claude sa page dans le Chrome piloté, sans rien cliquer
Alors nous relevons le bouton de suppression, sans l'utiliser
```
Test : manuel (vrai compte, prérequis de l'adaptateur). La fenêtre de confirmation et la réponse après suppression (404 ou page « introuvable ») ne s'observent qu'en supprimant : elles sont relevées à AT-09-21.

### AT-09-21 – Première vraie suppression
```gherkin
Étant donné une annonce peu importante que je choisis, sauvegardée, validée, après des passages en dry-run réussis
Quand je désactive le dry-run et que je la supprime
Alors elle disparaît de mon dressing Vinted
Et l'outil affiche « supprimée de Vinted le … », sa sauvegarde reste intacte
```
Test : manuel (vrai compte Vinted, action irréversible ; uniquement avec l'accord explicite de l'utilisateur). Tout écart se traite par une mise à jour de cette spec.

## Cas limites et erreurs
- Annonce absente du dressing : la vérification d'US-08 échoue (`LISTING_ABSENT`), refus.
- Annonce réservée ou masquée sur Vinted : données `Product` non `InStock` → « annonce plus active sur Vinted », refus.
- Sauvegarde remplacée après une suppression confirmée : impossible en pratique (l'annonce absente n'est plus sauvegardée par US-05) ; la suppression garde la version sur laquelle elle s'appuyait.
- Application arrêtée entre l'enregistrement `UNCONFIRMED` et la preuve : la suppression reste non confirmée, « Revérifier sur Vinted » la règle.
- Base indisponible au moment d'enregistrer `UNCONFIRMED` : aucun clic de confirmation (« échec de la suppression »).
- Base indisponible au moment d'enregistrer la preuve : la suppression reste `UNCONFIRMED`, « Revérifier sur Vinted » la règle.
- Vinted redirige vers la connexion pendant la suppression : avant le clic de confirmation → « session non connectée » ; après → doute.
- Deux demandes simultanées pour la même annonce : la seconde est refusée par le verrou.

## Hors périmètre
- Mode dry-run et son réglage (US-10), livré avec cette US.
- Attente de 5 minutes et republication (US-11 à US-13).
- États d'une annonce en cours de traitement, traitement en parallèle, « Tout lancer » (US-14, US-15, US-19).
- Journal des actions, dont les passages en dry-run et les refus (US-23).
- Limites de cadence (US-27) : une suppression à la fois, à la main.

## Décisions
- Périmètre, décidé par l'utilisateur (2026-11-11) : une annonce à la fois, à la demande du membre, depuis l'aperçu de sa sauvegarde ; aucune suppression en série dans cette US (US-19).
- Mode de suppression, décidé par l'utilisateur (2026-11-11) : comme le membre, dans le Chrome piloté (page de l'annonce, bouton de suppression, confirmation de Vinted) ; aucune requête directe vers l'API de Vinted (règle d'US-04). Prérequis à l'adaptateur : observer une fois avec l'utilisateur, sur une annonce qu'il choisit, les boutons et la confirmation de Vinted.
- Contrôles avant suppression, décidé par l'utilisateur (2026-11-11), tous avant le moindre clic, l'un en échec = refus sans rien toucher sur Vinted : (1) validation de la version actuelle de la sauvegarde et vérification d'US-08 réussie ; (2) lecture de la page de l'annonce sur Vinted juste avant de supprimer : annonce toujours active (`InStock`), titre et prix identiques à la sauvegarde. Description et attributs ne sont pas comparés (absents de la page publique).
- Confirmation par le membre, décidé par l'utilisateur (2026-11-11) : « Supprimer de Vinted » dans l'aperçu ouvre une confirmation dans l'outil (titre, prix, nombre de photos gardées, date de validation, mention « irréversible ») ; second clic « Supprimer définitivement ». La demande porte la version de la sauvegarde vue (date de sauvegarde) : sauvegarde remplacée depuis → refus, comme `BackupReplaced` (US-08). Pas de saisie de l'identifiant.
- Preuve et enregistrement, décidé par l'utilisateur (2026-11-11) : après la confirmation dans Vinted, l'outil rouvre l'adresse de l'annonce ; supprimée seulement si Vinted ne la montre plus (page « introuvable » ou HTTP 404, réponse exacte à observer avec l'utilisateur). Une suppression est enregistrée (date, version de la sauvegarde) ; l'annonce passe absente ; sauvegarde, photos et validation restent intactes (US-12). Annonce encore visible, ou échec après le clic de confirmation dans Vinted : aucun nouvel essai, problème « suppression non confirmée, vérifier sur Vinted », doute enregistré pour bloquer toute suite sur cette annonce.
- Levée du doute, décidé par l'utilisateur (2026-11-11) : « Revérifier sur Vinted » dans l'aperçu rouvre seulement l'adresse de l'annonce, sans clic. Plus montrée → suppression prouvée, enregistrée comme au cas nominal ; toujours visible et active → suppression non faite, doute levé, le membre peut la relancer (avec tous les contrôles) ; impossible de conclure → le doute reste. Un scan qui ne trouve plus l'annonce ne lève pas le doute (vente ou retrait par le membre possibles).
- Erreurs avant le clic de confirmation dans Vinted, décidé par l'utilisateur (2026-11-11), règles reprises du scan et de la sauvegarde ; refus sans rien toucher sur Vinted, problème affiché dans l'aperçu : nouvelle action navigateur « suppression » sur le verrou d'US-03 (autre action en cours → 409) ; session non connectée ; navigateur indisponible ; Vinted inaccessible (HTTP, ou délai dépassé après le nouvel essai d'AT-04-19), sans nouvel essai ; page de l'annonce illisible ou bouton de suppression introuvable ; erreur inattendue ou base indisponible → « échec de la suppression » (logs), verrou toujours libéré ; demande d'un autre site → 403. Tout échec après le clic de confirmation dans Vinted relève du doute (« suppression non confirmée »).
- Sécurité d'abord, décidé par l'utilisateur (2026-11-11) : US-09 et US-10 sont livrées ensemble, et le dry-run est actif par défaut. Tant que le membre ne le désactive pas explicitement, la suppression fait tous les contrôles, ouvre la page, trouve le bouton, puis s'arrête avant le moindre clic de suppression (« dry-run : rien supprimé »). Le réglage est précisé dans US-10. Première vraie suppression : sur une annonce peu importante choisie par l'utilisateur.
- Persistance, décidé par l'utilisateur (2026-11-11) : table `listing_deletion`, une ligne par tentative arrivée au clic de confirmation dans Vinted : annonce, version de la sauvegarde (`backup_saved_at`), date du clic, issue `CONFIRMED` / `UNCONFIRMED` / `NOT_DONE`, date de la preuve ou de la levée du doute. Jamais supprimée. Au plus une `CONFIRMED` par annonce ; nouvelle tentative refusée tant qu'une `UNCONFIRMED` existe. Refus avant le clic et dry-run : affichés seulement (historique : US-23). Raison : la republication (US-12) ne partira que d'une suppression `CONFIRMED`.
- Proposés à la rédaction et validés par l'utilisateur avec la spec (2026-11-11) : demandes synchrones (réponse à la fin de l'action) ; `UNCONFIRMED` enregistré juste avant le clic de confirmation, pour qu'un arrêt brutal laisse un doute plutôt qu'aucune trace ; contrat d'API et codes de refus ci-dessus ; « Revérifier » prend aussi le verrou `DELETING`.

- Messages de l'aperçu, proposés à l'implémentation (2026-11-11) : un refus avant tout clic dit « Suppression refusée, rien n'a été supprimé : … » ; un échec de revérification dit « Revérification impossible, le doute reste : … » ; une erreur inattendue (500) dit « Échec de la suppression : vérifiez l'annonce sur Vinted » sans affirmer que rien n'a été supprimé. Après tout échec, l'aperçu est relu pour montrer l'état enregistré.

## Avancement (2026-11-11)
- Fait : domaine, cas d'usage, base (`008-create-listing-deletion`), API, aperçu ; AT-09-01 à AT-09-15, AT-09-17 à AT-09-19 verts avec un faux navigateur.
- Reste : l'adaptateur navigateur. En attendant, `UnobservedListingDeleter` refuse toute suppression et toute revérification (`LISTING_PAGE_UNREADABLE`) sans ouvrir Chrome. Il s'écrira après AT-09-20 (observation avec l'utilisateur), avec AT-09-16 ; puis AT-09-21 (manuel).

## Questions ouvertes
- Aucune bloquante pour valider la spec. AT-09-20 (observation de la page réelle) est un prérequis de l'adaptateur, pas de la spec.

## Modèle de domaine
- `ListingDeletion` (suppression d'une annonce, agrégat) : annonce, version de la sauvegarde, date du clic, `DeletionOutcome`, date de règlement ; transitions `UNCONFIRMED` → `CONFIRMED` ou `NOT_DONE`, aucune autre.
- `DeletionOutcome` (issue de la suppression) : `CONFIRMED` (prouvée), `UNCONFIRMED` (doute), `NOT_DONE` (doute levé, annonce toujours là).
- `DeletionRefusal` (refus de suppression) : raison, avec les échecs de vérification ou les écarts relus sur Vinted.
- `ListingDifference` (écart relu sur Vinted) : non active, titre changé, prix changé (valeurs sauvegardée et lue).
- `BrowserAction` gagne `DELETING`.
- Ports sortants : `ListingDeleter` (adaptateur navigateur : `delete` relit l'annonce, demande l'accord de la `DeletionVisit`, clique, la prévient juste avant le clic de confirmation, puis rapporte ce que Vinted montre ensuite ; `show` revérifie sans clic) ; `ListingDeletionRepository` (suppressions).
- `DeletionVisit` (visite de suppression, rappel du cas d'usage) : `agreesToDelete(annonce relue)` (refus sur écart, `false` en dry-run) et `aboutToConfirm()` (enregistre `UNCONFIRMED`).
- Cas d'usage : `DeleteListing`, `RecheckListingDeletion` ; `ShowListingDeletion` donne l'aperçu avec la dernière suppression et le mode (`DeletionReport`).
- Exceptions : `BackupNotValidated`, `ListingChanged`, `ListingAlreadyDeleted`, `DeletionUnconfirmed`, `NoUnconfirmedDeletion`, `ListingPageUnreadable`, `BrowserBusy`.
- Glossaire : ajouter `ListingDeletion`, `DeletionOutcome`, `DeletionRefusal`, `ListingDifference`, `ListingDeleter`, `ListingDeletionRepository` ; compléter `BrowserAction`, `ListingBackupView`.

## Tests unitaires et d'intégration attendus
- `domain` : `ListingDeletionTest` (transitions permises et refusées), contrôles avant suppression (validation, version, vérification, écarts relus).
- `application` : `DeleteListingAcceptanceTest` (AT-09-01 à AT-09-15), faux `FakeListingDeleter` et `InMemoryListingDeletionRepository`.
- `adapter/out/browser` : `PlaywrightListingDeleterTest` (page locale : clics, preuve 404, bouton absent, session déconnectée, onglet fermé).
- `adapter/out/persistence` : `JpaListingDeletionRepositoryTest` ; changeset Liquibase `listing_deletion`.
- `adapter/in/web` : `ListingDeletionApiControllerTest` (codes de refus, 403), `ListingBackupPageTest` (bouton, confirmation, doute, revérification).
