# US-25 – Rafraîchir les statistiques
**Statut** : implémentée
**Priorité** : V2
**Dépend de** : US-04 (scan), US-07 (vues et favoris) ; prépare US-24

## Contexte
Les vues et favoris (US-07) ne sont relevés que par le scan (US-04), sur les cartes de la page du dressing. Mais le scan ouvre ensuite chaque annonce une à une : 30 à 35 minutes pour 146 annonces. Pour suivre l'effet d'une republication (US-24), le membre a besoin de relevés fréquents, sans ce coût ni ce trafic vers Vinted. Les cartes du dressing suffisent : les parcourir prend quelques minutes.

## Objectif
En tant que membre, je veux rafraîchir les vues et favoris de toutes mes annonces en un clic et en quelques minutes, afin de suivre leur évolution sans relancer un scan complet.

## Comportement attendu
1. **Lancement** : bouton « Rafraîchir les vues et favoris » sur la page du dressing (`POST /api/wardrobe/statistics-refresh`). Le rafraîchissement tourne en arrière-plan ; il est ignoré si une action navigateur est déjà en cours (scan, sauvegarde, suppression, republication). Entre deux relances d'une série « Tout lancer » (US-19), le navigateur est libre : le rafraîchissement peut tourner, et la série attend qu'il se termine (navigateur occupé, US-17). L'action navigateur affichée est `REFRESHING_STATISTICS`.
2. **Lecture** : parcourt la page du dressing comme le scan, au même rythme (`ScanPace`), sans ouvrir aucune annonce. Rien n'est modifié sur Vinted.
3. **Relevés** : un relevé (annonce, vues, favoris, date) par carte qui montre ses deux nombres, tous à la date du parcours, ajoutés à l'historique d'US-07 (jamais remplacés). Comme au scan, une carte d'une annonce que l'outil ne connaît pas encore est relevée aussi.
4. **Tout ou rien** : comme au scan, un dressing dont le nombre de liens trouvés diffère du total annoncé n'est pas fiable : aucun relevé n'est ajouté, problème `WARDROBE_UNREADABLE`. Un dressing dont aucune carte ne montre ses nombres (membre déconnecté, page changée) n'ajoute rien non plus : problème `NO_STATISTICS_SHOWN`.
5. **Sans effet sur le reste** : ni présence ni absence d'annonce, ni annonce ajoutée ou modifiée, ni scan terminé ou repris : seul le scan fait cela. Un scan interrompu reste à reprendre.
6. **Problèmes** : `BROWSER_UNAVAILABLE`, `VINTED_UNREACHABLE`, `WARDROBE_UNREADABLE`, `NO_STATISTICS_SHOWN`, `REFRESH_FAILED` (imprévu) ; montré jusqu'au rafraîchissement suivant, perdu au redémarrage (comme le problème du scan). Le navigateur est toujours libéré.
7. **Journal** (US-23) : action `STATISTICS_REFRESH`, lancée (`STARTED`), puis réussie (`SUCCEEDED`) ou en échec (`FAILED`, avec le code du problème).
8. **Aperçu du dressing** : `statisticsRefreshInProgress`, `lastStatisticsReadAt` (date du relevé le plus récent, qu'il vienne d'un scan ou d'un rafraîchissement ; absente si aucun) et `statisticsRefreshProblem`.
9. **Page du dressing** : le bouton est désactivé pendant le rafraîchissement ; une ligne indique « Vues et favoris relevés le … » ou « Rafraîchissement en cours… », et le problème éventuel. La liste se recharge à la fin.

## Contrat d'API
- `POST /api/wardrobe/statistics-refresh` → `202` `WardrobeOverview` (lancé ou ignoré) ; `403` autre site.
- `WardrobeOverview` gagne `statisticsRefreshInProgress` (booléen, obligatoire), `lastStatisticsReadAt` (date-heure, facultative), `statisticsRefreshProblem` (`StatisticsRefreshProblem`, facultatif).
- Nouvel enum `StatisticsRefreshProblem` : `BROWSER_UNAVAILABLE`, `VINTED_UNREACHABLE`, `WARDROBE_UNREADABLE`, `NO_STATISTICS_SHOWN`, `REFRESH_FAILED`.
- `BrowserAction` gagne `REFRESHING_STATISTICS` ; `JournaledAction` gagne `STATISTICS_REFRESH`.

## Critères d'acceptation
- [x] Un rafraîchissement ajoute un relevé par carte qui montre ses nombres, à la date du parcours, sans ouvrir aucune annonce.
- [x] Il ne change ni les annonces, ni leur présence, ni les scans.
- [x] Un dressing incomplet, ou sans aucun nombre affiché, n'ajoute aucun relevé et donne son problème.
- [x] Il est ignoré pendant une autre action navigateur, et libère toujours le navigateur.
- [x] Il est journalisé (lancé, réussi ou en échec avec sa raison).
- [x] L'API et la page le lancent et en montrent l'état, la date du dernier relevé et le problème.

## Tests d'acceptation
### AT-25-01 – Relevé de toutes les cartes sans ouvrir d'annonce
```gherkin
Étant donné un dressing de trois annonces connues dont les cartes montrent vues et favoris
Quand je rafraîchis les statistiques
Alors chaque annonce gagne un relevé à la date du parcours
Et aucune annonce n'est ouverte
Et la date du dernier relevé est celle du parcours
```
Test : `RefreshStatisticsAcceptanceTest.records_a_reading_per_card_without_opening_any_listing()`

### AT-25-02 – Sans effet sur les annonces ni sur les scans
```gherkin
Étant donné une annonce connue absente du dressing, une annonce inconnue présente, et un scan interrompu
Quand je rafraîchis les statistiques
Alors l'annonce absente reste présente dans l'outil, l'inconnue n'est pas ajoutée mais ses nombres sont relevés
Et le scan interrompu reste à reprendre
```
Test : `RefreshStatisticsAcceptanceTest.changes_neither_listings_nor_scans()`

### AT-25-03 – Dressing incomplet
```gherkin
Étant donné un dressing qui annonce quatre annonces et n'en montre que trois
Quand je rafraîchis les statistiques
Alors aucun relevé n'est ajouté et le problème est WARDROBE_UNREADABLE
```
Test : `RefreshStatisticsAcceptanceTest.incomplete_wardrobe_records_nothing()`

### AT-25-04 – Aucun nombre affiché
```gherkin
Étant donné un dressing dont aucune carte ne montre vues et favoris
Quand je rafraîchis les statistiques
Alors aucun relevé n'est ajouté et le problème est NO_STATISTICS_SHOWN
```
Test : `RefreshStatisticsAcceptanceTest.no_statistics_shown_is_a_problem()`

### AT-25-05 – Navigateur déjà occupé
```gherkin
Étant donné un scan en cours
Quand je rafraîchis les statistiques
Alors rien n'est lu ni relevé, et le scan reste l'action en cours
```
Test : `RefreshStatisticsAcceptanceTest.is_ignored_while_the_browser_is_busy()`

### AT-25-06 – Échecs du navigateur ou de Vinted
```gherkin
Étant donné un navigateur qui ne démarre pas, puis Vinted qui refuse le dressing, puis une erreur imprévue
Quand je rafraîchis les statistiques à chaque fois
Alors le problème est BROWSER_UNAVAILABLE, puis VINTED_UNREACHABLE, puis REFRESH_FAILED
Et le navigateur est libéré, et un rafraîchissement réussi efface le problème
```
Test : `RefreshStatisticsAcceptanceTest.failures_are_reported_and_free_the_browser()`

### AT-25-07 – Journal
```gherkin
Quand je rafraîchis les statistiques avec succès, puis sur un dressing incomplet
Alors le journal montre STATISTICS_REFRESH lancé puis réussi, puis lancé puis en échec (WARDROBE_UNREADABLE)
```
Test : `RefreshStatisticsAcceptanceTest.is_journaled()`

### AT-25-08 – API et page
```gherkin
Quand la page demande le rafraîchissement
Alors l'API répond 202 avec l'aperçu du dressing
Et la page montre la date du dernier relevé, le rafraîchissement en cours et le problème éventuel
```
Test : `WardrobeApiControllerTest.statistics_refresh_is_accepted_and_shows_the_overview()`, `WardrobePageTest.statistics_refresh_button_and_state()`

## Cas limites et erreurs
- Une carte sans ses nombres alors que d'autres les montrent : seules celles qui les montrent sont relevées (comme au scan).
- Rafraîchissement juste après un scan : deux relevés proches, gardés tous deux (historique, jamais remplacé).
- Redémarrage pendant un rafraîchissement : les relevés sont ajoutés en une fois (tout ou rien) ; rien à reprendre.

## Hors périmètre
- Rafraîchissement automatique planifié, ou pendant « Tout lancer ».
- Mise à jour des titres et prix, détection des annonces nouvelles ou parties : c'est le scan.
- Comparaison avant et après republication, cumul : US-24.

## Décisions
Décidé par Claude sur délégation de l'utilisateur (2026-11-17, « fais US-24 et US-25 comme tu le sens ») :
- Rafraîchir = parcourir les cartes du dressing seulement : ce sont elles qui montrent les nombres (US-07), et c'est le seul moyen d'être rapide et discret vis-à-vis de Vinted.
- Même règle de confiance que le scan (tout ou rien sur un dressing incomplet), plus un garde-fou propre : aucun nombre affiché n'est pas un succès (sinon un membre déconnecté verrait un rafraîchissement « réussi » sans rien).
- Problème propre au rafraîchissement, séparé de celui du scan : un rafraîchissement raté ne doit pas masquer ni effacer le problème du dernier scan.
- Pas de planification : l'outil n'agit jamais seul sur Vinted (même prudence que US-19).
- Pendant une série « Tout lancer » : rafraîchissement accepté entre deux relances (il n'écrit rien sur Vinted) ; la série le traite comme un navigateur occupé et réessaie 30 s plus tard.

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `RefreshStatistics` (application) : cas d'usage ; s'appuie sur `WardrobeReader.read` avec une visite qui n'ouvre aucune annonce.
- `StatisticsRefreshProblem` (enum), `LastStatisticsRefreshProblem` (problème du dernier rafraîchissement, en mémoire).
- `NoStatisticsShown` (exception du domaine).
- `BrowserAction.REFRESHING_STATISTICS`, `JournaledAction.STATISTICS_REFRESH`.
- `WardrobeOverview` gagne l'état du rafraîchissement, la date du dernier relevé et son problème ; `ListingStatisticsRepository.findLastReadAt()`.

## Tests unitaires et d'intégration attendus
- `domain` : `WardrobeListingTest` (statistiques relevées : aucune → `NoStatisticsShown`).
- `application` : couverts par `RefreshStatisticsAcceptanceTest`.
- `adapter/out/persistence` : `JpaListingStatisticsRepositoryTest` (`findLastReadAt`).
- `adapter/in/web` : `WardrobeApiControllerTest`, `ContractEnumsTest`, `WardrobePageTest`.
