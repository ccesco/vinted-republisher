# US-36 – Suivi des ventes
**Statut** : implémentée
**Priorité** : V2
**Dépend de** : US-04 (scan, cartes « Vendu »), US-25 (rafraîchissement des statistiques), US-18b (fiche d'une annonce)

## Contexte
Une annonce vendue reste dans la grille du dressing sous un bandeau « Vendu » ; le scan l'ignore (US-04, AT-04-18) et la marque absente, comme une annonce retirée par le membre. L'outil ne sait donc pas distinguer une vente d'un retrait, et le membre n'a aucune vue de ce qu'il a vendu. Les cartes « Vendu » sont déjà sous les yeux du lecteur du dressing : il suffit de les relever, sans rien ouvrir ni cliquer.

## Objectif
En tant que membre, je veux voir les ventes que l'outil a constatées dans mon dressing, avec leur date, leur prix et leur total, afin de suivre ce que mes annonces me rapportent.

## Comportement attendu
1. **Constat** : à chaque relevé du dressing (scan, US-04, et rafraîchissement des statistiques, US-25), l'outil note les annonces dont la carte affiche « Vendu ». Rien n'est ouvert ni cliqué pour cela : lecture seule, la grille est déjà parcourue.
2. **Annonces concernées** : seules les annonces connues de l'outil (déjà dans l'inventaire) sont enregistrées comme vendues. Une carte « Vendu » d'une annonce inconnue (vendue avant le premier scan) est ignorée.
3. **Une vente par annonce** : la vente est enregistrée la première fois que l'annonce est vue vendue, datée de ce relevé (« constatée le »). Les relevés suivants ne la changent pas. Une vente n'est jamais supprimée.
4. **Tout ou rien** : comme les relevés de statistiques, aucune vente n'est enregistrée si le dressing est incomplet (total incohérent, aucun lien), ou si le relevé échoue avant la fin de la grille.
5. **Prix** : celui de l'annonce tel que l'outil l'a lu pour la dernière fois (prix affiché, hors frais), pas le montant réellement encaissé, que la carte ne donne pas.
6. **Page « Ventes »** (`sales.html`, lien depuis le dressing) : les ventes, la plus récemment constatée d'abord (à date égale, par identifiant d'annonce), avec date de constat, titre de l'annonce (lien vers sa fiche) et prix ; au-dessus, le nombre de ventes et le total des prix, par devise. Sans vente : « Aucune vente constatée. » API indisponible : « Ventes indisponibles. »

## Contrat d'API
- `GET /api/sales` (`operationId: showSales`, tag `sales`) → `200` `SalesOverview`.
- `SalesOverview` : `sales` (tableau de `Sale`, la plus récente d'abord), `totals` (tableau de `Price`, un par devise, trié par devise ; vide sans vente) ; obligatoires.
- `Sale` : `listingId` (int64), `title` (texte), `price` (`Price`), `noticedAt` (date-heure) ; obligatoires.

## Critères d'acceptation
- [x] Le scan et le rafraîchissement des statistiques enregistrent comme vendues les annonces connues dont la carte affiche « Vendu », datées du relevé.
- [x] Une annonce vendue n'est enregistrée qu'une fois, à sa première date de constat.
- [x] Une annonce inconnue de l'outil n'est pas enregistrée ; un dressing incomplet n'enregistre rien.
- [x] Le lecteur du dressing relève les cartes « Vendu » sans les compter dans le total ni les ouvrir.
- [x] L'API et la page « Ventes » montrent les ventes, leur nombre et le total des prix par devise.

## Tests d'acceptation
### AT-36-01 – Vente constatée au scan
```gherkin
Étant donné les annonces 1 et 2 connues de l'outil
Et un dressing qui montre l'annonce 1 en vente et l'annonce 2 sous un bandeau « Vendu »
Quand je scanne le dressing le 17 novembre
Alors l'annonce 2 est enregistrée comme vendue, constatée le 17 novembre
Et elle n'est pas ouverte, et elle est marquée absente comme avant
```
Test : `TrackSalesAcceptanceTest.a_scan_notices_the_sold_listings()`

### AT-36-02 – Vente constatée au rafraîchissement des statistiques
```gherkin
Étant donné l'annonce 2 connue de l'outil, montrée « Vendu » dans le dressing
Quand je rafraîchis les vues et favoris
Alors l'annonce 2 est enregistrée comme vendue, à la date du rafraîchissement
```
Test : `TrackSalesAcceptanceTest.a_statistics_refresh_notices_the_sold_listings()`

### AT-36-03 – Une seule vente par annonce
```gherkin
Étant donné l'annonce 2 enregistrée vendue le 17 novembre
Quand un nouveau relevé du dressing la montre encore « Vendu » le 18 novembre
Alors elle reste une seule vente, constatée le 17 novembre
```
Test : `TrackSalesAcceptanceTest.a_sale_is_noticed_once()`

### AT-36-04 – Annonce inconnue et dressing incomplet
```gherkin
Étant donné une carte « Vendu » d'une annonce que l'outil n'a jamais vue
Quand je scanne le dressing
Alors aucune vente n'est enregistrée
Étant donné un dressing incomplet qui montre une annonce connue « Vendu »
Quand je rafraîchis les vues et favoris
Alors aucune vente n'est enregistrée
```
Test : `TrackSalesAcceptanceTest.unknown_listings_and_incomplete_wardrobes_record_no_sale()`

### AT-36-05 – Relevé des cartes « Vendu »
```gherkin
Étant donné une copie locale d'un dressing annonçant 1 article, dont la grille montre aussi une carte « Vendu »
Quand le lecteur parcourt le dressing
Alors seul le lien de l'annonce non vendue est relevé, et l'identifiant de l'annonce vendue est noté comme vendu
```
Test : `PlaywrightWardrobeReaderTest.Reading.notes_the_cards_of_sold_listings()`

### AT-36-06 – Ventes, nombre et total
```gherkin
Étant donné l'annonce « Pull rayé » à 10 € vendue le 15 novembre et « Jean » à 25,50 € vendue le 17 novembre
Quand je demande les ventes
Alors je reçois « Jean » puis « Pull rayé », et un total de 35,50 €
```
Test : `SalesApiControllerTest.shows_the_sales_most_recent_first_with_their_totals()`, `SalesOverviewTest`

### AT-36-07 – Page « Ventes »
```gherkin
Quand le membre ouvre la page « Ventes »
Alors il voit le nombre de ventes, le total, et chaque vente avec sa date de constat, son prix et un lien vers la fiche de l'annonce
Et « Aucune vente constatée. » sans vente, « Ventes indisponibles. » si l'API ne répond pas
```
Test : `SalesPageTest.shows_the_sales_and_their_total()`, `SalesPageTest.says_when_there_is_no_sale()`, `SalesPageTest.says_when_the_sales_are_unavailable()`

## Cas limites et erreurs
- Annonce republiée puis vendue : seule l'annonce vendue (la nouvelle) est une vente ; l'ancienne a été supprimée par l'outil et n'est jamais montrée « Vendu ».
- Annonce vendue entre deux relevés puis disparue de la grille sans avoir été vue « Vendu » (si Vinted retire la carte d'une annonce vendue) : aucune vente ; l'annonce est seulement absente.
- Plusieurs devises : un total par devise, aucun change.
- Relevé interrompu (Vinted inaccessible, navigateur indisponible) : rien n'est enregistré ; le relevé suivant constatera la vente.
- Échec de l'écriture des ventes (base) : le relevé est en échec (`SCAN_FAILED` ou `REFRESH_FAILED`), mais les relevés de statistiques déjà écrits restent, comme pour un scan qui échoue après eux ; le relevé suivant constate la vente, à sa date. Accepté plutôt qu'une transaction commune aux deux écritures (revue du 2026-11-17).

## Hors périmètre
- Date réelle de la vente, acheteur, montant encaissé, frais, commandes et expéditions : il faudrait ouvrir les pages de commandes de Vinted.
- Ventes antérieures au premier scan de l'outil.
- État « vendue » dans la liste des annonces et sur leur fiche : relève du cycle de vie des annonces (US-32), qui pourra s'appuyer sur les ventes enregistrées ici.
- Filtres par période, graphiques, délai de vente.
- Entrée dans le journal des actions : constater une vente n'est pas une action de l'outil.

## Décisions
Décidé par Claude sur délégation de l'utilisateur (2026-11-17, « rédiger et valider la spec soi-même ») :
- Source = les cartes « Vendu » de la grille, déjà parcourue par le scan et le rafraîchissement : aucune page ni requête de plus vers Vinted, conformément à la lecture seule et à la prudence anti-robot (US-04).
- Constat au scan et au rafraîchissement : le rafraîchissement, rapide et fréquent, constate les ventes plus tôt, pour le même parcours.
- Seulement les annonces connues : une carte « Vendu » ne donne ni titre ni prix fiables à l'outil, qui ne l'ouvre pas.
- Date = date du premier constat, faute de date réelle sur la carte ; le libellé dit « constatée le » pour ne pas la confondre avec la date de vente.
- Prix = dernier prix lu de l'annonce, non stocké avec la vente : une annonce vendue n'est plus lue, son prix ne bouge plus.
- Tout ou rien sur un dressing incomplet, comme les relevés de statistiques (US-25).
- Page à part plutôt que colonne dans la liste : garde la liste et la fiche intactes (US-32 les fait évoluer en parallèle) ; YAGNI sur filtres et graphiques.

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `ListingSale` (`domain/sale`, value object) | Vente d'une annonce | Annonce connue vue « Vendu » dans le dressing, datée de son premier constat ; jamais modifiée ni supprimée.
- `ListingSaleRepository` (port sortant) : `findAll()`, `addAll(List<ListingSale>)`.
- `ListingSale.newlyNoticed(...)` : ventes à ajouter, à partir des annonces vues vendues, de l'inventaire et des ventes déjà enregistrées.
- `SalesOverview` (`domain/sale`) : ventes jointes à leur annonce (`Sale` : identifiant, titre, prix, date de constat), la plus récente d'abord, et totaux par devise.
- `WardrobeListing` gagne `soldListings` (identifiants des cartes « Vendu », chacun une fois), hors du total.
- `NoticeSales` (`application/sale`) : appelé par le scan et le rafraîchissement une fois le dressing complet ; `ShowSales` : la page.

## Tests unitaires et d'intégration attendus
- `domain` : `ListingSaleTest` (nouvelles ventes : connues, non enregistrées, une fois), `SalesOverviewTest` (ordre, totaux par devise), `WardrobeListingTest` (ventes notées une fois, hors total).
- `application` : couverts par `TrackSalesAcceptanceTest`.
- `adapter/out/persistence` : `JpaListingSaleRepositoryTest`.
- `adapter/out/browser` : `PlaywrightWardrobeReaderTest`.
- `adapter/in/web` : `SalesApiControllerTest`, `SalesPageTest`.
