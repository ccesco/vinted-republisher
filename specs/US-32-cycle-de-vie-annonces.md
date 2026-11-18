# US-32 – Cycle de vie des annonces
**Statut** : implémentée
**Priorité** : V2
**Dépend de** : US-04 (scan), US-09 (suppression), US-12 et US-13 (republication et lien vers la nouvelle annonce), US-18b (fiche), US-24 (chaîne de republications)

## Contexte
L'outil sait où en est une annonce dans le traitement (étape, US-14) et garde la date où le scan l'a vue pour la dernière fois, mais pas depuis quand il la connaît. Or un même article vit sous plusieurs annonces successives : mise en vente, suppression par l'outil, republication sous un nouvel identifiant, et un jour sortie du dressing sans l'outil (vendue ou retirée par le membre). Ces faits sont en base, dispersés entre l'annonce, les suppressions et les republications. Le membre veut les lire d'un coup d'œil, dans l'ordre, pour savoir depuis quand l'article est en ligne et ce que l'outil en a fait.

## Objectif
En tant que membre, je veux voir sur la fiche d'une annonce la chronologie de l'article (première détection, suppressions, republications, sortie du dressing) à travers toutes ses annonces successives, afin de savoir depuis quand il est en vente et par où il est passé.

## Comportement attendu
1. **Première détection** : chaque annonce garde la date où l'outil l'a vue pour la première fois (`firstSeenAt`) : premier scan qui l'a lue, ou date de preuve de la republication qui l'a trouvée. Elle n'est jamais modifiée ensuite, même si l'annonce est relue, sort du dressing puis y revient.
2. **Annonces connues avant cette US** : leur date de première détection est reprise du plus ancien de leurs relevés de statistiques et de leur date de dernière vue (une approximation : l'outil ne gardait pas cette date).
3. **Chaîne de l'article** : les annonces reliées par des republications publiées (`PUBLISHED`), dans les deux sens : celles que l'annonce remplace (US-24) et celles qui la remplacent. Une annonce sans republication publiée forme seule sa chaîne. Le parcours s'arrête sur une annonce déjà vue.
4. **Événements**, pour chaque annonce de la chaîne, de la plus ancienne à la plus récente :
   - `FIRST_SEEN` « vue pour la première fois », à sa date de première détection ;
   - `DELETED` « supprimée par l'outil », si sa dernière suppression est confirmée, à la date du clic de suppression ;
   - `REPUBLISHED` « republiée sous l'annonce N », si sa dernière republication est publiée, à sa date de preuve (`publishedAt`), avec la nouvelle annonce ;
   - `LEFT_WARDROBE` « sortie du dressing », si elle est absente sans suppression confirmée (vendue ou retirée hors de l'outil), datée de sa dernière vue par le scan (la date réelle de sortie n'est pas connue).
5. **Ordre** : celui de la chaîne puis, pour une annonce, l'ordre ci-dessus ; pas de tri par date (les dates reprises au point 2 sont approximatives).
6. **Faits incertains** : une suppression non confirmée ou non faite, une republication en erreur ou non publiée ne sont pas des événements ; l'étape de traitement et le journal les montrent déjà.
7. **Lecture seule** : tout vient de la base, rien n'est demandé à Vinted.
8. **Fiche** : à côté de « Vue pour la dernière fois », « Vue pour la première fois le … » ; une partie « Cycle de vie » liste les événements (date, annonce avec un lien vers sa fiche si ce n'est pas celle affichée, libellé), précédée de « Article suivi depuis le … » (date du premier événement).

## Contrat d'API
- `ListingDetail` gagne `firstSeenAt` (date-heure, obligatoire) et `lifecycle` (tableau de `LifecycleEvent`, obligatoire, jamais vide).
- `LifecycleEvent` : `type` (`LifecycleEventType`), `listingId` (int64), `at` (date-heure), tous obligatoires, et `newListingId` (int64, seulement pour `REPUBLISHED`).
- `LifecycleEventType` : `FIRST_SEEN`, `DELETED`, `REPUBLISHED`, `LEFT_WARDROBE`.

## Critères d'acceptation
- [x] La date de première détection d'une annonce est gardée telle quelle à chaque nouvelle lecture.
- [x] Les annonces connues avant l'US reçoivent une date de première détection reprise de leurs relevés ou de leur dernière vue.
- [x] La fiche d'une annonce montre la chronologie de tout l'article, quelle que soit l'annonce de la chaîne ouverte.
- [x] Une annonce sortie du dressing sans suppression confirmée a l'événement « sortie du dressing ».
- [x] Les faits incertains ne donnent aucun événement.
- [x] L'API et la fiche montrent la première détection et le cycle de vie.

## Tests d'acceptation
### AT-32-01 – Première détection gardée
```gherkin
Étant donné l'annonce 101 vue pour la première fois le 1er novembre
Quand elle est relue le 5 novembre, avec un autre prix
Alors sa première détection reste le 1er novembre, sa dernière vue est le 5 novembre
```
Test : `ListingLifecycleAcceptanceTest.keeps_the_first_seen_date()`, `JpaListingRepositoryTest.keeps_the_first_seen_date_of_a_known_listing()`

### AT-32-02 – Annonce jamais traitée
```gherkin
Étant donné l'annonce 101 présente, vue pour la première fois le 1er novembre
Quand j'ouvre sa fiche
Alors son cycle de vie a un seul événement : vue pour la première fois le 1er novembre
```
Test : `ListingLifecycleAcceptanceTest.a_listing_never_handled_has_only_its_first_sighting()`

### AT-32-03 – Article republié
```gherkin
Étant donné l'annonce 1 vue le 1er novembre, supprimée par l'outil le 10, republiée le 10 sous l'annonce 2
Et l'annonce 2 vue pour la première fois le 10, elle-même supprimée le 20 et republiée le 20 sous l'annonce 3
Quand j'ouvre la fiche de l'annonce 1, de la 2 ou de la 3
Alors chacune montre les mêmes événements dans l'ordre : 1 vue, 1 supprimée, 1 republiée sous 2, 2 vue, 2 supprimée, 2 republiée sous 3, 3 vue
```
Test : `ListingLifecycleAcceptanceTest.shows_the_whole_article_from_any_listing_of_the_chain()`

### AT-32-04 – Sortie du dressing hors de l'outil
```gherkin
Étant donné l'annonce 101 vue le 1er novembre, puis absente au scan terminé après sa dernière vue du 5 novembre, sans suppression confirmée
Quand j'ouvre sa fiche
Alors son cycle de vie montre : vue pour la première fois le 1er, sortie du dressing (vue pour la dernière fois le 5)
```
Test : `ListingLifecycleAcceptanceTest.a_listing_gone_without_the_tool_left_the_wardrobe()`

### AT-32-05 – Faits incertains
```gherkin
Étant donné l'annonce 101 dont la suppression n'est pas confirmée, et l'annonce 102 supprimée dont la republication est en erreur
Quand j'ouvre leur fiche
Alors la 101 n'a que sa première détection, la 102 sa première détection et sa suppression, sans republication
```
Test : `ListingLifecycleAcceptanceTest.uncertain_facts_are_no_events()`

### AT-32-06 – Annonces connues avant l'US
```gherkin
Étant donné, avant la migration, l'annonce 1 vue pour la dernière fois le 5 novembre et relevée les 2 et 4 novembre
Et l'annonce 2 vue pour la dernière fois le 5 novembre et jamais relevée
Quand la migration passe
Alors la première détection de l'annonce 1 est le 2 novembre, celle de l'annonce 2 le 5 novembre
```
Test : `ListingFirstSeenMigrationTest.takes_the_first_seen_date_from_the_oldest_reading_or_the_last_sighting()`

### AT-32-07 – API et fiche
```gherkin
Quand la fiche d'une annonce republiée est demandée
Alors l'API renvoie sa première détection et son cycle de vie
Et la fiche montre « Vue pour la première fois le … », « Article suivi depuis le … » et les événements, avec un lien vers la fiche des autres annonces de la chaîne
```
Test : `ListingDetailApiControllerTest.detail_shows_the_first_sighting_and_the_lifecycle()`, `ListingDetailPageTest.shows_the_lifecycle()`

## Cas limites et erreurs
- Chaîne qui bouclerait (impossible : un identifiant Vinted n'est jamais réutilisé) : le parcours s'arrête sur une annonce déjà vue, dans les deux sens (`ListingLifecycleAcceptanceTest.a_chain_that_loops_stops_on_a_listing_already_seen()`).
- Annonce de la chaîne inconnue de l'outil (impossible : la nouvelle annonce d'une republication publiée est toujours enregistrée) : elle est sautée, ses événements manquent.
- Annonce supprimée par l'outil puis jamais republiée : `DELETED` sans `LEFT_WARDROBE` (sa sortie est expliquée par la suppression).
- Annonce revenue dans le dressing après une absence : présente, donc pas de `LEFT_WARDROBE` ; sa première détection reste la première.

## Hors périmètre
- Distinguer une vente d'un retrait par le membre : la vente relève d'US-36 (suivi des ventes) ; ici, « sortie du dressing » pour les deux.
- Nouvel état stocké de l'annonce : l'étape de traitement (US-14) reste l'état courant, déduit des faits.
- Cycle de vie dans la liste des annonces, durée en ligne, tri ou filtre sur la première détection.
- Changements de prix et de titre dans la chronologie : historique de prix d'US-33.
- Les tentatives incertaines (déjà dans l'étape et le journal).

## Décisions
Décidé par Claude sur délégation de l'utilisateur (2026-11-17, « rédiger et valider la spec soi-même », le README ne donnant que le titre de l'US) :
- Le cycle de vie est une chronologie lue en base, pas un nouvel état : l'étape de traitement (US-14) dit déjà où en est l'annonce ; ce qui manque est l'historique de l'article et sa date d'entrée. Seule donnée nouvelle : la date de première détection.
- La première détection est gardée par le dépôt des annonces (une annonce déjà connue garde la sienne quand elle est réenregistrée ; l'adaptateur JPA relit la date gardée avant d'écrire), pour que tous les cas d'usage qui enregistrent une annonce (scan, republication, vérification, validation manuelle) la respectent sans changement.
- Reprise des annonces existantes depuis le plus ancien relevé ou la dernière vue plutôt qu'une colonne facultative : la fiche et l'API ont toujours une date, au prix d'une approximation dite dans la spec.
- La chronologie couvre toute la chaîne de republications, dans les deux sens, comme le total cumulé d'US-24 : c'est le même article.
- Date de suppression = date du clic (quand l'outil a supprimé), pas celle de la preuve ; date de republication = date de preuve, comme US-24.
- Les faits incertains ne sont pas des événements : la chronologie ne dit que ce qui est établi ; l'étape et le journal montrent le reste.
- « Sortie du dressing » regroupe vente et retrait, datée de la dernière vue : l'outil ne sait ni pourquoi ni quand exactement (US-36 précisera la vente).
- Fiche seulement : la liste partagée avec les autres US de l'Epic 9 n'est pas touchée (YAGNI).

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `Listing` gagne `firstSeenAt` (date de première détection) ; `Listing.seen` la fixe à la date de lecture.
- `ListingRepository.saveAll` : une annonce déjà connue garde sa première détection.
- `ListingLifecycle` (value object, `domain/inventory`) : événements de l'article, construits à partir des annonces de la chaîne, la plus ancienne d'abord, chacune avec sa dernière suppression et sa dernière republication.
- `LifecycleEvent` (record : type, annonce, date, nouvelle annonce éventuelle) et `LifecycleEventType` (`FIRST_SEEN`, `DELETED`, `REPUBLISHED`, `LEFT_WARDROBE`).
- `ListingDetail` gagne `ListingLifecycle lifecycle`.

## Tests unitaires et d'intégration attendus
- `domain` : `ListingLifecycleTest` (événements d'une annonce, chaîne, faits incertains, sortie du dressing), `ListingTest` (première détection).
- `application` : couverts par `ListingLifecycleAcceptanceTest`.
- `adapter/out/persistence` : `JpaListingRepositoryTest` (première détection gardée), `ListingFirstSeenMigrationTest` (reprise).
- `adapter/in/web` : `ListingDetailApiControllerTest`, `ListingDetailPageTest`.
