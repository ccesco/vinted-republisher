# US-44 – Compteur de republications dans la liste
**Statut** : implémentée
**Priorité** : V2
**Dépend de** : US-12 et US-13 (republication, vérification), US-18 (liste des annonces), US-24 et US-32 (chaîne de republications), US-38 (colonne « Republication »)

## Contexte
La colonne « Republication » de la liste (US-38) ne montre que la republication qui fixe l'étape de l'annonce : republiée, ou à vérifier. Le membre ne voit pas combien de fois l'article a déjà été remis en vente, ni combien de tentatives ont été constatées non publiées sur Vinted. Ces faits sont en base (`listing_republication`, jamais supprimées), mais une annonce n'est republiée qu'une fois au plus : l'historique d'un article est réparti sur toutes les annonces de sa chaîne de republications.

## Objectif
En tant que membre, je veux voir dans la liste des annonces le nombre de republications réussies et en échec déjà faites pour chaque article, afin de repérer d'un coup d'œil les articles souvent remis en vente et ceux dont les republications échouent.

## Comportement attendu
1. **Compteur de republications** (`RepublicationTally`) d'une ligne : calculé sur **tout l'article**, c'est-à-dire toutes les annonces de sa chaîne de republications, dans les deux sens (comme le total cumulé d'US-24 et la chronologie d'US-32). Toutes les annonces d'un même article portent donc le même compteur. Le parcours s'arrête sur une annonce déjà vue.
2. **Réussies** : nombre de republications publiées (`PUBLISHED`) de la chaîne, qu'elles aient été retrouvées par l'outil ou validées par le membre avec le lien de la nouvelle annonce.
3. **En échec** : nombre de tentatives constatées non publiées (`NOT_PUBLISHED`) sur les annonces de la chaîne. Une tentative en erreur encore à vérifier (`FAILED`) n'est pas comptée : la colonne « Republication » la montre déjà (« tentée le …, à vérifier »). Les refus et échecs survenus avant le clic sur « Publier » (journal, US-23) ne sont pas des republications et ne sont pas comptés.
4. **Colonne « Republications »** de la liste, à côté de la colonne « Republication » existante (inchangée) : « <n> réussie(s), <m> échec(s) », au singulier ou au pluriel selon le nombre ; une partie à zéro est omise (« 2 réussies », « 1 échec ») ; vide quand l'article n'a aucune republication réussie ni en échec.
5. Rien n'est lu sur Vinted ; la liste reste lue en un nombre de requêtes fixe, quel que soit le nombre d'annonces (US-42) : les tentatives non publiées sont comptées par la base, par annonce, en une requête.

## Contrat d'API
Contract first : `src/main/resources/static/openapi/vinted-republisher.yaml`.

| Requête | Opération | Changement |
| --- | --- | --- |
| `GET /api/listings` | `showListingInventory` (existante) | `Listing` gagne `republicationTally` |

- `RepublicationTally` (nouveau schéma) : `published` (int32, ≥ 0, requis), `notPublished` (int32, ≥ 0, requis).
- `Listing.republicationTally` : toujours rendu par la liste (zéros compris), absent de la fiche et de la relance, comme `tags`.

## Critères d'acceptation
- [x] Une annonce sans aucune republication a un compteur à zéro réussie, zéro échec (AT-44-01).
- [x] Le compteur couvre toute la chaîne : chaque annonce de l'article porte le même nombre de réussies et d'échecs (AT-44-02).
- [x] Seules les tentatives non publiées comptent comme échecs ; une tentative en erreur à vérifier n'est pas comptée (AT-44-03).
- [x] L'API rend `republicationTally` dans chaque `Listing` de la liste (AT-44-04).
- [x] La colonne « Republications » affiche les nombres, singulier ou pluriel, vide sans republication (AT-44-05).
- [x] La liste reste lue en un nombre de requêtes indépendant du nombre d'annonces (AT-44-06).

## Tests d'acceptation
### AT-44-01 – Annonce jamais republiée
```gherkin
Étant donné une annonce prête à supprimer, sans aucune republication
Quand le membre demande la liste
Alors elle porte 0 republication réussie et 0 en échec
```
Test : `ShowListingInventoryAcceptanceTest.a_listing_never_republished_has_an_empty_tally()`

### AT-44-02 – Chaîne de l'article
```gherkin
Étant donné une annonce A dont une première tentative a été constatée non publiée
Et A republiée ensuite en B
Et B republiée en C
Et une tentative de C constatée non publiée
Quand le membre demande la liste, présence « Toutes »
Alors A, B et C portent chacune 2 republications réussies et 2 en échec
```
Test : `ShowListingInventoryAcceptanceTest.the_tally_covers_the_whole_republication_chain()`

### AT-44-03 – Tentative à vérifier non comptée
```gherkin
Étant donné une annonce dont une tentative a été constatée non publiée
Et dont la tentative suivante est en erreur, à vérifier
Quand le membre demande la liste
Alors elle porte 0 republication réussie et 1 en échec
Et sa colonne « Republication » reste « tentée le …, à vérifier »
```
Test : `ShowListingInventoryAcceptanceTest.a_republication_to_check_is_not_counted()`

### AT-44-04 – Contrat
```gherkin
Quand le membre demande la liste
Alors chaque Listing porte republicationTally, avec published et notPublished
```
Test : `ListingInventoryApiControllerTest`

### AT-44-05 – Page : colonne « Republications »
```gherkin
Étant donné la page Annonces
Et une annonce avec 2 réussies et 1 échec, une avec 1 réussie, une avec 3 échecs, une sans republication
Quand la liste s'affiche
Alors la colonne « Republications » affiche « 2 réussies, 1 échec », « 1 réussie », « 3 échecs » et rien
```
Test : `WardrobePageTest.shows_republication_tally()` ; texte : Vitest `republication-tally.spec.ts`

### AT-44-06 – Une seule lecture des tentatives
```gherkin
Étant donné 3 annonces, puis 30 annonces, avec des tentatives non publiées
Quand le membre demande la liste
Alors les tentatives non publiées sont lues une seule fois, dans les deux cas
```
Test : `ShowListingInventoryAcceptanceTest.reads_not_published_attempts_once_whatever_the_number_of_listings()` ; comptage par la base : `JpaListingRepublicationRepositoryTest` (`@Tag("docker")`)

## Cas limites et erreurs
- Chaîne qui bouclerait (impossible : identifiant Vinted jamais réutilisé) : parcours arrêté sur une annonce déjà vue.
- Annonce de la chaîne inconnue de l'outil : ses tentatives comptent quand même (elles sont en base, reliées par identifiant).
- Filtres de la liste (présence, étape, tags) : le compteur est calculé sur toute la chaîne, même si d'autres annonces de l'article sont filtrées (`ShowListingInventoryAcceptanceTest.a_listing_of_the_chain_left_out_by_the_filters_still_counts()`).

## Hors périmètre
- Compteur sur la fiche d'une annonce (la chronologie d'US-32 montre déjà les republications).
- Tri ou filtre sur le compteur.
- Comptage des refus et échecs du journal survenus avant le clic sur « Publier ».

## Décisions
- Périmètre, décidé par l'utilisateur (2026-11-19) : tout l'article (chaîne de republications dans les deux sens), pas l'annonce seule.
- Échecs, décidé par l'utilisateur (2026-11-19) : seulement les tentatives constatées non publiées (`NOT_PUBLISHED`) ; `FAILED` reste montré par la colonne « Republication ».
- Affichage, décidé par l'utilisateur (2026-11-19) : une nouvelle colonne « Republications », texte « 2 réussies, 1 échec », vide sans republication ; colonne « Republication » inchangée.
- Validé par l'utilisateur avec la spec (2026-11-19) : parties à zéro omises dans le texte ; `republicationTally` toujours rendu par la liste (zéros compris) ; libellé « échec » plutôt que « non publiée » dans la colonne.

## Questions ouvertes
Aucune.

## Modèle de domaine
- `RepublicationTally` (value object, `domain/inventory`) : `published`, `notPublished`, ≥ 0 ; « Compteur de republications » à ajouter au glossaire.
- `ListingRepublicationRepository` gagne `findNotPublishedCounts()` : nombre de tentatives `NOT_PUBLISHED` par annonce, compté par la base en une requête.
- Chaîne : reconstruite par `ListingInventory` depuis les republications publiées déjà lues (`findAllLatest`, la dernière tentative d'une annonce republiée est la publiée), sans requête de plus.
- `InventoryEntry` gagne `RepublicationTally republicationTally`.

## Tests unitaires et d'intégration attendus
- Domaine : `RepublicationTallyTest` (valeurs négatives refusées) ; `ListingInventoryTest` (sans republication, chaîne dans les deux sens, boucle, `FAILED` non compté, annonce de la chaîne filtrée).
- Persistance (`@Tag("docker")`) : `findNotPublishedCounts` compte par annonce, ignore `PUBLISHED` et `FAILED`.
- Adaptateur web : `republicationTally` dans `Listing`.
- Interface : Vitest du texte de la colonne (singulier, pluriel, zéros omis, vide) ; test de page Playwright.
