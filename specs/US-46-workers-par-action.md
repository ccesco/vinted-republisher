# US-46 – Workers attitrés par type d'action
**Statut** : implémentée
**Priorité** : V2
**Dépend de** : US-15 (traitement en parallèle, tournées remplacées), US-19 (série automatique), US-20 (relance), US-21 (pause et stop), US-27 (cadence, modifiée), US-41 (file d'attente, modifiée)

## Contexte
Avec un parallélisme de N (US-15), la série avance par **tournées** : elle prend jusqu'à N annonces, les relance ensemble, attend la fin de **toutes**, puis passe à la tournée suivante. La relance la plus lente bloque les autres places ; une tournée ne porte qu'une écriture réelle, et une écriture que la cadence ne permet pas encore clôt la tournée. De plus, la cadence (US-27) compte l'intervalle depuis la dernière écriture de n'importe quel type : suppressions et republications se suivent, jamais ensemble.

Donner à chaque place un type d'action attitré, avec une cadence propre à chaque type d'écriture, permet à chaque place d'avancer à son rythme : le worker suppression retire les annonces, le worker republication les remet en vente, le worker vérification contrôle, sans s'attendre.

## Objectif
En tant que membre, je veux que la série mène en parallèle suppressions, republications et vérifications, chacune par son worker attitré qui avance à son rythme, et suivre ce que fait chaque worker, afin que la série aille plus vite sans dépasser les limites de cadence de chaque type d'écriture.

## Comportement attendu
1. **Mode de parallélisme** : `parallelism.mode` (application.yml) vaut `SEQUENTIAL` (**par défaut**) ou `WORKERS`. Il remplace `parallelism.listing-actions` (US-15), supprimé. Valeur absente, illisible ou inconnue → l'application ne démarre pas.
   - `SEQUENTIAL` : une action d'annonce à la fois ; la série suit US-19, inchangée.
   - `WORKERS` : trois workers attitrés (point 2). Les **tournées** d'US-15 disparaissent.
2. **Workers attitrés** (mode `WORKERS`), chacun dans son onglet du Chrome normal piloté par CDP (US-15 point 2), une annonce à la fois :
   - **worker republication** (`REPUBLICATION`) : annonces `TO_REPUBLISH`, et `WAITING` (attend leur date de republication) ;
   - **worker suppression** (`DELETION`) : annonces `DELETION_TO_CHECK` (revérification) puis `TO_DELETE` (suppression) ;
   - **worker vérification** (`VERIFICATION`) : annonces `REPUBLICATION_TO_CHECK`, sinon `DELETION_TO_CHECK` (entraide limitée aux lectures).

   Chaque worker choisit dans l'inventaire (US-14), relu à chaque choix, la première annonce de ses étapes, dans l'ordre ci-dessus puis par identifiant Vinted croissant, en laissant les annonces écartées de la série, celles dont l'étape a déjà été relancée dans la série (US-19) et **celles qu'un autre worker relance** : jamais deux workers sur la même annonce.
3. **Relance** : chaque worker relance son annonce comme la série d'US-19 (US-20, avec tous ses garde-fous : contrôles, dry-run, cadence, verrou du navigateur, journal) et applique son résultat : annonce écartée (étape inchangée ou déjà relancée), relance comptée, conduite d'US-17. Puis il choisit l'annonce suivante aussitôt.
4. **Cadence propre à chaque type d'écriture** (modifie US-27, dans les deux modes) : une suppression n'est possible qu'à partir de la dernière suppression plus `cadence.deletion-interval` ; une republication, à partir de la dernière republication plus `cadence.republication-interval`. Une écriture d'un type ne retarde plus celle de l'autre. Plafonds sur 24 heures glissantes inchangés (déjà propres à chaque type). La réservation d'US-15 (écriture qui a passé le contrôle, comptée jusqu'à sa fin) vaut pour son type seulement. Avant une écriture réelle, le worker demande à la cadence la date permise pour son type, sans rien réserver (US-19 point 3) : date future → **ce worker seul** attend jusque-là, puis refait son choix.
5. **Attentes par worker** : une attente (cadence de son type, date de republication, navigateur occupé : 30 secondes, `WAIT` d'US-17) ne bloque que le worker concerné ; les autres continuent.
6. **Worker sans travail** : aucune annonce pour lui → il est **inactif** ; il refait son choix dès qu'une action d'un autre worker se termine (l'inventaire a pu changer, par exemple une republication à vérifier).
7. **Fin de la série** : terminée (`FINISHED`) quand les trois workers sont inactifs en même temps, aucun n'étant en action ni en attente.
8. **État global de la série** : `RUNNING` tant qu'un worker est en action ; `WAITING` quand aucun n'est en action et qu'au moins un attend, avec la date d'attente la plus proche ; `PAUSED`, `FINISHED`, `STOPPED` comme en US-19 et US-21.
9. **Pause et arrêt (US-21)** : chaque worker finit son action en cours (jamais coupée), puis n'en prend plus ; une attente est interrompue. La série est en pause, ou arrêtée, quand plus aucun worker n'est en action. La reprise relance les trois workers.
10. **Problème du compte ou de Vinted** (`STOP_RUN`, US-17) chez un worker, ou échec inattendu (`RUN_FAILED`) : les autres workers finissent leur action en cours et n'en prennent plus ; la série s'arrête avec la raison du premier.
11. **Verrou du navigateur** (US-15 point 3) : en mode `WORKERS`, il admet au plus **3** actions d'annonce en même temps, jamais deux sur la même annonce ; les autres actions navigateur restent seules. Une relance manuelle (US-20) prend une place libre ; sans place, ou sur une annonce qu'un worker relance, elle est refusée (`BROWSER_BUSY`). Un worker refusé pour la même raison attend 30 secondes. Un worker en attente ne tient pas de place.
12. **Reprise (US-19)** : inchangée. Les étapes sont en base ; après un redémarrage, un nouveau « Tout lancer » reprend avec les workers.
13. **Suivi par worker (aperçu)** : `workers` remplace `currentListingIds` : en mode `WORKERS`, les trois workers dans l'ordre republication, suppression, vérification ; en mode `SEQUENTIAL`, un seul worker `ALL_ACTIONS`. Pour chacun : son type, son état (`WORKING` en action, `WAITING` avec sa date et sa raison, `IDLE` inactif, `STOPPED` hors série, en pause ou après l'arrêt), l'annonce et l'action en cours quand il est en action, et son nombre de relances. `relaunchCount` de la série reste le total.
14. **Page du dressing** : en mode `WORKERS`, la ligne « Traitement en cours » devient une ligne par worker, tant que la série tourne ou attend :
    - « Republication : republie Bonnet (annonce 1) » ; action en clair : « supprime », « revérifie la suppression de », « republie », « vérifie la republication de » ; titre lu dans la liste affichée, « annonce <id> » seul sinon ;
    - « Suppression : en attente jusqu'au <date> (cadence) » ; raisons : « cadence », « délai de republication », « navigateur occupé » (seules attentes que donne US-17) ;
    - « Vérification : rien à faire ».

    En mode `SEQUENTIAL`, la ligne reste celle d'US-19 (« Traitement en cours : Bonnet (annonce 1). »).
15. **File d'attente (US-41)** : sa simulation applique la cadence propre à chaque type (une suppression prévue ne compte que pour les suppressions suivantes, une republication pour les republications). Rien d'autre ne change : heures au plus tôt, actions supposées instantanées, dans les deux modes.

## Contrat d'API
Contract first : `src/main/resources/static/openapi/vinted-republisher.yaml`. Aucun nouveau chemin.
- `AutomaticRunOverview` : `workers` (tableau de `RunWorker`, obligatoire, vide quand aucune série n'a tourné depuis le démarrage) remplace `currentListingIds`.
- `RunWorker` : `kind` (`RunWorkerKind`, obligatoire), `state` (`RunWorkerState`, obligatoire), `listingId` (int64, présent pour `WORKING`), `action` (`RelaunchAction`, présent pour `WORKING`), `waitingUntil` (date-time, présent pour `WAITING`), `waitingReason` (`RunWaitingReason`, présent pour `WAITING`), `relaunchCount` (int32 ≥ 0, obligatoire).
- `RunWorkerKind` : `ALL_ACTIONS`, `REPUBLICATION`, `DELETION`, `VERIFICATION`.
- `RunWorkerState` : `WORKING`, `WAITING`, `IDLE`, `STOPPED`.
- `RunWaitingReason` : `CADENCE`, `REPUBLICATION_DELAY`, `BROWSER_BUSY`.

## Critères d'acceptation
- [x] Par défaut (`SEQUENTIAL`), une seule action d'annonce à la fois : la série se comporte comme en US-19.
- [x] En mode `WORKERS`, trois workers attitrés relancent chacun les annonces de leurs étapes, chacun dans son onglet, jamais deux sur la même annonce.
- [x] Le worker vérification revérifie les suppressions quand il n'a aucune republication à vérifier ; les écritures restent attitrées.
- [x] Chaque worker avance à son rythme : la fin d'une action ne dépend pas de celle des autres workers.
- [x] La cadence est propre à chaque type d'écriture : une suppression et une republication peuvent se suivre sans intervalle ; deux écritures du même type, jamais avant l'intervalle de leur type.
- [x] Une attente ne bloque que son worker.
- [x] Un worker inactif reprend dès qu'une action d'un autre worker se termine ; la série est terminée quand les trois sont inactifs.
- [x] Pause et arrêt laissent finir l'action en cours de chaque worker ; un problème du compte arrête la série une fois les actions en cours finies.
- [x] Le verrou admet trois actions d'annonce en mode `WORKERS` ; une relance manuelle prend une place libre ou est refusée.
- [x] L'aperçu et la page montrent l'état de chaque worker.
- [x] La file d'attente applique la cadence propre à chaque type.
- [x] Une configuration invalide empêche l'application de démarrer.

## Tests d'acceptation
Valeurs des scénarios : mode `WORKERS`, délai de republication 5 minutes, intervalle de cadence 2 minutes pour chaque type (les tests fixent leurs propres valeurs).

### AT-46-01 – Par défaut, une action à la fois
```gherkin
Étant donné la configuration par défaut
Quand l'application démarre
Alors le mode de parallélisme est SEQUENTIAL et la série suit US-19 (ses scénarios passent inchangés)
Et l'aperçu d'une série montre un seul worker ALL_ACTIONS
```
Test : `ParallelismConfigurationTest.defaults_to_sequential()` et `AutomaticRunAcceptanceTest` (US-19, US-21)

### AT-46-02 – Trois workers attitrés en même temps
```gherkin
Étant donné une annonce « À republier », une « Prête à supprimer » validée et une « Republication à vérifier », en mode réel
Quand la série tourne
Alors la republication, la suppression et la vérification sont en cours en même temps, chacune par son worker et dans son onglet
Et l'aperçu montre chaque worker en action avec son annonce et son action
```
Test : `AutomaticRunAcceptanceTest.Workers.each_worker_relaunches_its_own_steps_at_the_same_time()`

### AT-46-03 – Chaque worker avance à son rythme
```gherkin
Étant donné une vérification de republication lente et deux annonces « Prêtes à supprimer » en dry-run
Quand la série tourne
Alors le worker suppression relance les deux annonces l'une après l'autre pendant que la vérification est encore en cours
```
Test : `AutomaticRunAcceptanceTest.Workers.a_slow_action_does_not_hold_the_other_workers()`

### AT-46-04 – Entraide limitée aux lectures
```gherkin
Étant donné deux annonces « Suppression à vérifier » et aucune republication à vérifier
Quand la série tourne
Alors le worker suppression et le worker vérification revérifient chacun une des deux annonces, jamais la même
Étant donné une annonce « Prête à supprimer » et un worker republication sans annonce
Quand la série tourne
Alors seul le worker suppression la supprime
```
Test : `AutomaticRunAcceptanceTest.Workers.the_verification_worker_helps_with_deletion_rechecks()`, `writes_stay_with_their_own_worker()`

### AT-46-05 – Cadence propre à chaque type d'écriture
```gherkin
Étant donné une suppression tentée à 10:00
Quand une republication est demandée à 10:00:10
Alors elle a lieu
Quand une autre suppression est demandée à 10:01
Alors elle est refusée (CADENCE_LIMIT_REACHED, à partir de 10:02)
```
Test : `WriteCadenceTest.a_write_only_waits_for_the_interval_of_its_own_type()`, `DeleteListingAcceptanceTest` et `RepublishListingAcceptanceTest` (AT-27-02 réécrit)

### AT-46-06 – Une attente ne bloque que son worker
```gherkin
Étant donné deux annonces « Prêtes à supprimer » validées, en mode réel, et une annonce « À republier »
Quand la série tourne
Alors le worker suppression supprime la première puis attend 2 minutes pour la seconde
Et pendant cette attente le worker republication remet en vente l'annonce « À republier »
Et l'aperçu montre le worker suppression en attente (cadence) avec sa date
```
Test : `AutomaticRunAcceptanceTest.Workers.a_wait_only_holds_its_own_worker()`

### AT-46-07 – Un worker inactif reprend, la série finit quand tous sont inactifs
```gherkin
Étant donné une annonce « Prête à supprimer » validée et aucune autre candidate
Quand la série tourne et que la suppression aboutit (annonce « En attente »)
Alors le worker republication, jusque-là inactif, la remet en vente une fois le délai passé
Et la série est terminée quand les trois workers sont inactifs
```
Test : `AutomaticRunAcceptanceTest.Workers.an_idle_worker_picks_up_the_work_another_one_left()`

### AT-46-08 – Jamais deux workers sur la même annonce
```gherkin
Étant donné une seule annonce « Suppression à vérifier »
Quand la série tourne
Alors un seul worker la revérifie, l'autre reste inactif
```
Test : `AutomaticRunAcceptanceTest.Workers.never_two_workers_on_the_same_listing()`

### AT-46-09 – Pause et arrêt laissent finir les actions en cours
```gherkin
Étant donné deux workers en action, et une annonce « Prête à supprimer » après eux
Quand le membre demande une pause
Alors chaque worker finit son action, n'en prend pas d'autre, et la série est en pause quand tous ont fini
Quand il reprend
Alors les workers reprennent
Quand il demande l'arrêt pendant qu'un worker attend
Alors l'attente est interrompue et la série s'arrête quand plus aucun worker n'est en action (STOPPED_BY_MEMBER)
```
Test : `AutomaticRunAcceptanceTest.Workers.pause_lets_every_action_in_progress_finish()`, `stop_cuts_a_wait_short()`

### AT-46-10 – Un problème du compte arrête tous les workers
```gherkin
Étant donné deux workers en action, et une annonce « Prête à supprimer » après eux
Quand le worker suppression trouve la session Vinted déconnectée
Alors les deux autres finissent leur action et n'en prennent plus
Et la série s'arrête avec la raison SESSION_DISCONNECTED
```
Test : `AutomaticRunAcceptanceTest.Workers.an_account_problem_stops_every_worker()`

### AT-46-11 – Verrou à trois places et relance manuelle
```gherkin
Étant donné le mode WORKERS
Quand quatre actions d'annonce sont demandées, ou deux sur la même annonce
Alors la quatrième et la seconde sur la même annonce sont refusées (BROWSER_BUSY)
Étant donné une relance manuelle en cours sur une annonce « Prête à supprimer »
Quand la série tourne
Alors le worker qui en avait besoin attend 30 secondes (navigateur occupé), puis la relance
```
Test : `VintedSessionTest.Parallelism.admits_three_listing_actions_with_workers()`, `AutomaticRunAcceptanceTest.Workers.a_manual_relaunch_holding_a_listing_makes_its_worker_wait()`

### AT-46-12 – Page du dressing
```gherkin
Étant donné une série en mode WORKERS : republication de « Bonnet » (annonce 1) en cours, suppression en attente de cadence jusqu'au 19/11/2026 14:32, vérification inactive
Quand le membre ouvre la page du dressing
Alors il voit « Republication : republie Bonnet (annonce 1) », « Suppression : en attente jusqu'au 19/11/2026 14:32 (cadence) » et « Vérification : rien à faire »
```
Test : `WardrobePageTest.shows_each_worker_of_the_run()` (Playwright, API simulée) et `run-summary.spec.ts` (Vitest)

### AT-46-13 – File d'attente avec la cadence propre à chaque type
```gherkin
Étant donné une annonce « À republier » et une « Prête à supprimer » validée, en mode réel, aucune écriture récente
Quand le membre ouvre la file d'attente
Alors la republication et la suppression sont prévues « maintenant » toutes les deux
```
Test : `ShowRunQueueAcceptanceTest.each_write_type_has_its_own_cadence()`

### AT-46-14 – Configuration invalide
```gherkin
Étant donné parallelism.mode absent, vide ou à « TROIS »
Quand l'application démarre
Alors elle ne démarre pas, avec un message qui nomme parallelism.mode et ses valeurs permises
```
Test : `ParallelismConfigurationTest.refuses_a_missing_or_unknown_mode()`

### AT-46-15 – Sur le vrai compte
```gherkin
Étant donné le mode WORKERS et quelques annonces validées
Quand le membre lance la série
Alors suppressions, republications et vérifications avancent en parallèle sans captcha ni déconnexion, et la page suit chaque worker
```
Test : manuel (vrai compte Vinted : comportement anti-robot non simulable, validé par le membre)

## Cas limites et erreurs
- Deux workers voient la même annonce `DELETION_TO_CHECK` : le choix et la prise de l'annonce sont atomiques ; le second en prend une autre ou devient inactif.
- Annonce dont l'étape change pendant qu'un worker attend (relance manuelle, autre worker) : le worker refait son choix à la fin de son attente, dans l'inventaire relu.
- Annonce revenue à une étape déjà relancée dans la série : écartée comme en US-19, quel que soit le worker qui l'avait relancée.
- Worker vérification et worker suppression : la revérification d'une suppression est faite par l'un ou l'autre, jamais les deux ; elle est comptée une fois.
- Pause demandée pendant que tous les workers attendent : la série passe en pause aussitôt.
- Redémarrage pendant une série : rien ne reprend seul (US-19) ; une écriture douteuse reste « à vérifier ».
- Passage d'un mode à l'autre : seulement par configuration, au redémarrage.

## Hors périmètre
- Nombre de workers ou répartition des étapes réglables par configuration (trois workers fixes).
- Nom du worker dans le journal (US-23) : le type d'action l'indique déjà presque toujours.
- Écart minimal entre deux écritures de types différents.
- Priorité entre suppression et republication : chaque type a sa propre cadence, ils ne se disputent rien.
- Tournées d'US-15 et `parallelism.listing-actions` : supprimés.

## Décisions
- **Mode séquentiel = un seul worker `ALL_ACTIONS`** : le même moteur sert les deux modes ; comme en US-19, il attend la date permise de sa première candidate même si une écriture de l'autre type serait permise (comportement d'US-19 conservé).
- **Après une republication réussie**, l'annonce est republiée sans passer par « Republication à vérifier » : le worker vérification ne travaille que sur les republications douteuses et les revérifications de suppression (AT-46-07 décrit donc la reprise par le worker republication).
- **Pas de raison d'attente « nouvel essai »** : US-17 n'attend que pour la cadence, le délai de republication ou un navigateur occupé.
- **Trois workers attitrés** : republication (`TO_REPUBLISH`, `WAITING`) ; suppression et revérification de suppression (`TO_DELETE`, `DELETION_TO_CHECK`) ; vérification (`REPUBLICATION_TO_CHECK`).
- **Entraide limitée aux lectures** : le worker vérification prend la vérification de republication en priorité, sinon une revérification de suppression (en début de série, il n'a aucune republication à vérifier). Les écritures restent strictement attitrées. Jamais deux workers sur la même annonce.
- **Cadence propre à chaque type d'écriture** (modifie US-27, spec mise à jour dans le même commit que le code) : plus d'intervalle entre deux écritures de types différents, ni d'écart minimal ; plafonds journaliers inchangés ; réservation d'US-15 par type. Le risque anti-robot accru (jusqu'à deux écritures par intervalle) est accepté, borné par les intervalles et les plafonds.
- **Pas de priorité entre suppression et republication** : sans objet avec une cadence par type.
- **Pause, arrêt et `STOP_RUN`** : chaque worker finit son action en cours ; la série s'arrête ou se met en pause quand plus aucun n'est en action.
- **Attente par worker** : seul le worker concerné attend.
- **Suivi par worker** : `workers` remplace `currentListingIds` dans l'aperçu ; une ligne par worker sur la page du dressing ; journal inchangé.
- **Mode `SEQUENTIAL` ou `WORKERS`** remplace `parallelism.listing-actions` ; défaut `SEQUENTIAL` (prudence anti-robot) ; les tournées d'US-15 disparaissent avec leur code (trois modes à maintenir pour aucun gain). US-15 est mise à jour dans le même commit que le code.
- **En mode `SEQUENTIAL`, un worker `ALL_ACTIONS`** : un seul format d'aperçu ; la page garde la ligne d'US-19.
- **File d'attente (US-41)** : seule la cadence par type change ; spec US-41 mise à jour avec le code.
- **Worker inactif** : il refait son choix à la fin de chaque action d'un autre worker (aucune scrutation périodique).

## Questions ouvertes
- Aucune.

## Modèle de domaine
Termes à ajouter à `docs/glossary.md` :
- `RunWorker` | Worker de la série | Place de la série attitrée à un type d'action ; relance une annonce à la fois, à son rythme.
- `RunWorkerKind` (`ALL_ACTIONS`, `REPUBLICATION`, `DELETION`, `VERIFICATION`) | Type de worker | Étapes que le worker prend.
- `RunWorkerState` (`WORKING`, `WAITING`, `IDLE`, `STOPPED`) | État d'un worker.
- `RunWaitingReason` (`CADENCE`, `REPUBLICATION_DELAY`, `BROWSER_BUSY`) | Raison d'attente d'un worker.
- `ParallelismMode` (`SEQUENTIAL`, `WORKERS`) | Mode de parallélisme.

Termes retirés : `Round` (tournée), `ParallelListingActions`.

- `AutomaticRun` : porte l'état de chaque worker (`workers`) au lieu de `currentListings` ; état global déduit des workers.
- `WriteCadence` / `CadenceForecast` : intervalle compté depuis la dernière écriture du même type.
- Port inchangé pour le navigateur ; le verrou (`VintedSession`) prend sa capacité du mode.

## Tests unitaires et d'intégration attendus
- Domaine : `WriteCadenceTest` (intervalle par type, réservation par type), `AutomaticRunTest` (état global déduit des workers : `RUNNING`, `WAITING` avec la date la plus proche, `FINISHED`, pause), `RunWorkerKindTest` (étapes prises et ordre de chaque type), `RunQueueTest` (cadence par type).
- Application : couverte par `AutomaticRunAcceptanceTest.Workers` (choix d'une annonce par worker sans doublon, worker inactif réveillé, attente par worker, pause, arrêt, `STOP_RUN`) et les scénarios séquentiels d'US-19 et US-21.
- Adaptateurs : `ParallelismConfigurationTest` (mode par défaut, valeur invalide), `VintedSessionTest.Parallelism` (trois places en mode `WORKERS`, une en `SEQUENTIAL`), `AutomaticRunApiControllerTest` (`workers` dans l'aperçu), `WardrobePageTest` et `run-summary.spec.ts` (lignes par worker).
