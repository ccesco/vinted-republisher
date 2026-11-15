# US-14 – États d'une annonce en cours de traitement

**Statut** : validée
**Priorité** : MVP
**Dépend de** : US-08 (validation), US-09 (suppression), US-11 (délai), US-12 (republication, en cours), US-18 et US-18b (liste et fiche, PR #12) ; prépare US-16, US-17, US-19, US-20, US-21, US-23

## Contexte
Chaque annonce passe par sauvegarde, validation, suppression, attente, republication. Ces faits sont déjà enregistrés, chacun dans sa table, et ne se lisent aujourd'hui qu'annonce par annonce dans la fiche. Pour lancer tout le dressing (US-19), relancer une annonce (US-20) ou reprendre après un plantage (US-16), il faut savoir d'un coup d'œil où en est chaque annonce et ce qui reste à faire.

## Objectif
En tant que membre, je veux voir l'étape de traitement de chacune de mes annonces dans la liste et dans sa fiche, afin de savoir ce qui reste à faire et ce qui demande ma vérification sur Vinted.

## Comportement attendu
1. L'étape d'une annonce est **déduite** à chaque lecture des faits déjà en base : présence (US-04), sauvegarde actuelle et sa validation (US-05 à US-08), dernière suppression (US-09), date de republication (US-11), dernière republication (US-12). Rien de nouveau n'est stocké ; un redémarrage donne la même étape.
2. Règles, dans cet ordre (la première qui s'applique l'emporte) :

| # | Condition | Étape | Libellé |
|---|---|---|---|
| 1 | dernière republication réussie (`PUBLISHED`) | `REPUBLISHED` | Republiée |
| 2 | dernière republication en erreur (`FAILED`) | `REPUBLICATION_TO_CHECK` | Republication à vérifier |
| 3 | dernière suppression non confirmée (`UNCONFIRMED`) | `DELETION_TO_CHECK` | Suppression à vérifier |
| 4 | dernière suppression confirmée, date de republication pas atteinte | `WAITING` | En attente avant republication |
| 5 | dernière suppression confirmée, date de republication atteinte | `TO_REPUBLISH` | À republier |
| 6 | annonce absente (sans suppression confirmée) | `GONE` | Absente de Vinted |
| 7 | sauvegarde actuelle validée | `TO_DELETE` | Prête à supprimer |
| 8 | sauvegarde actuelle non validée | `TO_VALIDATE` | À valider |
| 9 | aucune sauvegarde | `TO_BACK_UP` | À sauvegarder |

   Une suppression `NOT_DONE` (doute levé, annonce toujours là) ne compte pas : l'annonce retombe dans les règles 6 à 9.
3. **Liste** (`wardrobe.html`) : nouvelle colonne « Étape » avec le libellé. Les étapes qui demandent une vérification sur Vinted (`DELETION_TO_CHECK`, `REPUBLICATION_TO_CHECK`) sont mises en évidence.
4. **Fiche** (`listing.html`) : l'étape est affichée sous le titre.
5. L'étape est calculée à l'heure du serveur au moment de la lecture : `WAITING` devient `TO_REPUBLISH` à la lecture suivante, sans compte à rebours dans la liste (celui de la fiche, US-11, reste).
6. Rien n'est demandé à Vinted, aucune action n'est lancée.

## Contrat d'API
Contract first : `src/main/resources/static/openapi/vinted-republisher.yaml`.
- Nouveau schéma `ListingStage` : énumération `TO_BACK_UP`, `TO_VALIDATE`, `TO_DELETE`, `DELETION_TO_CHECK`, `WAITING`, `TO_REPUBLISH`, `REPUBLICATION_TO_CHECK`, `REPUBLISHED`, `GONE`.
- Le schéma `Listing` gagne `stage` (`ListingStage`, obligatoire). Il est donc présent dans `GET /api/listings` (US-18) et dans `listing` de `GET /api/listings/{listingId}` (US-18b).
- Aucun nouveau chemin, aucun nouveau paramètre.

## Critères d'acceptation
- [ ] Chaque étape du tableau est déduite de sa condition, dans l'ordre de priorité.
- [ ] Une suppression `NOT_DONE` est ignorée.
- [ ] `WAITING` et `TO_REPUBLISH` dépendent de l'heure de lecture et du délai minimal configuré (US-11).
- [ ] Une validation qui ne porte pas sur la sauvegarde actuelle ne compte pas (`TO_VALIDATE`).
- [ ] L'API renvoie `stage` pour chaque annonce de la liste et dans la fiche.
- [ ] La liste affiche la colonne « Étape », avec les étapes « à vérifier » mises en évidence ; la fiche affiche l'étape.
- [ ] Rien n'est stocké en plus : aucun changement de schéma.

## Tests d'acceptation

### AT-14-01 – Annonce sans sauvegarde
```gherkin
Étant donné une annonce présente sans sauvegarde
Quand le membre affiche la liste
Alors son étape est « À sauvegarder »
```
Test : `ListingStageAcceptanceTest.listing_without_backup_is_to_back_up()`

### AT-14-02 – Sauvegarde non validée
```gherkin
Étant donné une annonce présente sauvegardée, sauvegarde non validée
Quand le membre affiche la liste
Alors son étape est « À valider »
```
Test : `ListingStageAcceptanceTest.unvalidated_backup_is_to_validate()`

### AT-14-03 – Validation d'une ancienne sauvegarde
```gherkin
Étant donné une annonce dont la sauvegarde validée a été remplacée par une nouvelle sauvegarde
Quand le membre affiche la liste
Alors son étape est « À valider »
```
Test : `ListingStageAcceptanceTest.validation_of_a_replaced_backup_does_not_count()`

### AT-14-04 – Prête à supprimer
```gherkin
Étant donné une annonce présente dont la sauvegarde actuelle est validée, sans suppression
Quand le membre affiche la liste
Alors son étape est « Prête à supprimer »
```
Test : `ListingStageAcceptanceTest.validated_listing_is_to_delete()`

### AT-14-05 – Suppression non confirmée
```gherkin
Étant donné une annonce dont la dernière suppression est non confirmée
Quand le membre affiche la liste
Alors son étape est « Suppression à vérifier »
```
Test : `ListingStageAcceptanceTest.unconfirmed_deletion_is_to_check()`

### AT-14-06 – Doute levé, suppression non faite
```gherkin
Étant donné une annonce présente, validée, dont la dernière suppression est « non faite »
Quand le membre affiche la liste
Alors son étape est « Prête à supprimer »
```
Test : `ListingStageAcceptanceTest.deletion_not_done_is_ignored()`

### AT-14-07 – Attente avant republication
```gherkin
Étant donné une annonce dont la suppression a été prouvée il y a 2 minutes, délai minimal de 5 minutes
Quand le membre affiche la liste
Alors son étape est « En attente avant republication »
```
Test : `ListingStageAcceptanceTest.confirmed_deletion_within_delay_is_waiting()`

### AT-14-08 – À republier
```gherkin
Étant donné une annonce dont la suppression a été prouvée il y a 5 minutes ou plus
Quand le membre affiche la liste
Alors son étape est « À republier »
```
Test : `ListingStageAcceptanceTest.confirmed_deletion_after_delay_is_to_republish()`

### AT-14-09 – Republication en erreur
```gherkin
Étant donné une annonce dont la dernière republication est en erreur
Quand le membre affiche la liste
Alors son étape est « Republication à vérifier »
```
Test : `ListingStageAcceptanceTest.failed_republication_is_to_check()`

### AT-14-10 – Republiée
```gherkin
Étant donné une annonce dont la republication a réussi
Quand le membre affiche la liste
Alors l'ancienne annonce est « Republiée »
Et la nouvelle annonce, sans sauvegarde, est « À sauvegarder »
```
Test : `ListingStageAcceptanceTest.republished_listing_and_its_new_listing()`

### AT-14-11 – Absente hors outil
```gherkin
Étant donné une annonce absente du dressing au dernier scan, sans suppression confirmée (vendue ou retirée par le membre)
Quand le membre affiche la liste avec toutes les annonces
Alors son étape est « Absente de Vinted »
```
Test : `ListingStageAcceptanceTest.absent_listing_without_deletion_is_gone()`

### AT-14-12 – API
```gherkin
Étant donné des annonces à des étapes différentes
Quand l'IHM demande la liste puis la fiche d'une annonce
Alors chaque annonce porte son étape (`stage`)
```
Test : `ListingInventoryApiControllerTest.listings_carry_their_stage()`, `ListingDetailApiControllerTest.detail_carries_the_stage()`

### AT-14-13 – Colonne de la liste
```gherkin
Étant donné une annonce « Suppression à vérifier » et une annonce « À sauvegarder »
Quand le membre ouvre la page du dressing
Alors la colonne « Étape » montre leurs libellés
Et seule « Suppression à vérifier » est mise en évidence
```
Test : `WardrobePageTest.stage_column_highlights_listings_to_check()`

### AT-14-14 – Étape dans la fiche
```gherkin
Étant donné une annonce « À republier »
Quand le membre ouvre sa fiche
Alors l'étape « À republier » est affichée sous le titre
```
Test : `ListingDetailPageTest.shows_the_stage()`

## Cas limites et erreurs
- Base indisponible : la liste et la fiche échouent comme aujourd'hui (US-18, US-18b) ; aucune étape partielle.
- Plusieurs suppressions ou republications pour une annonce : seule la dernière (par date de tentative) compte.
- Republication réussie mais annonce encore marquée présente (scan pas encore refait) : la règle 1 l'emporte, « Republiée ».
- Annonce absente avec suppression `NOT_DONE` : « Absente de Vinted ».

## Hors périmètre
- Action en cours sur une annonce (sauvegarde, suppression, republication qui tourne) : l'action navigateur est déjà affichée (US-03) ; l'état « en cours » par annonce viendra avec le traitement en parallèle (US-15).
- Filtre ou tri par étape ; compteurs par étape (plus tard si besoin, US-19).
- Enchaînement automatique, reprise, gestion des échecs, relance (US-16, US-17, US-19, US-20).
- Vérification de la republication (US-13) : `REPUBLICATION_TO_CHECK` reste tant qu'elle n'existe pas.
- Journal des changements d'étape (US-23).

## Décisions
- Prises par Claude sur délégation de l'utilisateur (« fait comme tu le penses », 2026-11-15), validées par l'utilisateur avec la spec (2026-11-15) :
  - Étape déduite des faits en base, jamais stockée : une seule source de vérité, rien à resynchroniser, survit aux redémarrages (base de la reprise d'US-16). Les transitions sont déjà gardées par les refus d'US-08, US-09 et US-12 ; US-14 n'en ajoute pas.
  - Neuf étapes et leur ordre de priorité (tableau ci-dessus) ; `GONE` pour une annonce disparue hors outil, sans traitement possible.
  - Nouvelle annonce après republication : annonce ordinaire, donc « À sauvegarder » (décision d'US-12 : nouvelle sauvegarde avant toute future suppression).
  - Affichage : colonne « Étape » dans la liste et ligne dans la fiche ; mise en évidence des deux étapes « à vérifier » ; pas de filtre ni de compteur.
  - Code : `stage` dans le schéma `Listing`, nouveau schéma `ListingStage`.

## Questions ouvertes
- Aucune bloquant la spec. L'implémentation attend la fin d'US-12 (republications en base) et la fusion de la PR #12 (liste et fiche).

## Modèle de domaine
- Nouveau terme : `ListingStage` | Étape de traitement | Où en est une annonce dans le cycle sauvegarde, validation, suppression, attente, republication ; déduite des faits en base à chaque lecture, jamais stockée.
- Règle dans le domaine : `ListingStage.of(…)` à partir de la présence, de la sauvegarde actuelle et de sa validation, de la dernière suppression, de la dernière republication, du délai minimal (`RepublicationDelay`) et de l'instant de lecture. Paquet `domain.inventory` (dépend de `deletion`, `republication`, `backup` ; aucun ne dépend de lui).
- `InventoryEntry` gagne l'étape ; `ShowListingInventory` et `ShowListingDetail` lisent en plus les suppressions et republications (ports existants, lecture de la dernière par annonce) et l'horloge.
- Aucun nouvel agrégat, aucun changement de schéma (Liquibase).

## Tests unitaires et d'intégration attendus
- Domaine : `ListingStageTest`, une règle par test, plus l'ordre de priorité (republication sur suppression, suppression sur absence) et la limite exacte du délai.
- Application : `ListingStageAcceptanceTest` (AT-14-01 à 11) avec les faux dépôts et une horloge fixe.
- Adaptateurs : contrôleurs (AT-14-12), lecture en lot des dernières suppressions et republications sur PostgreSQL (`@Tag("docker")`), pages (AT-14-13, AT-14-14, `@Tag("browser")`).
