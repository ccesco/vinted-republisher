# US-41 – File d'attente de la série automatique
**Statut** : implémentée
**Priorité** : V2
**Dépend de** : US-11 (délai de republication), US-14 (étapes), US-19 (série « Tout lancer »), US-10 et US-12 (dry-run), US-27 (cadence)

## Contexte
« Tout lancer » (US-19) ne construit pas de liste à l'avance : à chaque tour, il relit l'inventaire et prend la prochaine annonce par étape puis par identifiant, et attend quand la cadence (US-27) ou le délai de republication (US-11) l'impose. Le membre ne voit donc nulle part ce que la série va faire, dans quel ordre, ni quand : le filtre « Étape » de la liste ne donne ni l'ordre de passage, ni l'heure, ni la republication qui suivra chaque suppression.

## Objectif
En tant que membre, je veux voir la file d'attente des actions que la série automatique va lancer, dans leur ordre et avec leur heure au plus tôt, afin de savoir ce qui va se passer sur mon compte Vinted et quand.

## Comportement attendu
1. **Prévision** : la file est calculée à la demande, à l'heure du serveur, depuis l'inventaire (étapes d'US-14), les tentatives d'écriture gardées en base (cadence d'US-27, écritures en cours comprises), le délai de republication d'US-11 et les modes de suppression et de republication (dry-run ou réel). Elle ne lit jamais Vinted et ne stocke rien.
2. **Annonces prises** : celles que la série prendrait, aux mêmes étapes : `REPUBLICATION_TO_CHECK`, `DELETION_TO_CHECK`, `TO_REPUBLISH`, `TO_DELETE`, `WAITING`. Pendant une série en cours (en cours, en attente ou en pause), les annonces qu'elle a écartées n'y sont pas, ni celles de la tournée qu'elle est en train de relancer (leur action est déjà partie).
3. **Simulation de la série**, sur une horloge qui part de maintenant ; chaque action est supposée instantanée. À chaque tour :
   1. une annonce `WAITING` dont la date de republication est atteinte devient `TO_REPUBLISH` ;
   2. la première annonce dans l'ordre de la série (`REPUBLICATION_TO_CHECK`, `DELETION_TO_CHECK`, `TO_REPUBLISH`, `TO_DELETE`, puis identifiant Vinted croissant) est prise ;
   3. vérification (revérification de la suppression, vérification de la republication) : l'action est prévue tout de suite ;
   4. écriture réelle (suppression, republication hors dry-run) : si la cadence ne la permet pas encore, l'horloge avance jusqu'à la date permise et le tour recommence (une annonce devenue `TO_REPUBLISH` entre-temps passe alors devant, comme dans la série). Sinon l'action est prévue à cette heure et compte pour la cadence des suivantes ;
   5. après une suppression réelle prévue, l'annonce revient en `WAITING` jusqu'à l'heure prévue plus le délai de republication : sa republication suivra dans la file ;
   6. écriture en dry-run : l'action est prévue tout de suite, sans cadence ; l'annonce n'a pas de suite (la série l'écarte, US-19) ;
   7. s'il ne reste que des annonces `WAITING`, l'horloge avance jusqu'à la plus proche date de republication.

   Une vérification n'a pas de suite dans la file : son issue n'est connue que sur Vinted.
4. **Réponse** : la date du calcul et, dans l'ordre de passage, chaque action prévue : annonce (identifiant, titre), action (`RelaunchAction`), heure au plus tôt, dry-run ou non.
5. **Page** « File d'attente » (`/queue`), dans le menu : tableau numéroté avec l'annonce (lien vers sa fiche), l'action en clair, « maintenant » si l'heure prévue n'est pas après la date du calcul, sinon la date et l'heure, et la mention « dry-run ». Une phrase rappelle que les heures sont au plus tôt (chaque action prend en réalité du temps). File vide : « Aucune action en attente pour la série. » API injoignable : « File d'attente indisponible. » Bouton « Actualiser » pour recalculer.

## Contrat d'API
Contract first : `src/main/resources/static/openapi/vinted-republisher.yaml`.

| Méthode et chemin | operationId | Réponse |
|---|---|---|
| `GET /api/automatic-run/queue` | `showRunQueue` | 200 `RunQueue` {`computedAt`, `actions` : liste de `QueuedAction` {`listingId`, `listingTitle`, `action` (`RelaunchAction`), `earliestAt`, `dryRun`}} |

## Critères d'acceptation
- [x] La file liste les actions dans l'ordre où la série les lancerait : vérifications, republications, suppressions, puis par identifiant.
- [x] Chaque écriture réelle a l'heure au plus tôt que la cadence permet, en comptant les écritures déjà faites et celles prévues avant elle dans la file.
- [x] Une suppression réelle est suivie, dans la file, de la republication de la même annonce, au plus tôt à l'heure de la suppression plus le délai de republication.
- [x] Une annonce en attente de republication apparaît à sa date de republication, et passe devant les suppressions dès cette date atteinte.
- [x] En dry-run, l'écriture est prévue tout de suite, marquée « dry-run », sans republication à la suite.
- [x] Les annonces écartées par la série en cours n'y sont pas ; les étapes que la série ne prend pas non plus.
- [x] La page montre la file numérotée, avec liens, heures, mention dry-run, cas vide et API injoignable.

## Tests d'acceptation

### AT-41-01 – Ordre de la série
```gherkin
Étant donné les annonces 30 « Prête à supprimer », 20 « À republier », 10 « Suppression à vérifier » et 40 « À valider »
Et la suppression et la republication en dry-run
Quand le membre affiche la file d'attente
Alors la file est : revérification de 10, republication de 20, suppression de 30, toutes maintenant
Et la republication et la suppression sont marquées dry-run
Et l'annonce 40 n'y est pas
```
Test : `ShowRunQueueAcceptanceTest.queue_follows_the_run_order()`

### AT-41-02 – Cadence et republication à la suite
```gherkin
Étant donné les écritures réelles, un intervalle de 30 secondes avant une suppression et de 2 minutes avant une republication, un délai de republication de 5 minutes
Et une suppression faite il y a 10 secondes, sur une annonce déjà traitée
Et les annonces 1 et 2 « Prêtes à supprimer »
Quand le membre affiche la file d'attente
Alors la file est : suppression de 1 dans 20 secondes, suppression de 2 dans 50 secondes, republication de 1 dans 5 minutes 20 secondes, republication de 2 dans 7 minutes 20 secondes
```
Test : `ShowRunQueueAcceptanceTest.real_writes_follow_the_cadence_and_deletions_lead_to_republications()`

### AT-41-03 – Annonce en attente qui passe devant
```gherkin
Étant donné les écritures réelles, un intervalle de 30 secondes avant une suppression et de 2 minutes avant une republication
Et l'annonce 5 supprimée, républiable dans 1 minute
Et les annonces 1, 2 et 3 « Prêtes à supprimer », aucune écriture récente
Quand le membre affiche la file d'attente
Alors la file commence par : suppression de 1 maintenant, suppression de 2 dans 30 secondes, republication de 5 dans 2 minutes 30 secondes, suppression de 3 dans 3 minutes
```
Test : `ShowRunQueueAcceptanceTest.waiting_listing_goes_first_once_republishable()`

### AT-41-04 – Plafond sur 24 heures
```gherkin
Étant donné les écritures réelles et un plafond de 2 suppressions par 24 heures
Et deux suppressions faites il y a 23 heures et 22 heures, sur des annonces déjà republiées
Et l'annonce 1 « Prête à supprimer »
Quand le membre affiche la file d'attente
Alors la file commence par la suppression de 1, prévue dans 1 heure
```
Test : `ShowRunQueueAcceptanceTest.daily_limit_pushes_the_write_back()`

### AT-41-05 – Annonces écartées par la série en cours
```gherkin
Étant donné une série en cours qui a écarté l'annonce 1
Et les annonces 1 et 2 « Prêtes à supprimer »
Quand le membre affiche la file d'attente
Alors seule la suppression de 2 y figure
```
Test : `ShowRunQueueAcceptanceTest.listings_set_aside_by_the_current_run_are_left_out()` ; annonces de la tournée en cours : `listings_the_run_is_relaunching_are_left_out()`, de retour pendant l'attente : `listings_of_the_last_round_are_back_while_the_run_waits()` ; une série terminée ne les écarte plus : `listings_set_aside_by_an_ended_run_are_back()`

### AT-41-06 – API
```gherkin
Quand le client appelle GET /api/automatic-run/queue
Alors il reçoit la date du calcul et les actions prévues, avec annonce, titre, action, heure au plus tôt et dry-run
```
Test : `AutomaticRunApiControllerTest.shows_the_queue()`

### AT-41-07 – Page
```gherkin
Étant donné une file avec une suppression maintenant en dry-run et une republication dans 5 minutes
Quand le membre ouvre « File d'attente »
Alors il voit deux lignes numérotées, liens vers les fiches, « maintenant » et l'heure, et la mention dry-run sur la première
Et une file vide affiche « Aucune action en attente pour la série. », une API injoignable « File d'attente indisponible. »
```
Test : `RunQueuePageTest` (`shows_the_queued_actions()`, `refresh_reads_the_queue_again()`, `says_when_the_queue_is_empty()`, `says_when_the_queue_is_unavailable()`, `navigation_leads_to_the_queue_page()`) ; libellés : `run-queue-labels.spec.ts` (Vitest)

## Cas limites et erreurs
- Écriture en cours dans un onglet (US-15) : sa réservation compte dans la cadence, comme pour la série.
- Annonce `WAITING` sans date de republication lisible (cas théorique) : elle est laissée hors de la file.
- Série en pause : la file se calcule comme si elle reprenait maintenant.
- Base indisponible : erreur 500 générique, la page affiche « File d'attente indisponible. ».

## Hors périmètre
- Durée réelle des actions, pauses de navigation, parallélisme (US-15) : les heures sont au plus tôt.
- Issue des vérifications, refus et échecs à venir (US-17) : non prévus.
- Scan, sauvegarde, validation : jamais lancés par la série.
- Réordonner ou retirer une action depuis la file.
- Rafraîchissement automatique de la page.

## Décisions
- Décisions laissées à Claude par l'utilisateur (2026-11-19, « lance l'analyse et l'implem comme tu le sens »).
- Prévision recalculée à chaque lecture, sans stockage : la série elle-même ne garde pas de liste, et l'inventaire change à chaque action.
- Simulation fidèle au choix de la série (US-19, point 2 et 3) : ordre des étapes puis identifiant, attente de la cadence sur la première annonce plutôt que saut vers une autre, `WAITING` en dernier tant que sa date n'est pas atteinte.
- Suites prévues : seule la suppression réelle a une suite connue (attente puis republication) ; une vérification ou un dry-run n'en a pas.
- Actions instantanées : sans durée connue, l'heure affichée est la plus tôt possible ; la page le dit.
- Annonces écartées : exclues seulement pendant une série en cours ; une nouvelle série repart sans écartées.
- Chemin `/api/automatic-run/queue`, sous la ressource de la série ; page dans le menu, après « Échecs ».

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `RunQueue` (file d'attente de la série) : prévision pure, dans `domain/run`, des actions de la série depuis des `QueueCandidate` (annonce, titre, étape, date de republication), une `CadenceForecast`, le `RepublicationDelay` et les modes d'écriture.
- `QueuedAction` (action prévue) : annonce, titre, `RelaunchAction`, heure au plus tôt, dry-run.
- `CadenceForecast` (prévision de cadence), dans `domain/cadence` : la `WriteCadence` et les dates d'écriture comptées ; dit à partir de quand une écriture est permise et ajoute une écriture prévue.
- Cas d'usage `ShowRunQueue` (application/run) ; `CadenceCheck.forecast()` fournit la prévision de cadence, écritures en cours comprises.

## Tests unitaires et d'intégration attendus
- Domaine : `CadenceForecastTest` (intervalle, plafond, écriture prévue ajoutée) ; `RunQueueTest` (ordre, cadence, suite d'une suppression, attente qui passe devant, dry-run, seules attentes, vide).
- Application : `ShowRunQueueAcceptanceTest` (AT-41-01 à 05).
- Web : `RunQueueApiControllerTest` ; `RunQueuePageTest` (`@Tag("browser")`).
