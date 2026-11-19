# US-15 – Traitement en parallèle (plusieurs navigateurs)
**Statut** : implémentée — tournées et `parallelism.listing-actions` remplacés par les workers d'US-46 (verrou par annonce et réservation de cadence par type conservés)
**Priorité** : V2
**Dépend de** : US-19 (série automatique), US-20 (relance), US-21 (pause et stop), US-27 (cadence)

## Contexte
La série « Tout lancer » (US-19) relance les annonces une à une : chaque vérification, revérification ou mise en vente attend la fin de la précédente. Le verrou du navigateur (US-03) n'admet qu'une action à la fois. Mener plusieurs actions d'annonce en même temps raccourcirait la série, surtout ses lectures (vérifications, revérifications, dry-runs).

Le README range cette US en V2 pour deux raisons qui restent vraies et bornent la solution :
- plusieurs navigateurs augmentent le risque de blocage anti-robot de Vinted ;
- les limites de cadence (US-27) plafonnent déjà les écritures : deux suppressions ne peuvent pas se suivre à moins de l'intervalle minimal, quel que soit le nombre de navigateurs.

Le parallélisme est donc une option, **désactivée par défaut** (1 action à la fois, comportement actuel inchangé), et les limites de cadence restent **globales au compte**.

## Objectif
En tant que membre, je veux pouvoir autoriser l'outil à mener plusieurs actions sur mes annonces en même temps, chacune dans son onglet de Chrome, afin que la série automatique avance plus vite sans dépasser les limites de cadence de mon compte.

## Comportement attendu
1. **Parallélisme** : `parallelism.listing-actions` (application.yml, **1** par défaut) fixe le nombre d'actions d'annonce menées en même temps. Une action d'annonce est une suppression, une revérification de suppression, une republication, une validation ou une vérification de republication (actions navigateur `DELETING` et `REPUBLISHING`). Valeur absente, illisible, inférieure à 1 ou supérieure à 4 → l'application ne démarre pas.
2. **Navigateurs** : chaque action d'annonce travaille dans son propre onglet du Chrome normal déjà piloté par CDP (même profil, donc même session Vinted : une seule connexion). Aucun Chrome n'est lancé par Playwright ni en headless ; aucun profil supplémentaire.
3. **Verrou du navigateur** : il admet au plus `listing-actions` actions d'annonce en même temps, **jamais deux sur la même annonce**. Les autres actions navigateur (vérification et connexion de session, scan, sauvegarde, rafraîchissement des statistiques, archive) restent seules : refusées pendant toute action, et toute action est refusée pendant elles. Refus : `BROWSER_BUSY` comme aujourd'hui. Les actions manuelles (fiche, US-09, US-12, US-20) partagent les mêmes places que la série.
4. **Cadence globale** : les limites d'US-27 valent pour tout le compte, pas par onglet. Une écriture réelle qui passe le contrôle de cadence le **réserve** jusqu'à la fin de son action : un autre contrôle compte cette réservation comme une écriture faite à l'heure du contrôle. Deux écritures ne passent donc jamais le contrôle ensemble, même dans deux onglets. La réservation est en mémoire (perdue au redémarrage, où seules comptent les tentatives en base, comme aujourd'hui).
5. **Série par tournées** : à chaque tour, la série prend dans l'inventaire jusqu'à `listing-actions` annonces candidates, dans l'ordre d'US-19 (étapes puis identifiant ; annonces écartées et étapes déjà relancées traitées comme en US-19), et les relance en même temps : c'est une **tournée**. Elle attend la fin de toutes les relances de la tournée, puis applique leurs résultats dans l'ordre de la tournée (écartées, relances comptées, conduite d'US-17).
   - Au moins une relance a abouti ou une annonce a été écartée → tournée suivante aussitôt.
   - Toutes les relances de la tournée demandent d'attendre → la série attend jusqu'à la date la plus proche (30 secondes pour un navigateur occupé), puis reprend.
   - Une relance demande l'arrêt (`STOP_RUN`) → la série s'arrête, raison de la première dans l'ordre de la tournée, une fois toute la tournée finie.
   
   - **Une écriture réelle par tournée** (US-19, cadence vérifiée avant) : la tournée se clôt avant une seconde écriture réelle, ou avant une écriture que la cadence ne permet pas encore ; si cette écriture est la première candidate, la série attend la date permise. Les vérifications peuvent accompagner l'écriture.

   Avec 1 (défaut), une tournée est une relance : US-19 inchangée.
6. **Pause et arrêt (US-21)** : prennent effet entre deux tournées. Toutes les relances de la tournée en cours se terminent (jamais coupées), puis la série passe en pause ou s'arrête. Une attente reste interrompue comme en US-21.
7. **Reprise (US-19)** : inchangée. Les étapes sont en base ; après un redémarrage, un nouveau « Tout lancer » reprend, en tournées si le parallélisme le permet.
8. **Relance (US-20)** : une relance manuelle prend une place libre du verrou ; sans place, ou si la série relance déjà cette annonce, elle est refusée (`BROWSER_BUSY`). Une relance de la série refusée pour la même raison attend 30 secondes (US-19).
9. **Aperçu** : `currentListingIds` remplace `currentListingId` : les annonces de la dernière tournée, dans l'ordre, tant que la série tourne. Page du dressing : « Traitement en cours : Bonnet (annonce 1). » ou « Traitement en cours : Bonnet (annonce 1), Pull (annonce 2). », titre lu dans la liste affichée (« annonce <id> » seul pour une annonce absente de la liste)

## Contrat d'API
- `AutomaticRunOverview` : `currentListingIds` (tableau d'identifiants Vinted, obligatoire, vide hors tournée) remplace `currentListingId`. Aucun autre changement ; aucun nouveau chemin.

## Critères d'acceptation
- [x] Par défaut, une seule action d'annonce à la fois : la série se comporte comme en US-19.
- [x] Avec un parallélisme de N, la série relance jusqu'à N annonces en même temps, chacune dans son onglet.
- [x] Les limites de cadence restent globales : deux écritures ne passent jamais le contrôle ensemble.
- [x] Le verrou admet au plus N actions d'annonce, jamais deux sur la même annonce, et laisse seules les autres actions navigateur.
- [x] Pause et arrêt laissent finir toutes les relances de la tournée ; un problème du compte arrête la série une fois la tournée finie.
- [x] La série n'attend que si toutes les relances de la tournée le demandent, jusqu'à la date la plus proche.
- [x] L'aperçu et la page montrent les annonces de la tournée.
- [x] Une configuration invalide empêche l'application de démarrer.

## Tests d'acceptation
Valeurs des scénarios : parallélisme 2, délai de republication 5 minutes, intervalle de cadence 2 minutes.

### AT-15-01 – Par défaut, une action à la fois
```gherkin
Étant donné la configuration par défaut
Quand l'application démarre
Alors le parallélisme vaut 1 et la série suit US-19 (ses scénarios passent inchangés)
```
Test : `ParallelismConfigurationTest.defaultsToOneListingActionAtATime()` et `AutomaticRunAcceptanceTest` (US-19, US-21)

### AT-15-02 – Deux annonces relancées en même temps
```gherkin
Étant donné deux annonces « Prête à supprimer », suppression en dry-run, parallélisme 2
Quand la série tourne
Alors les deux dry-runs sont en cours en même temps, chacun sur son annonce
Et l'aperçu montre les deux annonces de la tournée
Et la série est terminée avec 2 relances, les deux annonces écartées
```
Test : `AutomaticRunAcceptanceTest.Parallel.relaunches_two_listings_at_the_same_time()`

### AT-15-03 – La cadence reste globale au compte
```gherkin
Étant donné deux annonces validées, en mode réel, parallélisme 2
Quand la série tourne
Alors la première tournée ne supprime que la première, la seconde n'est pas tentée
Et la série attend 2 minutes, puis supprime la seconde
Et le journal n'a aucun refus « limite de cadence atteinte »
```
Test : `AutomaticRunAcceptanceTest.Parallel.cadence_stays_global_to_the_account()`, `a_check_goes_without_the_write_the_cadence_does_not_allow_yet()` (vérification sans l'écriture pas encore permise)

### AT-15-04 – Réservation de cadence
```gherkin
Étant donné une écriture réelle qui a passé le contrôle de cadence et n'est pas finie
Quand une autre écriture est contrôlée
Alors elle est refusée jusqu'à la fin de l'intervalle compté depuis la réservation
Et une fois la première finie, seule sa tentative en base compte
```
Test : `CadenceCheckTest.a_write_in_progress_holds_the_cadence()`, `CadenceCheckTest.a_released_reservation_no_longer_counts()`

### AT-15-05 – Verrou à N places
```gherkin
Étant donné un parallélisme de 2
Quand trois actions d'annonce sont demandées, ou deux sur la même annonce, ou un scan pendant une action d'annonce, ou une action d'annonce pendant un scan
Alors la troisième, la seconde sur la même annonce, le scan et l'action pendant le scan sont refusés (BROWSER_BUSY)
Et une place libérée sert à l'action suivante
```
Test : `VintedSessionTest.Parallelism` (`admits_up_to_the_parallelism`, `never_two_actions_on_the_same_listing`, `other_actions_stay_alone`, `one_by_default`)

### AT-15-06 – Pause et arrêt laissent finir la tournée
```gherkin
Étant donné une tournée de deux suppressions en dry-run
Quand le membre demande une pause pendant la première
Alors les deux se terminent, puis la série passe en pause
Quand il demande l'arrêt pendant une tournée
Alors les deux relances de la tournée se terminent, puis la série s'arrête (STOPPED_BY_MEMBER)
```
Test : `AutomaticRunAcceptanceTest.Parallel.a_pause_lets_the_whole_round_end()`, `AutomaticRunAcceptanceTest.Parallel.a_stop_lets_the_whole_round_end()`

### AT-15-07 – Problème du compte : arrêt après la tournée
```gherkin
Étant donné deux annonces prêtes et une session Vinted perdue, parallélisme 2, suppression en dry-run (en réel, la cadence refuserait la seconde avant Vinted)
Quand la série les relance dans la même tournée
Alors la série s'arrête une fois la tournée finie, raison SESSION_DISCONNECTED
```
Test : `AutomaticRunAcceptanceTest.Parallel.stops_once_the_round_is_over()`

### AT-15-08 – Attendre seulement si toute la tournée attend
```gherkin
Étant donné une annonce « En attente » republiable dans 5 minutes et une annonce « Prête à supprimer » en dry-run, parallélisme 2
Quand la série tourne
Alors la première tournée passe la seconde en dry-run sans attendre
Et la série n'attend qu'ensuite, jusqu'à la date de republication de la première
```
Test : `AutomaticRunAcceptanceTest.Parallel.waits_only_when_the_whole_round_waits()`

### AT-15-09 – Contrat et page
```gherkin
Étant donné une série en cours sur une tournée de deux annonces, « Bonnet » (1) et « Pull » (2), affichées dans la liste
Quand le membre lit l'aperçu ou la page du dressing
Alors l'API donne currentListingIds et la page affiche « Traitement en cours : Bonnet (annonce 1), Pull (annonce 2). »
```
Test : `AutomaticRunApiControllerTest`, `WardrobePageTest.shows_every_listing_of_the_round()`

### AT-15-10 – Configuration invalide
```gherkin
Étant donné parallelism.listing-actions absent, illisible, nul ou supérieur à 4
Quand l'application démarre
Alors elle refuse de démarrer
```
Test : `ParallelismConfigurationTest` (`missingValuePreventsStartup`, `invalidValuePreventsStartup`)

### AT-15-11 – Deux onglets en même temps dans le même Chrome
```gherkin
Étant donné le Chrome piloté par CDP
Quand deux actions ouvrent leur onglet en même temps
Alors chacune travaille dans son onglet, sur sa page, sans gêner l'autre
```
Test : `ChromeTabsTest.two_actions_work_in_their_own_tab_at_the_same_time()` (`@Tag("browser")`, pages locales)

## Cas limites et erreurs
- Écriture réelle plus longue que l'intervalle minimal : sa réservation, comptée à l'heure du contrôle, ne la retient plus ; une autre écriture peut alors passer. Elle ne peut pas toucher la même annonce (verrou par annonce).
- Pendant une écriture, sa réservation et sa tentative en base peuvent compter toutes les deux : toujours dans le sens de la prudence (date permise plus tardive), jamais l'inverse.
- Tournée mêlant une relance aboutie et une refusée pour cadence : la suivante redemande aussitôt la relance refusée, refusée de nouveau sans rien toucher sur Vinted (journal : « refusée », comme US-27), puis la série attend si toute la tournée attend.
- Onglet en arrière-plan : Chrome peut ralentir un onglet non affiché. Raison de plus pour garder 1 par défaut ; tout parallélisme se rode en dry-run avant le vrai compte.
- Plusieurs annonces de la même tournée échouent pour le compte : raison de la première dans l'ordre de la tournée.
- Redémarrage : réservations et aperçu perdus ; tentatives en base relues (US-27).

## Hors périmètre
- Plusieurs profils Chrome ou plusieurs comptes Vinted.
- Scan, sauvegarde, rafraîchissement des statistiques en parallèle.
- Plafond de cadence par onglet.
- Aperçu en direct de chaque onglet : US-22.
- Tournée glissante (démarrer une relance dès qu'une place se libère).

## Décisions
- Décidé par Claude sur délégation de l'utilisateur (« rédiger et valider la spec soi-même », 2026-11-18) :
  - **parallélisme désactivé par défaut** (1), configurable de 1 à 4 : le risque anti-robot et la cadence le rendent optionnel ; 4 borne le risque, et au-delà la cadence rend les onglets inutiles ;
  - **« navigateur » = onglet du même Chrome normal piloté par CDP** : un seul profil, donc une seule connexion à Vinted et une seule empreinte de navigateur (plusieurs profils demanderaient autant de connexions manuelles et ressembleraient à plusieurs appareils sur un même compte) ; jamais de Chrome lancé par Playwright ni headless ;
  - **cadence globale par réservation** : le contrôle sous le verrou ne suffit plus quand le verrou a plusieurs places ; réserver jusqu'à la fin de l'action garde une seule écriture qui passe à la fois, sans table nouvelle ;
  - **verrou à N places pour les seules actions d'annonce, jamais deux sur la même annonce** : les autres actions gardent le navigateur seul ; le verrou par annonce empêche une double écriture si une relance manuelle croise la série ;
  - **série par tournées** plutôt que N boucles indépendantes : pause, arrêt, attente et « une seule écriture par étape » restent décidés par un seul fil, entre deux tournées ; avec 1, la série d'US-19 est inchangée ;
  - attente seulement quand toute la tournée attend, jusqu'à la date la plus proche ;
  - `currentListingIds` remplace `currentListingId` : seule la page du dressing le lit.
  - relevé par la revue : une tournée ne regarde que les annonces atteintes avant d'être pleine (comme US-19 avec 1) ; une action d'annonce ne se termine jamais comme une action seule, et seules suppression et republication partagent le navigateur ; une écriture qui échoue rend sa réservation (`DeleteListingAcceptanceTest.a_failed_deletion_gives_its_cadence_reservation_back()`) ; les fils des relances ne sont pas un bean `Executor` (il remplacerait l'exécuteur de Spring Boot, vu par `ApplicationStartupAcceptanceTest`).
- Premier usage d'un parallélisme supérieur à 1 sur le vrai compte : seulement avec l'accord explicite de l'utilisateur, en dry-run d'abord.
- Une écriture réelle par tournée, décidé par l'utilisateur (2026-11-18) : la série vérifie la cadence avant d'écrire (US-19) ; une seconde écriture dans la même tournée serait refusée par la réservation de la première, elle attend donc la tournée suivante.

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `VintedSession` : verrou à `parallelListingActions` places ; `start(BrowserAction, VintedListingId)` et `finish(VintedListingId)` pour une action d'annonce ; `start(BrowserAction)` et `finish()` pour une action seule.
- `CadenceCheck.reserve(VintedWrite)` → `WriteReservation` (réservation d'écriture), rendue par `release(WriteReservation)` ; remplace `CadenceCheck.check`.
- `AutomaticRun.currentListings` (annonces de la tournée) remplace `currentListing` ; `relaunching(List<VintedListingId>)`.
- `LaunchAutomaticRun` : tournée (`round`) de `parallelism` relances, menées par un exécuteur dédié.
- Configuration : `ParallelismConfiguration` (`parallelism.listing-actions`, `ParallelListingActions`) ; fils dédiés `RelaunchThreads` (un par place, volontairement pas un bean `Executor`, qui remplacerait l'`applicationTaskExecutor` de Spring Boot) ; `Chrome.ensureRunning` synchronisé (deux onglets ne démarrent jamais deux Chrome).

## Tests unitaires et d'intégration attendus
- Domaine : `VintedSessionTest`, `AutomaticRunTest` si besoin.
- Application : `CadenceCheckTest`, `AutomaticRunAcceptanceTest.Parallel`, et `AutomaticRunAcceptanceTest` (US-19, US-21) inchangé avec le parallélisme 1 ; contre-épreuve faite : sans la réservation, `CadenceCheckTest.a_write_in_progress_holds_the_cadence` et `cadence_stays_global_to_the_account` échouent.
- Configuration : `ParallelismConfigurationTest`.
- Navigateur : `ChromeTabsTest` (`@Tag("browser")`).
- Web : `AutomaticRunApiControllerTest`, `WardrobePageTest`.
