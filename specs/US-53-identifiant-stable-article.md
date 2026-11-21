# US-53 – Identifiant stable de l'article
**Statut** : implémentée
**Priorité** : V2
**Dépend de** : US-13, US-33, US-34, US-36, US-44, US-47, US-48, US-51

## Contexte
Toutes les données sont rattachées à l'identifiant Vinted de l'annonce (`listing.vinted_id`), qui change à chaque republication. L'article (le vêtement) n'existe pas en base : il n'est reconstruit qu'à la lecture, en suivant les republications réussies. Conséquences :
- les tags, la vente et l'historique de prix restent sur l'ancienne annonce après une republication ;
- une nouvelle annonce vue par le scan avant que sa republication soit prouvée (vérification différée, US-13 ; liaison hors outil, US-48) forme un article à part ;
- rien en base ne garantit qu'une chaîne de republications désigne un seul article.

## Objectif
En tant que membre, je veux que chaque article ait un identifiant stable, qui ne change jamais quelles que soient ses republications, afin que ses tags, sa vente et son historique de prix le suivent d'annonce en annonce.

## Comportement attendu
1. **Article** : table `article` (`id uuid`, `created_at`). L'identifiant est tiré par l'application et n'est jamais modifié.
2. **Annonce** : `listing.article_id uuid not null`, clé étrangère vers `article`. Une annonce appartient à un seul article ; un article a une ou plusieurs annonces. L'historique des identifiants Vinted d'un article est la liste de ses annonces, triées par `first_seen_at`.
3. **Annonce inconnue** : à son premier enregistrement (scan, nouvelle annonce retrouvée), elle reçoit un nouvel article. Une annonce déjà connue garde le sien.
4. **Republication réussie enregistrée** (republication, vérification sur Vinted, validation manuelle, liaison US-48) : la nouvelle annonce rejoint l'article de l'ancienne. Si elle avait déjà son propre article (vue par le scan avant la preuve), cet article est **fusionné** dans celui de l'ancienne, dans la même transaction :
   - tags : union ;
   - vente : une seule par article ; si les deux en ont une, la plus ancienne est gardée ;
   - historique de prix : réuni, trié par date ; une ligne qui répète le prix de la précédente est retirée (ce n'est pas un changement) ;
   - toutes les annonces de l'article absorbé passent dans l'article gardé ; l'article absorbé, devenu vide, est supprimé.
5. **Données de l'article** (suivent les republications) :
   - tags (`article_tag`) : un tag posé ou retiré sur une annonce l'est sur son article, donc sur toutes ses annonces ;
   - vente (`article_sale`) : une au plus par article, elle garde l'annonce vendue. La page « Ventes » et l'étape « Vendue » la montrent sur l'annonce vendue ; noter une vente sur une autre annonce d'un article vendu est refusé (`ListingAlreadySold`) ;
   - historique de prix (`listing_price_change`, qui garde l'annonce où le prix a été vu) : l'historique d'une annonce est celui de son article ; un prix n'est ajouté que s'il diffère du dernier prix de l'article, et jamais par une annonce absente une fois l'historique commencé (marquée absente après la fusion, l'ancienne annonce garde un vieux prix).
6. **Données de l'annonce** (propres à une annonce Vinted) : sauvegarde et photos, validation, vérification de sauvegarde, relevés de statistiques, suppressions, republications, journal.
7. **Lectures inchangées** : le compteur de republications, le cycle de vie et l'avant/après des statistiques suivent toujours les republications réussies, que la base garantit désormais internes à un article.
8. **Doublon effacé** (US-47) : son historique de prix est effacé ; son article l'est aussi, avec ses tags, s'il n'a plus aucune annonce.

## Critères d'acceptation
- [x] Une annonce enregistrée pour la première fois a un article à elle ; une annonce déjà connue garde le sien.
- [x] Après une republication réussie, la nouvelle annonce a le même article que l'ancienne, avec ses tags, sa vente et son historique de prix.
- [x] Une nouvelle annonce déjà scannée est fusionnée dans l'article de l'ancienne, selon les règles ci-dessus ; l'article absorbé n'existe plus.
- [x] Un tag posé sur une annonce se voit sur toutes les annonces de son article.
- [x] Un article n'a qu'une vente ; une vente constatée sur une annonce d'un article déjà vendu n'est pas ajoutée.
- [x] La base refuse une republication réussie entre deux annonces d'articles différents.
- [x] La migration crée un article par chaîne de republications existante et y rattache les tags, ventes et historiques de prix.
- [x] L'effacement d'un doublon efface son article s'il n'a plus d'annonce.

## Tests d'acceptation

### AT-53-01 – Une nouvelle annonce a son propre article
```gherkin
Étant donné deux annonces inconnues de l'outil
Quand elles sont enregistrées, puis l'une est enregistrée à nouveau
Alors elles ont chacune un article, distinct
Et l'annonce enregistrée à nouveau garde son article
```
Test : `ArticleIdentityAcceptanceTest.new_listing_gets_its_own_article()`

### AT-53-02 – La republication garde l'article
```gherkin
Étant donné une annonce taguée « été », vue à 20 € puis à 18 €
Quand sa republication réussie vers une nouvelle annonce à 18 € est enregistrée
Alors la nouvelle annonce a le même article que l'ancienne
Et elle porte le tag « été » et l'historique 18 € puis 20 €
```
Test : `ArticleIdentityAcceptanceTest.republished_listing_keeps_its_article()`

### AT-53-03 – Fusion d'une nouvelle annonce déjà scannée
```gherkin
Étant donné une annonce A taguée « été », vue à 20 €
Et la nouvelle annonce B, vue par le scan à 20 €, taguée « robe » et notée vendue
Quand la republication réussie de A vers B est enregistrée
Alors A et B ont le même article, celui de A
Et cet article a les tags « été » et « robe », la vente de B et l'historique 20 €
Et l'article qu'avait B n'existe plus
```
Test : `ArticleIdentityAcceptanceTest.scanned_new_listing_merges_into_article()`

### AT-53-04 – Fusion de deux articles vendus
```gherkin
Étant donné une annonce A notée vendue le 10 novembre et une annonce B notée vendue le 12 novembre
Quand la republication réussie de A vers B est enregistrée
Alors l'article n'a qu'une vente, celle de A du 10 novembre
```
Test : `ArticleIdentityAcceptanceTest.merge_keeps_the_oldest_sale()`

### AT-53-05 – Un tag suit l'article
```gherkin
Étant donné une annonce A republiée en B
Quand le membre tague A « hiver », puis retire « hiver » de B
Alors B a porté le tag « hiver », puis ni A ni B ne le portent
```
Test : `ArticleIdentityAcceptanceTest.tag_follows_article()`

### AT-53-06 – Une seule vente par article
```gherkin
Étant donné une annonce A republiée en B, B notée vendue
Quand le membre note A vendue
Alors c'est refusé (ListingAlreadySold)
Et quand le dressing montre A « Vendu », aucune vente n'est ajoutée
```
Test : `ArticleIdentityAcceptanceTest.one_sale_per_article()` et `ListingSaleTest.newly_noticed_skips_sold_articles()`

### AT-53-07 – Migration des chaînes existantes
```gherkin
Étant donné une base avant cette US avec une chaîne A → B → C, une annonce D seule, des tags sur A et C, une vente sur C et des prix sur A et C
Quand la migration s'exécute
Alors A, B et C partagent un article, D en a un autre
Et l'article de A, B, C a les tags de A et de C, la vente de C et les prix de A et C
```
Test : `ArticleMigrationTest.chains_become_articles()` (`@Tag("docker")`)

### AT-53-08 – Intégrité en base
```gherkin
Étant donné deux annonces d'articles différents
Quand on écrit en base une republication réussie de l'une vers l'autre sans passer par la fusion
Alors la base refuse l'écriture
```
Test : `ArticleIdentityAcceptanceTest.database_rejects_republication_across_articles()`

### AT-53-09 – Effacement d'un doublon
```gherkin
Étant donné une annonce doublon taguée « été », seule de son article
Quand elle est effacée
Alors son article et son tag n'existent plus
```
Test : `JpaListingEraserTest.erases_the_article_left_without_listing()`

## Cas limites et erreurs
- Les deux articles fusionnés ont chacun une vente : la plus ancienne est gardée, l'autre supprimée.
- Republication réussie enregistrée à nouveau (même nouvelle annonce, déjà dans l'article) : rien ne change.
- Une annonce sans republication avant cette US : la migration lui crée un article.
- Une boucle de republications (A → B → A) n'a pas de racine : la migration échouerait. Impossible en pratique, une republication menant toujours vers une annonce plus récente.
- Une republication réussie dont la nouvelle annonce porte déjà d'autres annonces (B republiée en C, puis A prouvée republiée en B) : tout l'article de B rejoint celui de A.

## Hors périmètre
- Exposer `articleId` dans l'API ou l'interface, routes par article : rien ne s'en sert encore.
- Historique des versions de la sauvegarde (une seule sauvegarde par annonce, remplacée) : US à part si le besoin se confirme.
- Journal par article : le journal reste par annonce.

## Décisions
- Le membre a délégué les choix de conception et accepte une perte de données (le dressing peut être rescanné) ; la migration garde tout de même ce qu'elle peut.
- Terme `Article` (`ArticleId`) plutôt que `Item`, que Vinted emploie pour ses annonces.
- UUID v4 tiré par l'application (`ArticleId.newArticle()`) : identité connue avant l'écriture, comme les autres identifiants de l'outil. Un UUID v7 n'apporte rien à l'échelle d'un dressing.
- `vinted_id` reste la clé primaire de `listing` : identifiant naturel, jamais réutilisé par Vinted.
- Intégrité garantie par la base : `listing` porte une contrainte unique `(vinted_id, article_id)` ; `listing_republication`, `listing_price_change` et `article_sale` portent `article_id` et des clés étrangères composites vers `listing (vinted_id, article_id)`, `ON UPDATE CASCADE` (la fusion déplace les annonces, leurs lignes suivent). Une republication réussie relie donc forcément deux annonces du même article.
- La fusion est faite par l'adaptateur de persistance, dans la transaction qui enregistre la republication réussie : c'est la seule écriture qui prouve que deux annonces sont le même article.
- `RepublicationChains` reste : il suit les republications réussies, désormais internes à un article ; le remplacer n'apporterait rien au membre.
- Pas de table d'événements générique (type + JSON) : les tables typées existantes sont l'historique, avec leurs contraintes.
- Performance : index `listing (article_id)`, `listing_price_change (article_id, changed_at DESC, id DESC)` ; `article_tag` et `article_sale` indexés par leur clé.

## Questions ouvertes
- Aucune.

## Modèle de domaine
- Glossaire : `Article` (article : le vêtement mis en vente, identité stable à travers ses annonces successives), `ArticleId` (identifiant de l'article).
- `Listing` porte son `ArticleId`.
- `ListingSale.newlyNoticed` écarte les annonces dont l'article est déjà vendu.
- Ports inchangés (`ListingTagRepository`, `ListingSaleRepository`, `ListingRepository`, `ListingRepublicationRepository`) ; leurs contrats précisent l'article.

## Tests unitaires et d'intégration attendus
- Domaine : `Listing.seen` tire un nouvel article ; `newlyNoticed` par article.
- Persistance (`@Tag("docker")`) : article conservé par `saveAll`, prix par article, tags et ventes par article, fusion, migration, effacement.
