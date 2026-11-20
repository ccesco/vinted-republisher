# US-50 – Une annonce vendue n'est jamais à supprimer
**Statut** : implémentée
**Priorité** : V2
**Dépend de** : US-09 (suppression), US-19 / US-46 (série), US-17 (échecs), US-25 (rafraîchissement), US-36 (ventes)

## Contexte
Constaté le 2026-11-20 sur l'annonce 10197264390 : notée vendue (US-36), elle restait « À supprimer » et la série tentait de la supprimer, puis s'arrêtait. Une nouvelle série s'arrêtait de la même façon. Deux causes se cumulent :
1. **L'étape ignore la vente.** `ListingStage.of` ne connaît que la présence de l'annonce. Le rafraîchissement des statistiques (US-25) enregistre la vente mais ne marque pas l'annonce absente (seul le scan le fait, US-04). Une annonce vendue dont la sauvegarde est validée reste donc `TO_DELETE` jusqu'au scan suivant.
2. **Le refus arrive trop tard.** Sur la page de l'annonce, l'adaptateur de suppression exige le bouton « Supprimer » avant de comparer l'annonce à sa sauvegarde. Une annonce vendue n'a pas ce bouton : `ListingPageUnreadable` arrête toute la série (`VINTED_PAGE_UNREADABLE`, US-17), au lieu du refus `ListingChanged` (« plus en vente »), qui n'écarte que l'annonce.

## Objectif
En tant que membre, je veux qu'une annonce vendue ne soit jamais proposée ni tentée à la suppression, et qu'une annonce qui n'est plus en vente n'arrête pas la série, afin que mes autres annonces continuent d'être republiées.

## Comportement attendu
1. **Étape « Vendue »** (`SOLD`) : une annonce dont une vente est enregistrée (US-36) est à l'étape « Vendue », que le relevé qui l'a constatée soit un scan ou un rafraîchissement. Cette règle vient après celles de la republication et de la suppression (une annonce republiée ou supprimée par l'outil garde son étape) et avant toutes les autres (« Disparue », « À supprimer », etc.).
2. **Rien à relancer** : « Vendue » n'a aucune action (`RelaunchAction.after(SOLD)` est vide). La série ne la prend pas, la relance manuelle la refuse (`NOT_RELAUNCHABLE`), et la suppression depuis la fiche est refusée comme pour une annonce qui n'est plus en vente.
3. **Comparer avant d'exiger le bouton** : sur la page de l'annonce, l'outil lit l'annonce et la compare à sa sauvegarde (`agreesToDelete`) **avant** d'attendre le bouton « Supprimer ». Une annonce vendue ou réservée entre-temps (pas « en stock » d'après la page) donne `ListingChanged` (« plus en vente ») : rien n'est cliqué, la série écarte l'annonce et continue (US-17). Une annonce en vente sans bouton « Supprimer » reste une page illisible, qui arrête la série comme aujourd'hui.
4. **IHM** : l'étape « Vendue » s'affiche dans la liste, sur la fiche et dans le filtre par étape. Elle n'apparaît ni dans les annonces en échec (US-39) ni dans les conseils de republication.

## Contrat d'API
- `ListingStage` (énumération du contrat) gagne `SOLD`. Aucun autre changement.

## Critères d'acceptation
- [x] Une annonce dont une vente est enregistrée est « Vendue », même si elle est encore présente et que sa sauvegarde est validée.
- [x] Une annonce republiée ou dont la suppression est enregistrée garde son étape, même vue vendue.
- [x] La série ne prend pas une annonce « Vendue » ; la relance manuelle la refuse.
- [x] Sur une page d'annonce qui n'est plus en vente et sans bouton « Supprimer », la suppression est refusée (`ListingChanged`), rien n'est cliqué, et la série continue avec les autres annonces.
- [x] Une annonce en vente sans bouton « Supprimer » reste une page illisible.
- [x] La liste, la fiche et le filtre montrent « Vendue ».

## Tests d'acceptation
### AT-50-01 – Vendue au rafraîchissement, plus à supprimer
```gherkin
Étant donné l'annonce 1, présente, sauvegardée et validée (« À supprimer »)
Quand un rafraîchissement des vues et favoris la montre « Vendu »
Alors l'annonce 1 est « Vendue »
Et elle n'est plus proposée à la suppression
```
Test : `TrackSalesAcceptanceTest.a_listing_noticed_sold_is_no_longer_to_delete()`

### AT-50-02 – La série ignore une annonce vendue
```gherkin
Étant donné l'annonce 1 « Vendue » et l'annonce 2 « À supprimer »
Quand je lance la série
Alors seule l'annonce 2 est supprimée
Et l'annonce 1 n'est jamais ouverte
```
Test : `AutomaticRunAcceptanceTest.the_run_leaves_sold_listings_alone()`

### AT-50-03 – Relance manuelle refusée
```gherkin
Étant donné l'annonce 1 « Vendue »
Quand je la relance depuis sa fiche
Alors la relance est refusée : rien à relancer à l'étape « Vendue »
```
Test : `RelaunchListingAcceptanceTest.stages_without_next_action_are_not_relaunchable(SOLD)`

### AT-50-04 – Annonce vendue entre-temps : refus, la série continue
```gherkin
Étant donné l'annonce 1 « À supprimer », que l'outil ne sait pas encore vendue
Et sa page sur Vinted la montre vendue, sans bouton « Supprimer »
Et l'annonce 2 « À supprimer »
Quand je lance la série
Alors la suppression de l'annonce 1 est refusée car elle n'est plus en vente, sans aucun clic
Et l'annonce 1 est écartée de la série
Et l'annonce 2 est supprimée
```
Test : `AutomaticRunAcceptanceTest.a_listing_sold_meanwhile_is_set_aside_and_the_run_goes_on()`

### AT-50-05 – Page d'annonce vendue sans bouton
```gherkin
Étant donné une copie locale d'une page d'annonce « épuisée » (schema.org SoldOut), sans bouton « Supprimer »
Quand l'adaptateur de suppression la visite
Alors la visite est consultée avec l'annonce lue, non active
Et rien n'est cliqué, et la page n'est pas déclarée illisible
```
Test : `PlaywrightListingDeleterTest.sold_listing_without_delete_button_is_left_to_the_visit_and_nothing_is_clicked()`

### AT-50-06 – Annonce en vente sans bouton : toujours illisible
```gherkin
Étant donné une copie locale d'une page d'annonce en vente, sans bouton « Supprimer »
Quand l'adaptateur de suppression la visite et que la visite accepte
Alors la page est illisible et rien n'est cliqué
```
Test : `PlaywrightListingDeleterTest.page_without_delete_button_is_unreadable_and_nothing_is_clicked()` (existant, adapté à l'ordre, comme `page_with_two_delete_buttons_is_unreadable_and_nothing_is_clicked()`)

### AT-50-07 – Étape affichée
```gherkin
Étant donné l'annonce 1 « Vendue »
Quand le membre ouvre la liste des annonces, filtre par l'étape « Vendue », puis ouvre sa fiche
Alors il voit « Vendue » dans la liste, le filtre et la fiche
```
Test : `WardrobePageTest.shows_the_sold_stage()`, `ListingDetailPageTest.shows_the_sold_stage()`

## Cas limites et erreurs
- Annonce vendue puis disparue de la grille au scan suivant (absente) : reste « Vendue », pas « Disparue ».
- Suppression en doute (« À revérifier ») puis annonce vue vendue : garde « À revérifier » (règle 1) ; la revérification la trouvera présente.
- Annonce republiée par l'outil, la nouvelle vendue : la nouvelle est « Vendue », l'ancienne reste « Republiée » (US-36).
- Annonce réservée (non vendue) : pas de vente enregistrée, donc pas « Vendue » ; si elle est tentée, le point 3 la refuse sans arrêter la série.
- Suppression confirmée puis vente enregistrée : ne se produit pas, une annonce supprimée ne peut plus être vendue ni montrée « Vendu » ; la règle de la suppression (règle 1) s'appliquerait.
- Suppression lancée depuis la page de sauvegarde d'une annonce « Vendue » : Chrome ouvre la page de l'annonce, qui n'est plus en vente, et le point 3 la refuse (`ListingChanged`) sans aucun clic.

## Hors périmètre
- Marquer l'annonce absente au rafraîchissement des statistiques : la vente enregistrée suffit à l'écarter.
- Retirer l'annonce de la série déjà en cours au moment où la vente est constatée : la série relit l'inventaire à chaque choix, cela se fait de lui-même.
- Annuler une vente enregistrée (US-36 : jamais supprimée).

## Décisions
- Étape dédiée `SOLD` plutôt que `GONE` : la vente est visible et filtrable, sans la confondre avec un retrait (proposé le 2026-11-20, accepté par l'utilisateur en demandant la spec).
- Les deux corrections ensemble : l'étape évite la tentative, la comparaison avant le bouton empêche qu'une annonce vendue pas encore constatée arrête la série.
- « Vendue » passe après les règles de republication et de suppression, pour ne masquer ni une republication ni une suppression en doute (validé par l'utilisateur le 2026-11-20).
- Libellé « Vendue », cohérent avec la carte Vinted et la page « Ventes » (validé par l'utilisateur le 2026-11-20).

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `ListingStage.SOLD` | Vendue | Annonce dont une vente est enregistrée ; rien à relancer. À ajouter au glossaire.
- `ListingStage.of(...)` gagne `boolean sold`, alimenté par `ListingInventory` depuis `ListingSaleRepository`.
- `PlaywrightListingDeleter.delete` : `visit.agreesToDelete(read)` avant `visible(DELETE_BUTTON)` ; en dry run, le bouton reste exigé avant de répondre « passé ».

## Tests unitaires et d'intégration attendus
- [x] `domain` : `ListingStageTest` (vendue présente validée, vendue absente, suppression et republication l'emportent), `FailedListingTest` (pas de `SOLD`). `RelaunchAction.after(SOLD)` vide est couvert par AT-50-03 ; `RepublicationAdvice` ne conseille déjà que les étapes « en vente », sans `SOLD`.
- [x] `application` : `TrackSalesAcceptanceTest`, `AutomaticRunAcceptanceTest`, `RelaunchListingAcceptanceTest` ; `ShowListingInventory` et `ShowListingDetail` lisent les ventes (`ListingSaleRepository`).
- [x] `adapter/out/browser` : `PlaywrightListingDeleterTest`.
- [x] `adapter/in/web` : contrat (`SOLD`), `WardrobePageTest`, `ListingDetailPageTest` ; libellé dans `STAGE_LABELS` (couvert par les tests de pages).
