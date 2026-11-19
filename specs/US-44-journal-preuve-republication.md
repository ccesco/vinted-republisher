# US-44 – Journal de la preuve d'une republication
**Statut** : implémentée
**Priorité** : V2
**Dépend de** : US-13 (vérifier la republication), US-20 (relancer), US-23 (journal), US-39 (annonces en échec)

## Contexte
Une republication cherche la nouvelle annonce dans le dressing 30 s après le clic sur « Publier » (`proof-delay`). Vinted met souvent plusieurs minutes à l'y montrer : la republication passe en erreur et le journal écrit « Republication : dans le doute ». La preuve arrive ensuite par la vérification sur Vinted (`VerifyListingRepublication`, bouton ou relance) ou par la validation manuelle du membre (`ValidateListingRepublication`), qui n'écrivent rien au journal.

Constat sur la base locale au 19/11/2026 : 21 republications publiées, une seule « réussie » au journal, 20 « dans le doute » à jamais. Le membre ne voit pas ses republications réussies, et ces annonces restent dans « Annonces en échec » (US-39), qui lit la dernière ligne de chaque annonce.

## Objectif
En tant que membre, je veux que le journal montre quand une republication en doute est finalement prouvée publiée (ou non), afin de voir après coup toutes mes republications réussies.

## Comportement attendu
1. Deux nouvelles actions journalisées, écrites à la réponse, avec les règles d'US-23 (une ligne par appel, même code que la réponse de l'API, journal en panne sans effet sur l'action, annonce jamais sauvegardée : rien d'écrit) :

| Action | Issues possibles |
|---|---|
| Vérification de la republication (`REPUBLICATION_VERIFICATION`) | publiée : erreur levée (même si la comparaison qui suit échoue ; la réponse reste alors cet échec) ou republication déjà publiée comparée (`SUCCEEDED`) ; reste en erreur, aucune ou plusieurs annonces correspondantes (`IN_DOUBT`) ; refusée (`REFUSED`, `NO_REPUBLICATION_TO_VERIFY`, `BROWSER_BUSY`) ; en échec côté navigateur ou Vinted (`FAILED`, code 502 : `BROWSER_UNAVAILABLE`, `VINTED_UNREACHABLE`, `SESSION_DISCONNECTED`, `LISTING_UNREADABLE`) ; échec inattendu (`FAILED`, `REPUBLICATION_FAILED`) |
| Validation manuelle de la republication (`REPUBLICATION_VALIDATION`) | « publiée » acceptée (`SUCCEEDED`) ; « non publiée » (`NOT_DONE`) ; refusée (`REFUSED`, `NO_REPUBLICATION_TO_VERIFY`, `NEW_LISTING_LINK_INVALID`, `NEW_LISTING_MISMATCH`, `BROWSER_BUSY`) ; en échec comme la vérification (`FAILED`, code 502 ou `REPUBLICATION_FAILED`) |

2. La vérification lancée par une relance (US-20) ou par la série « Tout lancer » passe par le même cas d'utilisation : elle est journalisée de la même façon.
3. Page du journal et fiche : libellés « Vérification de la republication » et « Validation de la republication » ; raisons en clair « aucune republication à vérifier », « lien de la nouvelle annonce invalide », « nouvelle annonce différente de la sauvegarde ». Une vérification qui reste en erreur est mise en évidence comme tout doute.
4. Annonces en échec (US-39), sans changement de leur règle : une vérification ou validation « publiée » devient la dernière ligne de l'annonce, qui sort de la liste ; « reste en erreur », « non publiée » et les échecs l'y laissent, avec cette cause.

## Contrat d'API
Contract first : `src/main/resources/static/openapi/vinted-republisher.yaml`.
- `JournaledAction` gagne `REPUBLICATION_VERIFICATION` et `REPUBLICATION_VALIDATION` (description mise à jour). Aucun autre changement : les réponses de vérification et de validation sont inchangées.

## Critères d'acceptation
- [x] Chaque issue du tableau écrit une ligne avec l'action, l'issue et la raison attendues.
- [x] Une vérification lancée par une relance est journalisée.
- [x] Un journal en panne ne change ni la vérification ni la validation, ni leur réponse.
- [x] La base accepte les deux nouvelles actions (contrainte du journal).
- [x] Une annonce dont la dernière ligne est une vérification ou validation « publiée » ne figure plus dans les annonces en échec.
- [x] Le journal et la fiche affichent les deux actions et les trois nouvelles raisons en clair.

## Tests d'acceptation

### AT-44-01 – Vérification qui lève le doute
```gherkin
Étant donné l'annonce 101 dont la republication est en erreur, journal « Republication : dans le doute »
Et une seule nouvelle annonce du dressing correspond à sa sauvegarde
Quand le membre clique « Vérifier sur Vinted »
Alors le journal contient « Vérification de la republication : réussie » pour l'annonce 101
```
Test : `VerifyListingRepublicationAcceptanceTest.verification_that_settles_the_error_is_written()`

### AT-44-02 – Vérification qui laisse le doute, puis comparaison d'une republication réussie
```gherkin
Étant donné l'annonce 101 dont la republication est en erreur et aucune nouvelle annonce correspondante
Quand le membre la vérifie
Alors le journal contient « Vérification de la republication : dans le doute »
Étant donné l'annonce 102 dont la republication est réussie
Quand le membre la vérifie
Alors le journal contient « Vérification de la republication : réussie » pour l'annonce 102
```
Test : `VerifyListingRepublicationAcceptanceTest.verification_still_in_error_and_comparison_are_written()`

### AT-44-03 – Vérification refusée ou en échec
```gherkin
Étant donné l'annonce 101 sans republication à vérifier
Quand le membre la vérifie
Alors le journal contient « Vérification de la republication : refusée », raison « aucune republication à vérifier »
Étant donné l'annonce 102 dont la republication est en erreur et le navigateur indisponible
Quand le membre la vérifie
Alors le journal contient « Vérification de la republication : en échec », raison « navigateur indisponible »
```
Test : `VerifyListingRepublicationAcceptanceTest.refused_and_failed_verifications_are_written()`

### AT-44-04 – Validation manuelle publiée ou non publiée
```gherkin
Étant donné les annonces 101 et 102 dont la republication est en erreur
Quand le membre valide 101 « publiée » avec le lien d'une nouvelle annonce de même titre et prix
Et valide 102 « non publiée »
Alors le journal contient « Validation de la republication : réussie » pour 101 et « non faite » pour 102
```
Test : `ValidateListingRepublicationAcceptanceTest.published_and_not_published_validations_are_written()`

### AT-44-05 – Validation refusée
```gherkin
Étant donné l'annonce 101 dont la republication est en erreur
Quand le membre la valide « publiée » avec le lien d'une annonce d'un autre titre
Alors le journal contient « Validation de la republication : refusée », raison « nouvelle annonce différente de la sauvegarde »
```
Test : `ValidateListingRepublicationAcceptanceTest.refused_validation_is_written()`

### AT-44-06 – Relance d'une vérification
```gherkin
Étant donné l'annonce 101 à l'étape « republication à vérifier »
Quand le membre la relance et la vérification la trouve publiée
Alors le journal contient « Vérification de la republication : réussie » pour l'annonce 101
```
Test : `RelaunchListingAcceptanceTest.relaunching_a_republication_to_check_verifies_it()`

### AT-44-07 – Sortie des annonces en échec
```gherkin
Étant donné l'annonce 101 dont la dernière ligne est « Republication : dans le doute »
Quand sa vérification la trouve publiée
Alors l'annonce 101 ne figure plus dans les annonces en échec
```
Test : `ShowFailedListingsAcceptanceTest.verified_republication_leaves_the_failed_listings()`

### AT-44-08 – Journal en panne
```gherkin
Étant donné un journal impossible à écrire
Quand le membre vérifie une republication en erreur qui se trouve publiée
Alors la réponse et la republication sont celles d'un journal qui marche
Et l'échec du journal est écrit dans les logs
```
Test : `VerifyListingRepublicationAcceptanceTest.journal_failure_changes_nothing()`, `JournalWriterTest.journal_down_changes_nothing_and_is_logged()` (logs)

### AT-44-09 – Affichage
```gherkin
Étant donné les lignes « Vérification de la republication : dans le doute » et « Validation de la republication : refusée », raison NEW_LISTING_LINK_INVALID
Quand le membre ouvre le journal
Alors il lit ces libellés et « lien de la nouvelle annonce invalide », la première ligne et la seconde mises en évidence
```
Test : `journal.spec.ts` (Vitest, `journalLineOf` et `causeOf`)

### AT-44-10 – Contrainte de la base
```gherkin
Étant donné la base PostgreSQL migrée
Quand une ligne de chaque nouvelle action est écrite
Alors elle est relue telle quelle
```
Test : `JpaActionJournalTest.every_journaled_action_can_be_written()` (`@Tag("docker")`)

### AT-44-11 – Preuve enregistrée, comparaison en échec
```gherkin
Étant donné l'annonce 101 dont la republication est en erreur et la nouvelle annonce retrouvée dans le dressing
Et Vinted inaccessible au moment de comparer la nouvelle annonce
Quand le membre la vérifie
Alors la réponse est « Vinted inaccessible » et la republication est enregistrée réussie
Et le journal contient « Vérification de la republication : réussie »
Étant donné une republication déjà réussie et Vinted inaccessible à la comparaison
Quand le membre la vérifie
Alors le journal contient « Vérification de la republication : en échec », raison « Vinted inaccessible »
```
Test : `VerifyListingRepublicationAcceptanceTest.proof_recorded_before_a_failed_comparison_is_written_succeeded()`

## Cas limites et erreurs
- Annonce jamais sauvegardée (`ListingNotBackedUp`, 404) : rien d'écrit, comme les autres actions.
- Échec d'écriture du journal après une vérification réussie : la republication reste publiée, l'échec est dans les logs.

## Hors périmètre
- Rattrapage des lignes « dans le doute » déjà écrites : elles restent ; la prochaine vérification ajoute sa ligne.
- Délai de preuve (`proof-delay`) inchangé.
- Aucun filtre ajouté à la page du journal.

## Décisions
- Deux actions séparées plutôt qu'une ligne « Republication : réussie » tardive : le journal dit qui a prouvé (outil ou membre) et à quel moment, sur le modèle de `DELETION_RECHECK` (choix de l'utilisateur, 19/11/2026).
- La vérification d'une republication déjà réussie (comparaison seule) écrit « réussie » : le journal garde trace de toute ouverture de Vinted.
- « Non publiée » de la validation manuelle écrit `NOT_DONE` : l'annonce reste dans les annonces en échec, cause « non faite », pour être relancée ; sa cause s'affiche « republication non faite » (une revérification non faite reste « suppression non faite »).
- Pas de rattrapage des lignes « dans le doute » déjà écrites.
- Une vérification qui enregistre la preuve écrit « réussie » même si la comparaison qui suit échoue : l'annonce sort des annonces en échec ; la réponse de l'API reste l'échec de la comparaison (choix de l'utilisateur, 19/11/2026, après la revue). Le libellé « republication non faite » est validé par l'utilisateur le même jour.

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `JournaledAction` gagne `REPUBLICATION_VERIFICATION` (vérification de la republication) et `REPUBLICATION_VALIDATION` (validation manuelle de la republication) ; glossaire mis à jour.
- `VerifyListingRepublication` et `ValidateListingRepublication` écrivent par `JournalWriter.journaled`, comme `RepublishListing`.
- `JournalWriter` : refus `NoRepublicationToVerify` → `NO_REPUBLICATION_TO_VERIFY`, `NewListingLinkInvalid` → `NEW_LISTING_LINK_INVALID`, `NewListingMismatch` → `NEW_LISTING_MISMATCH` ; échec `ListingUnreadable` → `LISTING_UNREADABLE`.

## Tests unitaires et d'intégration attendus
- `application/journal` : nouveaux codes de refus et d'échec couverts par les tests d'acceptation de la vérification et de la validation.
- `application/republication` : issues de la vérification et de la validation (tests d'acceptation ci-dessus).
- `adapter/out/persistence` : changeset Liquibase `017` qui ajoute les deux actions à la contrainte `action` du journal ; `JpaActionJournalTest`.
- `frontend` : `journal.spec.ts` (libellés et raisons).
