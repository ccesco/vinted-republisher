# US-13 – Vérifier la republication
**Statut** : validée
**Priorité** : MVP
**Dépend de** : US-12 (republication et « erreur de republication »), US-04 (lecture du dressing), US-05 et US-06 (lecture du contenu d'une annonce), US-03 (verrou du navigateur)

## Contexte
US-12 enregistre une tentative de republication juste avant le clic sur « Publier ». Tout échec après ce clic (nouvelle annonce absente du dressing, plusieurs candidates, plantage, page restée sur le formulaire) laisse « erreur de republication », qui bloque toute relance pour éviter un doublon sur Vinted. US-12 renvoie ici :
- la levée du doute d'une annonce en erreur de republication, en regardant Vinted ;
- la comparaison complète de la nouvelle annonce avec la sauvegarde (US-12 ne compare que le titre et le prix).

Cas réel (2026-11-15) : deux republications du bonnet 10208799832 sont restées sur le formulaire sans rien publier ; la ligne en erreur a dû être supprimée à la main en base pour pouvoir relancer.

Contraintes déjà posées : aucune requête directe à l'API de Vinted, l'outil lit comme le membre dans le Chrome piloté (US-04) ; une seule action navigateur à la fois (US-03).

## Objectif
En tant que membre, je veux vérifier sur Vinted le résultat d'une republication, afin de débloquer ou confirmer l'annonce sans toucher à la base ni risquer un doublon, et de savoir si la nouvelle annonce est identique à la sauvegarde.

## Comportement attendu
1. **Aperçu** : une annonce dont la dernière republication est en erreur ou réussie montre « Vérifier sur Vinted ». Une republication « non publiée » ne le montre pas : « Republier sur Vinted » redevient actif.
2. **Contrôles**, avant d'ouvrir Chrome : l'annonce a une sauvegarde ; sa dernière republication est en erreur ou réussie ; aucune autre action navigateur en cours (verrou `REPUBLISHING` d'US-03). Sinon refus, Chrome n'est pas ouvert.
3. **Levée du doute** (republication en erreur) : d'abord une lecture des seules annonces affichées à l'ouverture du dressing, sans défiler (comme la preuve d'US-12) ; si aucune candidate n'y a le titre et le prix de la sauvegarde, une lecture complète du dressing, au rythme du scan, avant de conclure. Sont candidates les annonces du dressing autres que l'ancienne et que les nouvelles annonces d'autres republications, inconnues de l'outil ou connues avec le titre et le prix de la sauvegarde ; leur page est ouverte pour lire titre et prix (comme au scan). Parmi elles :
   - exactement une avec le titre et le prix de la sauvegarde → republication **réussie** : nouvel identifiant et date de la vérification enregistrés, nouvelle annonce présente et reliée à l'ancienne (comme US-12), puis comparaison (étape 4) dans la même visite ;
   - aucune → republication **non publiée**, avec la date de la vérification ; une nouvelle republication redevient possible (mêmes contrôles qu'US-12) ;
   - plusieurs → reste **en erreur** ; l'aperçu dit « Plusieurs annonces correspondent sur Vinted : supprimez les doublons puis revérifiez ».
4. **Comparaison** (republication réussie) : le contenu de la nouvelle annonce est lu comme une sauvegarde (US-05, US-06), au rythme de la sauvegarde, sans rien enregistrer ; il est comparé champ par champ à la sauvegarde : titre, description, prix, catégorie, marque, état, taille, couleurs, matières, format du colis et champs propres à la catégorie, les options par leur identifiant Vinted ; photos par leur nombre seulement (Vinted réencode les photos publiées). La réponse dit « identique à la sauvegarde » ou liste les différences (champ, valeur sauvegardée, valeur lue). Rien n'est enregistré, rien n'est corrigé sur Vinted.
5. **Après** : l'aperçu montre l'issue et, s'il y a eu comparaison, son résultat. Aucun clic n'est jamais fait sur Vinted : la vérification ne fait que lire, en dry-run comme en réel.

## Contrat d'API
Contract first : `src/main/resources/static/openapi/vinted-republisher.yaml`. Demande synchrone.

| Méthode et chemin | operationId | Réponse |
| --- | --- | --- |
| `POST /api/listings/{listingId}/republication/verification` | `verifyListingRepublication` | 200 `ListingBackupView` à jour (avec `republication` et, si comparée, `republicationComparison`), y compris si le doute reste ; 404 sans sauvegarde ; 409 `RepublicationRefusal` (`NO_REPUBLICATION_TO_VERIFY`, `BROWSER_BUSY`) ; 502 `RepublicationRefusal` (`BROWSER_UNAVAILABLE`, `VINTED_UNREACHABLE`, `SESSION_DISCONNECTED`, `LISTING_UNREADABLE`) ; 500 `RepublicationRefusal` (`REPUBLICATION_FAILED`) ; 403 si la demande vient d'un autre site |

- `RepublicationOutcome` gagne `NOT_PUBLISHED` ; `ListingRepublication` gagne `checkedAt` (présent si `NOT_PUBLISHED`).
- `ListingBackupView` gagne `republicationComparison` (seulement dans la réponse d'une vérification qui a comparé) : tableau de `ListingFieldDifference` {`field` (`ListingFormField`), `saved`, `read`} ; vide = identique à la sauvegarde.
- Une erreur pendant la lecture du dressing laisse la republication telle qu'elle était. Une erreur pendant la lecture de la nouvelle annonce (illisible, Vinted inaccessible, session, navigateur), après une levée du doute réussie, garde la republication réussie : 502, l'aperçu rechargé la montre. L'aperçu dit donc « Vérification interrompue, l'état affiché est celui enregistré », sans affirmer que rien n'a changé.

## Critères d'acceptation
- [ ] Une republication en erreur se lève sur Vinted : nouvelle annonce retrouvée seule → réussie et reliée ; aucune → non publiée, relance possible ; plusieurs → reste en erreur.
- [ ] Une nouvelle annonce déjà vue par un scan entre-temps est retrouvée quand même (pas de doublon).
- [ ] Une republication réussie se compare champ par champ à la sauvegarde ; les différences sont listées, rien n'est enregistré ni corrigé.
- [ ] Après une republication non publiée, une nouvelle republication est possible et l'historique des tentatives reste en base.
- [ ] Toute erreur de lecture du dressing laisse la republication telle qu'elle était ; le verrou est toujours libéré.
- [ ] La vérification ne clique jamais sur Vinted et ne s'ouvre pas sans republication à vérifier.
- [ ] Un autre site ouvert dans le navigateur ne peut pas demander une vérification.
- [ ] Aucun test automatisé ne contacte le vrai site Vinted.

## Tests d'acceptation
### AT-13-01 – Erreur levée : nouvelle annonce retrouvée
```gherkin
Étant donné une annonce « Robe bleue » à 12,00 € en erreur de republication
Et le dressing montre une seule annonce inconnue « Robe bleue » à 12,00 €
Quand le membre clique sur « Vérifier sur Vinted »
Alors la republication est réussie, avec le nouvel identifiant et la date de la vérification
Et la nouvelle annonce est présente et reliée à l'ancienne
Et la nouvelle annonce est comparée à la sauvegarde
```
Test : `VerifyListingRepublicationAcceptanceTest.failed_republication_found_in_the_wardrobe_becomes_published()`

### AT-13-02 – Erreur levée : rien publié
```gherkin
Étant donné une annonce en erreur de republication
Et le dressing ne montre aucune annonce avec son titre et son prix
Quand le membre clique sur « Vérifier sur Vinted »
Alors la republication est « non publiée », avec la date de la vérification
Et « Republier sur Vinted » redevient possible
```
Test : `VerifyListingRepublicationAcceptanceTest.failed_republication_missing_from_the_wardrobe_becomes_not_published()`

### AT-13-03 – Plusieurs candidates
```gherkin
Étant donné une annonce en erreur de republication
Et le dressing montre deux annonces avec son titre et son prix
Quand le membre clique sur « Vérifier sur Vinted »
Alors la republication reste en erreur
```
Test : `VerifyListingRepublicationAcceptanceTest.several_candidates_leave_the_republication_failed()`

### AT-13-04 – Nouvelle annonce déjà vue par un scan
```gherkin
Étant donné une annonce en erreur de republication
Et un scan a enregistré entre-temps la nouvelle annonce, même titre et même prix
Quand le membre clique sur « Vérifier sur Vinted »
Alors la republication est réussie avec cette annonce
```
Test : `VerifyListingRepublicationAcceptanceTest.new_listing_already_scanned_is_still_found()`

### AT-13-05 – Comparaison : identique
```gherkin
Étant donné une republication réussie dont la nouvelle annonce a le même contenu et le même nombre de photos
Quand le membre clique sur « Vérifier sur Vinted »
Alors la réponse dit « identique à la sauvegarde »
Et rien n'est enregistré
```
Test : `VerifyListingRepublicationAcceptanceTest.identical_new_listing_shows_no_difference()`

### AT-13-06 – Comparaison : différences
```gherkin
Étant donné une republication réussie dont la nouvelle annonce a la taille 614 au lieu de 613, une couleur en plus et une photo de moins
Quand le membre clique sur « Vérifier sur Vinted »
Alors la réponse liste les différences de taille, de couleurs et de photos, avec valeur sauvegardée et valeur lue
Et rien n'est corrigé sur Vinted ni enregistré
```
Test : `VerifyListingRepublicationAcceptanceTest.different_new_listing_lists_the_differences()`

### AT-13-07 – Relance après « non publiée »
```gherkin
Étant donné une republication « non publiée »
Quand le membre republie l'annonce
Alors une nouvelle tentative est enregistrée et l'ancienne reste en base
```
Test : `VerifyListingRepublicationAcceptanceTest.not_published_republication_allows_a_new_attempt()`

### AT-13-08 – Rien à vérifier
```gherkin
Étant donné une annonce sans republication, ou « non publiée »
Quand une vérification est demandée
Alors elle est refusée (NO_REPUBLICATION_TO_VERIFY) et Chrome n'est pas ouvert
```
Test : `VerifyListingRepublicationAcceptanceTest.nothing_to_verify_is_refused_without_opening_chrome()`

### AT-13-09 – Navigateur occupé
```gherkin
Étant donné une autre action navigateur en cours
Quand une vérification est demandée
Alors elle est refusée (BROWSER_BUSY)
```
Test : `VerifyListingRepublicationAcceptanceTest.busy_browser_refuses_the_verification()`

### AT-13-10 – Erreur de lecture du dressing
```gherkin
Étant donné une annonce en erreur de republication
Et Vinted inaccessible, la session déconnectée ou le navigateur indisponible
Quand le membre clique sur « Vérifier sur Vinted »
Alors la vérification est refusée avec la raison, la republication reste en erreur et le verrou est libéré
```
Test : `VerifyListingRepublicationAcceptanceTest.wardrobe_failure_leaves_the_republication_as_it_was()`, `VerifyListingRepublicationAcceptanceTest.browser_unavailable_leaves_the_republication_as_it_was()` ; codes 502 : `ListingRepublicationApiControllerTest`

### AT-13-11 – Nouvelle annonce illisible
```gherkin
Étant donné une republication en erreur dont la nouvelle annonce est retrouvée dans le dressing
Et la page de la nouvelle annonce illisible
Quand le membre clique sur « Vérifier sur Vinted »
Alors la vérification est refusée (LISTING_UNREADABLE) et la republication reste réussie
```
Test : `VerifyListingRepublicationAcceptanceTest.unreadable_new_listing_keeps_the_republication()`, `VerifyListingRepublicationAcceptanceTest.vinted_unreachable_during_the_comparison_keeps_the_proven_republication()`, `ListingRepublicationApiControllerTest.unreadable_new_listing_is_a_bad_gateway()`

### AT-13-12 – Demande d'un autre site
```gherkin
Étant donné une page d'un autre site ouverte dans le navigateur
Quand elle demande une vérification
Alors la demande est refusée (403)
```
Test : `ListingRepublicationApiControllerTest.refusesCrossSiteVerification()`

### AT-13-13 – Aperçu
```gherkin
Étant donné une republication en erreur, réussie ou non publiée
Quand le membre ouvre l'aperçu
Alors « Vérifier sur Vinted » est proposé pour l'erreur et la réussite, « Republier sur Vinted » pour la non publiée
Et le résultat d'une vérification s'affiche (issue, différences ou « identique à la sauvegarde »)
```
Test : `ListingBackupPageTest.Republication` : `republication_in_error_asks_to_check_on_vinted()`, `failed_republication_is_verified_and_can_be_republished_once_not_published()`, `several_candidates_ask_to_remove_the_duplicates()`, `published_republication_shows_its_differences_as_text()`, `identical_new_listing_says_so()`, `refused_verification_says_nothing_changed()` (page servie par une fausse API)

### AT-13-14 – Vérification réelle
```gherkin
Étant donné la republication en erreur du bonnet 10208799832, rien de publié sur Vinted
Quand le membre clique sur « Vérifier sur Vinted »
Alors la republication est « non publiée » et une relance est possible
```
Test : manuel, sur le vrai compte, avec l'accord de l'utilisateur

### AT-13-15 – Nouvelle annonce parmi les annonces affichées à l'ouverture
```gherkin
Étant donné une republication en erreur
Et la nouvelle annonce parmi les annonces que le dressing affiche à son ouverture
Quand le membre clique sur « Vérifier sur Vinted »
Alors la republication est réussie, sans que le dressing soit parcouru en entier
```
Test : `VerifyListingRepublicationAcceptanceTest.new_listing_shown_on_opening_is_found_without_going_through_the_wardrobe()`

### AT-13-16 – Nouvelle annonce plus bas dans le dressing
```gherkin
Étant donné une republication en erreur
Et la nouvelle annonce absente des annonces affichées à l'ouverture, mais plus bas dans le dressing
Quand le membre clique sur « Vérifier sur Vinted »
Alors le dressing est parcouru en entier et la republication est réussie
```
Test : `VerifyListingRepublicationAcceptanceTest.new_listing_further_down_is_found_by_going_through_the_wardrobe()`

## Cas limites et erreurs
- Deux annonces identiques (titre et prix) réellement en vente : la levée du doute ne conclut pas ; le membre change le titre de l'une sur Vinted puis revérifie.
- Republication en erreur après un plantage avant le clic : rien de publié, la vérification la passe « non publiée ».
- Nouvelle annonce vendue ou réservée entre-temps : toujours dans le dressing, retrouvée si son titre et son prix n'ont pas changé.
- Nouvelle annonce modifiée par le membre depuis (titre ou prix) : la levée du doute ne la retrouve pas (« non publiée ») ; le membre ne relance pas, risque assumé et dit dans l'aperçu (« Rien trouvé sur Vinted avec ce titre et ce prix »).
- Différence de libellé seul (option au même identifiant) : pas une différence.
- Nouvelle annonce pas encore montrée dans le dressing (modération par Vinted) : la levée du doute conclurait « non publiée » et rouvrirait la relance. Jamais observé : la nouvelle annonce apparaît quasi instantanément (décision d'US-12, 2026-11-11), et la vérification se fait après coup. À surveiller lors d'AT-13-14 ; sinon, ajouter un délai minimal depuis le clic.
- Description dont Vinted normalise les espaces ou retours à la ligne : signalée comme différence ; à vérifier lors d'une vraie comparaison.
- Plantage pendant la vérification : rien n'est enregistré avant la conclusion ; la republication reste telle qu'elle était.

## Hors périmètre
- Correction automatique des différences sur Vinted.
- Comparaison des photos octet par octet (Vinted les réencode) ; comparaison d'image (V2).
- Vérification automatique juste après la republication, enchaînement et séries (US-15, US-16, US-19).
- Historique des tentatives dans l'aperçu : seule la dernière est montrée.

## Décisions
- Périmètre, décidé par l'utilisateur (2026-11-15) : les deux, levée du doute d'une erreur de republication et comparaison complète d'une republication réussie, par un seul bouton « Vérifier sur Vinted ».
- Les décisions suivantes ont été prises par Claude sur délégation explicite de l'utilisateur (« fais comme tu le sens », 2026-11-15), sur le modèle de « Revérifier sur Vinted » d'US-09 :
  - Levée du doute : une lecture du dressing ; une candidate → réussie, aucune → `NOT_PUBLISHED` (relance possible), plusieurs → reste `FAILED`. Comme US-12, pas d'attente ni de relecture.
  - Candidates : inconnues de l'outil, ou connues avec le titre et le prix de la sauvegarde, hors l'ancienne annonce et les nouvelles annonces d'autres republications ; sans quoi un scan fait entre-temps rendrait la nouvelle annonce invisible et autoriserait un doublon.
  - Historique : plusieurs tentatives par annonce, au plus une réussie ou en erreur à la fois ; la dernière fait foi (comme les suppressions d'US-09). Jamais supprimées.
  - Comparaison : lecture du contenu par le lecteur de la sauvegarde (US-05, US-06), rien enregistré ; options comparées par identifiant, photos par nombre ; résultat dans la réponse seulement.
  - Verrou `REPUBLISHING`, aucun clic, valable en dry-run comme en réel.
  - Une erreur de lecture de la nouvelle annonce après une levée du doute garde la republication réussie : la preuve est faite, seule la comparaison manque.
- Lecture en deux temps, décidée par Claude sur délégation explicite de l'utilisateur (« fait comme tu penses », 2026-11-17), après une vérification réelle arrêtée par l'utilisateur, jugée trop longue : d'abord les annonces affichées à l'ouverture du dressing, sans défiler ; au moins une candidate au titre et au prix de la sauvegarde → conclusion sur elles (une → réussie, plusieurs → reste en erreur). Aucune → lecture complète avant de conclure : « non publiée » autorise une nouvelle republication, la conclure sur une lecture partielle risquerait un doublon.

## Questions ouvertes
- Aucune.

## Avancement (2026-11-15)
- Fait : domaine (`NOT_PUBLISHED`, `ListingFieldDifference`), cas d'usage `VerifyListingRepublication`, recherche élargie (`NewListingSearch.findAgain`), changeset `010-verify-listing-republication`, API, aperçu. AT-13-01 à AT-13-13 verts avec un faux navigateur ; aucun nouvel adaptateur Playwright (lecture du dressing d'US-04, lecture du contenu d'US-05).
- Test ajouté hors scénario : `VerifyListingRepublicationAcceptanceTest.new_listing_of_another_republication_is_never_a_candidate()`.
- Rebasée sur US-14 (étapes) et US-18b (fiche `listing.html`) : l'étape d'une annonce lit la dernière tentative (`findAllLatest`), et une republication `NOT_PUBLISHED` ramène l'annonce à « À republier » (`ListingStageTest.republication_checked_not_published_is_to_republish_again()`).
- Reste : AT-13-14, vérification réelle du bonnet, sur accord explicite de l'utilisateur. L'US reste « validée » d'ici là.

## Modèle de domaine
Termes à ajouter ou modifier dans le glossaire :
- `RepublicationOutcome` gagne `NOT_PUBLISHED` | Non publiée | Vérification faite sur Vinted : aucune annonce avec le titre et le prix de la sauvegarde ; une nouvelle republication redevient possible.
- `VerifyListingRepublication` | Vérification de la republication | Lecture sur Vinted, sans clic, qui lève le doute d'une erreur de republication et compare une republication réussie à la sauvegarde.
- `ListingFieldDifference` | Différence avec la sauvegarde | Champ du formulaire dont la valeur lue sur la nouvelle annonce n'est pas celle sauvegardée (valeur sauvegardée, valeur lue).
- `NoRepublicationToVerify` | Aucune republication à vérifier | Refus : pas de republication, ou dernière « non publiée ».
- Port réutilisé : `ListingContentReader` (US-05) pour lire la nouvelle annonce ; `WardrobeReader` (US-04) pour le dressing.

## Tests unitaires et d'intégration attendus
- Domaine : `ListingRepublication` (passage `FAILED` → `PUBLISHED` / `NOT_PUBLISHED`, refus depuis les autres issues, nouvelle tentative permise après `NOT_PUBLISHED`) ; `ListingFieldDifference.between` (chaque champ, options par identifiant, photos par nombre, libellé seul ignoré).
- Application : `VerifyListingRepublication` (candidates, issues, verrou libéré, erreurs).
- Persistance : changeset `010` (plusieurs tentatives, une seule `PUBLISHED`/`FAILED`, `checked_at`), `findLatest`.
- Web : contrôleur, refus, 403.
