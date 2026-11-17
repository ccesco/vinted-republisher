# US-24 – Comparer vues et likes avant et après republication
**Statut** : implémentée
**Priorité** : V2
**Dépend de** : US-07 (relevés), US-12 et US-13 (lien vers la nouvelle annonce), US-18b (fiche), US-25 (relevés fréquents)

## Contexte
Republier remet à zéro les vues et favoris : la nouvelle annonce est une annonce à part, sous un nouvel identifiant Vinted, reliée à l'ancienne (US-12, « republiée depuis »). Le membre veut savoir si la republication a servi : combien l'ancienne annonce avait obtenu, combien la nouvelle obtient, et le total sur toute la vie de l'article. US-07 a gardé les relevés pour cela (décision du 2026-11-11 : historique et total cumulé sur les republications).

## Objectif
En tant que membre, je veux voir, sur la fiche d'une annonce republiée, ses vues et favoris à côté de ceux de l'annonce qu'elle remplace, et leur total cumulé, afin de juger si republier vaut la peine.

## Comportement attendu
1. **Annonce concernée** : une annonce qui est la nouvelle annonce d'une republication publiée (`PUBLISHED`). Les autres n'ont pas de comparaison.
2. **Avant** : le dernier relevé de l'annonce remplacée, c'est-à-dire ses nombres quand elle a quitté Vinted, avec sa date ; absent si elle n'a jamais été relevée.
3. **Après** : le dernier relevé de la nouvelle annonce, avec sa date ; absent tant qu'elle n'a pas été relevée (scan ou rafraîchissement, US-25).
4. **Date de republication** : celle où la nouvelle annonce a été trouvée sur Vinted (`publishedAt`), et le lien vers la fiche de l'annonce remplacée.
5. **Chaîne** : une annonce republiée plusieurs fois (A → B → C) : « avant » est l'annonce remplacée directement (B) ; le total cumulé additionne le dernier relevé de chaque annonce de la chaîne (A, B et C) ; le nombre d'annonces précédentes (2) est indiqué.
6. **Total cumulé** : somme des vues et des favoris des derniers relevés de la chaîne ; une annonce jamais relevée compte pour zéro.
7. **Lecture seule** : tout vient de la base, rien n'est demandé à Vinted.
8. **Fiche** : dans la partie « Vues et favoris », un tableau « Avant / après republication » : ligne « Avant (annonce remplacée, au …) », ligne « Après (cette annonce, au …) », ligne « Total depuis la première mise en ligne (N annonces) » ; « jamais relevée » à la place des nombres absents. Une phrase indique « Republiée le … depuis l'annonce … » avec un lien vers sa fiche.

## Contrat d'API
- `ListingDetail` gagne `statisticsComparison` (`StatisticsComparison`, facultatif : absent si l'annonce ne vient pas d'une republication).
- `StatisticsComparison` : `previousListingId` (int64), `republishedAt` (date-heure), `before` et `after` (`ListingStatistics`, facultatifs), `cumulatedViewCount` et `cumulatedFavouriteCount` (entiers ≥ 0), `previousListingCount` (entier ≥ 1) ; tous obligatoires sauf `before` et `after`.

## Critères d'acceptation
- [x] La fiche d'une nouvelle annonce montre le dernier relevé de l'annonce remplacée, le sien, la date de republication et l'annonce remplacée.
- [x] Sur une chaîne de republications, « avant » est l'annonce remplacée directement, et le total cumule toute la chaîne.
- [x] Une annonce qui ne vient pas d'une republication n'a pas de comparaison.
- [x] Un relevé absent est montré comme tel et compte pour zéro dans le total.
- [x] L'API et la fiche montrent la comparaison.

## Tests d'acceptation
### AT-24-01 – Avant et après une republication
```gherkin
Étant donné l'annonce 1, relevée à 120 vues et 8 favoris, republiée le 10 novembre sous l'annonce 2
Et l'annonce 2 relevée à 30 vues et 4 favoris
Quand j'ouvre la fiche de l'annonce 2
Alors avant : 120 vues et 8 favoris (annonce 1, avec la date du relevé)
Et après : 30 vues et 4 favoris
Et republiée le 10 novembre depuis l'annonce 1, total cumulé 150 vues et 12 favoris sur une annonce précédente
```
Test : `CompareRepublicationStatisticsAcceptanceTest.compares_before_and_after_a_republication()`

### AT-24-02 – Chaîne de republications
```gherkin
Étant donné l'annonce 1 (100 vues, 5 favoris) republiée sous la 2 (40 vues, 3 favoris), elle-même republiée sous la 3 (10 vues, 1 favori)
Quand j'ouvre la fiche de l'annonce 3
Alors avant : celle de l'annonce 2, 40 vues et 3 favoris
Et le total cumulé est de 150 vues et 9 favoris sur deux annonces précédentes
```
Test : `CompareRepublicationStatisticsAcceptanceTest.cumulates_a_chain_of_republications()`

### AT-24-03 – Annonce qui ne vient pas d'une republication
```gherkin
Étant donné une annonce jamais republiée, et une annonce dont la republication est en erreur ou non publiée
Quand j'ouvre leur fiche, et celle de l'annonce d'origine d'une republication
Alors aucune n'a de comparaison
```
Test : `CompareRepublicationStatisticsAcceptanceTest.no_comparison_without_a_published_republication()`

### AT-24-04 – Relevés absents
```gherkin
Étant donné l'annonce 1 jamais relevée, republiée sous l'annonce 2 pas encore relevée
Quand j'ouvre la fiche de l'annonce 2
Alors avant et après sont absents et le total cumulé est de 0 vue et 0 favori
```
Test : `CompareRepublicationStatisticsAcceptanceTest.missing_readings_count_for_nothing()`

### AT-24-05 – API et fiche
```gherkin
Quand la fiche d'une annonce republiée est demandée
Alors l'API renvoie la comparaison, et la fiche montre avant, après, le total et le lien vers l'annonce remplacée
```
Test : `ListingDetailApiControllerTest.detail_shows_the_republication_comparison()`, `ListingDetailPageTest.shows_the_republication_comparison()`

## Cas limites et erreurs
- Annonce remplacée relevée après sa suppression : impossible, elle n'a plus de carte ; son dernier relevé est donc celui d'avant sa suppression.
- Chaîne qui bouclerait (impossible : un identifiant Vinted n'est jamais réutilisé) : le parcours s'arrête sur une annonce déjà vue (`CompareRepublicationStatisticsAcceptanceTest.a_chain_that_loops_stops_on_a_listing_already_seen()`).

## Hors périmètre
- Total cumulé dans la liste des annonces et tri sur ce total : la liste garde le dernier relevé de chaque annonce.
- Comparaison à âge égal (vues par jour, au même nombre de jours en ligne) : l'âge réel d'une annonce sur Vinted n'est pas connu de l'outil.
- Comparaison montrée sur la fiche de l'annonce remplacée : elle a déjà le lien vers sa nouvelle annonce (US-13).
- Graphiques.

## Décisions
Décidé par Claude sur délégation de l'utilisateur (2026-11-17, « fais US-24 et US-25 comme tu le sens ») :
- La comparaison vit sur la fiche de la nouvelle annonce : c'est celle que le membre suit après une republication.
- « Avant » = dernier relevé de l'annonce remplacée : ses nombres au moment où elle a quitté Vinted.
- Total cumulé sur toute la chaîne, conformément à la décision d'US-07 (« cumul avec les anciens »), un relevé absent comptant pour zéro plutôt que de masquer le total.
- Pas de taux par jour : il supposerait la date de mise en ligne d'origine, que l'outil ne connaît pas (il ne connaît que la date du premier scan).

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `StatisticsComparison` (value object, `domain/inventory`) : annonce remplacée, date de republication, relevé avant, relevé après, total cumulé (`ListingStatistics`), nombre d'annonces précédentes ; construit à partir de la republication et des derniers relevés de la chaîne.
- `ListingDetail` gagne `Optional<StatisticsComparison>`.
- `ListingRepublicationRepository.findPublishedAs(VintedListingId)` : la republication publiée dont l'annonce est la nouvelle annonce.

## Tests unitaires et d'intégration attendus
- `domain` : `StatisticsComparisonTest` (avant, après, cumul, absents).
- `application` : couverts par `CompareRepublicationStatisticsAcceptanceTest`.
- `adapter/out/persistence` : `JpaListingRepublicationRepositoryTest` (`findPublishedAs`).
- `adapter/in/web` : `ListingDetailApiControllerTest`, `ListingDetailPageTest`.
