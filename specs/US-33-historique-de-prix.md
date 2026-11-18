# US-33 – Historique de prix
**Statut** : implémentée
**Priorité** : V2
**Dépend de** : US-04 (scan, prix lu sur la page de l'annonce), US-18b (fiche), US-12 et US-13 (nouvelle annonce enregistrée par la republication)

## Contexte
L'outil ne garde que le dernier prix lu de chaque annonce : quand le membre baisse un prix sur Vinted, le scan suivant écrase l'ancien. Le membre veut savoir à quel prix une annonce a été mise en ligne, quand il l'a baissé et de combien, pour juger ses baisses (avec les vues et favoris, US-07) et préparer les règles d'aide à la décision (US-35).

## Objectif
En tant que membre, je veux voir sur la fiche d'une annonce tous les prix qu'elle a eus et depuis quand, afin de suivre mes baisses de prix.

## Comportement attendu
1. **Changement de prix** : chaque fois que l'outil enregistre une annonce (scan, annonce trouvée par la republication ou sa vérification, annonce marquée absente) et que son prix diffère du dernier prix de son historique, ou qu'elle n'a pas encore d'historique, il ajoute à l'historique ce prix, daté de la date où l'annonce a été vue (`lastSeenAt`).
2. **Même prix** : enregistrer une annonce au même prix (montant et devise) que le dernier de son historique n'ajoute rien, quel que soit le nombre de scans.
3. **Retour à un ancien prix** : un prix qui revient à une valeur déjà connue est un nouveau changement (12 € → 10 € → 12 € donne trois lignes).
4. **Devise** : un changement de devise seule est un changement de prix.
5. **Jamais modifié ni supprimé** : l'historique ne fait que grandir, même quand l'annonce sort du dressing.
6. **Annonces déjà connues** : aucune reprise de données ; leur historique commence au prochain enregistrement de l'annonce (en pratique, le prochain scan), avec le prix lu ce jour-là.
7. **Lecture seule** : la fiche lit l'historique en base, rien n'est demandé à Vinted ; aucune lecture supplémentaire de Vinted n'est faite, le prix est celui que le scan lit déjà.
8. **Fiche** : sous le prix actuel, une partie « Historique des prix » : un tableau « Depuis le » / « Prix », du plus récent au plus ancien ; « Aucun prix relevé » si l'historique est vide.

## Contrat d'API
- `ListingDetail` gagne `priceHistory` (obligatoire) : tableau de `PriceChange`, du plus récent au plus ancien, vide si l'annonce n'a pas encore d'historique.
- `PriceChange` : `price` (`Price`) et `changedAt` (date-heure : la date où l'outil a vu l'annonce à ce prix pour la première fois), tous deux obligatoires.

```yaml
PriceChange:
  type: object
  description: Un prix de l'annonce et la date où l'outil l'a vu pour la première fois.
  required: [price, changedAt]
  properties:
    price:
      $ref: '#/components/schemas/Price'
    changedAt:
      type: string
      format: date-time
```

## Critères d'acceptation
- [x] La première fois qu'une annonce est enregistrée, son prix commence son historique.
- [x] Un prix différent du dernier de l'historique (montant ou devise) s'ajoute à l'historique, daté du scan qui l'a lu.
- [x] Un prix inchangé n'ajoute rien ; une annonce marquée absente n'ajoute rien.
- [x] Une annonce mise en vente par une republication commence son propre historique avec son prix.
- [x] Une annonce connue avant cette US commence son historique au prochain enregistrement.
- [x] L'API et la fiche montrent l'historique, du plus récent au plus ancien.

## Tests d'acceptation
### AT-33-01 – Le premier scan commence l'historique
```gherkin
Étant donné l'annonce 101 « Pull rayé » à 12,50 €, inconnue de l'outil
Quand le scan du 1er novembre la lit
Alors l'historique de prix de sa fiche est : 12,50 € depuis le 1er novembre
```
Test : `PriceHistoryAcceptanceTest.first_scan_starts_the_price_history()`

### AT-33-02 – Une baisse de prix s'ajoute à l'historique
```gherkin
Étant donné l'annonce 101 lue à 12,50 € le 1er novembre
Quand le scan du 2 novembre la lit à 10,00 €, puis celui du 3 novembre à 12,50 €
Alors l'historique de prix de sa fiche est : 12,50 € depuis le 3, 10,00 € depuis le 2, 12,50 € depuis le 1er
```
Test : `PriceHistoryAcceptanceTest.each_price_change_is_added_most_recent_first()`

### AT-33-03 – Un prix inchangé n'ajoute rien
```gherkin
Étant donné l'annonce 101 lue à 12,50 € le 1er novembre
Quand le scan du 2 novembre la lit encore à 12,50 €, puis celui du 3 ne la trouve plus dans le dressing
Alors l'historique de prix de sa fiche reste : 12,50 € depuis le 1er novembre
```
Test : `PriceHistoryAcceptanceTest.unchanged_price_adds_nothing()`

### AT-33-04 – Changement de devise
```gherkin
Étant donné l'annonce 101 lue à 12,50 EUR
Quand elle est enregistrée à 12,50 GBP
Alors un nouveau prix s'ajoute à son historique
```
Test : `PriceChangeTest.a_new_currency_is_a_price_change()`

### AT-33-05 – Annonce mise en vente par une republication
```gherkin
Étant donné l'annonce 101 republiée sous l'annonce 202 à 12,50 €
Quand la nouvelle annonce 202 est trouvée sur Vinted et enregistrée
Alors l'historique de prix de l'annonce 202 commence à 12,50 €, à la date où elle a été trouvée
Et celui de l'annonce 101 ne change pas
```
Test : `JpaListingRepositoryTest.saving_a_new_listing_starts_its_price_history()` (tout enregistrement d'annonce passe par le dépôt des annonces, qui tient l'historique : la republication n'a rien de particulier)

### AT-33-06 – Annonce connue avant l'historique
```gherkin
Étant donné l'annonce 101 enregistrée à 12,50 € sans historique de prix
Quand j'ouvre sa fiche
Alors l'historique de prix est vide (« Aucun prix relevé »)
Quand le scan suivant la lit à 12,50 €
Alors son historique commence : 12,50 € depuis ce scan
```
Test : `JpaListingRepositoryTest.listing_without_price_history_starts_it_on_its_next_save()`

### AT-33-07 – API et fiche
```gherkin
Quand la fiche d'une annonce est demandée
Alors l'API renvoie son historique de prix, du plus récent au plus ancien
Et la fiche montre « Historique des prix » avec, pour chaque prix, sa date et son montant, ou « Aucun prix relevé »
```
Test : `ListingDetailApiControllerTest.detail_shows_the_price_history()`, `ListingDetailPageTest.shows_the_price_history()`

## Cas limites et erreurs
- Enregistrement en échec (base indisponible, historique refusé) : l'annonce et son historique sont enregistrés ensemble ou pas du tout (`JpaListingRepositoryTest.a_failing_price_history_saves_no_listing()`).
- Plusieurs annonces enregistrées ensemble (fin de scan, annonces marquées absentes) : chacune est comparée à son propre dernier prix ; une même annonce deux fois dans un lot est comparée au prix ajouté juste avant (`JpaListingRepositoryTest.same_listing_twice_in_one_save_adds_its_price_once()`).
- Deux enregistrements de la même annonce à la même date avec deux prix (impossible en pratique : la date est celle du scan ou de la vérification) : les deux prix sont gardés.

## Hors périmètre
- Historique de prix cumulé sur une chaîne de republications : chaque annonce a le sien ; le lien « republiée depuis » (US-24) mène à l'historique de l'annonce remplacée.
- Changer un prix sur Vinted depuis l'outil.
- Prix dans la liste des annonces (dernière baisse, tri ou filtre sur les baisses) : relève d'US-35.
- Graphique de l'évolution du prix.
- Reprise de l'historique des annonces déjà connues.

## Décisions
Décidé par Claude sur délégation de l'utilisateur (2026-11-17, « rédiger et valider la spec soi-même ») :
- L'historique ne garde que les changements de prix, pas un relevé par scan : c'est ce que le membre cherche (quand et de combien il a baissé), et la table ne grandit pas à chaque scan.
- Comparaison avec le dernier prix de l'historique, non avec le prix enregistré de l'annonce : une annonce connue avant cette US commence ainsi son historique d'elle-même au scan suivant, sans reprise de données (dont la date serait fausse : l'outil ne sait pas depuis quand l'annonce avait ce prix).
- Le dépôt des annonces (`ListingRepository`) tient l'historique, à chaque enregistrement d'annonce, dans la même transaction : tous les cas d'usage qui enregistrent une annonce (scan, republication, vérification, validation de republication, suppression) en profitent sans risque d'en oublier un. La règle « nouveau prix ou non » est dans le domaine (`PriceChange`).
- Date d'un prix = `lastSeenAt` de l'annonce enregistrée : la date où l'outil l'a vue à ce prix ; le vrai moment du changement sur Vinted n'est pas connu.
- Chaque annonce a son propre historique, même republiée : la republication remet le prix sauvegardé, la nouvelle annonce commence donc au prix de l'ancienne, ce que montre déjà le lien vers celle-ci.
- Pas d'écart calculé (« −2,50 € ») dans l'API ni la fiche : les prix successifs suffisent à le lire (YAGNI).

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `PriceChange` (value object, `domain/wardrobe`) : annonce, prix, date ; `PriceChange.of(Optional<PriceChange> latest, Listing saved)` donne le changement à ajouter, s'il y en a un.
- `ListingRepository` : `saveAll` ajoute le changement de prix de chaque annonce enregistrée ; `priceHistoryOf(VintedListingId)` rend l'historique, du plus récent au plus ancien.
- `ListingDetail` gagne `List<PriceChange> priceHistory`.
- Glossaire : `PriceChange` (Changement de prix), historique de prix.

## Tests unitaires et d'intégration attendus
- `domain` : `PriceChangeTest` (premier prix, prix changé, prix inchangé, devise changée).
- `application` : `PriceHistoryAcceptanceTest` (scan puis fiche, avec les fakes).
- `adapter/out/persistence` : `JpaListingRepositoryTest` (historique enregistré avec l'annonce, rien si inchangé, plus récent d'abord, annonce sans historique), changeset `012-create-listing-price-change`.
- `adapter/in/web` : `ListingDetailApiControllerTest`, `ListingDetailPageTest`.
