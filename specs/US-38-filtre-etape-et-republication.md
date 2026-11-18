# US-38 – Filtre par étape et suivi des republications dans la liste
**Statut** : implémentée
**Priorité** : MVP
**Dépend de** : US-18 (liste des annonces), US-14 (étapes d'une annonce), US-12/US-13 (republication et sa vérification)

## Contexte
Pendant une série « Tout lancer » (US-19), le membre veut voir dans la page Annonces celles qui ont été republiées et où en est leur republication. Aujourd'hui :
- la colonne « Étape » affiche « Republiée » ou « Republication à vérifier », mais une annonce republiée est l'ancienne annonce, supprimée par l'outil : elle est absente, donc cachée par le filtre de présence par défaut (« Présentes ») ;
- aucun filtre par étape ;
- la date de republication et le lien vers la nouvelle annonce ne se voient que sur la fiche (`ListingRepublication` : `attemptedAt`, `publishedAt`, `newListingUrl`).

## Objectif
En tant que membre, je veux filtrer la liste par étape et voir, pour chaque annonce republiée, quand elle l'a été et sa nouvelle annonce, afin de suivre l'avancement de la republication de mon dressing sans ouvrir chaque fiche.

## Comportement attendu
1. **Filtre « Étape »** au-dessus du tableau, à côté des filtres existants : « Toutes » (défaut) ou une des neuf étapes, avec les libellés de la colonne Étape. Il se combine avec les autres filtres (présence, sauvegarde, recherche, tag) et redemande `GET /api/listings` comme eux.
2. **Présence** : choisir une étape met le filtre de présence sur « Toutes » (le membre peut le changer ensuite) ; revenir à « Toutes » les étapes ne touche pas la présence.
3. **Colonne « Republication »**, après « Étape » :
   - étape « Republiée » : « le <date de publication> » puis un lien « nouvelle annonce » vers la nouvelle annonce sur Vinted, ouvert dans un nouvel onglet ;
   - étape « Republication à vérifier » : « tentée le <date du clic sur Publier>, à vérifier » ;
   - autres étapes : vide.
   La republication montrée est celle qui fixe l'étape (dernière tentative, sauf une vérifiée « non publiée »).
4. Rien n'est lu sur Vinted : seules les données en base sont montrées.

## Contrat d'API
| Requête | Opération | Réponse |
| --- | --- | --- |
| `GET /api/listings` | `showListingInventory` | Nouveau paramètre facultatif `stage` (`ListingStage`) ; absent = toutes les étapes. 400 si la valeur est inconnue. |

`Listing` gagne `republication` (facultatif, schéma `ListingRepublication` existant) : présent seulement à l'étape `REPUBLISHED` ou `REPUBLICATION_TO_CHECK`, avec `attemptedAt` toujours, `publishedAt`, `newListingId` et `newListingUrl` si publiée (`newListingUrl` absent si la nouvelle annonce n'est pas connue de l'outil, comme sur la fiche).

## Critères d'acceptation
- [x] Le filtre `stage` ne renvoie que les annonces de l'étape choisie ; sans `stage`, aucune étape n'est filtrée.
- [x] Le filtre d'étape se cumule avec les autres filtres.
- [x] Une annonce republiée renvoie sa republication : date de publication et adresse de la nouvelle annonce ; une annonce à vérifier, sa date de tentative ; les autres, rien.
- [x] Une valeur d'étape inconnue est refusée (400).
- [x] Dans la page, choisir une étape passe la présence sur « Toutes » et demande la liste filtrée.
- [x] La colonne Republication affiche date et lien pour une annonce republiée, « tentée le …, à vérifier » pour une annonce à vérifier, rien sinon ; le lien s'ouvre dans un nouvel onglet.

## Tests d'acceptation

### AT-38-01 – Filtrer sur une étape
```gherkin
Étant donné une annonce republiée (absente), une annonce à republier (absente) et une annonce prête à supprimer (présente)
Quand le membre demande la liste avec l'étape « Republiée » et la présence « Toutes »
Alors seule l'annonce republiée est renvoyée
Et sans étape, les trois sont renvoyées
```
Test : `ShowListingInventoryAcceptanceTest.filters_on_stage()`

### AT-38-02 – Filtres cumulés
```gherkin
Étant donné une annonce republiée, donc absente
Quand le membre demande l'étape « Republiée » avec la présence « Présentes »
Alors la liste est vide
```
Test : `ShowListingInventoryAcceptanceTest.combines_stage_with_presence()`

### AT-38-03 – Republication renvoyée avec l'annonce
```gherkin
Étant donné une annonce republiée le 18/11/2026, dont la nouvelle annonce est connue de l'outil
Et une annonce dont la republication est à vérifier, tentée le 18/11/2026
Et une annonce prête à supprimer
Quand le membre demande la liste
Alors la première porte sa date de publication et l'adresse de la nouvelle annonce
Et la deuxième porte sa date de tentative, sans date de publication
Et la troisième ne porte aucune republication
```
Test : `ShowListingInventoryAcceptanceTest.shows_republication_of_republished_and_to_check_listings()`

### AT-38-04 – Contrat
```gherkin
Quand le membre demande la liste avec stage=REPUBLISHED
Alors l'API passe l'étape au cas d'usage et renvoie republication dans Listing
Et avec stage=INCONNUE, elle répond 400
```
Test : `ListingInventoryApiControllerTest`

### AT-38-05 – Page : filtre et colonne
```gherkin
Étant donné la page Annonces, présence « Présentes »
Quand le membre choisit l'étape « Republiée »
Alors la présence passe sur « Toutes » et la liste est demandée avec stage=REPUBLISHED et presence=ALL
Et une annonce republiée affiche « le 18/11/2026 » et un lien « nouvelle annonce » ouvert dans un nouvel onglet
Et une annonce à vérifier affiche « tentée le 18/11/2026, à vérifier »
```
Test : `WardrobePageTest.filters_on_stage_and_shows_republication()`

## Cas limites et erreurs
- Étape inconnue dans `stage` → 400, comme les autres filtres.
- Nouvelle annonce pas (encore) connue de l'outil : date affichée, pas de lien.
- Titre ou adresse venant de Vinted : jamais d'`innerHTML`.

## Hors périmètre
- Toute action depuis la liste (relance, vérification) : US-20, US-13.
- Lien de la nouvelle annonce vers l'ancienne dans le tableau (la fiche le montre déjà, US-24/US-32).
- Tri sur la date de republication.

## Décisions
- Besoin principal, précisé par l'utilisateur (2026-11-18) : voir les annonces dont la republication est validée, c'est-à-dire à l'étape « Republiée » (`REPUBLISHED` : republication publiée, nouvelle annonce retrouvée dans le dressing), à distinguer de « Republication à vérifier ».
- Étape et présence, décidé par l'utilisateur (2026-11-18) : choisir une étape fait passer le filtre de présence sur « Toutes », sinon « Republiée » (annonce absente) donnerait une liste vide avec le défaut « Présentes ». Côté API, les filtres se cumulent simplement ; c'est l'interface qui change la présence.
- Une seule colonne « Republication », décidé par l'utilisateur (2026-11-18) : le tableau a déjà 12 colonnes ; date et lien pour « Republiée », « tentée le …, à vérifier » pour « Republication à vérifier », vide sinon.
- Proposé par Claude, à valider avec la spec : filtre sur une seule étape à la fois ; réutilisation du schéma `ListingRepublication` de la fiche plutôt qu'un schéma nouveau.
- Implémentation (2026-11-18) : `InventoryEntry` porte aussi la nouvelle annonce (`newListing`) quand l'outil la connaît, pour donner son adresse ; le contrôleur réutilise la conversion de la fiche (`ListingBackupApiController.toDto`). Revue indépendante : AT-38-03 vérifie l'adresse de la nouvelle annonce, tests unitaires du filtre d'étape et de la republication à vérifier ajoutés, description du champ `republication` passée par `allOf`, `rel` vérifié par le test de page.

## Questions ouvertes
Aucune.

## Modèle de domaine
- `ListingStage` (étape d'une annonce), `ListingRepublication` (republication) : existants.
- `InventoryCriteria` gagne `Optional<ListingStage> stage` (critère « étape »).
- `InventoryEntry` gagne `Optional<ListingRepublication> republication` : celle qui fixe l'étape, déjà lue par `ListingInventory`.

## Tests unitaires et d'intégration attendus
- Domaine : `ListingInventory` sélectionne sur l'étape ; l'entrée porte la republication qui fixe l'étape (aucune, publiée, à vérifier, vérifiée non publiée ignorée).
- Adaptateur web : conversion de `stage`, 400 sur valeur inconnue, `republication` dans `Listing` avec `newListingUrl` tiré de l'annonce connue.
- Interface : Vitest pour le texte de la colonne Republication ; `inventoryCriteriaOf` transmet `stage`.
