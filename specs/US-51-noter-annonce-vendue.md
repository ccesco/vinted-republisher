# US-51 – Noter une annonce vendue à la main
**Statut** : implémentée
**Priorité** : V2
**Dépend de** : US-36 (ventes), US-50 (étape « Vendue »), US-18b (fiche d'une annonce)

## Contexte
L'outil ne sait une annonce vendue que s'il voit sa carte « Vendu » dans le dressing, au scan ou au rafraîchissement (US-36). Une vente hors Vinted (en main propre), ou une carte disparue sans avoir été vue « Vendu », reste inconnue : l'annonce peut rester « À supprimer », ou « À republier » après sa suppression, et l'outil republierait un article qui n'existe plus.

## Objectif
En tant que membre, je veux noter moi-même qu'une annonce est vendue, et annuler cette note en cas d'erreur, afin que l'outil ne la republie plus et la compte dans mes ventes.

## Comportement attendu
1. **Action locale** : rien n'est ouvert ni modifié sur Vinted.
2. **Bouton** : « Noter vendue », sur la fiche de l'annonce (US-18b) uniquement, quelle que soit l'étape ; absent si l'annonce a déjà une vente (notée ou constatée).
3. **Confirmation** : le clic ouvre « Noter cette annonce vendue ? » ; rien n'est enregistré sans confirmation.
4. **Vente notée** : l'annonce gagne une vente (`ListingSale`, US-36) d'origine « notée », datée du clic.
5. **Étape** : une vente notée est la première règle de l'étape (`ListingStage.of`) : l'annonce est « Vendue » quelle que soit son étape (republiée, à vérifier, en attente, à republier…). Une vente constatée (US-36) reste à sa place dans l'ordre de US-50, inchangé. Une annonce « Vendue » n'a rien à relancer (US-50) : la série ne la prend plus.
6. **Origine** : chaque vente porte son origine, « notée » (à la main) ou « constatée » (dans le dressing) ; les ventes existantes sont constatées.
7. **Annulation** : sur la fiche d'une annonce dont la vente est notée, un bouton « Annuler la vente » (avec confirmation « Annuler la vente notée ? ») supprime la vente. L'étape redevient celle déduite des autres règles. Une vente constatée ne s'annule pas (US-36 : jamais supprimée) : pas de bouton.
8. **Page « Ventes »** (US-36) : une vente notée y figure comme les autres, avec « notée le » au lieu de « constatée le ».
9. **Fiche** : affiche la vente (origine et date) quand l'annonce en a une.

## Contrat d'API
- `PUT /api/listings/{listingId}/sale` (`operationId: noteListingSold`, tag `sales`) : note la vente. `204` ; `404` annonce inconnue ; `409` l'annonce a déjà une vente (`SaleRefusal`, `reason: ALREADY_SOLD`) ; `403` demande venue d'un autre site.
- `DELETE /api/listings/{listingId}/sale` (`operationId: cancelNotedSale`, tag `sales`) : annule la vente notée. `204` ; `404` annonce inconnue ou sans vente ; `409` vente constatée (`SaleRefusal`, `reason: NOT_NOTED`) ; `403` demande venue d'un autre site.
- `SaleOrigin` (énumération) : `NOTED`, `NOTICED`.
- `Sale` gagne `origin` (`SaleOrigin`, obligatoire) ; `noticedAt` devient la date de constat ou de note (description mise à jour, nom inchangé).
- `ListingDetail` gagne `sale` (facultatif, schéma `ListingSale`) : `origin` (`SaleOrigin`), `noticedAt` (date-heure), obligatoires.
- `SaleRefusal` : `reason` (`ALREADY_SOLD`, `NOT_NOTED`), obligatoire.

## Critères d'acceptation
- [x] Noter une annonce vendue enregistre une vente notée, datée du clic, quelle que soit l'étape de l'annonce.
- [x] Une annonce avec une vente notée est « Vendue », quelle que soit son étape ; une vente constatée garde l'ordre de US-50.
- [x] Noter une annonce qui a déjà une vente est refusé ; rien ne change.
- [x] Annuler une vente notée la supprime et rend à l'annonce l'étape déduite des autres règles ; une vente constatée ne s'annule pas.
- [x] Un relevé du dressing qui voit « Vendu » une annonce déjà notée vendue ne change pas sa vente.
- [x] La fiche propose « Noter vendue » ou « Annuler la vente » selon la vente, avec confirmation ; la page « Ventes » distingue « notée le » de « constatée le ».

## Tests d'acceptation

### AT-51-01 – Noter vendue une annonce à republier
```gherkin
Étant donné l'annonce 1 « À republier » (supprimée par l'outil, délai écoulé)
Quand le membre la note vendue le 20 novembre
Alors l'annonce 1 a une vente notée le 20 novembre
Et elle est « Vendue »
Et la série ne la prend pas
```
Test : `NoteSaleAcceptanceTest.a_listing_noted_sold_is_sold_whatever_its_stage()`

### AT-51-02 – Noter vendue une annonce à supprimer
```gherkin
Étant donné l'annonce 1 « À supprimer »
Quand le membre la note vendue
Alors elle est « Vendue » et ne sera pas supprimée
```
Test : `NoteSaleAcceptanceTest.a_listing_to_delete_noted_sold_is_no_longer_to_delete()`

### AT-51-03 – Déjà vendue
```gherkin
Étant donné l'annonce 1 avec une vente constatée le 17 novembre
Quand le membre la note vendue
Alors c'est refusé : l'annonce a déjà une vente
Et sa vente reste constatée le 17 novembre
```
Test : `NoteSaleAcceptanceTest.a_listing_already_sold_cannot_be_noted_sold()`

### AT-51-04 – Annuler une vente notée
```gherkin
Étant donné l'annonce 1 « À republier », notée vendue
Quand le membre annule la vente
Alors l'annonce 1 n'a plus de vente
Et elle est de nouveau « À republier »
```
Test : `NoteSaleAcceptanceTest.cancelling_a_noted_sale_gives_back_the_previous_stage()`

### AT-51-05 – Une vente constatée ne s'annule pas
```gherkin
Étant donné l'annonce 1 avec une vente constatée
Quand le membre demande d'annuler la vente
Alors c'est refusé : la vente n'a pas été notée à la main
```
Test : `NoteSaleAcceptanceTest.a_noticed_sale_cannot_be_cancelled()`

### AT-51-06 – Relevé d'une annonce déjà notée vendue
```gherkin
Étant donné l'annonce 1 notée vendue le 20 novembre
Quand un scan la montre « Vendu » le 22 novembre
Alors sa vente reste notée le 20 novembre
```
Test : `TrackSalesAcceptanceTest.a_scan_keeps_a_noted_sale()`

### AT-51-07 – Une vente constatée garde l'ordre de US-50
```gherkin
Étant donné l'annonce 1 republiée par l'outil, avec une vente constatée
Alors elle reste « Republiée »
```
Test : `ListingStageTest` (existant, inchangé)

### AT-51-08 – API
```gherkin
Quand le membre note vendue une annonce inconnue, ou annule la vente d'une annonce sans vente
Alors l'API répond 404
Et 409 ALREADY_SOLD ou NOT_NOTED dans les cas refusés
```
Test : `SalesApiControllerTest` (`notes_a_listing_sold_now`, `noting_a_listing_already_sold_is_a_conflict`, `cancels_a_noted_sale`, `cancelling_a_noticed_sale_is_a_conflict`, `unknown_listing_or_missing_sale_is_not_found`)

### AT-51-09 – Fiche et page « Ventes »
```gherkin
Étant donné une annonce sans vente
Quand le membre ouvre sa fiche, clique « Noter vendue » et confirme
Alors la fiche montre « Vendue », la vente notée et le bouton « Annuler la vente »
Et la page « Ventes » la montre « notée le »
Et sans confirmation, rien n'est envoyé
```
Test : `ListingDetailPageTest.notes_the_listing_sold_after_confirmation()`, `ListingDetailPageTest.cancels_a_noted_sale_after_confirmation()`, `ListingDetailPageTest.a_noticed_sale_offers_neither_note_nor_cancellation()`, `ListingDetailPageTest.a_refused_sale_note_says_why()`, `ListingDetailApiControllerTest.detail_shows_a_noted_sale_and_the_sold_stage()`, `SalesPageTest.tells_noted_sales_from_noticed_ones()`

### AT-51-10 – Vérification manuelle
```gherkin
Étant donné l'outil branché sur le vrai compte
Quand le membre note vendue une annonce vendue en main propre, puis annule, puis la note de nouveau
Alors la fiche, la liste et la page « Ventes » suivent, et rien n'est fait sur Vinted
```
Test : manuel (validation de bout en bout par le membre) — à faire

## Cas limites et erreurs
- Note puis carte « Vendu » au relevé suivant : la vente notée reste (une vente par annonce, US-36) ; elle reste annulable.
- Vente notée annulée, puis carte « Vendu » au relevé suivant : une vente constatée est enregistrée normalement.
- Annonce en cours de traitement par la série au moment de la note : l'action déjà commencée sur Vinted se termine ; la série ne prend plus l'annonce ensuite.
- Deux clics (double envoi), même simultanés : le second est refusé `ALREADY_SOLD`, sans effet. Une vente s'insère et ne remplace jamais une vente déjà enregistrée (insertion atomique en base, revue du 2026-11-20).
- Note et relevé du dressing simultanés sur la même annonce : la première vente enregistrée reste. Si c'est la note, l'écriture des ventes du relevé échoue (relevé en échec, comme tout échec d'écriture des ventes, US-36) et le relevé suivant ne la constate plus. Si c'est le relevé, la note est refusée `ALREADY_SOLD`.
- Annulation : seule une vente notée est supprimée, jamais une vente constatée apparue entre-temps.
- Base indisponible : erreur, rien n'est enregistré ; la fiche le signale.

## Hors périmètre
- Toute action sur Vinted (marquer l'annonce vendue ou la retirer sur le site).
- Bouton dans la liste des annonces.
- Saisie de la date réelle, du montant encaissé ou de l'acheteur.
- Annuler une vente constatée.
- Entrée dans le journal des actions : noter une vente n'est pas une action de l'outil sur Vinted (comme US-36).

## Décisions
- Action locale, sans Vinted : l'outil ne fait que lire le dressing pour les ventes (US-36).
- Bouton « Noter vendue » sur la fiche de l'annonce seulement, pas dans la liste : action rare, évite un clic par erreur (2026-11-20).
- Bouton proposé quelle que soit l'étape, sauf si l'annonce a déjà une vente (2026-11-20).
- Une vente notée à la main passe avant toutes les étapes : l'annonce est « Vendue » même republiée, à vérifier, en attente ou à republier (un article vendu en main propre ne doit pas être republié). Une vente constatée garde l'ordre de US-50, inchangé. La vente porte donc son origine (notée ou constatée), stockée en base (2026-11-20).
- Date de la vente notée = date du clic, sans saisie ; la page « Ventes » l'affiche « notée le » (au lieu de « constatée le ») (2026-11-20).
- Confirmation avant de noter ; une vente notée peut être annulée depuis la fiche (avec confirmation), une vente constatée jamais (2026-11-20).

Proposé par Claude, validé par l'utilisateur (2026-11-20) :
- Un relevé qui voit « Vendu » une annonce notée vendue garde la vente notée (pas de remplacement par une vente constatée).
- Pas d'entrée au journal des actions.
- Une action déjà commencée par la série sur l'annonce se termine.
- API : `PUT` / `DELETE /api/listings/{listingId}/sale`, `204`, refus `409` (`SaleRefusal`).

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `ListingSale` (US-36) gagne `origin` (`SaleOrigin`) ; `ListingSale.noted(listingId, at)` ; une vente notée peut être annulée, une vente constatée non.
- `SaleOrigin` | Origine d'une vente | `NOTED` (notée à la main par le membre), `NOTICED` (constatée dans le dressing). À ajouter au glossaire.
- `ListingStage.of` : première règle « vente notée → `SOLD` » ; la règle « vente constatée → `SOLD` » de US-50 ne bouge pas.
- `ListingSaleRepository` gagne `find(VintedListingId)` et `remove(VintedListingId)` ; l'ajout passe par `addAll`.
- Refus : `ListingAlreadySold`, `ListingNotSold`, `SaleNotNoted` (`domain/sale`), `ListingNotFound` (existant).
- `NoteSale`, `CancelNotedSale` (`application/sale`).
- Liquibase : colonne `origin` (texte, non nulle, `NOTICED` pour les ventes existantes) sur la table des ventes.

## Tests unitaires et d'intégration attendus
- `domain` : `ListingSaleTest` (vente notée, annulable ou non selon l'origine), `ListingStageTest` (vente notée avant toutes les règles, vente constatée à sa place), `SalesOverviewTest` (origine portée).
- `application` : `NoteSaleTest` (refus déjà vendue, annonce inconnue), `CancelNotedSaleTest` (refus vente constatée, sans vente).
- `adapter` : `SalesApiControllerTest`, `ListingDetailApiControllerTest`, `JpaListingSaleRepositoryTest`, test JPA du dépôt des ventes avec l'origine (`@Tag("docker")`), `ListingDetailPageTest`, `SalesPageTest`.
