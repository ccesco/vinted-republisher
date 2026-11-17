# US-34 – Édition en masse et tags
**Statut** : validée
**Priorité** : V2
**Dépend de** : US-18 (liste des annonces, critères d'affichage)

## Contexte
Avec ~150 annonces, le membre veut les regrouper à sa façon (« été », « à baisser », « lot enfant ») pour les retrouver et les traiter ensemble. Vinted n'offre rien de tel. La liste d'US-18 sait filtrer sur des faits de l'outil (présence, sauvegarde) mais pas sur un classement du membre, et chaque modification se fait annonce par annonce.

Contraintes déjà posées :
- La liste lit sa ressource `GET /api/listings` avec ses critères (US-18) ; les titres venus de Vinted ne passent jamais par `innerHTML`.
- Toute écriture sur Vinted passe par les garde-fous existants (dry-run US-10, limites de cadence US-27) ; rien ne doit être modifié sur le vrai compte pendant le développement.
- US-32, US-33, US-35 et US-36 (Epic 9) sont développées en parallèle : cette US ne suppose pas leur code (prix, cycle de vie, règles).

## Objectif
En tant que membre, je veux poser des tags sur une sélection d'annonces en une fois, et filtrer la liste sur un tag, afin de regrouper mes annonces à ma façon et de les retrouver ensemble.

## Comportement attendu
1. **Tag** : étiquette libre posée par le membre sur une annonce, gardée dans l'outil seulement ; jamais envoyée à Vinted. Une annonce porte zéro, un ou plusieurs tags, chacun une seule fois. Présentes ou absentes, toutes les annonces connues peuvent en porter.
2. **Forme d'un tag** : texte saisi, espaces autour retirés, espaces intérieurs réduits à un seul, mis en minuscules ; accents gardés (« Été » devient « été »). Entre 1 et 30 caractères une fois mis en forme. Deux saisies de même forme sont le même tag.
3. **Édition en masse** : le membre coche des annonces dans le tableau (ou toutes les lignes affichées d'un clic), saisit un tag et choisit « Ajouter le tag » ou « Retirer le tag ». L'opération porte sur toutes les annonces cochées, tout ou rien. Ajouter un tag déjà porté, ou retirer un tag absent, ne change rien à cette annonce et n'est pas une erreur.
4. **Annonce inconnue** : si une annonce de la sélection n'est pas connue de l'outil, rien n'est modifié (404).
5. **Sélection** : de 1 à 1 000 annonces, sans doublon. Elle se garde quand la liste est relue (scan, série, changement de critère) mais ne contient jamais que des lignes affichées : une annonce qui sort de la liste sort de la sélection. Elle se vide après une édition réussie.
6. **Liste** : une colonne « Tags » montre les tags de chaque annonce, par ordre alphabétique, séparés par des virgules (texte, jamais HTML). Une colonne de cases à cocher en tête de ligne ; la case de l'en-tête coche ou décoche toutes les lignes affichées. Au-dessus du tableau : « N annonces sélectionnées », un champ « Tag » et les deux boutons, inactifs tant que la sélection est vide ou le tag vide.
7. **Filtre par tag** : une liste déroulante « Tag » parmi les critères (« Tous » par défaut, puis chaque tag porté par au moins une annonce, par ordre alphabétique). Elle n'affiche que les annonces portant ce tag. Le tag demandé est mis en forme comme en 2 ; un tag que personne ne porte donne une liste vide.
8. **Après une édition** : message « Tag « x » ajouté à N annonces » ou « Tag « x » retiré de N annonces » (N = nombre d'annonces sélectionnées), puis la page relit les tags proposés au filtre et la liste. En cas de refus : « Une annonce sélectionnée n'est plus connue : aucun tag modifié » (404), « Tag invalide (1 à 30 caractères) : aucun tag modifié » (400), « Échec de la modification des tags : aucun tag modifié » sinon.
9. Rien n'est lu ni écrit sur Vinted ; l'édition n'entre pas au journal des actions (US-23), qui ne trace que les actions sur Vinted.

## Contrat d'API
| Requête | Opération | Réponse |
| --- | --- | --- |
| `POST /api/listings/tag-edits` | `editListingTags` | Corps `TagEdit` : `listingIds` (tableau d'entiers int64 ≥ 1, de 1 à 1 000, sans doublon), `tag` (chaîne, 1 à 100 caractères, au moins un caractère non blanc), `operation` (`TagOperation` : `ADD`, `REMOVE`), tous obligatoires. 204 fait ; 400 corps invalide ou tag de plus de 30 caractères une fois mis en forme ; 403 autre origine ; 404 annonce inconnue, rien modifié. |
| `GET /api/listing-tags` | `showListingTags` | 200, `ListingTags` : `tags` (tableau de chaînes, ordre alphabétique, chaque tag porté par au moins une annonce). |
| `GET /api/listings` | `showListingInventory` | Nouveau paramètre facultatif `tag` (chaîne, 100 caractères au plus ; vide = pas de filtre). 400 si `tag` dépasse 100 caractères, ou 30 une fois mis en forme. |

`Listing` gagne `tags` (tableau de chaînes, obligatoire, ordre alphabétique, vide si aucun tag). Il est aussi rendu par la fiche (US-18b) et la relance (US-20), qui ne l'affichent pas.

## Critères d'acceptation
- [ ] Ajouter un tag à une sélection le pose sur chacune ; une annonce qui le portait déjà le garde une seule fois.
- [ ] Retirer un tag d'une sélection l'ôte de chacune ; une annonce qui ne le portait pas est inchangée.
- [ ] Un tag est mis en forme (espaces, minuscules) avant d'être posé, retiré ou filtré ; un tag vide ou trop long est refusé.
- [ ] Une sélection contenant une annonce inconnue est refusée en entier.
- [ ] La liste montre les tags de chaque annonce et se filtre sur un tag.
- [ ] La liste des tags proposés contient chaque tag porté, une fois, par ordre alphabétique.
- [ ] Les tags survivent au redémarrage de l'application (base PostgreSQL).
- [ ] La page permet de cocher des annonces, d'ajouter ou retirer un tag à la sélection, et de filtrer sur un tag ; tags affichés comme texte.

## Tests d'acceptation
### AT-34-01 – Ajouter un tag à une sélection
```gherkin
Étant donné les annonces 101, 102 et 103, et 102 portant déjà le tag « été »
Quand le membre ajoute le tag "  Été " aux annonces 101 et 102
Alors 101 et 102 portent le tag « été », une seule fois chacune
  et 103 ne porte aucun tag
```
Test : `EditListingTagsAcceptanceTest.adds_a_tag_to_every_selected_listing()`

### AT-34-02 – Retirer un tag d'une sélection
```gherkin
Étant donné 101 portant « été » et « lot », 102 portant « lot », 103 portant « été »
Quand le membre retire le tag « ÉTÉ » des annonces 101 et 102
Alors 101 porte seulement « lot », 102 porte seulement « lot », 103 porte toujours « été »
```
Test : `EditListingTagsAcceptanceTest.removes_a_tag_from_every_selected_listing()`

### AT-34-03 – Annonce inconnue : rien n'est modifié
```gherkin
Étant donné l'annonce 101 sans tag, et aucune annonce 999
Quand le membre ajoute le tag « été » aux annonces 101 et 999
Alors l'édition est refusée : annonce 999 inconnue
  et 101 ne porte aucun tag
```
Test : `EditListingTagsAcceptanceTest.refuses_the_whole_edit_when_a_listing_is_unknown()`

### AT-34-04 – Tag invalide
```gherkin
Quand le membre saisit un tag fait d'espaces, ou de 31 caractères une fois mis en forme
Alors le tag est refusé
```
Test : `TagTest.rejects_blank_and_too_long_tags()`

### AT-34-05 – La liste montre et filtre les tags
```gherkin
Étant donné 101 portant « lot » et « été », 102 portant « lot », 103 sans tag
Quand le membre affiche la liste
Alors 101 montre « été, lot », 102 « lot », 103 aucun tag
Quand il filtre sur le tag "Été"
Alors il voit seulement 101
Quand il filtre sur le tag « hiver »
Alors la liste est vide
```
Test : `EditListingTagsAcceptanceTest.inventory_shows_and_filters_on_tags()`

### AT-34-06 – Tags proposés au filtre
```gherkin
Étant donné 101 portant « lot » et « zèbre », 102 portant « lot » et « été »
Quand la page demande les tags
Alors elle reçoit « été », « lot » puis « zèbre », chacun une fois (collation française : « été » avant « lot »)
```
Test : `EditListingTagsAcceptanceTest.lists_every_tag_once_in_alphabetical_order()`

### AT-34-07 – API : édition, refus et paramètres
```gherkin
Quand la page envoie POST /api/listings/tag-edits avec une sélection connue, le tag "été" et ADD
Alors la réponse est 204
Quand la sélection contient une annonce inconnue
Alors la réponse est 404
Quand le tag est vide, fait de 31 caractères mis en forme, la sélection vide ou l'opération inconnue
Alors la réponse est 400
Quand elle demande GET /api/listings?tag=Été
Alors les annonces renvoyées portent « été » et chacune donne ses tags
```
Test : `ListingTagsApiControllerTest.edits_tags_and_refuses_invalid_edits()` et `ListingInventoryApiControllerTest.filters_on_tag_and_shows_tags()`

### AT-34-08 – Persistance des tags
```gherkin
Étant donné les annonces 101 et 102 en base
Quand le tag « été » est posé sur les deux, puis retiré de 102
Alors relus, les tags sont : 101 « été », 102 aucun
  et poser deux fois le même tag ne crée pas de doublon
```
Test : `JpaListingTagRepositoryTest.adds_and_removes_tags()` (`@Tag("docker")`)

### AT-34-09 – Page : sélection, édition en masse et filtre
```gherkin
Étant donné une API qui renvoie deux annonces, dont une portant le tag "<b>lot</b>", et les tags proposés « <b>lot</b> »
Quand le membre ouvre wardrobe.html
Alors la colonne Tags montre "<b>lot</b>" tel quel et le filtre Tag propose « Tous » et "<b>lot</b>"
  et les boutons d'édition sont inactifs
Quand il coche toutes les lignes, saisit « été » et clique « Ajouter le tag »
Alors la page envoie POST /api/listings/tag-edits avec les deux identifiants, "été" et ADD
  et affiche « Tag « été » ajouté à 2 annonces », puis relit les tags et la liste
Quand il choisit le tag "<b>lot</b>" dans le filtre
Alors la page demande GET /api/listings avec tag=<b>lot</b>
Quand l'API refuse une édition par 404
Alors la page affiche « Une annonce sélectionnée n'est plus connue : aucun tag modifié »
```
Test : `ListingTagsPageTest.selects_listings_edits_their_tags_and_filters_on_a_tag()` (`@Tag("browser")`)

## Cas limites et erreurs
- Sélection vide côté page : boutons inactifs ; côté API, 400.
- Une même annonce deux fois dans la sélection : 400 (contrat `uniqueItems`).
- Annonce absente de Vinted : on peut lui poser ou retirer des tags comme aux autres.
- Annonce republiée : la nouvelle annonce (nouvel identifiant Vinted) ne reprend pas les tags de l'ancienne (voir Hors périmètre).
- Deux éditions simultanées sur les mêmes annonces : chacune en une transaction ; ajout et retrait sont idempotents, la dernière l'emporte.
- Tag contenant `<`, `%` ou `'` : gardé tel quel, affiché comme texte, comparé en Java.
- Base indisponible : 500 de Spring, la page affiche « Échec de la modification des tags : aucun tag modifié » (la transaction garantit le tout ou rien).

## Hors périmètre
- Édition en masse de champs de l'annonce sur Vinted (prix, description, catégorie…) : écrirait sur le compte réel ; le prix relève d'US-33 (historique de prix), développée en parallèle. À reprendre dans une US dédiée, derrière dry-run et limites de cadence.
- Report des tags sur la nouvelle annonce d'une republication : demande de toucher la vérification et la validation manuelle de republication (US-13), que le cycle de vie (US-32) redéfinit en parallèle. À reprendre avec US-32.
- Renommer ou supprimer un tag partout d'un coup (se fait par sélection de toutes les annonces filtrées sur ce tag, puis « Retirer »).
- Filtre sur plusieurs tags à la fois, tri par tag, couleurs de tags.
- Tags sur la fiche d'une annonce (US-18b) : l'API les rend, la fiche ne les affiche pas.
- Journalisation des éditions de tags.

## Décisions
Toutes prises par Claude, par délégation de l'utilisateur (2026-11-17 : « rédiger et valider la spec soi-même »).
- **Édition en masse = tags, locale** : l'édition en masse porte sur les tags, gardés dans l'outil seulement. Raison : aucune écriture sur Vinted (pas de risque sur le compte réel ni anti-robot), et le seul champ éditable « en masse » utile sur Vinted, le prix, appartient à US-33 développée en parallèle.
- **Tag libre, mis en forme** (espaces réduits, minuscules, accents gardés, 1 à 30 caractères). Raison : éviter « Été » / « été » en double, sans liste de tags à gérer à part ; 30 caractères suffisent à une étiquette.
- **Pas d'entité « tag » à part** : un tag existe tant qu'une annonce le porte ; stockage en table `listing_tag` (annonce, tag). Raison : YAGNI, pas de renommage ni de couleur demandés.
- **Une commande `POST /api/listings/tag-edits`** avec l'opération `ADD` ou `REMOVE`, tout ou rien, réponse 204. Raison : convention du contrat (commande = `POST` sur une sous-ressource nommée), une seule transaction pour toute la sélection ; la page relit la liste ensuite, comme après les autres actions.
- **Annonce inconnue = 404 sur toute la sélection**. Raison : tout ou rien, et la sélection vient de la liste affichée, donc un identifiant inconnu signale une erreur de la page.
- **Sélection limitée à 1 000** : borne de contrat, bien au-dessus d'un dressing (~150).
- **Tag accepté jusqu'à 100 caractères dans le contrat, 30 après mise en forme** : la mise en forme peut raccourcir le texte (espaces) ; le domaine décide, le contrat empêche seulement les entrées démesurées.
- **Filtre sur un seul tag**, combiné aux autres critères (ET). Raison : besoin exprimé « retrouver ensemble » ; plusieurs tags = YAGNI.
- **`GET /api/listing-tags`** pour remplir le filtre, plutôt que de déduire les tags de la liste affichée (qui est déjà filtrée).
- **Ordre alphabétique** des tags : collation française (`java.text.Collator` `fr`), pour que « été » se range avant « lot ».
- **Sélection** gardée à travers les relectures pour les seules lignes affichées, vidée après une édition réussie. Raison : la liste est relue toutes les 2 s pendant un scan ; perdre la sélection serait pénible, et éditer une ligne invisible serait surprenant.
- **Pas de journal** : le journal d'US-23 trace les actions sur Vinted ; une édition locale n'y a pas sa place (et `JournaledAction` changerait le contrat partagé).
- **Page de test séparée** (`ListingTagsPageTest`) : la page reste `wardrobe.html`, mais le test des tags vit à part de `WardrobePageTest` pour limiter les conflits avec les US parallèles.

## Questions ouvertes
(aucune)

## Modèle de domaine
- `Tag` (tag, value object, `domain.tag`) : étiquette mise en forme (espaces, minuscules), 1 à 30 caractères ; `InvalidTag` sinon. Ordre : collation française.
- `TagOperation` (`ADD` / `REMOVE`) : ajouter ou retirer.
- `TagEdit` (édition des tags, value object) : sélection d'annonces (identifiants Vinted, non vide), tag, opération.
- `ListingTag` (tag d'une annonce) : couple annonce, tag.
- `ListingTagRepository` (port sortant) : `findAll()` ; `add(Tag, List<VintedListingId>)` et `remove(Tag, List<VintedListingId>)`, chacun tout ou rien, idempotents.
- `EditListingTags` (cas d'usage, `application.tag`) : vérifie que chaque annonce est connue (`ListingNotFound` sinon), puis ajoute ou retire.
- `ShowListingTags` (cas d'usage) : tous les tags portés, une fois chacun, triés.
- `InventoryEntry` gagne ses tags (triés) ; `InventoryCriteria` gagne un filtre de tag facultatif ; `ShowListingInventory` lit aussi `ListingTagRepository`.

## Tests unitaires et d'intégration attendus
- Domaine : `TagTest` (mise en forme, bornes, ordre), `TagEditTest` (sélection non vide), `InventoryCriteriaTest` (filtre de tag mis en forme, absent par défaut), `ListingInventoryTest` (tags rattachés aux lignes, filtre de tag).
- Application : `EditListingTagsAcceptanceTest` avec les fakes (AT-34-01 à 03, 05, 06).
- Persistance : `JpaListingTagRepositoryTest` (`@Tag("docker")`, AT-34-08) ; `DatabaseMigrationAcceptanceTest` si besoin.
- Web : `ListingTagsApiControllerTest` (204, 400, 404, liste des tags), `ListingInventoryApiControllerTest` (paramètre `tag`, champ `tags`).
- Page : `ListingTagsPageTest` (`@Tag("browser")`, AT-34-09).
