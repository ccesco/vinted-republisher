# US-39 – Page des annonces en échec
**Statut** : implémentée
**Priorité** : MVP
**Dépend de** : US-23 (journal), US-14 (étapes d'une annonce), US-20 (relance), US-19 (série automatique), US-17 (gestion des échecs)

## Contexte
À la fin d'une série « Tout lancer », la page du dressing affiche « Traitement terminé : <n> relances, <m> annonces écartées » sans dire lesquelles ni pourquoi. Aujourd'hui :
- l'API de la série renvoie `skippedListingIds`, mais la page n'en montre que le nombre ;
- la raison de la mise à l'écart n'est pas gardée ; elle se devine par le journal de l'annonce ;
- la série vit en mémoire et repart à `IDLE` au redémarrage du back ;
- le journal (US-23) met en évidence refus, doutes et échecs, mais sans filtre, mêlés aux succès.

## Objectif
En tant que membre, je veux une page qui récapitule les annonces en échec, avec la cause de chacune et de quoi les relancer, afin de savoir lesquelles demandent mon intervention et pourquoi, sans ouvrir chaque fiche.

## Comportement attendu
1. **Annonce en échec** (`FailedListing`) : annonce dont la **dernière ligne du journal** qui la concerne (US-23) a pour issue :
   - un refus (`REFUSED`), sauf les refus qui demandent seulement d'attendre : `CADENCE_LIMIT_REACHED`, `TOO_EARLY`, `BROWSER_BUSY` ;
   - un doute (`IN_DOUBT`) ;
   - une revérification « non faite » (`NOT_DONE`) ;
   - un échec (`FAILED`), y compris un problème du compte ou de Vinted (session non connectée, navigateur indisponible, Vinted inaccessible, page illisible).

   Un refus d'attente ne fait rien sur Vinted : il est ignoré, il ne crée ni n'efface un échec. Une annonce sort de la page dès qu'une ligne plus récente la concernant, hors refus d'attente, n'est pas un échec (réussite, dry-run). Une annonce à l'étape « Absente de Vinted » n'y figure pas : il n'y a plus rien à faire (US-45 : ni une annonce « Republiée » ; `MANUAL_REPUBLICATION_IN_DRY_RUN` est aussi un refus d'attente).
2. **Cause** : la raison de cette dernière ligne, en clair, avec les mêmes libellés que le journal ; pour un `NOT_DONE` sans raison, « suppression non faite ».
3. **Page « Échecs »** (route `/failures`) : lien « Échecs » dans la navigation. Une ligne par annonce en échec, la plus récente d'abord : annonce (titre, lien vers sa fiche), étape actuelle, action, issue, cause, date de la ligne. Sans annonce en échec : « Aucune annonce en échec. » (remplacé par US-45 : trois sections, vides avec « Aucune annonce. »). Toutes les annonces en une fois, sans pagination ni filtre.
4. **Relancer** : bouton « Relancer » sur les lignes dont l'étape se relance (comme dans la liste, US-20), même appel (`POST /api/listings/{listingId}/relaunch`) et mêmes messages, affichés au-dessus du tableau sous la forme « <titre> : <message> ». Après la relance, la page relit les annonces en échec : une annonce relancée avec succès disparaît.
5. **Depuis la page du dressing** : dans « Traitement terminé : <n> relances, <m> annonces écartées », « <m> annonces écartées » (quand il y en a au moins une) est un lien vers la page « Échecs ». « Traitement arrêté : <raison> » ne compte pas les annonces écartées et ne change pas.
6. Rien n'est lu sur Vinted : seuls le journal et les faits en base sont lus.

## Contrat d'API
| Requête | Opération | Réponse |
| --- | --- | --- |
| `GET /api/failed-listings` | `showFailedListings` (tag `failed-listings`) | 200 `FailedListings` {`listings` : liste de `FailedListing`, plus récente d'abord} |

- `FailedListing` : `listingId`, `listingTitle`, `stage` (`ListingStage`), `failure` (`JournalEntry` existant : la dernière ligne de l'annonce, qui porte action, issue, raison et date).

## Critères d'acceptation
- [x] Une annonce dont la dernière ligne du journal est un refus, un doute, une revérification non faite ou un échec est en échec, avec cette ligne pour cause.
- [x] Une réussite ou un dry-run plus récent fait sortir l'annonce de la page ; un refus d'attente (cadence, trop tôt, navigateur occupé) est ignoré.
- [x] Une annonce absente de Vinted n'est pas en échec.
- [x] Les annonces en échec sont triées de la plus récente à la plus ancienne.
- [x] La page « Échecs » affiche titre (lien vers la fiche), étape, action, issue, cause en clair et date ; un message quand il n'y en a aucune.
- [x] Le bouton « Relancer » n'apparaît que pour une étape qui se relance ; après la relance, la page relit la liste.
- [x] « annonces écartées » de la ligne d'état de la série est un lien vers la page « Échecs ».

## Tests d'acceptation

### AT-39-01 – Dernière action en échec
```gherkin
Étant donné une annonce dont la suppression a été refusée (sauvegarde remplacée)
Et une annonce dont la republication est en doute
Et une annonce dont la revérification a conclu « non faite »
Et une annonce dont la suppression a échoué, session non connectée
Et une annonce supprimée avec succès
Quand le membre demande les annonces en échec
Alors les quatre premières sont renvoyées, chacune avec sa dernière ligne du journal
Et l'annonce supprimée avec succès n'y est pas
```
Test : `ShowFailedListingsAcceptanceTest.lists_listings_whose_last_action_failed()`

### AT-39-02 – Une action plus récente efface l'échec
```gherkin
Étant donné une annonce dont la suppression a échoué, puis réussi
Et une annonce dont la suppression a échoué, puis été refusée pour cadence
Et une annonce dont la suppression a été refusée pour cadence seulement
Quand le membre demande les annonces en échec
Alors seule la deuxième est renvoyée, avec l'échec de sa suppression pour cause
```
Test : `ShowFailedListingsAcceptanceTest.later_success_clears_failure_and_waiting_refusal_does_not()`

### AT-39-03 – Annonce absente de Vinted
```gherkin
Étant donné une annonce dont la republication a échoué, puis vue absente de Vinted
Quand le membre demande les annonces en échec
Alors elle n'est pas renvoyée
```
Test : `ShowFailedListingsAcceptanceTest.ignores_listings_absent_from_vinted()`

### AT-39-04 – Ordre
```gherkin
Étant donné deux annonces en échec, la première le 17/11/2026, la seconde le 18/11/2026
Quand le membre demande les annonces en échec
Alors la seconde vient d'abord
```
Test : `ShowFailedListingsAcceptanceTest.most_recent_failure_first()`

### AT-39-05 – Contrat
```gherkin
Quand le membre demande GET /api/failed-listings
Alors l'API renvoie chaque annonce en échec avec titre, étape et ligne du journal
```
Test : `FailedListingsApiControllerTest`

### AT-39-06 – Page « Échecs »
```gherkin
Étant donné une annonce « Pull bleu » prête à supprimer, dont la revérification a conclu « non faite » le 18/11/2026
Et une annonce « Robe » à sauvegarder, dont la validation a été refusée (sauvegarde remplacée)
Quand le membre ouvre la page « Échecs »
Alors il voit « Pull bleu », lien vers sa fiche, « Prête à supprimer », la cause « suppression non faite » et la date
Et « Pull bleu » a un bouton « Relancer », « Robe » n'en a pas
Quand il relance « Pull bleu » et que la suppression réussit
Alors la page relit la liste, qui ne contient plus « Pull bleu »
Et sans annonce en échec, la page affiche « Aucune annonce en échec. »
```
Test : `FailedListingsPageTest.shows_failures_with_cause_and_relaunches()`

### AT-39-07 – Lien depuis la ligne d'état de la série
```gherkin
Étant donné une série terminée avec 4 relances et 2 annonces écartées
Quand le membre regarde la page du dressing
Alors « 2 annonces écartées » est un lien vers la page « Échecs »
```
Test : `WardrobePageTest.skipped_listings_link_to_failures_page()`

## Cas limites et erreurs
- Annonce sans aucune ligne au journal : pas en échec.
- Ligne de journal dont l'annonce n'est plus connue de l'outil (jamais attendu) : ignorée.
- Annonce écartée par la série pour un dry-run : pas un échec, absente de la page (le compteur « écartées » peut donc dépasser le nombre d'annonces de la page).
- Relance refusée depuis la page : message de refus comme dans la liste ; la ligne reste.
- Titre venant de Vinted : jamais d'`innerHTML`.

## Hors périmètre
- Garder en base la liste des annonces écartées par une série.
- Filtre, tri ou pagination de la page.
- Compteur des échecs dans la navigation.
- Relance de toutes les annonces en échec d'un coup (c'est « Tout lancer »).

## Décisions
- Champ « Cause », demandé par l'utilisateur (2026-11-18) : chaque annonce de la page montre la cause de son échec.
- Périmètre, décidé par l'utilisateur (2026-11-18) : une annonce est en échec si la dernière ligne du journal qui la concerne est un refus, un doute ou un échec, sauf un refus qui demande seulement d'attendre. Lu dans le journal : survit au redémarrage, couvre la série et les relances manuelles, sans nouvelle table.
- Emplacement, décidé par l'utilisateur (2026-11-18) : page dédiée « Échecs », lien dans la navigation et depuis « <m> annonces écartées » de la page du dressing. La liste des annonces ne change pas.
- Relance, demandée par l'utilisateur (2026-11-18) : bouton « Relancer » comme dans la liste (US-20).
- Revérification « non faite », décidé par l'utilisateur (2026-11-18) : c'est un échec, cause « suppression non faite ».
- Échec dû au compte ou à Vinted, décidé par l'utilisateur (2026-11-18) : l'annonce est en échec, avec sa cause ; une fois le problème réglé, « Relancer » suffit.
- Refus d'attente après un échec, décidé par l'utilisateur (2026-11-18, contradiction relevée pendant l'implémentation entre le comportement 1 et AT-39-02) : il est ignoré, l'échec précédent reste affiché avec sa cause.
- Proposé par Claude, validé avec la spec par l'utilisateur (2026-11-18) : refus d'attente exclus = `CADENCE_LIMIT_REACHED`, `TOO_EARLY`, `BROWSER_BUSY` (conduite `WAIT` d'US-17) ; annonces « Absente de Vinted » exclues ; tri de la plus récente à la plus ancienne, sans pagination ; réutilisation du schéma `JournalEntry` pour la cause.

## Questions ouvertes
Aucune.

## Modèle de domaine
- `FailedListing` (annonce en échec) : nouveau terme du glossaire ; annonce, son étape actuelle et la dernière ligne du journal qui la concerne, quand cette ligne est un échec.
- `ShowFailedListings` (cas d'usage) : lit les dernières lignes du journal par annonce et l'inventaire (étape) ; `JournalEntry`, `ActionOutcome`, `ListingStage` existants.
- Règle « cette ligne est-elle un échec ? » portée par le domaine du journal (issue et raison).
- Port du journal : nouvelle lecture « dernière ligne de chaque annonce ».

## Tests unitaires et d'intégration attendus
- Domaine : règle d'échec d'une ligne (chaque issue, refus d'attente exclus).
- Application : `ShowFailedListings` (dernière ligne seulement, annonce absente de Vinted exclue, ordre).
- Adaptateur persistance : requête « dernière ligne par annonce » sur PostgreSQL (`@Tag("docker")`), refus d'attente et refus sans raison compris. Elle lit toutes les lignes qui nomment une annonce (journal d'un seul membre, petit) : pas de contrôle `EXPLAIN`, à revoir si le journal grossit.
- Adaptateur web : `FailedListingsApiControllerTest`.
- Interface : Vitest pour la cause en clair ; `FailedListingsPageTest` (Playwright, API simulée).
