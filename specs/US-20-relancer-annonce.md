# US-20 – Bouton « Relancer » par annonce
**Statut** : implémentée
**Priorité** : MVP
**Dépend de** : US-09 (suppression), US-12 (republication), US-13 (vérification), US-14 (étapes), US-18 (liste), US-27 (cadence) ; prépare US-17, US-19

## Contexte
Chaque étape du traitement se lance aujourd'hui depuis la fiche de l'annonce, avec le bouton propre à l'étape (supprimer, revérifier, republier, vérifier sur Vinted). Depuis la liste (US-18), le membre voit l'étape de chaque annonce (US-14) mais doit ouvrir la fiche pour agir. « Relancer » fait avancer une annonce d'une étape, depuis la liste, sans choisir l'action : l'outil la déduit de l'étape.

C'est aussi la brique de « Tout lancer » (US-19) : la même règle « étape → action suivante » servira à enchaîner tout le dressing.

## Objectif
En tant que membre, je veux relancer une annonce depuis la liste, afin qu'elle passe à l'étape suivante sans ouvrir sa fiche ni choisir l'action.

## Comportement attendu
1. **Action déduite de l'étape** (US-14), une seule étape par clic :

| Étape | Action lancée | Cas d'usage existant |
|---|---|---|
| `TO_DELETE` (Prête à supprimer) | suppression de l'annonce | US-09 |
| `DELETION_TO_CHECK` (Suppression à vérifier) | revérification sur Vinted | US-09 |
| `TO_REPUBLISH` (À republier) | republication depuis la sauvegarde | US-12 |
| `REPUBLICATION_TO_CHECK` (Republication à vérifier) | vérification sur Vinted | US-13 |

   Les autres étapes ne se relancent pas : `TO_BACK_UP` (sauvegarde du dressing entier par « Sauvegarder »), `TO_VALIDATE` (décision du membre sur la fiche), `WAITING` (date de republication pas atteinte), `GONE` et `REPUBLISHED` (rien à faire).
2. **Mêmes garde-fous** : l'action passe par le cas d'usage existant, avec tous ses contrôles, le verrou du navigateur, les limites de cadence (US-27), le dry-run (US-10) et le journal (US-23). Relancer n'ajoute ni contrôle en moins ni entrée de journal en plus.
3. **Sauvegarde concernée** : la suppression et la republication portent sur la sauvegarde actuelle de l'annonce. La validation (US-08) ne vaut que pour la sauvegarde actuelle : une sauvegarde remplacée fait retomber l'annonce en `TO_VALIDATE`, qui ne se relance pas.
4. **Étape vue** : la demande porte l'étape que le membre a vue. Étape différente au moment de la demande → refus, rien n'est lancé ; la liste affiche la nouvelle étape.
5. **Synchrone** : la réponse arrive une fois l'action finie, avec l'action lancée, si c'était un dry-run, et l'annonce relue (nouvelle étape).
6. **Liste** (`wardrobe.html`) : bouton de relance sur la ligne des seules annonces dont l'étape se relance ; son libellé dit l'action qu'il lancera : « Supprimer de Vinted » (`TO_DELETE`), « Revérifier la suppression » (`DELETION_TO_CHECK`), « Republier sur Vinted » (`TO_REPUBLISH`), « Vérifier la republication » (`REPUBLICATION_TO_CHECK`). Pendant l'action, le bouton est désactivé. Ensuite, la ligne affiche la nouvelle étape et un message court :
   - action faite : « <action> faite » (ex. « Suppression faite ») ; dry-run : « Dry-run réussi, rien n'a été modifié sur Vinted » ;
   - annonce en attente : « Republication possible à partir du <date> » ; étape changée : la ligne prend l'étape actuelle, d'où repartira le prochain clic ;
   - refus : sa raison (« limite de cadence atteinte (à partir du <date>) », navigateur occupé, étape changée, « Relance refusée : voir la fiche » avec le lien de la fiche) ; le bouton reste proposé ;
   - échec : « Échec de la relance : vérifiez la fiche avant de relancer ».
7. La fiche ne change pas : elle garde ses boutons par étape.

## Contrat d'API
Contract first : `src/main/resources/static/openapi/vinted-republisher.yaml`.
- `POST /api/listings/{listingId}/relaunch`, tag `listing-relaunch`, `operationId: relaunchListing`.
- Corps `RelaunchRequest` : `stage` (`ListingStage`, obligatoire), l'étape vue par le membre.
- `200` `RelaunchReport` : `action` (`RelaunchAction` : `DELETION`, `DELETION_RECHECK`, `REPUBLICATION`, `REPUBLICATION_VERIFICATION`), `dryRun` (booléen), `listing` (`Listing`, relu après l'action).
- `400` corps invalide ; `403` autre site ; `404` annonce inconnue.
- `409` `RelaunchRefusal` : `reason` parmi
  - `STAGE_CHANGED` : l'étape n'est plus celle vue ; `stage` donne l'étape actuelle ;
  - `NOT_RELAUNCHABLE` : l'étape ne se relance pas ; `stage` donne l'étape, `allowedAt` la date de republication pour `WAITING` ;
  - `CADENCE_LIMIT_REACHED` : `allowedAt` (US-27) ;
  - `BROWSER_BUSY` ;
  - `ACTION_REFUSED` : refus du cas d'usage lancé (vérification de sauvegarde, annonce changée, champ impossible à remplir…) ; le détail est sur la fiche et dans le journal (US-23).
- `502` `RelaunchRefusal` : `BROWSER_UNAVAILABLE`, `VINTED_UNREACHABLE`, `SESSION_DISCONNECTED`, `VINTED_PAGE_UNREADABLE` (page de l'annonce, formulaire de mise en vente ou nouvelle annonce illisible).
- `500` `RelaunchRefusal` : `RELAUNCH_FAILED`.

## Critères d'acceptation
- [x] Chaque étape qui se relance lance son action, par le cas d'usage existant, sur la sauvegarde actuelle.
- [x] Les autres étapes sont refusées sans rien lancer ; `WAITING` donne la date de republication.
- [x] Une étape différente de celle vue est refusée sans rien lancer.
- [x] Le dry-run, la cadence, le verrou et le journal s'appliquent comme depuis la fiche.
- [x] Les refus et échecs des cas d'usage sont rendus selon le contrat.
- [x] La réponse donne l'action, le dry-run et l'annonce relue.
- [x] La liste montre le bouton de relance, libellé par son action, sur les seules étapes qui se relancent, puis la nouvelle étape et le message.

## Tests d'acceptation

### AT-20-01 – Relancer une annonce prête à supprimer
```gherkin
Étant donné une annonce présente dont la sauvegarde actuelle est validée (« Prête à supprimer »), en mode réel
Quand le membre la relance en ayant vu « Prête à supprimer »
Alors l'annonce est supprimée sur Vinted, pour sa sauvegarde actuelle
Et la réponse donne l'action « suppression » et l'étape « En attente avant republication »
```
Test : `RelaunchListingAcceptanceTest.relaunching_a_listing_to_delete_deletes_it()`

### AT-20-02 – Relancer une suppression à vérifier
```gherkin
Étant donné une annonce dont la suppression est non confirmée
Et Vinted ne montre plus l'annonce
Quand le membre la relance en ayant vu « Suppression à vérifier »
Alors la suppression est revérifiée et confirmée
Et la réponse donne l'action « revérification » et l'étape « En attente avant republication »
```
Test : `RelaunchListingAcceptanceTest.relaunching_a_deletion_to_check_rechecks_it()`

### AT-20-03 – Relancer une annonce à republier
```gherkin
Étant donné une annonce supprimée dont la date de republication est atteinte, en mode réel
Quand le membre la relance en ayant vu « À republier »
Alors elle est republiée depuis sa sauvegarde actuelle
Et la réponse donne l'action « republication » et l'étape « Republiée »
```
Test : `RelaunchListingAcceptanceTest.relaunching_a_listing_to_republish_republishes_it()`

### AT-20-04 – Relancer une republication à vérifier
```gherkin
Étant donné une annonce dont la dernière republication est en erreur
Et la nouvelle annonce est dans le dressing
Quand le membre la relance en ayant vu « Republication à vérifier »
Alors la republication est vérifiée et trouvée
Et la réponse donne l'action « vérification » et l'étape « Republiée »
```
Test : `RelaunchListingAcceptanceTest.relaunching_a_republication_to_check_verifies_it()`

### AT-20-05 – Dry-run
```gherkin
Étant donné une annonce « Prête à supprimer », suppression en dry-run
Quand le membre la relance
Alors rien n'est supprimé ni enregistré
Et la réponse indique un dry-run, l'étape reste « Prête à supprimer »
```
Test : `RelaunchListingAcceptanceTest.dry_run_changes_nothing()`

### AT-20-06 – Étape changée
```gherkin
Étant donné une annonce « Suppression à vérifier »
Quand le membre la relance en ayant vu « Prête à supprimer »
Alors la relance est refusée (STAGE_CHANGED, étape actuelle « Suppression à vérifier ») et rien n'est lancé
```
Test : `RelaunchListingAcceptanceTest.relaunch_is_refused_when_the_stage_changed()`

### AT-20-07 – Étapes qui ne se relancent pas
```gherkin
Étant donné une annonce « À sauvegarder », « À valider », « Absente de Vinted » ou « Republiée »
Quand le membre la relance en ayant vu cette étape
Alors la relance est refusée (NOT_RELAUNCHABLE) et rien n'est lancé
```
Test : `RelaunchListingAcceptanceTest.stages_without_next_action_are_not_relaunchable()`

### AT-20-08 – En attente : date de republication
```gherkin
Étant donné une annonce supprimée à 10:00, délai de 5 minutes, il est 10:02
Quand le membre la relance en ayant vu « En attente avant republication »
Alors la relance est refusée (NOT_RELAUNCHABLE, à partir de 10:05) et rien n'est lancé
```
Test : `RelaunchListingAcceptanceTest.waiting_listing_gives_its_republication_date()`

### AT-20-09 – Les garde-fous s'appliquent
```gherkin
Étant donné une annonce « Prête à supprimer » et une écriture sur Vinted il y a une minute (intervalle de 2 minutes)
Quand le membre la relance
Alors la relance est refusée (CADENCE_LIMIT_REACHED, avec la date), rien n'est touché sur Vinted ni enregistré
Et le journal garde le refus de la suppression, comme depuis la fiche
```
Test : `RelaunchListingAcceptanceTest.cadence_limit_applies()`

### AT-20-10 – Annonce inconnue
```gherkin
Étant donné un identifiant inconnu de l'outil
Quand le membre la relance
Alors la réponse est 404
```
Test : `RelaunchListingAcceptanceTest.unknown_listing_is_not_found()`

### AT-20-11 – Refus et échecs selon le contrat
```gherkin
Étant donné une relance refusée par l'étape, la cadence, le verrou ou le cas d'usage, ou qui échoue côté Vinted ou de façon inattendue
Quand la réponse est rendue
Alors le statut et la raison sont ceux du contrat (409, 502, 500), avec allowedAt, stage ou actionReason quand ils s'appliquent
```
Test : `ListingRelaunchApiControllerTest`

### AT-20-12 – Bouton de la liste
```gherkin
Étant donné une liste avec une annonce « Prête à supprimer » et une annonce « À valider »
Quand le membre l'affiche
Alors seule la première a un bouton « Supprimer de Vinted »
Quand il clique dessus
Alors le bouton est désactivé pendant l'action, puis la ligne montre la nouvelle étape et « Suppression faite »
```
Test : `WardrobePageTest.relaunches_a_listing_and_shows_its_new_stage()` et `WardrobePageTest.shows_a_dry_run_relaunch()`

### AT-20-13 – Refus affiché dans la liste
```gherkin
Étant donné une relance refusée pour cadence, ou par le cas d'usage
Quand la réponse arrive
Alors la ligne affiche « limite de cadence atteinte (à partir du <date>) », ou « refusée : voir la fiche » avec le lien de la fiche
```
Test : `WardrobePageTest.shows_a_cadence_refusal_with_its_date()`, `WardrobePageTest.shows_a_refusal_of_the_launched_action_with_a_link_to_the_listing()` , `WardrobePageTest.shows_when_a_waiting_listing_can_be_republished()`, `WardrobePageTest.shows_the_current_stage_when_it_changed_and_relaunches_from_it()` et `WardrobePageTest.shows_an_unexpected_relaunch_failure()`

## Cas limites et erreurs
- Deux relances en même temps : le verrou du navigateur en refuse une (`BROWSER_BUSY`), ou la seconde voit l'étape changée.
- Étape devenue `TO_REPUBLISH` entre l'affichage (`WAITING`) et le clic : refus `STAGE_CHANGED`, la ligne montre « À republier » ; un second clic republie.
- Une erreur après le clic décisif sur Vinted laisse l'annonce « à vérifier », comme depuis la fiche (US-09, US-12) : jamais de doublon.
- La vérification de republication ne conclut jamais seule « non publiée » (US-13) : la validation manuelle reste sur la fiche.

## Hors périmètre
- Enchaîner plusieurs étapes d'un clic, attendre la date de republication ou la fin d'une limite de cadence : US-19.
- Sauvegarder une seule annonce : la sauvegarde reste celle du dressing entier.
- Réessais automatiques et politique d'échec : US-17.
- Bouton « Relancer » sur la fiche.

## Décisions
- Décidé par Claude sur délégation de l'utilisateur (« fait le tout comme tu le sens », 2026-11-17) :
  - une seule étape par clic, synchrone ; l'enchaînement relève d'US-19 ;
  - action déduite de l'étape d'US-14, côté serveur (une seule règle, réutilisée par US-19), par les cas d'usage existants ;
  - les quatre étapes relançables du tableau ; la validation reste une décision du membre ;
  - la demande porte l'étape vue : protège d'un double clic ou d'une liste périmée ;
  - suppression et republication sur la sauvegarde actuelle, couverte par la validation ;
  - bouton dans la liste seulement ; la fiche garde ses boutons ;
  - un refus du cas d'usage est rendu `ACTION_REFUSED` sans recopier sa raison : le détail est sur la fiche et dans le journal, ce qui évite de dupliquer tous les schémas de refus ;
  - pages, formulaire ou nouvelle annonce illisibles regroupés en `VINTED_PAGE_UNREADABLE` (502).
- Libellé du bouton, demandé par l'utilisateur (2026-11-19) : « Relancer » ne disait pas assez ce qui allait se passer ; le bouton porte l'action déduite de l'étape (tableau du comportement 1).

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `RelaunchAction` (action de relance) : `DELETION`, `DELETION_RECHECK`, `REPUBLICATION`, `REPUBLICATION_VERIFICATION` ; `RelaunchAction.after(ListingStage)` donne l'action de l'étape, vide si elle ne se relance pas.
- `StageChanged` (étape changée) : refus, porte l'étape actuelle.
- `NotRelaunchable` (étape non relançable) : refus, porte l'étape et, pour `WAITING`, la date de republication.
- Application : `RelaunchListing` (relancer une annonce) lit l'étape (comme la fiche), choisit l'action et appelle `DeleteListing`, `RecheckListingDeletion`, `RepublishListing` ou `VerifyListingRepublication` ; rend `RelaunchReport` (action, dry-run, annonce relue).

## Tests unitaires et d'intégration attendus
- Domaine : `RelaunchActionTest` (action de chaque étape).
- Application : scénarios d'acceptation ci-dessus, avec les doublures existantes.
- Web : `ListingRelaunchApiControllerTest` (200, 404, 409 par raison, 502, 500, 400) ; page `WardrobePageTest`.
