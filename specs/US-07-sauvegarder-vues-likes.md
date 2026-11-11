# US-07 – Sauvegarder vues et likes
**Statut** : implémentée
**Priorité** : MVP
**Dépend de** : US-04

## Contexte
Republier une annonce remet à zéro ses vues et ses likes (« favoris » sur Vinted). Pour garder ce qu'elle avait obtenu, et plus tard cumuler ses nombres d'une republication à l'autre et comparer avant / après (US-12, US-24), l'outil doit relever ces deux nombres et en garder l'historique. Vinted ne les montre qu'au membre connecté, sur les cartes de la page de son dressing, que le scan (US-04) fait déjà défiler.

## Objectif
En tant que membre, je veux que l'outil garde, à chaque scan, le nombre de vues et de favoris de chaque annonce de mon dressing, afin de connaître ce qu'elle avait obtenu avant sa republication.

## Comportement attendu
Le scan d'US-04 (bouton « Scanner », verrou `SCANNING`, reprise, problèmes) est étendu ; aucune nouvelle action, aucune page ni défilement de plus.
1. **Relevé** : en défilant la page du dressing, l'outil lit sur chaque carte d'annonce, en plus de son lien, les textes « N vue(s) » et « N favori(s) ». Une carte dont l'un des deux manque ou a une autre forme n'a pas de statistiques relevées.
2. **Enregistrement** : une fois le relevé complet (autant de liens que le total affiché), chaque annonce du relevé qui a ses statistiques reçoit un nouveau relevé : vues, favoris et date du relevé (fin du relevé du dressing). Il est enregistré avant la lecture des annonces une à une, et reste même si le scan échoue ensuite. Un relevé n'est jamais modifié ni supprimé.
3. **Page du dressing** : chaque annonce présente affiche son dernier relevé, « 12 vues · 3 favoris » (« 1 vue · 1 favori » au singulier, « 0 vue · 0 favori » à zéro), rien si elle n'a jamais été relevée.
4. **Échec** : un relevé du dressing incomplet ou illisible arrête le scan comme en US-04, sans enregistrer de statistiques.

## Contrat d'API
Contract first : `src/main/resources/static/openapi/vinted-republisher.yaml`.

| Méthode et chemin | operationId | Changement |
|---|---|---|
| `GET /api/wardrobe` | `showWardrobe` | chaque `Listing` gagne `statistics` (absent = jamais relevée) : `viewCount` (entier ≥ 0), `favouriteCount` (entier ≥ 0), `readAt` (date-time du relevé) — le dernier relevé |
| `POST /api/wardrobe/scan` | `scanWardrobe` | inchangé |

L'historique des relevés n'est pas exposé par l'API.

## Critères d'acceptation
- [x] Chaque scan dont le relevé est complet ajoute, pour chaque annonce qui affiche ses vues et ses favoris, un relevé daté de ces deux nombres.
- [x] Les relevés précédents ne sont jamais modifiés ni supprimés, y compris pour une annonce absente.
- [x] Une carte sans vues ni favoris lisibles (session non connectée, autre forme d'affichage) n'arrête pas le scan et ne touche pas aux relevés de l'annonce.
- [x] Vues et favoris se relèvent ensemble ou pas du tout.
- [x] La page du dressing affiche le dernier relevé de chaque annonce.
- [x] Le scan n'ouvre aucune page et ne fait aucun défilement de plus qu'en US-04, et ne fait toujours aucun clic, aucune saisie ni aucune requête directe à l'API de Vinted.
- [x] Aucun test automatisé ne contacte le vrai site Vinted.

## Tests d'acceptation
### AT-07-01 – Statistiques relevées pendant le scan
```gherkin
Étant donné un dressing dont la carte de l'annonce A affiche « 12 vues » et « 3 favoris »
Quand je lance le scan
Alors l'annonce A a un relevé de 12 vues et 3 favoris, daté de la fin du relevé du dressing
```
Test : `ScanWardrobeAcceptanceTest.scan_records_the_statistics_of_each_listing()`

### AT-07-02 – Historique conservé
```gherkin
Étant donné une annonce A relevée à 12 vues et 3 favoris par un premier scan
Et sa carte affiche maintenant « 15 vues » et « 3 favoris »
Quand je lance un nouveau scan
Alors l'annonce A a deux relevés : 12 / 3 puis 15 / 3
Et la page du dressing affiche « 15 vues · 3 favoris »
```
Test : `ScanWardrobeAcceptanceTest.each_scan_adds_a_statistics_reading()`

### AT-07-03 – Statistiques non affichées
```gherkin
Étant donné une annonce A déjà relevée à 12 vues et 3 favoris
Et sa carte n'affiche plus ses vues et ses favoris (session non connectée)
Quand je lance le scan
Alors le scan se termine normalement
Et l'annonce A a toujours un seul relevé, 12 / 3
```
Test : `ScanWardrobeAcceptanceTest.listing_without_statistics_keeps_its_readings()`

### AT-07-04 – Relevé incomplet
```gherkin
Étant donné un dressing dont le nombre de liens relevés diffère du total affiché
Quand je lance le scan
Alors le scan s'arrête avec le problème « dressing illisible »
Et aucun relevé de statistiques n'est enregistré
```
Test : `ScanWardrobeAcceptanceTest.incomplete_wardrobe_records_no_statistics()`

### AT-07-05 – Échec après le relevé
```gherkin
Étant donné un relevé du dressing complet avec ses statistiques
Et la lecture d'une annonce qui échoue ensuite (Vinted inaccessible)
Quand je lance le scan
Alors le scan s'arrête et reste non terminé
Et les relevés de statistiques sont enregistrés
```
Test : `ScanWardrobeAcceptanceTest.statistics_stay_recorded_when_the_scan_fails_afterwards()`

### AT-07-06 – Lecture des textes des cartes
```gherkin
Étant donné des cartes qui affichent « 0 vues », « 1 vue », « 1 234 vues » (espace insécable), « 1 favori », « 2 favoris »
Et une carte qui affiche « 1,2 k vues », une carte sans favoris et une carte sans aucun de ces textes
Quand l'outil lit les statistiques
Alors il lit 0, 1 et 1234 vues, 1 et 2 favoris
Et les trois dernières cartes n'ont pas de statistiques
```
Test : `ListingStatisticsTest` (domaine : lecture des textes)

### AT-07-07 – Lecture d'une fausse page de dressing locale
```gherkin
Étant donné une fausse page de dressing servie localement, dont les cartes affichent vues et favoris
Quand l'outil la relève avec le vrai Chrome
Alors il rend chaque lien avec ses vues et ses favoris
Et aucun clic, aucune saisie ni aucune soumission n'a eu lieu sur la page
```
Test : `PlaywrightWardrobeReaderTest`

### AT-07-08 – Conservation en base
```gherkin
Étant donné des relevés de statistiques enregistrés pour une annonce
Quand l'application redémarre
Alors ils sont relus à l'identique, et le dernier est celui de la date la plus récente
```
Test : `JpaListingStatisticsRepositoryTest` (Testcontainers)

### AT-07-09 – Page du dressing
```gherkin
Étant donné une annonce dont le dernier relevé est 12 vues et 3 favoris, et une annonce jamais relevée
Quand j'ouvre la page du dressing
Alors je vois « 12 vues · 3 favoris » pour la première, et rien pour la seconde
```
Test : `WardrobePageTest.shows_statistics()` et `WardrobeApiControllerTest`

### AT-07-10 – Relevé réel
```gherkin
Étant donné l'application lancée avec le profil local, une session connectée et ma validation explicite
Quand je lance le scan de mon vrai dressing
Alors chaque annonce affiche les mêmes vues et favoris que sur ma page Vinted
Et aucune annonce n'a été modifiée sur Vinted
```
Test : manuel (vrai compte Vinted, non automatisable).
Non fait à la clôture (2026-11-11) : l'utilisateur a choisi de passer l'US en « implémentée » avec ce test encore ouvert. Tout écart constaté se traite par une mise à jour de cette spec.
Recette du 2026-11-11 : vues et favoris relevés sur les 144 cartes non vendues à chaque relevé du dressing. Reste ouvert : comparaison avec la page Vinted par l'utilisateur.

## Cas limites et erreurs
- Session non connectée : les cartes n'affichent pas les nombres, le scan se fait comme en US-04 sans relevé de statistiques.
- Texte d'une autre forme (ex. « 1,2 k vues ») ou un seul des deux nombres : pas de statistiques pour cette annonce, le scan continue.
- Annonce du relevé non active (pas `InStock`) ou pas encore enregistrée : son relevé de statistiques est enregistré quand même (rattaché à son identifiant Vinted) ; seules les annonces présentes l'affichent.
- Scan repris : le relevé du dressing est refait, donc un nouveau relevé de statistiques est ajouté.
- Deux scans aux nombres identiques : deux relevés identiques à des dates différentes (pas de dédoublonnage).

## Hors périmètre
- Cumul des nombres sur les republications (suppose le lien entre une annonce et sa republication, US-12), comparaison avant / après (US-24), rafraîchissement des statistiques (US-25).
- Affichage de l'historique (US-18b), filtres et tri sur vues et favoris (US-18).
- Sauvegarde du contenu et des photos (US-05, US-06), inchangée.

## Décisions
- Observation du vrai site (2026-11-11, lecture seule, avec l'accord de l'utilisateur) : la page publique d'une annonce (`/items/10208778166`) ne donne ni vues ni likes, ni à l'écran ni dans ses données (`is_favourite` seulement). L'utilisateur précise que vues et likes ne sont visibles que sur la page de son dressing (`/member/21027207`). Sur cette page, chaque carte (`grid-item`, 20 relevées) affiche exactement deux textes : `data-testid="product-item-id-<id>--description-title"` = « 2 vues » (aussi « 0 vues ») et `…--description-subtitle` = « 0 favoris ». Ces nombres ne figurent dans aucune donnée JSON de la page : seulement dans le texte affiché. Les likes s'appellent « favoris » sur Vinted.
- Moment et source, décidé par l'utilisateur (2026-11-11) : pendant le relevé du scan (US-04), sur les cartes de la page du dressing que le scan fait déjà défiler, en même temps que les liens ; aucune page ni défilement de plus. La sauvegarde (US-05/06) n'est pas modifiée. (Remplace une première décision du même jour, « pendant la sauvegarde, sur la page publique », prise avant l'observation.)
- Nombres absents, décidé par l'utilisateur (2026-11-11) : si une carte n'affiche pas ses nombres (session non connectée, carte d'une autre forme), le scan continue normalement et les relevés déjà gardés de l'annonce restent intacts. Aucune session connectée n'est exigée par le scan (US-04 inchangée).
- Format, décidé par l'utilisateur (2026-11-11) : « N vue » / « N vues » et « N favori » / « N favoris », N en chiffres, espaces (y compris insécables) de milliers acceptés. Un texte d'une autre forme (ex. « 1,2 k vues ») est traité comme absent. Vues et favoris se relèvent ensemble ou pas du tout. Ils sont reconnus par leur texte dans la carte, pas par leur position.
- Conservation, décidé par l'utilisateur (2026-11-11, « cumul avec les anciens », précisé « les deux ») : (1) historique : chaque relevé est gardé (annonce, vues, favoris, date), jamais remplacé ni supprimé, un par scan qui les lit, même si les nombres n'ont pas changé ; (2) total cumulé sur les republications : vues et favoris de l'ancienne annonce s'ajoutent à ceux de la nouvelle. Le cumul suppose le lien entre une annonce et sa republication, qui n'existe qu'avec US-12 : il est hors périmètre d'US-07, qui en garde les données.
- Affichage, décidé par l'utilisateur (2026-11-11) : la page du dressing affiche le dernier relevé, « 12 vues · 3 favoris », rien si jamais relevée ; l'API ajoute `statistics` {`viewCount`, `favouriteCount`, `readAt`} à chaque `Listing`. L'historique n'est pas affiché.
- Moment de l'enregistrement, proposé à la rédaction (2026-11-11) : à la fin du relevé du dressing, une fois vérifié complet, pour toutes les annonces du relevé (y compris non actives ou pas encore enregistrées), rattaché à l'identifiant Vinted ; date = fin du relevé. Raison : un scan repris ne rouvre pas les annonces déjà lues, et les relevés ne doivent pas en dépendre.
- Unicité, précisé à l'implémentation (2026-11-11) : un seul relevé par annonce et par date (contrainte unique `listing_statistics_reading_once`) ; un relevé du dressing ne compte chaque annonce qu'une fois et un scan repris relève à une autre date, donc aucun relevé légitime n'est refusé. Le dernier relevé de chaque annonce se lit en parcourant cet index à rebours.
- Zéro, décidé par l'utilisateur (2026-11-11) : la page affiche « 0 vue · 0 favori » (singulier pour 0 et 1, comme le nombre de photos), alors que Vinted écrit « 0 vues ».

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `ListingStatistics` (statistiques d'une annonce, value object) : nombre de vues et de favoris, ≥ 0 ; sait se lire depuis les textes d'une carte (absent si l'un manque ou a une autre forme).
- `StatisticsReading` (relevé des statistiques) : identifiant Vinted de l'annonce, `ListingStatistics`, date du relevé.
- `ListingLink` (lien d'annonce) gagne ses statistiques éventuelles, lues sur la même carte.
- Port sortant `ListingStatisticsRepository` (historique des statistiques) : ajoute des relevés ; rend le dernier relevé de chaque annonce.
- `WardrobeOverview` : chaque annonce présente porte son dernier relevé.
- Persistance : table `listing_statistics_reading` (identifiant technique, identifiant Vinted, vues, favoris `integer` ≥ 0, `read_at timestamptz`), contrainte unique (identifiant Vinted, `read_at`), qui sert aussi d'index au dernier relevé, sans clé étrangère vers `listing` (une annonce non active a aussi son relevé).
- Glossaire : ajouter `ListingStatistics` (vues et favoris), `StatisticsReading`, `ListingStatisticsRepository` ; compléter `ListingLink`, `WardrobeOverview`.

## Tests unitaires et d'intégration attendus
- `domain` : `ListingStatisticsTest` (textes, singulier / pluriel, espaces, formes refusées, négatifs refusés), `ListingLinkTest`.
- `application` : `ScanWardrobeTest` (relevés enregistrés après un relevé complet, aucun sinon, cartes sans statistiques), `ShowWardrobeTest` (dernier relevé) ; `ScanWardrobeAcceptanceTest` ; faux `InMemoryListingStatisticsRepository`.
- `adapter/out/browser` : `PlaywrightWardrobeReaderTest` contre une page de dressing locale avec cartes à statistiques.
- `adapter/out/persistence` : `JpaListingStatisticsRepositoryTest` (ajout, dernier relevé, historique conservé).
- `adapter/in/web` : `WardrobeApiControllerTest`, `WardrobePageTest`.
