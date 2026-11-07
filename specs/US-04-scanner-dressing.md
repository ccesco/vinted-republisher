# US-04 – Scanner mon dressing

**Statut** : validée
**Priorité** : MVP
**Dépend de** : US-02, US-03

## Contexte
Avant de sauvegarder, supprimer ou republier, l'outil doit savoir quelles annonces contient le dressing. Le scan ne fait que lire, aucune annonce n'est modifiée. Une première version lisait l'API interne de Vinted : son unique requête directe a été refusée (HTTP 403). Le scan se comporte désormais comme un membre qui parcourt son dressing puis ouvre chaque annonce, lentement et visiblement.

## Objectif
En tant que membre, je veux lancer un scan de mon dressing, afin que l'outil connaisse mes annonces actives et puisse les traiter ensuite.

## Comportement attendu
1. Une page web minimale (`/wardrobe.html`, HTML statique + JS) affiche un bouton « Scanner », la date et le nombre d'annonces du dernier scan terminé, un éventuel problème, et la liste simple des annonces présentes (titre, prix, lien vers Vinted). Elle n'appelle que l'API REST. Tant qu'un scan est en cours, elle affiche « scan en cours » et relit l'état toutes les 2 secondes.
2. « Scanner » lance le scan en arrière-plan. Il partage le verrou « une seule action navigateur » d'US-03 : une demande reçue pendant une action en cours est ignorée. Aucune session connectée n'est exigée.
3. **Reprise** : s'il existe un scan non terminé, le nouveau scan le reprend ; sinon un scan est créé avec sa date de début.
4. **Relevé** : l'outil ouvre `VINTED_WARDROBE_URL` dans un onglet du Chrome piloté, passé au premier plan, et lit le total affiché par l'en-tête. Il défile avec une pause aléatoire, en lisant dans la page les liens des annonces, jusqu'à en avoir autant que le total, ou après 30 secondes sans nouveau lien. Le nombre de liens doit égaler le total, et être non nul ; sinon le scan échoue et rien ne change.
5. **Lecture** : dans le même onglet, l'outil ouvre une à une les annonces non encore lues par ce scan, avec une pause aléatoire de 10 à 15 secondes entre deux annonces. Il lit le titre, le prix, la devise et la disponibilité dans les données structurées de la page (`Product` en JSON-LD).
6. Une annonce est active si sa disponibilité est `InStock`. Chaque annonce active est enregistrée aussitôt, créée ou mise à jour, marquée présente, avec `last_seen_at` à l'instant de sa lecture.
7. **Fin** : quand toutes les annonces ont été lues, l'outil ferme l'onglet, marque absentes les annonces présentes non vues depuis le début du scan ou sorties du dernier relevé, et termine le scan. Aucune ligne n'est supprimée.
8. **Échec** : le scan s'arrête, reste non terminé, et ce qui a été lu reste en base. Aucune annonce n'est marquée absente.

## Contrat d'API
Contract first : `src/main/resources/static/openapi/vinted-republisher.yaml`.

| Méthode et chemin | operationId | Réponse |
|---|---|---|
| `GET /api/wardrobe` | `showWardrobe` | 200, `WardrobeOverview` : `scanInProgress` (booléen), `lastScanAt` (date de fin du dernier scan terminé, absente = aucun), `problem` (`BROWSER_UNAVAILABLE` / `VINTED_UNREACHABLE` / `WARDROBE_UNREADABLE` / `SCAN_FAILED`, absent = aucun), `listings` (annonces présentes : `vintedId`, `title`, `price` avec `amount` et `currency`, `url`) |
| `POST /api/wardrobe/scan` | `scanWardrobe` | 202 + `WardrobeOverview` juste après la demande, scan lancé en arrière-plan (ou ignoré si une action navigateur est en cours) ; 403 si la demande vient d'un autre site |

`SessionOverview.currentAction` gagne la valeur `SCANNING`, affichée « scan en cours » sur la page de la session.

## Critères d'acceptation
- [ ] Le scan ne fait que des navigations de lecture et ne modifie rien sur le compte ; il n'envoie aucune requête directe à l'API de Vinted.
- [ ] Toutes les annonces actives du dressing sont enregistrées avec identifiant Vinted, URL, titre et prix.
- [ ] Une annonce dont la disponibilité n'est pas `InStock` n'est pas retenue.
- [ ] Chaque annonce est enregistrée dès sa lecture.
- [ ] Un scan interrompu se reprend sans relire les annonces déjà enregistrées par lui.
- [ ] Une annonce n'est marquée absente qu'à la fin d'un scan terminé.
- [ ] Un nouveau scan met à jour les annonces connues, sans créer de doublon.
- [ ] Toute réponse HTTP ≥ 400 sur la page du dressing ou sur une page d'annonce arrête le scan, sans nouvelle tentative automatique.
- [ ] La pause entre deux annonces est comprise entre 10 et 15 secondes.
- [ ] Une seule action navigateur a lieu à la fois.
- [ ] Le dernier inventaire reste consultable après un redémarrage de l'application.
- [ ] Un autre site ouvert dans le navigateur ne peut pas lancer de scan, et l'application n'écoute que sur 127.0.0.1.
- [ ] Aucun test automatisé ne contacte le vrai site Vinted.

## Tests d'acceptation

### AT-04-01 – Scan d'un dressing
```gherkin
Étant donné un dressing de 45 annonces actives
Quand je lance le scan
Alors les 45 annonces sont enregistrées avec leur identifiant, URL, titre et prix
Et la date de fin du scan est enregistrée
```
Test : `ScanWardrobeAcceptanceTest.scan_records_every_active_listing()`

### AT-04-02 – Annonces non actives ignorées
```gherkin
Étant donné un dressing contenant une annonce dont la disponibilité n'est pas "InStock"
Quand je lance le scan
Alors seules les annonces actives sont enregistrées
Et le scan se termine normalement
```
Test : `ScanWardrobeAcceptanceTest.only_active_listings_are_kept()`

### AT-04-03 – Rescan
```gherkin
Étant donné une annonce connue dont le prix a changé et une annonce connue qui n'est plus dans le dressing
Quand je lance le scan jusqu'à sa fin
Alors la première est mise à jour, sans doublon
Et la seconde reste en base, marquée absente
Et elle n'apparaît plus dans la liste
```
Test : `ScanWardrobeAcceptanceTest.rescan_updates_known_listings_and_keeps_missing_ones()`

### AT-04-04 – Relevé incohérent
```gherkin
Étant donné un inventaire existant et un dressing dont l'en-tête annonce plus d'annonces que la page n'en montre
Quand je lance le scan
Alors le scan échoue avec le problème "dressing illisible"
Et aucune annonce n'est ouverte
Et l'inventaire existant est inchangé
```
Test : `ScanWardrobeAcceptanceTest.incomplete_listing_of_the_wardrobe_changes_nothing()`

### AT-04-05 – Aucune annonce relevée
```gherkin
Étant donné un inventaire existant et aucune annonce sur la page du dressing
Quand je lance le scan
Alors le scan échoue avec le problème "dressing illisible"
Et l'inventaire existant est inchangé
```
Test : `ScanWardrobeAcceptanceTest.scan_without_any_listing_fails()`

### AT-04-06 – Blocage en cours de scan
```gherkin
Étant donné une annonce que Vinted refuse par une erreur HTTP
Quand le scan atteint cette annonce
Alors le scan s'arrête sans nouvelle tentative
Et le problème indique que Vinted est inaccessible
Et les annonces déjà lues restent enregistrées
Et aucune annonce n'est marquée absente
```
Test : `ScanWardrobeAcceptanceTest.http_error_stops_the_scan_and_keeps_what_was_read()` et `PlaywrightWardrobeReaderTest.Blocked_wardrobe`

### AT-04-07 – Reprise d'un scan interrompu
```gherkin
Étant donné un scan interrompu après avoir lu 2 annonces sur 3
Quand je lance le scan
Alors seule la troisième annonce est ouverte
Et le scan se termine
Et les annonces disparues sont alors marquées absentes
```
Test : `ScanWardrobeAcceptanceTest.interrupted_scan_resumes_with_what_is_left()`

### AT-04-08 – Navigateur indisponible
```gherkin
Étant donné un navigateur injoignable
Quand je lance le scan
Alors le problème indique que le navigateur est indisponible
Et l'inventaire existant est inchangé
```
Test : `ScanWardrobeAcceptanceTest.unavailable_browser_fails_the_scan()`

### AT-04-09 – Une seule action navigateur à la fois
```gherkin
Étant donné une action navigateur en cours
Quand je demande un scan, ou une vérification pendant un scan
Alors la demande est ignorée
```
Test : `ScanWardrobeAcceptanceTest.request_during_a_browser_action_is_ignored()`

### AT-04-10 – Lecture sur un faux dressing local
```gherkin
Étant donné un faux dressing et de fausses annonces servis localement, à chargement progressif au défilement
Quand l'adaptateur navigateur lit le dressing
Alors il relève le total affiché et tous les liens, sans doublon
Et il lit chaque annonce dans le même onglet
Et le faux site n'a reçu aucune requête vers son API
Et l'onglet utilisé est fermé
```
Test : `PlaywrightWardrobeReaderTest.Reading` (pages locales, nécessite Chrome, aucun accès au vrai site)

### AT-04-11 – Rythme de lecture
```gherkin
Étant donné un scan en cours
Quand l'outil passe d'une annonce à la suivante
Alors chaque pause dure entre 10 et 15 secondes
```
Test : `ScanWardrobeAcceptanceTest.pauses_stay_within_the_configured_bounds()` et `PlaywrightWardrobeReaderTest.Reading.pauses_between_two_listings()`

### AT-04-12 – Conservation en base
```gherkin
Étant donné un scan terminé
Quand l'application redémarre
Alors l'inventaire et la date du dernier scan restent affichés
```
Test : `ListingPersistenceAcceptanceTest.inventory_survives_a_restart()` (`@Tag("docker")`)

### AT-04-13 – Page du dressing
```gherkin
Étant donné un inventaire de deux annonces
Quand j'ouvre la page du dressing
Alors je vois la date du scan, le nombre d'annonces, leur titre, leur prix et leur lien
Et un bouton "Scanner"
```
Test : `WardrobePageTest.shows_the_inventory_and_offers_to_scan()` (page statique dans Chrome, API simulée)

### AT-04-14 – API conforme au contrat
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

### AT-04-16 – Scan réel
```gherkin
Étant donné l'application lancée avec le profil local et ma validation explicite
Quand je lance un scan de mon vrai dressing et que je le regarde
Alors le scan se termine
Et le nombre et le contenu des annonces correspondent à mon dressing
```
Test : manuel (vrai compte Vinted, non automatisable). Commencer par arrêter le scan après quelques annonces pour vérifier la reprise. Ce test vérifie aussi : la disponibilité affichée pour une annonce réservée, s'il y en a une ; le total de l'en-tête dans le Chrome connecté, y compris avec des annonces masquées. Tout écart se traite par une mise à jour de cette spec.
Premier essai (2026-11-06, ancienne lecture par l'API, un seul scan) : `wardrobe.html` fonctionne dans l'application lancée, mais Vinted refuse la requête directe de la première tranche (HTTP 403). Scan arrêté sans nouvelle tentative, base inchangée, cause exacte non établie.

### AT-04-17 – Annonce disparue pendant une interruption
```gherkin
Étant donné un scan interrompu après avoir lu 2 annonces sur 3
Et la deuxième annonce a quitté le dressing depuis
Quand je lance le scan
Alors seule la troisième annonce est ouverte
Et la deuxième annonce est marquée absente
```
Test : `ScanWardrobeAcceptanceTest.listing_gone_during_an_interruption_ends_up_absent()`

## Cas limites et erreurs
- Page d'annonce sans données `Product`, ou données incomplètes : problème « dressing illisible », le scan s'arrête et reste non terminé.
- En-tête sans total lisible, ou plusieurs totaux différents sur la page : problème « dressing illisible », rien n'est changé.
- Annonce vendue ou retirée entre le relevé et sa lecture, page en HTTP 404 : traitée comme toute erreur HTTP, le scan s'arrête ; le suivant la relèvera ou non.
- Annonce publiée ou retirée pendant le relevé : le total ne correspond plus, le scan échoue et l'utilisateur le relance.
- Dressing réellement vide : traité comme illisible (zéro annonce se confond avec une page changée, et marquerait tout l'inventaire absent).
- Erreur HTTP sur une autre ressource de la page (publicité, traceur) : ignorée.
- Erreur inattendue, ou base indisponible : problème « échec du scan » (détail dans les logs), le verrou est libéré, le scan reste reprenable.
- Application arrêtée pendant un scan : il reste non terminé et se reprend au scan suivant.
- `VINTED_WARDROBE_URL` absente : l'application ne démarre pas.
- Annonce absente qui réapparaît à un scan suivant : elle redevient présente, sans nouvelle ligne.

## Hors périmètre
- Description, photos et attributs d'une annonce (US-05, US-06), vues et likes (US-07), même si la page d'annonce est ouverte ici.
- Filtres, tri et recherche (US-18).
- Limites de cadence générales (US-27).
- Suivi d'une annonce republiée sous un nouvel identifiant (US-12).

## Décisions
- Le scan produit un inventaire conservé en PostgreSQL (table `listing`) : identifiant Vinted, URL, titre, prix, présence, dernière lecture. Seules les annonces actives y entrent ; aucun statut Vinted n'est stocké.
- Le dressing est désigné par `VINTED_WARDROBE_URL` (obligatoire), de la forme `https://www.vinted.fr/member/<id>`.
- Lecture décidée par l'utilisateur après le 403 : annonce par annonce, en ouvrant chaque page. Aucune requête directe vers l'API de Vinted. Écartés : recopier les en-têtes de la page (contournerait un refus) ; lire seulement les cartes du dressing (état réservé ou masqué non garanti).
- Observations du 2026-11-06, en visiteur, navigateur intégré de l'app : le dressing affiche « 146 articles » et ses 146 annonces apparaissent en défilant, chacune dans un `grid-item` avec un lien `/items/<id>`. Une page d'annonce contient un JSON-LD `Product` avec `name`, `offers.price`, `offers.priceCurrency`, `offers.url` et `offers.availability` (`InStock`). Aucune annonce non active n'a été observée.
- Une annonce est active si `offers.availability` vaut `InStock`.
- Enregistrement au fil de la lecture, décidé par l'utilisateur : un échec garde ce qui a été lu. Il remplace le scan tout ou rien.
- Table `wardrobe_scan` (`id`, `started_at`, `finished_at`) : elle permet de reprendre un scan et de distinguer un scan partiel d'un scan terminé. Sa date de fin est la date du dernier scan affichée.
- Une annonce est déjà lue par le scan en cours si son `last_seen_at` n'est pas antérieur à son début. Une annonce non active n'est pas enregistrée : elle est donc relue en cas de reprise.
- Les annonces présentes pendant un scan restent affichées ; la liste peut mêler anciennes et nouvelles valeurs jusqu'à la fin.
- Le scan n'exige aucune session connectée et partage le verrou d'US-03 (nouvelle action `SCANNING`). Il n'efface pas le problème de session.
- Rythme : pause aléatoire de 10 à 15 secondes entre deux annonces, soit environ 30 à 35 minutes pour 146 annonces, et de 3 à 6 secondes entre deux défilements du relevé. Un seul onglet, un seul scan à la fois. Valeurs en configuration. US-27 généralisera ces limites.
- Toute réponse HTTP ≥ 400 de la page du dressing ou d'une page d'annonce arrête le scan, sans nouvelle tentative. Les ressources tierces des pages sont ignorées.
- Le lien d'une annonce doit être en `http` ou `https`, car il finit dans une page. La raison d'un échec est écrite dans les logs.
- Sécurité : `server.address: 127.0.0.1`, et refus des `POST` dont l'`Origin` diffère de l'application, sur toute l'API.
- Page `wardrobe.html` avec liste simple. Aucune nouvelle dépendance.

## Questions ouvertes
- Aucune. Les points inconnus sont vérifiés par AT-04-16.

## Modèle de domaine
- `Listing` (annonce, agrégat) : `VintedListingId`, URL, titre, `Price`, présence, `lastSeenAt`.
- `Price` (value object) : montant décimal et devise.
- `ListingLink` : identifiant et URL d'une annonce relevée sur le dressing.
- `WardrobeListing` (relevé du dressing) : total affiché et liens ; dit si le relevé est complet. Remplace `WardrobeSnapshot`.
- `ScannedListing` (annonce lue) : identifiant, URL, titre, prix, active ou non. Remplace `ListingStatusFlags`.
- `WardrobeScan` (scan du dressing, agrégat) : date de début, date de fin ; dit si une annonce est déjà lue et lesquelles ont quitté le dressing.
- `ScanProblem`, `LastScanProblem` (problème du dernier scan, en mémoire), `ScanPace` et `PauseRange`, `WardrobeOverview`, `WardrobeUnreadable`, `InvalidListing` ; `BrowserAction` gagne `SCANNING`.
- Ports sortants : `WardrobeReader` (rappelle une `WardrobeVisit` : annonces à ouvrir, annonce lue ; rend le relevé), `ListingRepository`, `WardrobeScanRepository`. Cas d'usage : `ScanWardrobe`, `ShowWardrobe`.

## Tests unitaires et d'intégration attendus
- `domain` : `ListingLinkTest`, `WardrobeListingTest`, `WardrobeScanTest`, `ScannedListingTest`, `ListingTest`, `PriceTest`, `ScanPaceTest`.
- `application` : `ScanWardrobeTest` (lecture, enregistrement au fil de l'eau, reprise, chaque problème, verrou libéré), `ShowWardrobeTest`.
- `adapter/out/browser` : `ListingPageTest` (JSON-LD, sans navigateur) et `PlaywrightWardrobeReaderTest` contre des pages locales avec le vrai Chrome.
- `adapter/out/persistence` : tests Testcontainers des deux repositories.
- `adapter/in/web` : `WardrobeApiControllerTest`, `ContractEnumsTest`, `WardrobePageTest`.
