# US-23 – Journal des actions

**Statut** : implémentée
**Priorité** : MVP
**Dépend de** : US-04 (scan), US-05 à US-08 (sauvegarde, validation), US-09 et US-10 (suppression, dry-run), US-12 (republication), US-18 et US-18b (liste et fiche)

## Contexte
L'outil agit sur le vrai compte Vinted. Aujourd'hui, seules les tentatives arrivées au point de non-retour sont gardées (suppressions, republications) ; les refus, les passages en dry-run et les erreurs ne sont affichés qu'une fois, puis perdus. US-09 et US-10 renvoient leur historique à cette US.

## Objectif
En tant que membre, je veux un journal de ce que l'outil a fait ou refusé de faire, afin de comprendre après coup ce qui s'est passé sur mon compte Vinted.

## Comportement attendu
1. **Écriture** : chaque action journalisée écrit une ligne au moment où elle se produit : date, action, issue, annonce concernée s'il y en a une, raison s'il y en a une (même code que la réponse de l'API : refus ou problème).
2. **Actions et issues** :

| Action | Moment | Issues possibles |
|---|---|---|
| Scan du dressing (`SCAN`) | lancement ; fin | lancé (`STARTED`) ; terminé (`SUCCEEDED`) ou en échec (`FAILED`, problème du scan) |
| Sauvegarde du dressing (`WARDROBE_BACKUP`) | lancement ; fin | comme le scan, problème de la sauvegarde |
| Validation d'une sauvegarde (`BACKUP_VALIDATION`) | réponse | validée (`SUCCEEDED`) ; refusée (`REFUSED`, `BACKUP_REPLACED` ou `VERIFICATION_FAILED`) |
| Suppression (`DELETION`) | réponse | confirmée (`SUCCEEDED`) ; non confirmée (`IN_DOUBT`) ; dry-run réussi (`DRY_RUN`) ; refusée (`REFUSED`, code d'un refus 409 ou 422 de `DeletionRefusal`, dont `CADENCE_LIMIT_REACHED` depuis US-27) ; en échec côté navigateur ou Vinted (`FAILED`, code d'une réponse 502 : `BROWSER_UNAVAILABLE`, `VINTED_UNREACHABLE`, `SESSION_DISCONNECTED`, `LISTING_PAGE_UNREADABLE`) ; échec inattendu (`FAILED`, `DELETION_FAILED`) |
| Revérification d'une suppression (`DELETION_RECHECK`) | réponse | confirmée (`SUCCEEDED`) ; non faite (`NOT_DONE`) ; doute qui reste (`IN_DOUBT`) ; refusée (`REFUSED`) ; en échec comme la suppression (`FAILED`, code 502 ou `DELETION_FAILED`) |
| Republication (`REPUBLICATION`) | réponse | réussie (`SUCCEEDED`) ; erreur de republication (`IN_DOUBT`) ; dry-run réussi (`DRY_RUN`) ; refusée (`REFUSED`, code d'un refus 409 ou 422 de `RepublicationRefusal`, dont `PHOTOS_NOT_VERIFIED` et `CADENCE_LIMIT_REACHED` depuis US-27) ; en échec côté navigateur ou Vinted (`FAILED`, code 502, dont `FORM_UNREADABLE`) ; échec inattendu (`FAILED`, `REPUBLICATION_FAILED`) |

   Ne sont pas journalisés : lancement de scan ou de sauvegarde ignoré parce qu'une action navigateur est déjà en cours (rien ne se passe, comme aujourd'hui), vérifications de session et connexions, lectures (pages, aperçus), demandes mal formées (400) ou venues d'un autre site (403).
3. **Échec de l'écriture** : si la ligne ne peut pas être écrite (base indisponible à ce moment), l'action continue et sa réponse ne change pas ; l'échec est écrit dans les logs de l'application.
4. **Page du journal** (`journal.html`, lien « Journal » depuis `wardrobe.html`) : lignes les plus récentes d'abord, 50 par page ; « Plus ancien » charge les 50 suivantes. Chaque ligne : date et heure, action, issue, raison en clair, et titre de l'annonce (lien vers sa fiche) s'il y en a une. Les refus, doutes et échecs sont mis en évidence. Aucun filtre.
5. **Fiche** (`listing.html`) : section « Journal » avec toutes les lignes de l'annonce, les plus récentes d'abord.
6. **Rétention** : les lignes ne sont jamais supprimées.

## Contrat d'API
Contract first : `src/main/resources/static/openapi/vinted-republisher.yaml`.

| Méthode et chemin | operationId | Réponse |
| --- | --- | --- |
| `GET /api/journal` | `showActionJournal` | paramètre `before` (facultatif, entier ≥ 1 : identifiant de la dernière ligne déjà vue) ; 200 `ActionJournal` {`entries` : 50 `JournalEntry` au plus, plus récentes d'abord ; `hasMore`} ; 400 si `before` est hors contrat |

- `JournalEntry` : `entryId` (entier, croissant dans l'ordre d'écriture), `occurredAt`, `action` (`JournaledAction`), `outcome` (`ActionOutcome`), `listingId` et `listingTitle` (absents pour scan et sauvegarde du dressing ; titre actuel de l'annonce), `reason` (code, absent sans raison).
- `JournaledAction` : `SCAN`, `WARDROBE_BACKUP`, `BACKUP_VALIDATION`, `DELETION`, `DELETION_RECHECK`, `REPUBLICATION`.
- `ActionOutcome` : `STARTED`, `SUCCEEDED`, `DRY_RUN`, `REFUSED`, `IN_DOUBT`, `NOT_DONE`, `FAILED`.
- `ListingDetail` (`GET /api/listings/{listingId}`, US-18b) gagne `journal` : liste de `JournalEntry` de l'annonce, plus récentes d'abord (vide si aucune).

## Critères d'acceptation
- [x] Chaque action du tableau écrit une ligne avec l'issue et la raison attendues.
- [x] Les actions non journalisées n'écrivent rien.
- [x] Un échec d'écriture du journal ne change ni l'action ni sa réponse, et apparaît dans les logs.
- [x] L'API donne les lignes par pages de 50, plus récentes d'abord, avec `hasMore` ; `before` donne la page suivante.
- [x] La fiche d'une annonce donne toutes ses lignes.
- [x] La page `journal.html` affiche les lignes en clair, met en évidence refus, doutes et échecs, et charge la suite avec « Plus ancien ».
- [x] La fiche affiche la section « Journal ».
- [x] Les lignes survivent à un redémarrage (base PostgreSQL).

## Tests d'acceptation

### AT-23-01 – Scan lancé puis terminé
```gherkin
Étant donné aucune action navigateur en cours
Quand le membre lance un scan qui se termine sans problème
Alors le journal contient « Scan du dressing : lancé » puis « Scan du dressing : terminé »
```
Test : `ScanWardrobeAcceptanceTest.scan_writes_its_start_and_its_end()`

### AT-23-02 – Sauvegarde du dressing en échec
```gherkin
Étant donné une session Vinted non connectée
Quand le membre lance la sauvegarde du dressing
Alors le journal contient « Sauvegarde du dressing : lancée » puis « en échec », raison « session non connectée »
```
Test : `BackUpWardrobeAcceptanceTest.failed_wardrobe_backup_writes_its_problem()`, `finished_wardrobe_backup_is_written()`

### AT-23-03 – Lancement ignoré
```gherkin
Étant donné une sauvegarde du dressing en cours
Quand le membre lance un scan
Alors le scan est ignoré comme aujourd'hui
Et le journal ne contient aucune ligne de scan
```
Test : `ScanWardrobeAcceptanceTest.ignored_launch_writes_nothing()`

### AT-23-04 – Validation et son refus
```gherkin
Étant donné l'annonce 101 sauvegardée, vérification réussie
Quand le membre valide la version vue
Alors le journal contient « Validation : validée » pour l'annonce 101
Quand il valide une version remplacée depuis
Alors le journal contient « Validation : refusée », raison « sauvegarde remplacée »
```
Test : `ValidateListingBackupAcceptanceTest.validation_and_its_refusal_are_written()`

### AT-23-05 – Suppression en dry-run
```gherkin
Étant donné le dry-run de suppression actif et l'annonce 101 validée
Quand le membre demande sa suppression
Alors le journal contient « Suppression : dry-run » pour l'annonce 101
```
Test : `DeleteListingAcceptanceTest.deletion_dry_run_is_written()`

### AT-23-06 – Suppression confirmée, non confirmée, refusée
```gherkin
Étant donné des suppressions réelles
Quand la suppression de 101 est prouvée, celle de 102 reste dans le doute et celle de 103 est refusée car non validée
Alors le journal contient « Suppression : confirmée » (101), « non confirmée » (102) et « refusée », raison « sauvegarde non validée » (103)
```
Test : `DeleteListingAcceptanceTest.confirmed_deletion_is_written()`, `unconfirmed_deletion_is_written()`, `refused_deletion_is_written_with_its_reason()` (une annonce par test, avec la mise en place existante)

### AT-23-07 – Revérification
```gherkin
Étant donné une suppression non confirmée de l'annonce 101
Quand le membre la revérifie et que l'annonce est toujours sur Vinted
Alors le journal contient « Revérification : non faite » pour l'annonce 101
```
Test : `DeleteListingAcceptanceTest.recheck_is_written()`

### AT-23-08 – Republication
```gherkin
Étant donné l'annonce 101 supprimée, date de republication atteinte
Quand sa republication réussit
Alors le journal contient « Republication : réussie » pour l'annonce 101
Et une republication trop tôt d'une autre annonce écrit « Republication : refusée », raison « trop tôt »
```
Test : `RepublishListingAcceptanceTest.republication_outcomes_are_written()`, `republication_dry_run_and_error_are_written()`

### AT-23-09 – Échec inattendu
```gherkin
Étant donné une erreur inattendue pendant la suppression de l'annonce 101
Quand le membre demande sa suppression
Alors le journal contient « Suppression : échec », raison « échec de la suppression »
```
Test : `DeleteListingAcceptanceTest.unexpected_failure_is_written()`

### AT-23-10 – Le journal en panne ne bloque rien
```gherkin
Étant donné un journal impossible à écrire
Quand le membre valide une sauvegarde
Alors la validation est enregistrée et la réponse est la même que sans panne
Et l'échec du journal est écrit dans les logs
```
Test : `ValidateListingBackupAcceptanceTest.journal_failure_changes_nothing()`, `JournalWriterTest.journal_down_changes_nothing_and_is_logged()`

### AT-23-11 – Pages de 50
```gherkin
Étant donné 120 lignes dans le journal
Quand la page demande le journal, puis la suite avec `before` = dernière ligne reçue, deux fois
Alors elle reçoit 50 lignes (hasMore vrai), puis 50 (hasMore vrai), puis 20 (hasMore faux), des plus récentes aux plus anciennes
```
Test : `ActionJournalApiControllerTest.pages_of_fifty_most_recent_first()`

### AT-23-12 – Journal dans la fiche
```gherkin
Étant donné des lignes pour les annonces 101 et 102
Quand la page demande la fiche de 101
Alors `journal` contient seulement les lignes de 101, plus récentes d'abord
```
Test : `ShowListingDetailAcceptanceTest.detail_shows_the_journal_of_the_listing()`

### AT-23-13 – Page du journal
```gherkin
Étant donné un scan terminé et une suppression refusée de « Pull rayé »
Quand le membre ouvre journal.html
Alors il voit la suppression refusée en premier, mise en évidence, avec la raison en clair et le lien vers la fiche de « Pull rayé »
Et le scan terminé, sans annonce
Et « Plus ancien » charge la page suivante quand il y en a une
```
Test : `ActionJournalPageTest.shows_entries_and_loads_older_ones()`

### AT-23-14 – Section de la fiche
```gherkin
Étant donné une annonce avec une validation et une suppression en dry-run
Quand le membre ouvre sa fiche
Alors la section « Journal » montre ces deux lignes
```
Test : `ListingDetailPageTest.shows_the_journal_of_the_listing()`

### AT-23-15 – Persistance
```gherkin
Étant donné des lignes écrites dans le journal
Quand l'outil redémarre
Alors elles sont relues à l'identique, dans l'ordre d'écriture
```
Test : `JpaActionJournalTest.entries_survive_restart()` (`@Tag("docker")`)

## Cas limites et erreurs
- Base indisponible à la lecture : `GET /api/journal` répond 500, la page affiche « Journal indisponible. ».
- `before` inconnu ou plus ancien que tout : liste vide, `hasMore` faux.
- Annonce absente de l'outil (impossible aujourd'hui) : la ligne garde l'identifiant, sans titre.
- Demande sur une annonce jamais sauvegardée (404) : rien n'est journalisé, comme une demande mal formée.
- La section « Journal » de la fiche montre l'état à l'ouverture de la page ; elle se met à jour au rechargement.
- Une action en dry-run qui échoue à un contrôle est un refus (`REFUSED`), pas un dry-run.

## Hors périmètre
- Vérifications de session et connexions ; une ligne par annonce pendant le scan ou la sauvegarde du dressing.
- Filtres, recherche, export du journal.
- Purge ou rotation (lignes gardées pour toujours).
- Journal des changements d'étape (US-14) : l'étape se déduit, seules les actions sont journalisées.
- Actions automatiques (US-19 à US-21) : elles réutiliseront ce journal.
- Vérification de la republication (US-13, fusionnée pendant cette US) : pas journalisée ici, hors du périmètre validé ; ajoutée par US-45 (`REPUBLICATION_VERIFICATION`, vérification et validation manuelle).

## Décisions
- Source, décidé par l'utilisateur (2026-11-15) : table dédiée ; chaque action écrit sa ligne au moment où elle se produit, refus, dry-runs et erreurs compris.
- Actions journalisées, décidé par l'utilisateur (2026-11-15) : suppressions et revérifications, republications, validations (et leurs refus), lancements et fins de scan et de sauvegarde du dressing (une ligne par lancement et une par fin, pas une par annonce). Les vérifications de session et connexions ne sont pas journalisées.
- Rétention, décidé par l'utilisateur (2026-11-15) : les lignes ne sont jamais supprimées.
- Affichage, décidé par l'utilisateur (2026-11-15) : page `journal.html` (lien depuis `wardrobe.html`) et section « Journal » dans la fiche, limitée à l'annonce.
- Parcours, décidé par l'utilisateur (2026-11-15) : plus récentes d'abord, pages de 50, « Plus ancien » pour la suite ; aucun filtre (le filtre par annonce, c'est la fiche).
- Échec d'écriture, décidé par l'utilisateur (2026-11-15) : l'action continue et sa réponse ne change pas ; l'échec est écrit dans les logs.
- Proposés à la rédaction, validés par l'utilisateur avec la spec (« fait comme tu recommandes », 2026-11-15) : issues et codes de raison du tableau (mêmes codes que l'API) ; pagination par identifiant de ligne (`before`) ; journal de la fiche porté par `ListingDetail` (pas de requête en plus) ; titre actuel de l'annonce lu à l'affichage, pas copié dans la ligne.
- Après la revue, proposé par Claude sur délégation de l'utilisateur (« fait comme tu recommandes », 2026-11-16) : un problème côté navigateur ou Vinted (réponse 502 de l'API) est un échec (`FAILED`) avec son code, comme pour le scan et la sauvegarde du dressing, et non un refus ; seuls les refus 409 et 422 sont `REFUSED`. Chaque raison garde le code exact de la réponse de l'API de l'action (`PHOTOS_NOT_VERIFIED` pour la republication, `VERIFICATION_FAILED` ailleurs).

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `ActionJournal` | Journal des actions | Port sortant : écrit une ligne, lit une page (plus récentes d'abord) et les lignes d'une annonce.
- `ActionRecord` | Action enregistrée | Ce qu'une ligne contient : date, action, issue, annonce et raison éventuelles.
- `JournalWriter` | Écriture du journal | Service applicatif des cas d'usage : écrit la ligne de l'action (issue de la réponse, refus avec son code, échec) sans jamais faire échouer l'action.
- `JournalEntry` | Ligne du journal | Date, action, issue, annonce et raison éventuelles ; jamais modifiée ni supprimée.
- `JournaledAction` | Action journalisée | Scan, sauvegarde du dressing, validation, suppression, revérification, republication.
- `ActionOutcome` | Issue d'une action | Lancée, réussie, dry-run, refusée, dans le doute, non faite, en échec.
- Écriture depuis les cas d'usage `ScanWardrobe`, `BackUpWardrobe`, `ValidateListingBackup`, `DeleteListing`, `RecheckListingDeletion`, `RepublishListing`, par `JournalWriter`, sans faire échouer l'action. Les tests d'acceptation du journal sont dans les classes d'acceptation de chaque action, qui en ont déjà la mise en place (choix d'implémentation, 2026-11-16).
- Cas d'usage de lecture : `ShowActionJournal` ; `ShowListingDetail` lit en plus les lignes de l'annonce.
- Persistance (Liquibase) : table `action_journal_entry` (identifiant croissant, date, action, issue, annonce facultative, raison facultative), index sur l'annonce.

## Tests unitaires et d'intégration attendus
- Domaine : `JournalEntry` (champs obligatoires, annonce et raison facultatives).
- Application : `ActionJournalAcceptanceTest` (AT-23-01 à 10) avec un faux journal en mémoire, y compris en panne ; `ShowActionJournal` (pages).
- Adaptateurs : `JpaActionJournalTest` (`@Tag("docker")`, AT-23-15, pages et lignes d'une annonce) ; `ActionJournalApiControllerTest` (AT-23-11, 400) ; pages `ActionJournalPageTest` et `ListingDetailPageTest` (`@Tag("browser")`).
