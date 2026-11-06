# US-04 – Scanner mon dressing

**Statut** : validée
**Priorité** : MVP
**Dépend de** : US-02, US-03

## Contexte
Avant de sauvegarder, supprimer ou republier, l'outil doit savoir quelles annonces contient le dressing. Le scan est le premier accès automatisé à Vinted portant sur plusieurs tranches d'annonces ; il ne fait que lire, aucune annonce n'est modifiée.

## Objectif
En tant que membre, je veux lancer un scan de mon dressing, afin que l'outil connaisse mes annonces actives et puisse les traiter ensuite.

## Comportement attendu
1. Une page web minimale (`/wardrobe.html`, HTML statique + JS) affiche un bouton « Scanner », la date et le nombre d'annonces du dernier scan réussi, un éventuel problème, et la liste simple des annonces présentes (titre, prix, lien vers Vinted). Elle n'appelle que l'API REST. Tant qu'un scan est en cours, elle affiche « scan en cours » et relit l'état toutes les 2 secondes.
2. « Scanner » lance le scan en arrière-plan. Il partage le verrou « une seule action navigateur » d'US-03 : une demande reçue pendant une action en cours est ignorée. Aucune session connectée n'est exigée.
3. L'outil ouvre `VINTED_WARDROBE_URL` dans un onglet du Chrome piloté et lit le total affiché par l'en-tête du dressing (« 146 articles »).
4. Il obtient la première tranche par une seule requête lancée depuis la page (`GET /api/v2/wardrobe/<id>/items?page=1&per_page=20`), identique à celles de la page.
5. Il défile vers le bas, avec une pause aléatoire de 3 à 6 secondes entre deux défilements, et capte les réponses JSON de `/api/v2/wardrobe/<id>/items` déclenchées par la page.
6. Il s'arrête quand le nombre d'annonces distinctes lues atteint le total affiché, ou après 30 secondes sans nouvelle tranche. Il ferme alors l'onglet.
7. Le scan est complet si au moins une annonce a été lue et si le nombre d'annonces lues, actives ou non, est égal au total affiché. Sinon il échoue et la base ne change pas.
8. Une annonce est retenue si elle est active : `is_draft`, `is_closed`, `is_reserved`, `is_hidden` et `is_processing` sont tous faux.
9. Un scan complet est enregistré en une transaction : les annonces retenues sont créées ou mises à jour (URL, titre, prix), marquées présentes, avec `last_seen_at` à la date du scan. Les autres annonces connues sont marquées absentes. Aucune ligne n'est supprimée.

## Contrat d'API
Contract first : `src/main/resources/static/openapi/vinted-republisher.yaml`.

| Méthode et chemin | operationId | Réponse |
|---|---|---|
| `GET /api/wardrobe` | `showWardrobe` | 200, `WardrobeOverview` : `scanInProgress` (booléen), `lastScanAt` (absent = aucun scan réussi), `problem` (`BROWSER_UNAVAILABLE` / `VINTED_UNREACHABLE` / `WARDROBE_UNREADABLE` / `SCAN_FAILED`, absent = aucun), `listings` (annonces présentes : `vintedId`, `title`, `price` avec `amount` et `currency`, `url`) |
| `POST /api/wardrobe/scan` | `scanWardrobe` | 202 + `WardrobeOverview` juste après la demande, scan lancé en arrière-plan (ou ignoré si une action navigateur est en cours) |

`SessionOverview.currentAction` gagne la valeur `SCANNING`, affichée « scan en cours » sur la page de la session.

Toute demande `POST` venue d'un autre site que l'application reçoit `403`, sans rien lancer (`SameOriginFilter`).

## Critères d'acceptation
- [x] Le scan n'envoie que des requêtes de lecture à Vinted et ne modifie rien sur le compte.
- [x] Toutes les annonces actives du dressing sont enregistrées avec identifiant Vinted, URL, titre et prix.
- [x] Les annonces réservées, fermées, masquées, brouillons ou en cours de traitement ne sont pas retenues.
- [x] Un nouveau scan met à jour les annonces connues, sans créer de doublon.
- [x] Une annonce qui n'est plus retenue reste en base, marquée absente.
- [x] Un scan en échec ou incomplet ne change rien en base.
- [x] Toute réponse HTTP ≥ 400 sur la page du dressing ou sur une tranche d'annonces arrête le scan, sans nouvelle tentative automatique.
- [x] La pause entre deux défilements est comprise entre 3 et 6 secondes.
- [x] Une seule action navigateur a lieu à la fois.
- [x] Le dernier inventaire reste consultable après un redémarrage de l'application.
- [x] Un autre site ouvert dans le navigateur ne peut pas lancer de scan, et l'application n'écoute que sur 127.0.0.1.
- [x] Aucun test automatisé ne contacte le vrai site Vinted.

## Tests d'acceptation

### AT-04-01 – Scan d'un dressing
```gherkin
Étant donné un dressing de 45 annonces actives réparties sur 3 tranches
Quand je lance le scan
Alors les 45 annonces sont enregistrées avec leur identifiant, URL, titre et prix
Et la date du dernier scan est enregistrée
```
Test : `ScanWardrobeAcceptanceTest.scan_records_every_active_listing()`

### AT-04-02 – Annonces non actives ignorées
```gherkin
Étant donné un dressing contenant une annonce réservée, une fermée, une masquée, un brouillon et une en cours de traitement
Quand je lance le scan
Alors seules les annonces actives sont enregistrées
Et le total lu tient compte de toutes les annonces
```
Test : `ScanWardrobeAcceptanceTest.only_active_listings_are_kept()`

### AT-04-03 – Rescan
```gherkin
Étant donné une annonce connue dont le prix a changé et une annonce connue qui n'est plus dans le dressing
Quand je lance le scan
Alors la première est mise à jour, sans doublon
Et la seconde reste en base, marquée absente
Et elle n'apparaît plus dans la liste
```
Test : `ScanWardrobeAcceptanceTest.rescan_updates_known_listings_and_keeps_missing_ones()`

### AT-04-04 – Total incohérent
```gherkin
Étant donné un inventaire existant et un dressing dont l'en-tête annonce plus d'annonces que Vinted n'en renvoie
Quand je lance le scan
Alors le scan échoue avec le problème "dressing illisible"
Et l'inventaire existant est inchangé
```
Test : `ScanWardrobeAcceptanceTest.incomplete_scan_leaves_the_inventory_unchanged()`

### AT-04-05 – Aucune annonce lue
```gherkin
Étant donné un inventaire existant et aucune annonce renvoyée par Vinted
Quand je lance le scan
Alors le scan échoue avec le problème "dressing illisible"
Et l'inventaire existant est inchangé
```
Test : `ScanWardrobeAcceptanceTest.scan_without_any_listing_fails()`

### AT-04-06 – Blocage en cours de scan
```gherkin
Étant donné une tranche que Vinted refuse par une erreur HTTP
Quand le scan atteint cette tranche
Alors le scan s'arrête sans nouvelle tentative
Et le problème indique que Vinted est inaccessible
Et l'inventaire existant est inchangé
```
Test : `ScanWardrobeAcceptanceTest.http_error_stops_the_scan()` et `PlaywrightWardrobeReaderTest.Blocked_wardrobe`

### AT-04-07 – Navigateur indisponible
```gherkin
Étant donné un navigateur injoignable
Quand je lance le scan
Alors le problème indique que le navigateur est indisponible
Et l'inventaire existant est inchangé
```
Test : `ScanWardrobeAcceptanceTest.unavailable_browser_fails_the_scan()`

### AT-04-08 – Une seule action navigateur à la fois
```gherkin
Étant donné une action navigateur en cours
Quand je demande un scan, ou une vérification pendant un scan
Alors la demande est ignorée
```
Test : `ScanWardrobeAcceptanceTest.request_during_a_browser_action_is_ignored()`

### AT-04-09 – Lecture sur un faux dressing local
```gherkin
Étant donné un faux dressing servi localement, à chargement progressif au défilement
Quand l'adaptateur navigateur lit le dressing
Alors il renvoie le total affiché et toutes les annonces, sans doublon
Et le faux site n'a reçu que des requêtes de lecture
Et l'onglet utilisé est fermé
```
Test : `PlaywrightWardrobeReaderTest.Reading` (pages locales, nécessite Chrome, aucun accès au vrai site)

### AT-04-10 – Rythme du défilement
```gherkin
Étant donné un scan en cours
Quand l'outil défile plusieurs fois
Alors chaque pause dure entre 3 et 6 secondes
```
Test : `ScanWardrobeAcceptanceTest.pauses_stay_within_the_configured_bounds()` (horloge et aléa simulés)

### AT-04-11 – Conservation en base
```gherkin
Étant donné un scan réussi
Quand l'application redémarre
Alors l'inventaire et la date du dernier scan restent affichés
```
Test : `ListingPersistenceAcceptanceTest.inventory_survives_a_restart()` (`@Tag("docker")`)

### AT-04-12 – Page du dressing
```gherkin
Étant donné un inventaire de deux annonces
Quand j'ouvre la page du dressing
Alors je vois la date du scan, le nombre d'annonces, leur titre, leur prix et leur lien
Et un bouton "Scanner"
```
Test : `WardrobePageTest.shows_the_inventory_and_offers_to_scan()` (page statique dans Chrome, API simulée)

### AT-04-13 – API conforme au contrat
```gherkin
Étant donné le contrat OpenAPI du dressing
Quand j'appelle GET /api/wardrobe et POST /api/wardrobe/scan
Alors les réponses ont les codes et le format JSON du contrat
Et le scan est lancé en arrière-plan
```
Test : `WardrobeApiControllerTest` (`@WebMvcTest`)

### AT-04-15 – Demande venue d'un autre site
```gherkin
Étant donné un autre site ouvert dans mon navigateur
Quand il demande un scan à l'application
Alors la demande est refusée
Et aucun scan n'est lancé
```
Test : `WardrobeApiControllerTest.scan_requested_by_another_site_is_refused()`

### AT-04-14 – Scan réel
```gherkin
Étant donné l'application lancée avec le profil local et ma validation explicite
Quand je lance un scan de mon vrai dressing
Alors le scan réussit
Et le nombre et le contenu des annonces correspondent à mon dressing
```
Test : manuel (vrai compte Vinted, non automatisable). Ce test vérifie aussi les points inconnus à ce jour : la requête de la première tranche passe sans en-tête particulier ; le total de l'en-tête vu depuis le Chrome connecté correspond aux annonces renvoyées, y compris masquées ; aucun autre élément de la page ne ressemble à ce total ; le sens de `is_processing` ; le JSON expose-t-il un total, contrôle plus sûr que le texte de l'en-tête. Tout écart se traite par une mise à jour de cette spec.

## Cas limites et erreurs
- Réponse JSON de format inattendu, ou tranche redirigée ailleurs : problème « dressing illisible », base inchangée.
- Erreur HTTP sur une autre ressource de la page (publicité, traceur) : ignorée, elle ne dit rien du dressing.
- Toutes les annonces lues sont non actives : le scan réussit, tout l'inventaire passe absent, et la date du dernier scan affichée reste l'ancienne (limite acceptée, cas très improbable).
- En-tête sans total lisible, ou plusieurs totaux différents sur la page : problème « dressing illisible », base inchangée.
- Dressing réellement vide : traité comme illisible (zéro annonce lue se confond avec une page changée, et marquerait tout l'inventaire absent).
- Annonce publiée ou retirée pendant le scan : le total ne correspond plus, le scan échoue et l'utilisateur le relance.
- Même annonce reçue deux fois : comptée une fois.
- Erreur inattendue, ou base indisponible : problème « échec du scan » (détail dans les logs), transaction annulée, le verrou est libéré.
- Application arrêtée pendant un scan : le scan est perdu, la base reste inchangée, l'utilisateur le relance ; aucune reprise.
- `VINTED_WARDROBE_URL` absente : l'application ne démarre pas.
- L'annonce d'une ligne absente réapparaît à un scan suivant : elle redevient présente, sans nouvelle ligne.

## Hors périmètre
- Contenu détaillé d'une annonce (US-05), images (US-06), vues et likes (US-07), bien que le JSON contienne `favourite_count`.
- Filtres, tri et recherche (US-18).
- Reprise après plantage (US-16).
- Limites de cadence générales (US-27).
- Suivi d'une annonce republiée sous un nouvel identifiant (US-12).

## Décisions
- Le scan produit un inventaire conservé en PostgreSQL (table `listing`, migration Liquibase dans cette US) : identifiant Vinted, URL, titre, prix. Il survit à un redémarrage et sert de base aux US-05 à US-09 ; le contenu complet reste dans US-05.
- Seules les annonces actives entrent dans l'inventaire : réservées, vendues et masquées sont ignorées. Aucun statut Vinted n'est donc stocké.
- Le dressing est désigné par une URL configurée : `VINTED_WARDROBE_URL` (obligatoire, dans `.env` pour le profil `local`), de la forme `https://www.vinted.fr/member/<id>`.
- Observation du dressing public (2026-11-06, visiteur, en lecture seule, navigateur intégré de l'app et non le Chrome piloté) : l'en-tête affiche le total (« 146 articles ») et les 146 annonces ont été retrouvées, sans doublon, en défilant jusqu'en bas (quelques secondes d'attente par tranche). Les 20 premières sont dans le HTML initial, sans indicateurs d'état. Les suivantes arrivent par `GET /api/v2/wardrobe/<id>/items?page=N&per_page=20`, JSON avec notamment `id`, `title`, `price.amount`, `price.currency_code`, `url`, `is_draft`, `is_closed`, `is_reserved`, `is_hidden`, `is_processing`. Aucune annonce non active vue en visiteur ; la vue du membre connecté n'a pas été observée.
- Lecture : défilement réel et capture des réponses JSON de la page, plus une seule requête identique pour la première tranche, absente de ces réponses. Écartés : la lecture du DOM seul (texte à analyser, marques d'état jamais observées) et les appels API seuls (éloignés d'un usage humain).
- Une annonce est active si `is_draft`, `is_closed`, `is_reserved`, `is_hidden` et `is_processing` sont tous faux.
- Contrôle : le nombre d'annonces lues (actives ou non) est comparé au total affiché par l'en-tête. L'API étant interne et non documentée, ce contrôle détecte un changement de Vinted.
- Scan tout ou rien : en cas d'échec ou de total incohérent, la base ne change pas. Après un scan réussi, les annonces connues sont mises à jour et celles qui ne sont plus retenues restent en base, absentes et non éligibles. Aucune ligne n'est jamais supprimée (elles porteront les sauvegardes des US suivantes).
- Zéro annonce lue est un échec, jamais un scan réussi.
- Le scan n'exige aucune session connectée : le dressing est public et seules les annonces actives sont retenues. Il partage le verrou « une seule action navigateur » d'US-03 (nouvelle action `SCANNING`).
- Rythme : pause aléatoire de 3 à 6 secondes entre deux défilements, un seul onglet, un seul scan à la fois. Toute réponse HTTP ≥ 400 arrête le scan, sans nouvelle tentative automatique. Valeurs en configuration (`vinted.scan.min-pause`, `vinted.scan.max-pause`, `vinted.scan.stall-timeout` à 30 secondes). US-27 généralisera ces limites.
- Date du dernier scan réussi : la plus grande valeur de `last_seen_at`, sans table de scan (YAGNI). Le problème et l'état du scan restent en mémoire, comme l'état de session d'US-03.
- Page `wardrobe.html` avec liste simple, pour contrôler le résultat sans outil de base de données.
- Sécurité décidée sur revue : `server.address: 127.0.0.1` dans `application.yml`, et refus des `POST` dont l'`Origin` diffère de l'application, sur toute l'API, car celle-ci n'a pas d'authentification et pilote un Chrome connecté au compte.
- Décisions prises à l'implémentation : l'arrêt sur HTTP ≥ 400 vise la page du dressing et les tranches d'annonces, pas les ressources tierces de la page ; le lien d'une annonce doit être en `http` ou `https` (il finit dans une page) ; l'onglet du scan passe au premier plan, Chrome ne chargeant pas la suite dans un onglet en arrière-plan ; un scan n'efface pas le problème de session, car il ne dit rien de la session ; la raison d'un échec est écrite dans les logs.
- Aucune nouvelle dépendance.

## Questions ouvertes
- Aucune. Les trois points inconnus sont vérifiés par AT-04-14.

## Modèle de domaine
Ajouts au glossaire :
- `Listing` (annonce, agrégat) : `VintedListingId` (identifiant Vinted), URL, titre, `Price`, présence, `lastSeenAt`.
- `Price` (prix, value object) : montant décimal et devise.
- `ListingStatusFlags` : indicateurs lus sur Vinted, qui disent si une annonce est active ; jamais stockés.
- `WardrobeScan` (scan du dressing) : lecture complète du dressing par le Chrome piloté.
- `WardrobeSnapshot` : résultat brut d'une lecture, total affiché et annonces lues ; dit si le scan est complet et produit l'inventaire à jour.
- `ScannedListing` (annonce lue), `InvalidListing`, `ScanPace` (rythme du scan), `WardrobeOverview` (aperçu du dressing).
- `ScanProblem` (`BROWSER_UNAVAILABLE`, `VINTED_UNREACHABLE`, `WARDROBE_UNREADABLE`, `SCAN_FAILED`).
- `BrowserAction` gagne `SCANNING`.
- Ports sortants : `WardrobeReader` (`read`), `ListingRepository`. Cas d'usage : `ScanWardrobe`, `ShowWardrobe`.

Table `listing` : `vinted_id` (`bigint`, clé primaire), `url` (`text`), `title` (`text`), `price_amount` (`numeric(10,2)`), `price_currency` (`char(3)`), `present` (`boolean`), `last_seen_at` (`timestamptz`), tous non nuls.

## Tests unitaires et d'intégration attendus
- `domain` : `WardrobeSnapshotTest` (complet, total incohérent, zéro annonce, doublons), `ListingStatusFlagsTest` (actif ou non, chaque indicateur), `ListingTest` (mise à jour, absence, réapparition), `PriceTest`.
- `application` : `ScanWardrobeTest` avec faux `WardrobeReader` et faux `ListingRepository` (scan réussi, chaque problème, action déjà en cours, verrou libéré après erreur), `ShowWardrobeTest`.
- `adapter/out/browser` : `WardrobeItemsTest` (JSON d'une tranche, sans navigateur) et `PlaywrightWardrobeReaderTest` contre des pages locales avec le vrai Chrome (lecture complète, pauses, erreur HTTP, tranche redirigée, tranche jamais reçue, JSON inattendu, en-tête illisible, ressources tierces en erreur, onglet fermé).
- `adapter/out/persistence` : test Testcontainers du repository (création, mise à jour, marquage absent, date du dernier scan, annulation de la transaction).
- `adapter/in/web` : `WardrobeApiControllerTest`, `ContractEnumsTest` étendu, `WardrobePageTest`.
