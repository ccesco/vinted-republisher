# US-42 – Suivi léger du dressing
**Statut** : implémentée
**Priorité** : V2
**Dépend de** : US-04 (scan), US-05 (sauvegarde), US-14 (inventaire et étapes), US-19 (série « Tout lancer »), US-37 (interface Angular), US-41 (file d'attente)

## Contexte
Pendant un scan, une sauvegarde, un rafraîchissement des vues et favoris ou une série, la page « Dressing » interroge l'API toutes les 2 secondes et relit à chaque fois l'inventaire complet (`GET /api/listings`), même si rien n'a changé. Si une série et un scan tournent ensemble, chacun relance son propre suivi : l'inventaire est relu deux fois par période.

Chaque lecture de l'inventaire (`ShowListingInventory`) fait une dizaine de requêtes et charge toutes les sauvegardes complètes (description, toutes les colonnes du formulaire, lignes `listing_photo`), alors que la liste n'en affiche que la date de sauvegarde et le nombre de photos. La série (`LaunchAutomaticRun`) et la file d'attente (`ShowRunQueue`) refont la même lecture complète.

Le coût reste faible avec quelques centaines d'annonces, mais il croît avec le dressing et l'historique des relevés, et il est payé en continu pendant des actions qui durent des heures.

## Objectif
En tant que membre, je veux que la page « Dressing » suive les actions en cours sans relire tout l'inventaire quand rien n'a changé, afin que l'outil reste fluide quand le dressing et son historique grossissent.

## Comportement attendu
1. **Un seul suivi** : la page n'a jamais plus d'un rafraîchissement de la liste en attente, même si plusieurs actions sont suivies en même temps (scan et série, par exemple).
2. **Relecture sur changement pendant une série** : pendant le suivi d'une série, la liste n'est relue que si l'aperçu de la série reçu diffère, en quoi que ce soit, du précédent. Elle est toujours relue une dernière fois à la fin de la série, comme aujourd'hui.
3. **Scan, sauvegarde, rafraîchissement** : inchangé, la liste est relue à chaque période (ces actions enregistrent une annonce toutes les 5 à 15 secondes), puis une dernière fois à la fin. Si l'une d'elles tourne pendant une série, cette relecture par période vaut aussi pour la série.
4. **Inventaire allégé** : l'inventaire ne lit de chaque sauvegarde que ce dont il a besoin (identifiant, date de sauvegarde, nombre de photos), sans la description, les autres champs du formulaire ni les lignes de photos. La réponse de `GET /api/listings` est inchangée.
5. Les critères de la liste (recherche, filtres, tri) déclenchent toujours une relecture, comme aujourd'hui.

## Contrat d'API
Contract first : `src/main/resources/static/openapi/vinted-republisher.yaml`.

Aucun changement de contrat : `GET /api/listings`, `WardrobeOverview` et `AutomaticRunOverview` restent tels quels.

## Critères d'acceptation
- [x] Pendant une série, la liste n'est relue que quand l'aperçu de la série diffère du précédent, puis une dernière fois à sa fin (AT-42-01, AT-42-02).
- [x] Le message d'une ligne relancée à la main pendant une série reste affiché tant que la série ne change pas (AT-42-07).
- [x] Pendant un scan, une sauvegarde ou un rafraîchissement, la liste reste relue à chaque période, puis une dernière fois à la fin (AT-42-03, AT-42-06).
- [x] Avec une série et un scan en cours ensemble, la liste n'est jamais relue plus d'une fois par période de suivi (AT-42-04).
- [x] L'inventaire donne la même réponse qu'avant (AT-42-05) sans charger ni description ni photos des sauvegardes (test de persistance des résumés).
- [x] La série, la file d'attente, les annonces en échec et la vérification en lot donnent les mêmes résultats qu'avant (tests d'acceptation existants d'US-19, US-39, US-40 et US-41, inchangés).

## Tests d'acceptation

### AT-42-01 – Série sans changement : pas de relecture
```gherkin
Étant donné une série en cours dont l'aperçu ne change pas pendant trois périodes de suivi
Quand le membre reste sur la page « Dressing »
Alors la liste n'est pas relue pendant ces trois périodes
```
Test : `WardrobePageTest.run_overview_unchanged_does_not_reload_listings()`

### AT-42-02 – Série qui avance : relecture
```gherkin
Étant donné une série en cours
Quand son nombre d'annonces relancées passe de 3 à 4
Alors la liste est relue une fois
```
Test : `WardrobePageTest.run_progress_reloads_listings()`

### AT-42-03 – Fin de l'action : dernière relecture
```gherkin
Étant donné un scan en cours
Quand le scan se termine
Alors la liste est relue une dernière fois et le suivi s'arrête
```
Test : `WardrobePageTest.scan_end_reloads_listings_once_more()`

### AT-42-04 – Deux actions suivies : un seul suivi
```gherkin
Étant donné une série en cours
Quand le membre lance un scan
Alors la liste n'est relue qu'une fois par période de suivi au plus
```
Test : `WardrobePageTest.run_and_scan_share_one_follow_up()`

### AT-42-05 – Inventaire allégé, réponse inchangée
```gherkin
Étant donné une annonce sauvegardée avec 5 photos et validée, une annonce sauvegardée dont la dernière vérification a échoué, et une annonce sans sauvegarde
Quand le membre affiche l'inventaire
Alors la première a sa date de sauvegarde, 5 photos et sa date de validation
Et la deuxième est à l'étape « vérification échouée »
Et la troisième n'a pas de sauvegarde
```
Test : `ShowListingInventoryAcceptanceTest.inventory_shows_backup_summaries()`

### AT-42-06 – Scan en cours : relecture à chaque période
```gherkin
Étant donné un scan en cours
Quand trois périodes de suivi passent sans que l'aperçu du dressing change
Alors la liste est relue à chacune des trois périodes
```
Test : `WardrobePageTest.scan_in_progress_reloads_listings_every_period()`

### AT-42-07 – Message d'une relance manuelle gardé pendant la série
```gherkin
Étant donné une série en cours
Quand le membre relance une annonce depuis sa ligne et que l'aperçu de la série ne change pas pendant deux périodes
Alors le message de la relance reste affiché sous la ligne
```
Test : `WardrobePageTest.manual_relaunch_message_stays_while_run_unchanged()`

## Cas limites et erreurs
- API injoignable pendant le suivi : comme aujourd'hui, le suivi réessaie à la période suivante ; la liste garde son dernier état.
- Changement de critères pendant un suivi : la relecture demandée par le membre passe tout de suite, sans attendre la période ; la garde existante sur la dernière requête reste.
- Action déjà terminée à l'ouverture de la page : une seule lecture, aucun suivi.
- Relance d'une ligne à la main pendant une série : la ligne prend la réponse, et son message reste jusqu'au prochain changement de la série.

## Hors périmètre
- Cache HTTP des photos (`ETag`), à traiter à part.
- Doublon possible de `findAllLatest()` pour les republications, à traiter à part.
- Pagination ou virtualisation de la liste.
- Requêtes de statistiques (`DISTINCT ON`) : pas de changement sans `EXPLAIN` qui le justifie.
- Push serveur (SSE, WebSocket) à la place du suivi par périodes.

## Décisions
- Une seule US pour l'interface (un suivi, relecture sur changement) et le serveur (inventaire allégé) : même besoin, chacun petit. Commits séparés : d'abord l'inventaire allégé (sans effet visible), puis le suivi.
- Inventaire allégé : value object `BackupSummary` (identifiant, date de sauvegarde, nombre de photos) lu par un nouveau port `ListingBackupRepository.findAllSummaries()` (une requête, photos comptées en SQL), porté par `InventoryEntry` à la place de `ListingBackup` ; `BackupValidation.holdsFor`, `VerificationResult.holdsFor` et `failsFor` prennent un résumé. `ShowListingDetail` lit une seule sauvegarde complète et en tire le résumé. Écarté : garder `ListingBackup` en chargement paresseux (le domaine dépendrait de JPA). Vérifié : aucun utilisateur de `InventoryEntry.backup()` (liste, filtres, tri, série, file d'attente, annonces en échec, vérification en lot, fiche) n'a besoin du contenu.
- Scan, sauvegarde et rafraîchissement gardent la relecture à chaque période : ils enregistrent une annonce toutes les 5 à 15 secondes, et l'inventaire allégé rend cette relecture bon marché. Écarté : des compteurs de progression dans `WardrobeOverview` (contrat et état de trois actions changés pour passer de 5 lectures à 1 toutes les 10 secondes). La relecture sur changement ne vaut que pour la série, qui change au plus une fois par minute (cadence).
- Changement de série = toute différence entre l'aperçu reçu et le précédent (comparaison de l'objet entier) : aucune liste de champs à tenir à jour, et une relance peut écrire dès son début. Écarté : ne regarder que `status`, `relaunchCount`, `currentListingIds`, `skippedListingIds`.
- Actions à la main pendant une série : rien à ajouter. La relance depuis une ligne remplace la ligne par sa réponse, l'édition de tags relit la liste, la validation se fait sur la fiche (liste relue au retour), la vérification en lot n'est pas proposée pendant une série. Le message d'une ligne relancée reste affiché jusqu'au prochain changement de la série (aujourd'hui effacé toutes les 2 secondes).
- Période de suivi inchangée, 2 secondes (`POLL_INTERVAL_MS`) : l'aperçu de la série est en mémoire, sans base ; seule la relecture de l'inventaire coûtait.
- Mesure manuelle notée dans la spec (section « Mesure »), sans seuil bloquant ni test automatisé de temps (instable selon la machine) ; la garantie automatisée est le test de persistance des résumés.
- Mise en œuvre (revue) : un seul limiteur (`OncePerPeriod`) sert toutes les relectures de suivi, fin de vérification en lot comprise ; une demande faite moins d'une période après la dernière relecture est différée à la fin de la période, jamais perdue. La dernière relecture d'une action peut donc arriver jusqu'à 2 secondes après sa fin. Le limiteur est fermé à la sortie de l'écran. Les relectures demandées par le membre (critères, tags) restent immédiates.

## Questions ouvertes
- (aucune)

## Mesure
Manuelle, sur la base de dev, avant et après : médiane du temps de réponse de `GET /api/listings` sur 20 appels et nombre de requêtes SQL par appel. Chiffres à noter ici.

| | Médiane `GET /api/listings` | Requêtes SQL par appel |
|---|---|---|
| Avant | 34,7 ms | 10 (dont les sauvegardes complètes et toutes les lignes de `listing_photo`) |
| Après | 29,2 ms | 9 (résumés : une requête, photos comptées par la base) |

Mesuré le 19/11/2026 sur la base de dev (142 annonces, 136 sauvegardes, 207 photos, 272 relevés), après un appel de chauffe ; réponse de 50 698 octets dans les deux cas. Le gain par lecture reste modeste à ce volume ; l'essentiel vient du suivi : pendant une série, la liste n'est plus relue toutes les 2 secondes (30 lectures par minute) mais seulement quand la série change (au plus une fois par minute avec la cadence).

## Modèle de domaine
- Ajout : `BackupSummary` (Résumé de sauvegarde) : identifiant de l'annonce, date de sauvegarde, nombre de photos ; ce que l'inventaire sait d'une sauvegarde.
- Changés : `InventoryEntry` (porte un `BackupSummary`), `ListingBackupRepository` (port `findAllSummaries()`), `BackupValidation` et `VerificationResult` (comparent un résumé).
- Inchangés : `WardrobeOverview` et `AutomaticRunOverview`.

## Tests unitaires et d'intégration attendus
- Domaine : `ListingInventoryTest` sur des `BackupSummary` (étapes, validation qui vaut pour la sauvegarde actuelle, échec de vérification) ; `BackupValidationTest` et `VerificationResultTest` sur un résumé.
- Adapter persistance (`@Tag("docker")`) : `JpaListingBackupRepositoryTest.find_all_summaries_counts_photos_without_loading_them()` (une seule requête, statistiques Hibernate).
- Adapter web : `ListingInventoryApiControllerTest`, réponse inchangée (`savedAt`, `photoCount`).
- Interface : logique pure « l'aperçu a changé » testée par Vitest (`wardrobe-follow.spec.ts`) ; pages : AT-42-01 à AT-42-04, AT-42-06 et AT-42-07 contre l'API simulée.
