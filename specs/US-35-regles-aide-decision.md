# US-35 – Règles d'aide à la décision
**Statut** : validée
**Priorité** : V2
**Dépend de** : US-07 et US-25 (relevés de vues et favoris), US-14 (étapes), US-18 (liste), US-18b (fiche)

## Contexte
Republier remet à zéro les vues et les favoris (US-24) : ce n'est utile que pour une annonce qui ne se voit plus. Avec plus d'une centaine d'annonces, le membre ne peut pas comparer lui-même l'historique de chacune pour savoir lesquelles republier. Les relevés gardés depuis US-07 et rafraîchis à la demande (US-25) permettent de le lui dire : une annonce qui ne gagne presque plus de vues est « endormie » et mérite une republication ; une annonce que beaucoup de membres ont mise en favori ne doit pas perdre ces favoris.

## Objectif
En tant que membre, je veux que l'outil me signale les annonces qu'il conseille de republier, avec la raison chiffrée, afin de choisir lesquelles republier sans éplucher l'historique de chacune.

## Comportement attendu
1. **Conseil de republication** : pour chaque annonce, l'outil dit s'il conseille de la republier ; il ne fait rien de lui-même (aucune étape, relance ni série n'en dépend).
2. **Annonce en vente seulement** : seule une annonce présente dans le dressing et pas engagée dans une suppression ou une republication (étapes « à sauvegarder », « à valider », « prête à supprimer ») peut être conseillée.
3. **Période d'observation** : 7 jours. Le relevé de référence est le dernier relevé de l'annonce fait au plus tard il y a 7 jours ; le relevé actuel est son dernier relevé, qui doit dater de moins de 7 jours. Sans relevé de référence (annonce suivie depuis moins de 7 jours, nouvelle annonce d'une republication comprise) ou sans relevé récent, pas de conseil : les données ne suffisent pas.
4. **Vues gagnées** : vues du relevé actuel moins vues du relevé de référence (zéro si négatif).
5. **Annonce endormie** : moins de 10 vues gagnées sur la période → conseil de republication.
6. **Favoris protégés** : une annonce qui a 5 favoris ou plus à son relevé actuel n'est jamais conseillée, même endormie : la republier les perdrait.
7. **Lecture seule** : tout vient de la base, rien n'est demandé à Vinted ; le conseil est recalculé à chaque lecture, jamais gardé.
8. **Liste** (`wardrobe.html`) : une colonne « Conseil » affiche « À republier » pour une annonce conseillée, avec en infobulle « +N vues depuis le … » ; vide sinon.
9. **Fiche** (`listing.html`) : dans « Vues et favoris », une phrase « Conseil : à republier, seulement N vues gagnées depuis le … (moins de 10 en 7 jours). » pour une annonce conseillée ; rien sinon.

## Contrat d'API
- `Listing` gagne `republicationAdvice` (`RepublicationAdvice`, facultatif : absent si l'annonce n'est pas conseillée). Il apparaît donc dans la liste, la fiche et le compte rendu d'une relance.
- `RepublicationAdvice` : `viewsGained` (entier ≥ 0, vues gagnées depuis le relevé de référence) et `since` (date-heure du relevé de référence), tous deux obligatoires.

## Critères d'acceptation
- [ ] Une annonce en vente qui a gagné moins de 10 vues depuis son relevé d'il y a au moins 7 jours est conseillée, avec les vues gagnées et la date du relevé de référence.
- [ ] Une annonce qui a gagné 10 vues ou plus n'est pas conseillée.
- [ ] Une annonce qui a 5 favoris ou plus n'est pas conseillée.
- [ ] Sans relevé de référence ou sans relevé de moins de 7 jours, pas de conseil.
- [ ] Une annonce absente, ou engagée dans une suppression ou une republication, n'est pas conseillée.
- [ ] L'API, la liste et la fiche montrent le conseil.

## Tests d'acceptation
Dates du 17 novembre à 10 h ; « il y a 7 jours » = le 10 novembre à 10 h.

### AT-35-01 – Annonce endormie
```gherkin
Étant donné l'annonce 1 en vente, relevée le 8 novembre à 40 vues et 2 favoris, puis le 16 novembre à 46 vues et 2 favoris
Quand je consulte la liste des annonces le 17 novembre
Alors l'annonce 1 est conseillée à republier : 6 vues gagnées depuis le 8 novembre
```
Test : `AdviseRepublicationAcceptanceTest.advises_a_dormant_listing()`

### AT-35-02 – Annonce qui se voit encore
```gherkin
Étant donné l'annonce 1 en vente, relevée le 8 novembre à 40 vues, puis le 16 novembre à 50 vues
Quand je consulte la liste
Alors l'annonce 1 n'est pas conseillée
```
Test : `AdviseRepublicationAcceptanceTest.does_not_advise_a_listing_still_seen()`

### AT-35-03 – Favoris protégés
```gherkin
Étant donné l'annonce 1 en vente, relevée le 8 novembre à 40 vues et 5 favoris, puis le 16 novembre à 41 vues et 5 favoris
Quand je consulte la liste
Alors l'annonce 1 n'est pas conseillée
```
Test : `AdviseRepublicationAcceptanceTest.does_not_advise_a_listing_with_many_favourites()`

### AT-35-04 – Données insuffisantes
```gherkin
Étant donné l'annonce 1 relevée seulement depuis le 12 novembre
Et l'annonce 2 relevée le 1er novembre puis le 8 novembre, plus depuis
Quand je consulte la liste
Alors aucune n'est conseillée
```
Test : `AdviseRepublicationAcceptanceTest.does_not_advise_without_enough_readings()`

### AT-35-05 – Annonce pas en vente
```gherkin
Étant donné trois annonces endormies : l'une absente du dressing, l'une dont la suppression est confirmée, l'une republiée
Quand je consulte la liste
Alors aucune n'est conseillée
```
Test : `AdviseRepublicationAcceptanceTest.does_not_advise_a_listing_not_on_sale()`

### AT-35-06 – API, liste et fiche
```gherkin
Quand la liste ou la fiche d'une annonce conseillée est demandée
Alors l'API renvoie le conseil, la liste affiche « À republier » et la fiche la phrase du conseil
```
Test : `ListingInventoryApiControllerTest.lists_the_republication_advice()`, `ListingDetailApiControllerTest.detail_shows_the_republication_advice()`, `WardrobePageTest.shows_the_republication_advice()`, `ListingDetailPageTest.shows_the_republication_advice()`

## Cas limites et erreurs
- Relevé fait exactement il y a 7 jours : il sert de référence (au plus tard), pas de relevé actuel.
- Vues en baisse (relevé incohérent de Vinted) : vues gagnées comptées zéro, l'annonce est conseillée si elle remplit les autres règles.
- Annonce sans aucun relevé (membre jamais connecté pendant un relevé) : pas de conseil.

## Hors périmètre
- Filtre ou tri de la liste sur le conseil : la colonne suffit à repérer les annonces ; à ajouter si le besoin se confirme.
- Seuils réglables par le membre ou la configuration : valeurs fixes tant que l'usage ne les remet pas en cause.
- Autres conseils (baisser le prix, garder, retirer) : ils dépendraient de l'historique des prix (US-33) ou des ventes (US-36), pas encore disponibles ; seul le conseil de republication est couvert.
- Lancer une série sur les seules annonces conseillées : le conseil recommande, il n'agit pas.
- Prise en compte des chaînes de republication (US-24) : une nouvelle annonce n'a pas d'historique propre de 7 jours, elle n'est donc pas conseillée avant une semaine ; les cumuls de la chaîne ne changent pas ce qu'elle gagne aujourd'hui.

## Décisions
Décidé par Claude sur délégation de l'utilisateur (2026-11-17, « rédiger et valider la spec soi-même ») :
- Un seul conseil, « à republier » : c'est la décision que l'outil sert ; les autres (prix, garder) attendent US-33 et US-36, développées en parallèle et non disponibles.
- Critère sur les vues gagnées, pas sur le total : le total ne dit pas si l'annonce se voit encore ; les vues récentes oui.
- Période de 7 jours et seuil de 10 vues : une semaine couvre les variations du jour de la semaine, et moins de 10 vues en une semaine, c'est une annonce que Vinted ne montre presque plus. Valeurs fixes dans le domaine (YAGNI), modifiables par un changement de code et de spec.
- 5 favoris protègent l'annonce : les favoris sont des acheteurs intéressés (Vinted les prévient d'une baisse de prix) ; les perdre coûte plus que les vues gagnées.
- Référence = dernier relevé au plus tard il y a 7 jours, et relevé actuel de moins de 7 jours : sans relevé récent, on ne sait pas si l'annonce se voit encore ; on ne conseille pas sur des données périmées.
- Annonces en vente seulement (étapes « à sauvegarder », « à valider », « prête à supprimer ») : conseiller une annonce déjà supprimée ou republiée n'a pas de sens.
- Conseil dans la liste (colonne) et la fiche (phrase), porté par l'annonce dans l'API : c'est là que le membre choisit quoi relancer ; aucune table ni changeset, le conseil est calculé à la lecture comme l'étape.

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `RepublicationAdvice` (value object, `domain/inventory`) : vues gagnées et date du relevé de référence ; `RepublicationAdvice.of(stage, latest, baseline, now)` applique les règles ; constantes `OBSERVATION_PERIOD` (7 jours), `MINIMUM_VIEWS_GAINED` (10), `PROTECTING_FAVOURITE_COUNT` (5).
- `InventoryEntry` gagne `Optional<RepublicationAdvice> advice` ; `ListingInventory.of` reçoit en plus les relevés de référence.
- `ListingStatisticsRepository.findLatestAsOf(Instant)` : le dernier relevé de chaque annonce fait au plus tard à cette date.
- Glossaire : `RepublicationAdvice` (Conseil de republication).

## Tests unitaires et d'intégration attendus
- `domain` : `RepublicationAdviceTest` (endormie, seuil de vues, favoris, référence absente, relevé périmé, étapes, vues en baisse, relevé exactement il y a 7 jours) ; `ListingInventoryTest` (conseil porté par l'entrée).
- `application` : couverts par `AdviseRepublicationAcceptanceTest`.
- `adapter/out/persistence` : `JpaListingStatisticsRepositoryTest` (`findLatestAsOf`).
- `adapter/in/web` : `ListingInventoryApiControllerTest`, `ListingDetailApiControllerTest`, `WardrobePageTest`, `ListingDetailPageTest`.
