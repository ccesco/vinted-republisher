# US-19 – Bouton « Tout lancer » (automatique)
**Statut** : implémentée
**Priorité** : MVP
**Dépend de** : US-14 (étapes), US-17 (conduite après un échec), US-20 (relance), US-27 (cadence) ; couvre la reprise d'US-16 ; prépare US-21

## Contexte
« Relancer » (US-20) fait avancer une annonce d'une étape, à la main. Pour tout le dressing, le membre devrait cliquer des centaines de fois et revenir après chaque délai de republication (US-11) ou limite de cadence (US-27). « Tout lancer » enchaîne les relances, annonce après annonce, attend quand il le faut, et s'arrête au premier problème du compte ou de Vinted (US-17).

## Objectif
En tant que membre, je veux lancer d'un clic le traitement de toutes mes annonces prêtes, afin qu'elles soient supprimées puis republiées sans que j'aie à revenir à chaque étape.

## Comportement attendu
1. **Lancement** : bouton « Tout lancer » sur la page du dressing. La série tourne en arrière-plan ; une seule à la fois (un second lancement pendant une série est ignoré). Rien n'est sauvegardé ni validé : seules les annonces que le membre a validées (US-08) peuvent être supprimées.
2. **Choix de la prochaine annonce**, relu à chaque tour dans l'inventaire (US-14), hors annonces écartées de la série, dans cet ordre d'étapes, puis par identifiant Vinted croissant :
   1. `REPUBLICATION_TO_CHECK`, puis `DELETION_TO_CHECK` (lectures qui débloquent une annonce) ;
   2. `TO_REPUBLISH` (remettre en vente au plus vite) ;
   3. `TO_DELETE` ;
   4. `WAITING` (sa relance est refusée avec sa date de republication : la série attend jusque-là).
   
   Aucune annonce candidate → la série est **terminée**.
3. **Chaque tour** relance l'annonce (US-20, avec l'étape lue), donc avec tous ses garde-fous : contrôles, dry-run, cadence, verrou du navigateur, journal.
4. **Après une relance réussie** : si l'étape de l'annonce n'a pas changé (dry-run, issue toujours « à vérifier »), l'annonce est **écartée** de la série : le membre tranchera. Sinon, tour suivant.
   Une annonce revenue à une étape déjà relancée dans la série (suppression non confirmée, revérifiée « non faite », de nouveau prête) est **écartée** sans être relancée : jamais deux écritures pour la même étape dans une série.
5. **Après un échec**, conduite d'US-17 :
   - `WAIT` avec date : la série attend jusqu'à cette date, puis reprend le tour suivant ;
   - `WAIT` sans date (navigateur occupé) : attente de 30 secondes ;
   - `SKIP_LISTING` : l'annonce est écartée de la série ;
   - `STOP_RUN` : la série s'**arrête**, avec sa raison.
6. **Aperçu** : état (`IDLE` aucune série depuis le démarrage, `RUNNING`, `WAITING` avec la date de fin d'attente, `FINISHED`, `STOPPED` avec la raison), date de début et de fin, annonce en cours, nombre de relances faites, annonces écartées.
7. **Page du dressing** : bouton « Tout lancer », masqué pendant une série ou un scan ou une sauvegarde ; ligne d'état (« Traitement en cours : annonce <id> », « En attente jusqu'au <date> », « Traitement terminé : <n> relances, <m> annonces écartées », « Traitement arrêté : <raison> ») ; la page suit la série et relit la liste tant qu'elle tourne.
8. **Reprise (US-16)** : l'étape de chaque annonce est en base et une écriture douteuse reste « à vérifier » : après un redémarrage, un nouveau « Tout lancer » reprend là où la série s'était arrêtée. La série ne redémarre jamais seule.

## Contrat d'API
- `GET /api/automatic-run` → `200` `AutomaticRunOverview`.
- `POST /api/automatic-run` → `202` `AutomaticRunOverview` (lancée, ou ignorée si une série tourne) ; `403` autre site.
- `AutomaticRunOverview` : `status` (`IDLE`, `RUNNING`, `WAITING`, `FINISHED`, `STOPPED`, obligatoire), `startedAt`, `endedAt`, `waitingUntil`, `currentListingId`, `relaunchCount` (obligatoire), `skippedListingIds` (obligatoire), `stopReason` (`SESSION_DISCONNECTED`, `BROWSER_UNAVAILABLE`, `VINTED_UNREACHABLE`, `VINTED_PAGE_UNREADABLE`, `RUN_FAILED`).

## Critères d'acceptation
- [x] La série relance les annonces dans l'ordre des étapes, puis des identifiants, jusqu'à ne plus en trouver.
- [x] Elle attend la date de republication ou la fin de la limite de cadence, puis reprend.
- [x] Une annonce dont l'étape ne change pas, ou refusée pour elle-même, est écartée ; la série continue.
- [x] Un problème du compte ou de Vinted arrête la série avec sa raison.
- [x] Une seule série à la fois ; elle ne redémarre jamais seule.
- [x] L'aperçu et la page montrent l'état, l'attente, les compteurs et la raison d'arrêt.

## Tests d'acceptation

### AT-19-01 – Supprimer, attendre, republier
```gherkin
Étant donné deux annonces validées 1 et 2, en mode réel, délai de republication 5 minutes, intervalle de cadence 2 minutes
Quand le membre clique sur « Tout lancer »
Alors l'annonce 1 est supprimée, puis l'annonce 2 deux minutes plus tard
Et la série attend la date de republication de l'annonce 1, la republie, puis republie l'annonce 2
Et la série est terminée avec 4 relances et aucune annonce écartée
```
Test : `AutomaticRunAcceptanceTest.deletes_waits_and_republishes_every_validated_listing()`

### AT-19-02 – Vérifier d'abord
```gherkin
Étant donné une annonce « Republication à vérifier » dont la nouvelle annonce est dans le dressing, et une annonce « Prête à supprimer »
Quand la série tourne
Alors la republication est vérifiée avant la suppression de l'autre annonce
```
Test : `AutomaticRunAcceptanceTest.checks_before_writing()`

### AT-19-03 – Dry-run : chaque annonce une fois
```gherkin
Étant donné deux annonces « Prête à supprimer », suppression en dry-run
Quand la série tourne
Alors chaque annonce passe une fois en dry-run, puis est écartée
Et la série est terminée, rien n'est supprimé
```
Test : `AutomaticRunAcceptanceTest.dry_run_goes_through_each_listing_once()`

### AT-19-04 – Issue toujours à vérifier : écartée
```gherkin
Étant donné une annonce « Republication à vérifier » dont la nouvelle annonce n'est pas dans le dressing
Quand la série la vérifie et qu'elle reste « à vérifier »
Alors elle est écartée et la série se termine
```
Test : `AutomaticRunAcceptanceTest.listing_still_to_check_is_skipped()`

### AT-19-05 – Refus propre à l'annonce : écartée
```gherkin
Étant donné une annonce « Prête à supprimer » modifiée sur Vinted depuis sa sauvegarde, et une autre annonce prête
Quand la série tourne
Alors la première est écartée (refus du cas d'usage), la seconde est supprimée
```
Test : `AutomaticRunAcceptanceTest.listing_refused_for_itself_is_skipped()`

### AT-19-06 – Limite de cadence : attendre
```gherkin
Étant donné une écriture sur Vinted il y a une minute, intervalle 2 minutes, et une annonce « Prête à supprimer »
Quand la série tourne
Alors elle attend une minute, puis supprime l'annonce
```
Test : `AutomaticRunAcceptanceTest.waits_for_the_cadence_limit()`

### AT-19-07 – Problème du compte : tout arrêter
```gherkin
Étant donné deux annonces prêtes et une session Vinted perdue
Quand la série tourne
Alors elle s'arrête à la première, raison SESSION_DISCONNECTED, la seconde n'est pas touchée
```
Test : `AutomaticRunAcceptanceTest.stops_on_a_lost_session()`

### AT-19-08 – Une seule série à la fois
```gherkin
Étant donné une série en cours
Quand le membre clique de nouveau sur « Tout lancer »
Alors le second lancement est ignoré
```
Test : `AutomaticRunAcceptanceTest.a_second_launch_is_ignored_while_running()`

### AT-19-11 – Retour à une étape déjà relancée : écartée
```gherkin
Étant donné une annonce « Prête à supprimer » que Vinted montre encore après la confirmation
Quand la série la supprime (non confirmée), puis la revérifie (non faite : de nouveau prête)
Alors elle n'est pas supprimée une seconde fois : elle est écartée et la série se termine
```
Test : `AutomaticRunAcceptanceTest.listing_back_at_a_stage_already_relaunched_is_skipped()`

### AT-19-12 – Navigateur occupé : attendre 30 secondes
```gherkin
Étant donné le navigateur pris par une action du membre, et une annonce « Prête à supprimer »
Quand la série tourne
Alors elle attend 30 secondes, puis supprime l'annonce
```
Test : `AutomaticRunAcceptanceTest.waits_for_a_busy_browser()`

### AT-19-09 – Contrat
```gherkin
Étant donné une série terminée, arrêtée ou en attente
Quand le membre lit l'aperçu ou lance une série
Alors l'API répond selon le contrat
```
Test : `AutomaticRunApiControllerTest`

### AT-19-10 – Page du dressing
```gherkin
Étant donné la page du dressing
Quand le membre clique sur « Tout lancer »
Alors la série est lancée, le bouton est masqué, et la page affiche l'état puis le résultat (terminée, en attente, arrêtée)
```
Test : `WardrobePageTest.launches_an_automatic_run_and_follows_it()`, `WardrobePageTest.shows_a_waiting_and_a_stopped_run()`

## Cas limites et erreurs
- Navigateur pris par une action manuelle : attente de 30 secondes, puis nouveau tour.
- Plafond de cadence par 24 heures atteint : la série attend jusqu'à la date permise, éventuellement des heures ; l'arrêt manuel relève d'US-21.
- Plusieurs annonces en attente : la série attend la date de celle d'identifiant le plus petit ; les autres devenues republiables entre-temps passent au tour suivant.
- Annonce disparue ou changée pendant la série : la relance la refuse (étape changée, annonce inconnue) → écartée.
- Redémarrage pendant une série : l'aperçu repart à `IDLE` ; rien n'est relancé seul.

## Hors périmètre
- Pause et arrêt manuel : US-21.
- Scan et sauvegarde automatiques, validation automatique.
- Plusieurs navigateurs : US-15.
- Reprise automatique au démarrage.

## Décisions
- Décidé par Claude sur délégation de l'utilisateur (« fait le tout comme tu le sens », 2026-11-17) :
  - la série n'enchaîne que des relances (US-20), donc aucun garde-fou contourné ;
  - ordre : vérifications, republications, suppressions, attentes ; puis identifiant croissant ;
  - écarter une annonce dont l'étape ne bouge pas (dry-run, doute persistant) : évite de boucler ;
  - attente de 30 secondes quand le navigateur est occupé ;
  - US-16 couverte ici : étapes déduites de la base, pas de redémarrage automatique (prudence anti-robot) ;
  - aperçu en mémoire, perdu au redémarrage : les actions elles-mêmes sont dans le journal (US-23).
  - relevé par la revue : une annonce n'est relancée qu'une fois par étape dans une série (sinon suppression, revérification « non faite » et nouvelle suppression en boucle sur le vrai compte) ; une tâche refusée par l'exécuteur arrête la série (RUN_FAILED).
- Premier lancement sur le vrai compte : seulement avec l'accord explicite de l'utilisateur (règle du projet), en dry-run d'abord.

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `AutomaticRun` (série automatique) : état (`RunStatus`), dates, annonce en cours, relances faites, annonces écartées, raison d'arrêt (`RunStopReason`).
- `RunWaiting` (attente de la série) : port sortant, attendre jusqu'à une date.
- Application : `LaunchAutomaticRun` (lance la série en arrière-plan, garde son aperçu), réutilise `ShowListingInventory`, `RelaunchListing`, `FailureHandling`.

## Tests unitaires et d'intégration attendus
- Application : `AutomaticRunAcceptanceTest` (exécution et attente simulées, horloge réglable).
- Web : `AutomaticRunApiControllerTest`, page `WardrobePageTest`.
- Adaptateur d'attente : `SleepingRunWaitingTest`.
- Domaine : `RunStopReasonTest`.
