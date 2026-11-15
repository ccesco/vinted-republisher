# US-18 – Liste complète des annonces (filtres, tri, recherche, vues et likes)
**Statut** : brouillon
**Priorité** : MVP
**Dépend de** : US-04 (inventaire), US-05/US-06 (sauvegarde), US-07 (vues et favoris), US-08 (validation)

## Contexte
L'inventaire est aujourd'hui affiché par `wardrobe.html` (`GET /api/wardrobe`) : une liste à puces des seules annonces présentes, les plus récentes d'abord, sans filtre, tri ni recherche. Avec ~150 annonces et bientôt leur suppression puis republication, le membre doit retrouver vite une annonce et voir où en est sa sauvegarde.

Contraintes déjà posées par d'autres specs :
- Les annonces absentes restent en base (US-04) ; leurs relevés de statistiques aussi (US-07).
- Filtres, tri et recherche, y compris sur vues et favoris, sont renvoyés à cette US (US-04, US-07) ; l'historique des relevés va à US-18b.
- Les états d'une annonce en cours de traitement relèvent d'US-14 (Épic 5), pas encore spécifiée.
- Les titres et liens viennent de Vinted : jamais d'`innerHTML`.

## Objectif
En tant que membre, je veux voir toutes mes annonces dans un tableau que je peux filtrer, trier et fouiller, avec leurs vues et favoris, afin de retrouver vite une annonce et de choisir celles à traiter.

## Comportement attendu
1. **Page** : dans `wardrobe.html`, la liste à puces est remplacée par un tableau ; boutons « Scanner » / « Sauvegarder » et état du scan et de la sauvegarde restent au-dessus, inchangés.
2. **Commandes** au-dessus du tableau : présence (présentes, absentes, toutes ; défaut présentes), sauvegarde (toutes, non sauvegardées, sauvegardées non validées, validées ; défaut toutes), champ de recherche, tri (critère et sens). Chaque changement redemande `GET /api/listings` avec ces paramètres ; la saisie dans la recherche attend 300 ms sans frappe avant de demander.
3. **Tableau**, une ligne par annonce, colonnes : titre (lien vers l'annonce sur Vinted), prix, vues, favoris (vides si jamais relevés ; date du relevé en infobulle), présence (« absente » si elle n'est plus dans le dressing, vide sinon), sauvegarde (date et nombre de photos, lien « Voir la sauvegarde » ; « non sauvegardée » sinon), validation (date, vide sinon). Au-dessus : « N annonces » affichées.
4. **Tri** : critères ordre de mise en ligne (identifiant Vinted, le plus haut = la plus récente), titre, prix, vues, favoris, date de sauvegarde ; sens croissant ou décroissant. Défaut : mise en ligne, décroissant (ordre actuel). Une annonce sans la valeur triée (jamais relevée, jamais sauvegardée) va toujours en fin de liste, quel que soit le sens. À égalité, la plus récente d'abord.
5. **Recherche** : sur le titre seul, « contient », sans tenir compte de la casse ni des accents ; espaces autour ignorés ; vide = pas de recherche.
6. **Pas de pagination** : toutes les annonces correspondantes sont renvoyées.
7. **Rafraîchissement** : quand un scan ou une sauvegarde se termine, la page redemande le tableau avec les mêmes critères.
8. Rien n'est lu sur Vinted : seules les données en base sont montrées.

## Contrat d'API
| Requête | Opération | Réponse |
| --- | --- | --- |
| `GET /api/listings` | `showListingInventory` | 200, `ListingInventory` : `listings` (tableau de `Listing`). Paramètres de requête, tous facultatifs : `presence` (`PRESENT` défaut, `ABSENT`, `ALL`), `backup` (`ALL` défaut, `NOT_BACKED_UP`, `NOT_VALIDATED`, `VALIDATED`), `search` (chaîne, 100 caractères au plus), `sort` (`RECENCY` défaut, `TITLE`, `PRICE`, `VIEWS`, `FAVOURITES`, `SAVED_AT`), `direction` (`DESC` défaut, `ASC`). 400 si une valeur est inconnue ou si `search` dépasse 100 caractères. |
| `GET /api/wardrobe`, `POST /api/wardrobe/scan`, `POST /api/wardrobe/backup` | inchangées | `WardrobeOverview` perd `listings`. |

`Listing` gagne `present` (booléen, obligatoire) ; sa description devient « Annonce connue de l'outil ». Ses autres champs sont inchangés (`statistics` = dernier relevé, `savedAt`, `photoCount`, `validatedAt`).

## Critères d'acceptation
- [ ] Sans paramètre, la liste contient les annonces présentes, les plus récentes d'abord.
- [ ] Le filtre de présence montre les présentes, les absentes ou toutes.
- [ ] Le filtre de sauvegarde distingue non sauvegardées, sauvegardées non validées et validées (validation de la sauvegarde actuelle seulement).
- [ ] La recherche trouve sur le titre sans tenir compte de la casse ni des accents.
- [ ] Le tri se fait sur chaque critère, dans les deux sens ; les annonces sans la valeur triée finissent la liste ; à égalité, la plus récente d'abord.
- [ ] Chaque annonce montre son dernier relevé de vues et favoris, sa sauvegarde, sa validation et sa présence.
- [ ] Une valeur de paramètre inconnue ou une recherche trop longue est refusée (400).
- [ ] La page affiche le tableau, ses commandes, et le redemande à chaque changement de critère.
- [ ] Titres et liens venus de Vinted sont affichés comme texte, jamais interprétés comme HTML.

## Tests d'acceptation
### AT-18-01 – Par défaut, les annonces présentes, les plus récentes d'abord
```gherkin
Étant donné les annonces 101 et 103 présentes et l'annonce 102 absente
Quand le membre affiche la liste sans critère
Alors il voit 103 puis 101
```
Test : `ShowListingInventoryAcceptanceTest.present_listings_most_recent_first_by_default()`

### AT-18-02 – Filtre de présence
```gherkin
Étant donné les annonces 101 et 103 présentes et l'annonce 102 absente
Quand le membre demande les absentes
Alors il voit seulement 102, marquée absente
Quand il demande toutes les annonces
Alors il voit 103, 102 puis 101
```
Test : `ShowListingInventoryAcceptanceTest.filters_on_presence()`

### AT-18-03 – Filtre de sauvegarde
```gherkin
Étant donné l'annonce 101 jamais sauvegardée, 102 sauvegardée non validée, 103 sauvegardée et validée
  et 104 validée puis sauvegardée à nouveau
Quand le membre demande les non sauvegardées
Alors il voit 101
Quand il demande les sauvegardées non validées
Alors il voit 104 puis 102
Quand il demande les validées
Alors il voit 103
```
Test : `ShowListingInventoryAcceptanceTest.filters_on_backup_state()`

### AT-18-04 – Recherche sur le titre, sans casse ni accents
```gherkin
Étant donné les annonces « Pull rayé », « ROBE d'été » et « Jean bleu »
Quand le membre cherche "  rAye "
Alors il voit seulement « Pull rayé »
Quand il cherche "ETE"
Alors il voit seulement « ROBE d'été »
```
Test : `ShowListingInventoryAcceptanceTest.searches_title_ignoring_case_and_accents()`

### AT-18-05 – Tri par vues et favoris, sans relevé en fin de liste
```gherkin
Étant donné l'annonce 101 relevée à 5 vues et 2 favoris, 102 à 40 vues et 0 favori, 103 jamais relevée
Quand le membre trie par vues décroissantes
Alors il voit 102, 101 puis 103
Quand il trie par vues croissantes
Alors il voit 101, 102 puis 103
Quand il trie par favoris décroissants
Alors il voit 101, 102 puis 103
```
Test : `ShowListingInventoryAcceptanceTest.sorts_on_statistics_with_unread_listings_last()`

### AT-18-06 – Tri par titre, prix et date de sauvegarde
```gherkin
Étant donné les annonces 101 « b », 5 €, sauvegardée le 2 ; 102 « A », 12 €, jamais sauvegardée ; 103 « c », 5 €, sauvegardée le 1
Quand le membre trie par titre croissant
Alors il voit 102, 101 puis 103
Quand il trie par prix croissant
Alors il voit 103, 101 puis 102
Quand il trie par date de sauvegarde décroissante
Alors il voit 101, 103 puis 102
```
Test : `ShowListingInventoryAcceptanceTest.sorts_on_title_price_and_backup_date()`

### AT-18-07 – Contenu d'une ligne
```gherkin
Étant donné l'annonce 101 présente, relevée à 12 vues et 3 favoris, sauvegardée avec 3 photos et validée
Quand le membre affiche la liste
Alors la ligne montre son titre, son prix, 12 vues, 3 favoris, la date du relevé, la date de sauvegarde, 3 photos et la date de validation
```
Test : `ShowListingInventoryAcceptanceTest.row_shows_statistics_backup_and_validation()`

### AT-18-08 – Paramètres refusés
```gherkin
Quand la page demande GET /api/listings?sort=COLOUR
Alors la réponse est 400
Quand elle demande une recherche de 101 caractères
Alors la réponse est 400
```
Test : `ListingInventoryApiControllerTest.rejects_unknown_values_and_too_long_search()`

### AT-18-09 – Page : tableau et commandes
```gherkin
Étant donné une API qui renvoie deux annonces, dont une au titre "<b>gras</b>"
Quand le membre ouvre wardrobe.html
Alors il voit un tableau de deux lignes avec titre, prix, vues, favoris, présence, sauvegarde et validation
  et le titre "<b>gras</b>" affiché tel quel
Quand il choisit « toutes », « validées », tape "pull" et trie par vues croissantes
Alors la page demande GET /api/listings?presence=ALL&backup=VALIDATED&search=pull&sort=VIEWS&direction=ASC
```
Test : `WardrobePageTest.shows_listing_table_and_requests_chosen_criteria()` (`@Tag("browser")`)

## Cas limites et erreurs
- Aucune annonce ne correspond : tableau vide, « 0 annonce ».
- Inventaire vide (aucun scan) : idem.
- Base de données indisponible : erreur 500 de Spring, la page affiche « Liste indisponible ».
- Prix en devises différentes : tri sur le montant seul (le dressing est mono-devise).
- Recherche contenant `%` ou `_` : cherchés comme texte (filtre en Java, pas de `LIKE`).

## Hors périmètre
- Fiche détail d'une annonce et historique des relevés (US-18b).
- Actions de traitement depuis la liste : tout lancer, relancer, pause (US-19, US-20, US-21).
- États de traitement (suppression, republication, échec) en colonne ou en filtre : attendent US-14.
- Cumul des vues et favoris sur les republications (lien d'US-12) et comparaison avant / après (US-24).
- Recherche dans la description ou les attributs de la sauvegarde.
- Pagination, critères gardés dans l'adresse de la page.

## Décisions
- Annonces affichées, décidé par l'utilisateur (2026-11-15) : toutes les annonces connues, présentes et absentes, avec un filtre de présence réglé par défaut sur « présentes ». Raison : une annonce supprimée puis republiée devient absente et doit rester retrouvable.
- Page, décidé par l'utilisateur (2026-11-15) : le tableau remplace la liste à puces de `wardrobe.html` ; boutons et état du scan et de la sauvegarde restent au-dessus. Le tableau lit sa propre ressource `GET /api/listings` ; `WardrobeOverview` perd `listings`. Raison : une seule page de tableau de bord, où viendront les boutons d'US-19 à US-21.
- Toutes les décisions suivantes : recommandations de Claude, acceptées par délégation de l'utilisateur (2026-11-15, « suis tes recommandations »).
- Filtres, tri et recherche côté serveur, en paramètres de `GET /api/listings`. Raison : règles métier dans le domaine, testées unitairement, décrites par le contrat.
- Filtrage et tri en mémoire, en Java, sur `findAll` : ~150 annonces, aucun index ni requête dédiée (YAGNI).
- Filtre de sauvegarde en trois états, dérivés de l'existant (sauvegarde actuelle, validation de sa version) ; aucun état d'US-14.
- Tri : six critères, sans valeur en fin de liste quel que soit le sens, égalité départagée par la plus récente. Défaut = ordre actuel.
- Recherche : titre seul, contient, insensible à la casse et aux accents, 100 caractères au plus.
- Vues et favoris : dernier relevé seul ; le cumul attend le lien d'US-12.
- Pas de pagination (~150 lignes).
- Recherche déclenchée 300 ms après la dernière frappe.

## Questions ouvertes
(aucune)

## Modèle de domaine
- `ListingInventory` (inventaire des annonces) : toutes les annonces connues, chacune avec son dernier relevé de statistiques, sa sauvegarde actuelle et sa validation ; sait se filtrer et se trier selon des `InventoryCriteria`.
- `InventoryCriteria` (critères d'affichage, value object) : `PresenceFilter` (`PRESENT` / `ABSENT` / `ALL`), `BackupFilter` (`ALL` / `NOT_BACKED_UP` / `NOT_VALIDATED` / `VALIDATED`), recherche (≤ 100 caractères, normalisée), `InventorySort` (`RECENCY` / `TITLE` / `PRICE` / `VIEWS` / `FAVOURITES` / `SAVED_AT`) et `SortDirection` (`ASC` / `DESC`).
- `ShowListingInventory` (cas d'usage) : lit annonces, derniers relevés, sauvegardes et validations, applique les critères.
- Ports réutilisés : `ListingRepository`, `ListingStatisticsRepository`, `ListingBackupRepository`, `BackupValidationRepository` ; aucun nouveau port, aucune modification de schéma.

## Tests unitaires et d'intégration attendus
- Domaine : `InventoryCriteriaTest` (défauts, recherche trop longue refusée, normalisation casse/accents/espaces) ; `ListingInventoryTest` (chaque filtre, chaque tri dans les deux sens, valeurs absentes en fin, égalités).
- Application : `ShowListingInventoryAcceptanceTest` avec les fakes (AT-18-01 à 07).
- Web : `ListingInventoryApiControllerTest` (paramètres transmis, défauts, 400, mapping de `present`) ; `WardrobeApiControllerTest` mis à jour (plus de `listings`).
- Page : `WardrobePageTest` (`@Tag("browser")`, page locale, API simulée).
