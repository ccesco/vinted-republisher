# US-48 – Relier une annonce republiée hors de l'outil
**Statut** : validée
**Priorité** : V2
**Dépend de** : US-13 (vérifier la republication, validation manuelle), US-45 (page « Échecs », republication manuelle), US-09 (suppression), US-18b (fiche de l'annonce), US-23 (journal)

## Contexte
Cas réel (2026-11-19) : des annonces supprimées par l'outil (Chaussure geox taille 22, Chausson babygro dinosaures, Sandales creeks taille 21, Tongs décathlon taille 23-24…) sont classées « Refusées » sur la page « Échecs » (`FIELD_NOT_FILLABLE` à chaque tentative), alors qu'une nouvelle annonce identique est en ligne sur Vinted, sous un nouvel identifiant (l'ancien identifiant donne « page introuvable »). L'outil n'a aucune republication enregistrée pour elles.

Aujourd'hui, « Vérifier sur Vinted » et la validation manuelle (US-13) n'existent que pour une republication **en erreur** (`FAILED`). Pour y arriver, le membre doit passer par « Republier manuellement » (US-45), qui ouvre un formulaire pré-rempli : risque de publier un **doublon** de l'annonce déjà en ligne.

## Objectif
En tant que membre, je veux relier directement une annonce supprimée non republiée à la nouvelle annonce déjà en ligne sur Vinted, par une vérification ou en collant son lien, afin que l'outil la sache republiée sans repasser par un formulaire qui créerait un doublon.

## Comportement attendu
1. **Annonce à relier** : sa suppression est confirmée (US-09), elle n'a ni republication réussie ni republication en erreur en vigueur (une republication `NOT_PUBLISHED` n'empêche rien), et elle n'est pas disparue de Vinted. Ce sont les étapes « En attente » et « À republier » (US-14), qu'elle soit en échec ou non. Une republication en erreur garde le chemin d'US-13, inchangé.
2. **« Vérifier sur Vinted »** (`verifyListingRepublication`), sur une annonce à relier :
   - contrôles avant d'ouvrir Chrome : sauvegarde présente ; annonce à relier ou republication à vérifier (US-13) ; verrou `REPUBLISHING` libre. Sinon refus, Chrome n'est pas ouvert ;
   - lecture des annonces affichées à l'ouverture du dressing, comme US-13 (sans clic) ;
   - **une seule** annonce au titre et au prix de la sauvegarde, autre que l'ancienne et que les nouvelles annonces d'autres republications → republication **réussie** enregistrée (date de tentative et de publication = date de la vérification, sauvegarde = sauvegarde actuelle, nouvel identifiant), nouvelle annonce présente et reliée à l'ancienne, puis comparaison champ par champ dans la même visite (US-13, étape 4) ;
   - **aucune** ou **plusieurs** → rien n'est enregistré dans les republications, l'annonce reste dans son état ; la réponse porte `newListingSearch` = `NOT_FOUND` ou `SEVERAL`, et l'aperçu dit « Aucune nouvelle annonce trouvée sur Vinted » ou « Plusieurs annonces correspondent sur Vinted », « : collez le lien de la nouvelle annonce dans « Publiée » ».
3. **« Publiée »** (`validateListingRepublication`, `published: true`), sur une annonce à relier : mêmes contrôles du lien et même lecture de la seule page de la nouvelle annonce qu'en US-13 (adresse construite depuis l'identifiant, jamais le lien collé ; titre et prix différents → `NEW_LISTING_MISMATCH`, rien enregistré). Sinon republication **réussie** (dates = date de la validation, sauvegarde actuelle), reliée et comparée.
4. **« Non publiée »** (`published: false`) n'est pas proposé pour une annonce à relier : demandé quand même → refus `NO_REPUBLICATION_TO_VERIFY`, Chrome n'est pas ouvert.
5. **Fiche de l'annonce** (US-18b), section Republication : pour une annonce à relier, « Vérifier sur Vinted » et le bloc « Publiée » (champ du lien), à côté des boutons de republication existants ; pas de « Non publiée ».
6. **Page « Échecs »** (US-39, US-45) : chaque annonce en échec à relier (étape « En attente » ou « À republier ») montre « Vérifier sur Vinted », à côté de « Relancer » ou « Republier manuellement ». Liée → elle quitte la liste relue, le message le dit ; rien trouvé → le message le dit et renvoie à la fiche (lien) pour coller le lien de la nouvelle annonce.
7. **Journal** (US-23) : une ligne par demande, actions existantes `REPUBLICATION_VERIFICATION` (vérification) et `REPUBLICATION_VALIDATION` (« Publiée ») :
   - reliée → `SUCCEEDED` ;
   - aucune ou plusieurs candidates → `NOT_DONE`, raison `NEW_LISTING_NOT_FOUND` ou `SEVERAL_NEW_LISTINGS` ; la page « Échecs » **ignore** ces deux raisons (comme les refus d'attente) : l'annonce garde son groupe et sa cause d'origine ;
   - refus et erreurs → `REFUSED` ou `FAILED` avec leur raison, comme US-13.
8. **Série** : inchangée. Une annonce reliée est republiée : la série ne la reprend plus.

## Contrat d'API
| Opération | `operationId` | Changement |
|---|---|---|
| `POST /api/listings/{listingId}/republication/verification` | `verifyListingRepublication` | Accepte aussi une annonce à relier (sinon 409 `NO_REPUBLICATION_TO_VERIFY`, inchangé). 200 `ListingBackupView` avec `newListingSearch` quand aucune ou plusieurs candidates ont été trouvées pour une annonce à relier. |
| `POST /api/listings/{listingId}/republication/manual-validation` | `validateListingRepublication` | `published: true` accepté aussi pour une annonce à relier ; `published: false` y est refusé (409 `NO_REPUBLICATION_TO_VERIFY`). |

- `ListingBackupView` gagne `newListingSearch` (facultatif, enum `NOT_FOUND`, `SEVERAL`) : présent seulement dans la réponse d'une vérification d'annonce à relier restée sans résultat.
- `ListingBackupView` (ou ce que la fiche lit déjà) doit dire si l'annonce est à relier ; la page « Échecs » le déduit de l'étape (`WAITING`, `TO_REPUBLISH`), sans changement de `FailedListing`.
- Aucune nouvelle raison de refus, aucun changement de schéma de base (`listing_republication` accepte déjà une republication `PUBLISHED` sans tentative préalable).

## Critères d'acceptation
- [x] Une annonce à relier peut être vérifiée sur Vinted : une seule candidate → republication réussie, reliée et comparée, datée de la vérification.
- [x] Aucune ou plusieurs candidates → rien n'est enregistré dans les republications, l'annonce garde son étape et sa place sur la page « Échecs », la réponse dit pourquoi.
- [x] Une annonce à relier peut être validée « Publiée » avec le lien de la nouvelle annonce, avec les contrôles d'US-13 ; « Non publiée » y est refusé.
- [x] Une annonce non supprimée ou disparue de Vinted reste refusée (`NO_REPUBLICATION_TO_VERIFY`) sans ouvrir Chrome ; une annonce déjà republiée l'est à la validation « Publiée » (sa vérification la compare, US-13).
- [x] Le journal garde une ligne par demande ; les lignes `NEW_LISTING_NOT_FOUND` et `SEVERAL_NEW_LISTINGS` ne changent pas la page « Échecs ».
- [x] La fiche et la page « Échecs » proposent « Vérifier sur Vinted » aux annonces à relier ; la fiche propose aussi « Publiée ».
- [x] US-13 (republication en erreur) est inchangée.

## Tests d'acceptation

### AT-48-01 – Vérification : nouvelle annonce retrouvée
```gherkin
Étant donné une annonce supprimée, refusée à la republication (FIELD_NOT_FILLABLE), sans republication enregistrée
Et une seule annonce au titre et au prix de sa sauvegarde affichée à l'ouverture du dressing
Quand le membre demande « Vérifier sur Vinted »
Alors une republication réussie est enregistrée, datée de la vérification, avec la sauvegarde actuelle et l'identifiant de la nouvelle annonce
Et la nouvelle annonce est présente et reliée à l'ancienne, et comparée à la sauvegarde
Et le journal a une ligne REPUBLICATION_VERIFICATION SUCCEEDED
```
Test : `LinkRepublishedListingAcceptanceTest.single_candidate_links_the_new_listing()`

### AT-48-02 – Vérification : aucune candidate
```gherkin
Étant donné une annonce à relier, refusée à la republication (FIELD_NOT_FILLABLE)
Et aucune annonce à son titre et son prix à l'ouverture du dressing
Quand le membre demande « Vérifier sur Vinted »
Alors aucune republication n'est enregistrée et l'annonce garde son étape
Et la réponse porte newListingSearch NOT_FOUND
Et le journal a une ligne REPUBLICATION_VERIFICATION NOT_DONE (NEW_LISTING_NOT_FOUND)
Et la page « Échecs » la montre toujours dans « Refusées », avec la cause FIELD_NOT_FILLABLE
```
Test : `LinkRepublishedListingAcceptanceTest.no_candidate_changes_nothing()`, `ActionRecordTest.search_for_a_new_listing_without_result_leaves_failures_as_they_were()`, `JpaActionJournalTest.last_entry_of_each_listing()` (`@Tag("docker")`), `ListingRepublicationApiControllerTest.listing_to_link_without_new_listing_answers_with_the_search()`

### AT-48-03 – Vérification : plusieurs candidates
```gherkin
Étant donné une annonce à relier
Et deux annonces à son titre et son prix à l'ouverture du dressing
Quand le membre demande « Vérifier sur Vinted »
Alors aucune republication n'est enregistrée
Et la réponse porte newListingSearch SEVERAL
Et le journal a une ligne REPUBLICATION_VERIFICATION NOT_DONE (SEVERAL_NEW_LISTINGS)
```
Test : `LinkRepublishedListingAcceptanceTest.several_candidates_change_nothing()`

### AT-48-04 – Validation « Publiée »
```gherkin
Étant donné une annonce à relier
Quand le membre valide « Publiée » avec le lien d'une annonce au titre et au prix de la sauvegarde
Alors une republication réussie est enregistrée, datée de la validation, reliée et comparée
Et le journal a une ligne REPUBLICATION_VALIDATION SUCCEEDED
```
Test : `LinkRepublishedListingAcceptanceTest.published_link_links_the_new_listing()`

### AT-48-05 – Validation « Publiée » : page qui ne correspond pas, lien invalide
```gherkin
Étant donné une annonce à relier
Quand le membre valide « Publiée » avec le lien d'une annonce d'un autre titre ou prix
Alors elle est refusée (NEW_LISTING_MISMATCH) et rien n'est enregistré
Et un lien sans identifiant d'annonce, ou celui de l'ancienne annonce, est refusé (NEW_LISTING_LINK_INVALID) sans ouvrir Chrome
```
Test : `LinkRepublishedListingAcceptanceTest.mismatching_or_invalid_link_is_refused()`

### AT-48-06 – « Non publiée » refusé
```gherkin
Étant donné une annonce à relier
Quand le membre valide « Non publiée »
Alors elle est refusée (NO_REPUBLICATION_TO_VERIFY) et Chrome n'est pas ouvert
```
Test : `LinkRepublishedListingAcceptanceTest.not_published_is_refused_for_a_listing_to_link()`, `ListingRepublicationApiControllerTest.listing_to_link_cannot_be_validated_not_published()`

### AT-48-07 – Annonce qui n'est pas à relier
```gherkin
Étant donné une annonce non supprimée, ou une annonce disparue de Vinted sans suppression par l'outil
Quand le membre demande « Vérifier sur Vinted » ou valide « Publiée »
Alors elle est refusée (NO_REPUBLICATION_TO_VERIFY) et Chrome n'est pas ouvert
Et une annonce déjà republiée est refusée (NO_REPUBLICATION_TO_VERIFY) à la validation « Publiée », sa vérification la comparant comme en US-13
```
Test : `LinkRepublishedListingAcceptanceTest.listing_not_to_link_is_refused_without_opening_chrome()`

### AT-48-08 – Erreurs du navigateur
```gherkin
Étant donné une annonce à relier
Quand la vérification ou la validation échoue (navigateur occupé, indisponible, Vinted inaccessible, session déconnectée, page illisible)
Alors elle est refusée avec la raison, rien n'est enregistré dans les republications et le verrou est libéré
```
Test : `LinkRepublishedListingAcceptanceTest.browser_errors_record_nothing()`

### AT-48-09 – Fiche de l'annonce
```gherkin
Étant donné une annonce à relier
Quand le membre ouvre sa fiche
Alors la section Republication propose « Vérifier sur Vinted » et « Publiée » avec le champ du lien, sans « Non publiée »
Et une vérification sans résultat affiche « Aucune nouvelle annonce trouvée sur Vinted » ou « Plusieurs annonces correspondent sur Vinted », avec l'invitation à coller le lien
Et une liaison réussie affiche l'annonce republiée et la comparaison
```
Test : `ListingDetailPageTest.Republication` : `listing_to_link_offers_verification_and_published_link()`, `verification_without_result_asks_for_the_link()` (page servie par une fausse API), logique pure `listing-link.spec.ts` (Vitest)

### AT-48-10 – Page « Échecs »
```gherkin
Étant donné une annonce refusée à relier et une annonce en échec non supprimée
Quand le membre ouvre la page « Échecs »
Alors seule l'annonce à relier propose « Vérifier sur Vinted »
Et après une liaison réussie elle quitte la liste et le message le dit
Et après une vérification sans résultat elle reste, le message le dit avec un lien vers sa fiche
```
Test : `FailedListingsPageTest.listing_to_link_can_be_verified_from_the_failures_page()` (page servie par une fausse API)

### AT-48-11 – US-13 inchangée
```gherkin
Étant donné une annonce avec une republication en erreur
Quand le membre demande « Vérifier sur Vinted » sans trouver de candidate
Alors la republication reste en erreur, comme en US-13, sans newListingSearch
```
Test : tests existants de `VerifyListingRepublicationAcceptanceTest` et `ValidateListingRepublicationAcceptanceTest`, verts. Les refus « rien à vérifier » partent désormais d'une annonce dont la suppression n'est pas confirmée (`nothing_to_verify_is_refused_without_opening_chrome()`, `refused_and_failed_verifications_are_written()`, `ListingRepublicationApiControllerTest.nothing_to_verify_is_a_conflict()`) : supprimée sans republication, elle est désormais à relier. Fiche : après « Non publiée », « Vérifier sur Vinted » réapparaît (`ListingDetailPageTest.member_validates_not_published_and_can_republish()`).

### AT-48-12 – Liaison réelle
```gherkin
Étant donné Chaussure geox taille 22 (10112258455), refusée, dont la nouvelle annonce est en ligne
Quand le membre demande « Vérifier sur Vinted » depuis la page « Échecs »
Alors elle est reliée à la nouvelle annonce et quitte la page « Échecs »
```
Test : manuel (vrai compte Vinted, lecture seule, par le membre)

## Cas limites et erreurs
- Nouvelle annonce déjà reliée à une autre republication : écartée des candidates (vérification), refusée `NEW_LISTING_LINK_INVALID` (validation), comme US-13.
- Nouvelle annonce déjà vue par un scan : reliée sans doublon de ligne dans `listing`, comme AT-13-04.
- Dry-run de la republication actif : la vérification et la validation ne font que lire Vinted, elles restent possibles (comme US-13).
- Vérification sans résultat puis nouvelle vérification : nouvelle ligne de journal, toujours sans effet sur la page « Échecs ».
- Liaison puis « Republier manuellement » ou série : refusées (`ALREADY_REPUBLISHED`), l'annonce étant republiée.

## Hors périmètre
- Trouver qui a publié ces annonces (l'outil refuse toujours avant de cliquer sur « Publier ») : enquête à part.
- Suppression des doublons (US-47).
- Liaison en lot (toutes les annonces à relier d'un coup).
- Lire la vraie date de mise en ligne de la nouvelle annonce.

## Décisions
- Demandé par l'utilisateur (2026-11-19) : rendre « Vérifier sur Vinted » et la validation manuelle accessibles à une annonce refusée, pour forcer la liaison et éviter les doublons.
- Annonces concernées, décidé par l'utilisateur (2026-11-19) : toute annonce supprimée par l'outil (suppression confirmée) et pas encore republiée, aux étapes « En attente » ou « À republier », en échec ou non. Exclues : non supprimée, déjà republiée, disparue de Vinted, ou avec une republication en erreur (US-13 s'applique déjà).
- Vérification, décidé par l'utilisateur (2026-11-19) : une seule candidate (titre et prix de la sauvegarde) parmi les annonces affichées à l'ouverture du dressing → republication réussie, reliée et comparée (comme US-13). Aucune ou plusieurs → rien n'est enregistré, l'annonce reste dans son état ; le message le dit et invite à coller le lien dans « Publiée ». « Non publiée » n'est pas proposé (rien à déclarer).
- Dates, décidé par l'utilisateur (2026-11-19) : la republication reliée a pour date de tentative et de publication la date de la liaison (vérification ou « Publiée »), et pour sauvegarde la sauvegarde actuelle, comme la validation manuelle d'US-13.
- Boutons, décidé par l'utilisateur (2026-11-19) : sur la fiche (section Republication), « Vérifier sur Vinted » et le bloc « Publiée » avec le lien, pour toute annonce concernée ; sur la page « Échecs », « Vérifier sur Vinted » sur chaque annonce concernée, à côté de « Relancer » ou « Republier manuellement » ; rien trouvé → le message renvoie à la fiche pour coller le lien.
- Journal, décidé par l'utilisateur (2026-11-19) : actions `REPUBLICATION_VERIFICATION` et `REPUBLICATION_VALIDATION` ; reliée → `SUCCEEDED` ; aucune ou plusieurs candidates → `NOT_DONE` (`NEW_LISTING_NOT_FOUND`, `SEVERAL_NEW_LISTINGS`), ignorées par la page « Échecs » ; refus et erreurs comme US-13.
- Contrat et détails, laissés à Claude par l'utilisateur (« fait comme tu penses », 2026-11-19) : mêmes opérations qu'US-13 élargies (pas de nouvelle route), `newListingSearch` dans `ListingBackupView`, aucune nouvelle raison de refus, pas de changement de schéma de base.
- AT-48-07 corrigé à l'implémentation (2026-11-20, relevé par la revue) : une annonce déjà republiée garde la vérification d'US-13 (comparaison) ; seule la validation « Publiée » est refusée, conformément à « US-13 inchangée ».

## Questions ouvertes
- Aucune.

## Modèle de domaine
- Termes ajoutés au glossaire : `listing to link` | annonce à relier | annonce supprimée, ni republiée ni en erreur de republication, encore présente dans l'outil ; `new listing search` | recherche de la nouvelle annonce | issue d'une vérification d'annonce à relier restée sans résultat (`NOT_FOUND`, `SEVERAL`).
- `ListingRepublication` : nouvelle fabrique d'une republication réussie sans tentative préalable (liaison), datée de la liaison.
- `ActionRecord` : `NEW_LISTING_NOT_FOUND` et `SEVERAL_NEW_LISTINGS` rejoignent les raisons ignorées par la page « Échecs » (avec les raisons d'attente).
- Cas d'usage élargis : `VerifyListingRepublication`, `ValidateListingRepublication`.

## Tests unitaires et d'intégration attendus
- Domaine : règle « annonce à relier » (supprimée, sans republication en vigueur, présente) ; fabrique de la republication liée ; raisons ignorées par `FailedListing`.
- Application : `VerifyListingRepublication` et `ValidateListingRepublication` sur une annonce à relier (faux navigateur, faux dépôts) ; journal écrit.
- Persistance : `findLastOfEachListingExcept` ignore les deux nouvelles raisons (`@Tag("docker")`).
- Web : contrôleur (`newListingSearch`, `published: false` refusé) ; pages Angular (fiche, « Échecs ») avec fausse API ; logique pure en Vitest si besoin.
