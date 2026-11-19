# US-45 – Échecs triés par conduite et republication manuelle pré-remplie
**Statut** : implémentée
**Priorité** : V2
**Dépend de** : US-39 (annonces en échec), US-20 (relance), US-12 (republication), US-13 (vérification et validation manuelle), US-17 (gestion des échecs), US-27 (limites de cadence)

## Contexte
La page « Échecs » (US-39) mêle en un seul tableau des annonces qui ne demandent pas la même action :
- un **doute** (`IN_DOUBT`) : « Relancer » lance la vérification sur Vinted (étape `REPUBLICATION_TO_CHECK` ou `DELETION_TO_CHECK`), c'est la bonne action ;
- un **refus** (`REFUSED`) : l'annonce reste à la même étape, « Relancer » refait la même action automatique, qui sera refusée pour la même raison (ex. `FieldNotFillable` : « Taille impossible à remplir (identifiant 588) ») ;
- un **échec** (`FAILED`) ou une revérification **non faite** (`NOT_DONE`) : régler le problème (session, Vinted), puis « Relancer » suffit.

Pour une republication refusée, le membre n'a aujourd'hui aucun moyen de passer outre depuis l'outil : il doit recréer l'annonce à la main sur Vinted, sans l'outil, et la republication n'est alors ni reliée à l'ancienne annonce ni comptée.

## Objectif
En tant que membre, je veux voir mes annonces en échec groupées selon ce que je dois faire, et pouvoir republier moi-même une annonce refusée à partir d'un formulaire pré-rempli par l'outil, afin de débloquer les annonces que l'automatisme ne sait pas republier sans perdre le lien avec l'ancienne annonce.

## Comportement attendu
1. **Groupes de la page « Échecs »** : la page garde la même définition de l'annonce en échec (US-39) et les répartit en trois sections, chacune triée de la plus récente à la plus ancienne, avec son nombre d'annonces ; les trois sections sont toujours affichées, une section vide indique « Aucune annonce. » :
   - **« Dans le doute »** (`IN_DOUBT`) : « Relancer » = vérifier sur Vinted (inchangé).
   - **« Refusées »** (`REFUSED`, hors refus d'attente) : actions selon le comportement 2.
   - **« Autres échecs »** (`FAILED`, `NOT_DONE`) : « Relancer » comme aujourd'hui.

   Le groupe se déduit de l'issue de la dernière ligne du journal, côté domaine (`FailureKind`), et voyage dans l'API.
2. **Bouton « Republier manuellement »** :
   - page « Échecs », section « Refusées » : pour une annonce à l'étape `TO_REPUBLISH` ; « Relancer » reste proposé (le refus a pu être levé, ex. option ajoutée par Vinted). Les autres refus (suppression, validation) gardent les actions d'US-39 ;
   - fiche de l'annonce (US-18b) : pour toute annonce à l'étape `TO_REPUBLISH`, refusée ou non, à côté de « Republier ».
3. **Republication manuelle** (`POST /api/listings/{listingId}/republication/manual`) :
   1. mêmes contrôles qu'une republication (US-12) : étape `TO_REPUBLISH`, date atteinte, sauvegarde validée, ancienne annonce supprimée, verrou du navigateur, limite de cadence (US-27), la préparation comptant comme une republication ;
   2. l'outil ouvre le formulaire « Vendre » dans un **nouvel onglet** du Chrome piloté, qu'il laisse ouvert et ne réutilise jamais (ses autres actions ouvrent chacune leur onglet, US-03), et le remplit depuis la sauvegarde (photos, champs) comme une republication ; un champ impossible à remplir est **laissé vide**, au lieu d'arrêter le remplissage, et l'outil remplit les champs suivants ;
   3. il **ne clique jamais sur « Publier »** : il laisse l'onglet ouvert, rend la main et enregistre une republication **en erreur** (`ListingRepublication`, issue `FAILED`, comme un clic sur « Publier » sans preuve, US-12) : l'annonce passe à l'étape `REPUBLICATION_TO_CHECK`, sans nouvel état ni changement de schéma ;
   4. la réponse liste les champs laissés vides, que le membre complète dans Chrome avant de publier lui-même.
4. **Après la publication par le membre** : la fiche propose la validation manuelle existante (US-13) : « publiée » avec le lien de la nouvelle annonce, contrôlée (titre et prix de la sauvegarde) puis comparée et reliée à l'ancienne ; ou « non publiée », et l'annonce revient à `TO_REPUBLISH`. « Vérifier sur Vinted », « Relancer » et « Tout lancer » la traitent comme toute republication à vérifier (US-13, US-20) : une vérification lancée avant que le membre publie ne trouve rien et ne conclut jamais « non publiée » d'elle-même.
5. **Journal** (US-23) : une ligne action `REPUBLICATION`, issue `IN_DOUBT`, raison `MANUAL_REPUBLICATION_PREPARED`, libellé « republication manuelle : publiez sur Vinted puis validez ». L'annonce reste donc sur la page « Échecs », section « Dans le doute », jusqu'à la vérification ou la validation manuelle. Les champs laissés vides sont dans la réponse, pas dans le journal. Un refus ou un échec de la préparation est journalisé comme ceux d'une republication.
6. **Vérification et validation d'une republication journalisées** (US-13, dette d'US-23) : nouvelle action `REPUBLICATION_VERIFICATION` (« Vérification de la republication »), pour toute republication, manuelle ou non :
   - vérification : retrouvée ou déjà publiée → réussie (`SUCCEEDED`) ; toujours en erreur → doute (`IN_DOUBT`) ;
   - validation « publiée » → réussie ; « non publiée » → non faite (`NOT_DONE`, cause « republication non faite » : l'annonce passe dans « Autres échecs », « Relancer » republie) ;
   - refus (`NO_REPUBLICATION_TO_VERIFY`, `NEW_LISTING_LINK_INVALID`, `NEW_LISTING_MISMATCH`, `BROWSER_BUSY`) et échecs (dont `LISTING_UNREADABLE`) avec leur raison, comme les autres actions.

   Une annonce dont la republication est retrouvée ou validée « publiée » quitte ainsi la page « Échecs ». Changeset `017-journal-republication-verification` (contrainte `CHECK` de la colonne `action`).
7. **Dry-run** (US-10) : tant que le dry-run de republication est actif, la republication manuelle est refusée (`MANUAL_REPUBLICATION_IN_DRY_RUN`) sans ouvrir Chrome ; le bouton est désactivé, avec « Indisponible en dry-run ».

## Contrat d'API
Dans `vinted-republisher.yaml`, commité avec le code (`kind` requis ne compile qu'avec son implémentation) :

| Requête | Opération | Réponse |
| --- | --- | --- |
| `POST /api/listings/{listingId}/republication/manual` | `prepareManualRepublication` (tag `listing-republication`) | Corps `RepublicationRequest` {`savedAt`}. 200 `ManualRepublicationReport` {`listing` : `ListingBackupView` à jour, `unfilledFields` : liste de `UnfilledField` {`field`, `optionId`}} ; 409 `RepublicationRefusal` (refus de la republication, et `MANUAL_REPUBLICATION_IN_DRY_RUN`) ; 422 photos ; 500 ; 502 (rien n'est publié ni enregistré) ; 403 si la demande vient d'un autre site |
| `GET /api/failed-listings` | `showFailedListings` | `FailedListing` gagne `kind` (`FailureKind` : `IN_DOUBT`, `REFUSED`, `OTHER`), requis |

- `RepublicationRefusal.reason` gagne `MANUAL_REPUBLICATION_IN_DRY_RUN`.
- `JournaledAction` gagne `REPUBLICATION_VERIFICATION`.
- La page « Échecs » lit la sauvegarde (`GET /api/listings/{listingId}/backup`) pour envoyer le `savedAt` vu : `FailedListing` ne le porte pas.
- La fiche sait si le dry-run est actif par `ListingBackupView.republicationDryRun` (existant).

## Critères d'acceptation
- [x] La page « Échecs » sépare « Dans le doute », « Refusées » et « Autres échecs », chacune triée de la plus récente à la plus ancienne.
- [x] Une annonce à republier, refusée, propose « Republier manuellement » en plus de « Relancer ».
- [x] La republication manuelle passe par les mêmes contrôles que la republication.
- [x] Elle remplit le formulaire depuis la sauvegarde, laisse vides les champs impossibles à remplir, les liste, et ne publie jamais.
- [x] Le formulaire est ouvert dans un onglet que l'outil laisse ouvert et ne réutilise pas.
- [x] La préparation enregistre une republication en erreur et une ligne de journal en doute (`MANUAL_REPUBLICATION_PREPARED`) ; l'annonce est « Republication à vérifier » et dans « Dans le doute ».
- [x] Une republication manuelle se termine par la vérification ou la validation manuelle d'US-13 (« publiée » avec lien, ou « non publiée »).
- [x] Elle compte dans la limite de cadence et est refusée en dry-run.
- [x] La vérification et la validation d'une republication sont journalisées ; retrouvée ou validée « publiée », l'annonce quitte la page « Échecs ».
- [ ] AT-45-07 (vrai compte) : non joué, l'utilisateur a décidé de s'en passer (2026-11-19).

Vérifié (2026-11-19) : `mvn test "-Pbrowser,docker"` vert (1337 tests), `npm test` vert (41 tests) ; AT-45-07 (manuel, vrai compte) non joué sur décision de l'utilisateur.

## Tests d'acceptation
### AT-45-01 – Groupes de la page « Échecs »
```gherkin
Étant donné une annonce dont la republication est en doute
Et une annonce dont la republication a été refusée (taille impossible à remplir)
Et une annonce dont la suppression a échoué, session non connectée
Et une annonce dont la revérification a conclu « non faite »
Quand le membre demande les annonces en échec
Alors la première est « dans le doute », la deuxième « refusée », les deux autres « autres échecs »
```
Test : `ShowFailedListingsAcceptanceTest.groups_failures_by_kind()`

### AT-45-02 – Page « Échecs » en trois sections
```gherkin
Étant donné une annonce « Pull bleu » dont la republication est en doute
Et une annonce « Robe » à republier, dont la republication a été refusée (taille impossible à remplir)
Quand le membre ouvre la page « Échecs »
Alors « Pull bleu » est dans « Dans le doute » avec « Relancer »
Et « Robe » est dans « Refusées » avec « Relancer » et « Republier manuellement »
Et la section « Autres échecs » est affichée avec « Aucune annonce. »
```
Test : `FailedListingsPageTest.groups_failures_and_offers_manual_republication()`

### AT-45-03 – Formulaire pré-rempli, jamais publié
```gherkin
Étant donné une annonce à republier dont la taille (identifiant 588) n'existe plus sur Vinted
Quand le membre la republie manuellement
Alors le formulaire « Vendre » est rempli depuis la sauvegarde dans un nouvel onglet, la taille laissée vide
Et « Publier » n'est jamais cliqué, l'onglet reste ouvert
Et la réponse liste la taille (option 588) parmi les champs à compléter
Et une republication en erreur est enregistrée, l'annonce est « Republication à vérifier »
Et le journal a une ligne « republication » en doute « republication manuelle : publiez sur Vinted puis validez »
Et l'annonce est dans la section « Dans le doute » de la page « Échecs »
Et la fiche propose de valider la republication « publiée » ou « non publiée »
```
Test : `PrepareManualRepublicationAcceptanceTest.fills_the_form_leaves_unfillable_fields_empty_and_never_publishes()`

### AT-45-04 – Mêmes contrôles que la republication
```gherkin
Étant donné une annonce dont la date de republication n'est pas atteinte
Ou dont l'ancienne annonce n'est pas supprimée
Ou alors que le navigateur est occupé
Quand le membre la republie manuellement
Alors c'est refusé avec la raison de la republication et Chrome n'ouvre aucun formulaire
```
Test : `PrepareManualRepublicationAcceptanceTest.refused_like_a_republication()`, `refused_when_the_old_listing_is_not_deleted()`, `refused_while_the_browser_is_busy()`

### AT-45-04b – Cadence et dry-run
```gherkin
Étant donné la limite de cadence atteinte
Quand le membre republie manuellement une annonce à republier
Alors c'est refusé (CADENCE_LIMIT_REACHED) et Chrome n'ouvre aucun formulaire
Étant donné le dry-run de republication actif
Quand le membre republie manuellement une annonce à republier
Alors c'est refusé (MANUAL_REPUBLICATION_IN_DRY_RUN), rien n'est enregistré
Et sur la fiche le bouton « Republier manuellement » est désactivé, « Indisponible en dry-run »
```
Test : `PrepareManualRepublicationAcceptanceTest.counts_in_cadence()`, `refused_in_dry_run()`, `ListingDetailPageTest.Republication.manual_republication_unavailable_in_dry_run()`

### AT-45-04c – Fiche
```gherkin
Étant donné une annonce « À republier », jamais refusée
Quand le membre ouvre sa fiche
Alors il voit « Republier » et « Republier manuellement »
Quand il republie manuellement et que la taille n'a pas pu être remplie
Alors la fiche affiche « Formulaire rempli dans Chrome, à compléter : Taille (588). Publiez puis validez ici. »
Et la fiche propose la validation manuelle
```
Test : `ListingDetailPageTest.Republication.member_prepares_a_manual_republication()`

### AT-45-05 – Validation après publication par le membre
```gherkin
Étant donné une republication manuelle préparée
Quand le membre publie sur Vinted puis valide « publiée » avec le lien de la nouvelle annonce
Alors la nouvelle annonce est contrôlée, comparée et reliée à l'ancienne
Et l'annonce quitte la page « Échecs »
```
Test : `ValidateListingRepublicationAcceptanceTest.validates_a_manual_republication()`, `validations_are_written()`, `VerifyListingRepublicationAcceptanceTest.verifications_are_written()`

### AT-45-06 – Contrat
```gherkin
Quand le membre demande la republication manuelle d'une annonce
Alors l'API répond selon le contrat (200, 409, 502, 500)
Et GET /api/failed-listings donne le groupe de chaque annonce
```
Test : `ListingRepublicationApiControllerTest`, `FailedListingsApiControllerTest`

### AT-45-07 – Vrai compte
```gherkin
Étant donné une annonce réelle refusée pour un champ impossible à remplir
Quand le membre la republie manuellement, complète le champ et publie
Puis la valide « publiée » avec son lien
Alors elle est reliée à l'ancienne annonce
```
Test : manuel (vrai compte Vinted, publication faite par le membre).

## Cas limites et erreurs
- Le membre ferme l'onglet ou ne publie jamais : l'annonce reste « Republication à vérifier » ; « non publiée » la ramène à « À republier ».
- Deuxième republication manuelle alors qu'une est à vérifier : refusée, l'étape n'est plus `TO_REPUBLISH`.
- Page du formulaire illisible, session non connectée, Vinted inaccessible : échec comme la republication (`FORM_UNREADABLE`, `SESSION_DISCONNECTED`…), onglet refermé, rien d'enregistré (`PrepareManualRepublicationAcceptanceTest.failure_of_the_form_records_nothing()`).
- Catégorie introuvable : ses champs dépendants sont listés à leur tour, chacun une fois ; couleurs ou matières peuvent rester en partie cochées.
- Le membre modifie le titre ou le prix avant de publier : la validation « publiée » est refusée (`NEW_LISTING_MISMATCH`), comme en US-13.
- Titres et noms de champs venant de Vinted : jamais d'`innerHTML`.

## Hors périmètre
- Détection automatique de l'annonce publiée par le membre (il colle son lien).
- Correction de la sauvegarde depuis l'outil (choisir une nouvelle taille et la garder).
- Republication manuelle depuis la liste des annonces (US-18).
- Suppression manuelle pré-remplie.

## Décisions
- Demandé par l'utilisateur (2026-11-19) : la page « Échecs » sépare les annonces dans le doute des annonces refusées, les actions n'étant pas les mêmes ; une annonce refusée peut être republiée à la main à partir d'un formulaire pré-rempli.
- Groupes, décidé par l'utilisateur (2026-11-19) : trois groupes ; `FAILED` et `NOT_DONE` ensemble dans « Autres échecs », leur conduite étant la même (régler le problème, puis « Relancer »).
- Sections vides, décidé par l'utilisateur (2026-11-19) : les trois sections restent affichées, avec « Aucune annonce. » quand elles sont vides, y compris quand il n'y a aucune annonce en échec (le message « Aucune annonce en échec. » d'US-39 disparaît).
- Emplacement du bouton, décidé par l'utilisateur (2026-11-19) : page « Échecs » pour une annonce refusée à republier, fiche pour toute annonce à republier ; pas dans la liste des annonces.
- Champ impossible à remplir, décidé par l'utilisateur (2026-11-19) : laissé vide, le remplissage continue, la réponse liste les champs à compléter.
- État après pré-remplissage, décidé par l'utilisateur (2026-11-19) : republication enregistrée « en erreur » ; l'annonce passe « Republication à vérifier » et se termine par la vérification ou la validation manuelle d'US-13. Ni nouvel état ni changeset.
- Journal, décidé par l'utilisateur (2026-11-19) : la préparation est un doute (`REPUBLICATION`, `IN_DOUBT`, `MANUAL_REPUBLICATION_PREPARED`), pour que l'annonce reste dans « Dans le doute » comme rappel jusqu'à la validation.
- Cadence, décidé par l'utilisateur (2026-11-19) : la republication manuelle compte comme une republication (US-27).
- Dry-run, décidé par l'utilisateur (2026-11-19) : republication manuelle refusée en dry-run (`MANUAL_REPUBLICATION_IN_DRY_RUN`, nouvelle raison de `RepublicationRefusal`).
- Titre et prix, décidé par l'utilisateur (2026-11-19) : la validation « publiée » garde son contrôle (titre et prix de la sauvegarde), qui prouve que le lien est bien cette annonce ; les modifier reste hors périmètre.
- Onglet, décidé par l'utilisateur (2026-11-19) : formulaire dans un onglet dédié, laissé ouvert, jamais réutilisé ; aucun verrou du navigateur après la préparation, les autres actions et « Tout lancer » continuent dans leurs propres onglets.
- Proposé par Claude (2026-11-19) : `ListingRepublication.attemptedAt` d'une republication manuelle = date de la préparation ; réponse `ManualRepublicationReport` avec l'aperçu à jour, pour que la fiche montre la nouvelle étape sans relecture.
- Journal de la vérification d'une republication, décidé par l'utilisateur (2026-11-19, relevé pendant l'implémentation : sans lui, une annonce dans le doute restait sur la page « Échecs » après une vérification réussie ou une validation « publiée », ce qui contredisait AT-45-05) : vérification et validation journalisées sous `REPUBLICATION_VERIFICATION`, changeset `017`.
- Proposé par Claude pendant l'implémentation (2026-11-19) : validation « non publiée » journalisée `NOT_DONE`, comme une revérification de suppression non faite ; page « Échecs » qui lit la sauvegarde pour le `savedAt` ; message de refus « Republication manuelle refusée : <raison> », d'échec « Échec de la republication manuelle : voir la fiche ».
- Annonce republiée, décidé par l'utilisateur (2026-11-19, relevé en revue : republication retrouvée puis comparaison en échec) : une annonce « Republiée » n'est jamais en échec, comme une annonce « Absente de Vinted » ; la comparaison se relance depuis la fiche.
- Refus en dry-run, décidé par l'utilisateur (2026-11-19, relevé en revue) : `MANUAL_REPUBLICATION_IN_DRY_RUN` est un refus d'attente (rien n'est fait sur Vinted) : la page « Échecs » l'ignore et garde la cause d'origine.
- Corrigé après revue (2026-11-19) : l'onglet est refermé si la préparation échoue (aucun formulaire laissé sans republication enregistrée) ; le dry-run est vérifié après la lecture de la sauvegarde (une annonce jamais sauvegardée reste un 404 non journalisé) ; un champ n'est listé qu'une fois ; les détails jamais observés viennent après les champs du formulaire ; `LISTING_UNREADABLE` n'est la raison d'un échec que pour la vérification d'une republication.
- Constat (2026-11-19) : « Relancer » déduit l'action de l'étape (US-20) ; un doute lance déjà la vérification, un refus refait la même action automatique.

## Questions ouvertes
Aucune.

## Modèle de domaine
- `FailureKind` (type d'échec) : nouveau terme ; `IN_DOUBT`, `REFUSED`, `OTHER`, déduit de l'issue de la ligne d'échec.
- `PrepareManualRepublication` (cas d'usage) : contrôles de `RepublishListing`, remplissage sans publication.
- `ListingPublisher` (port) : nouvelle opération de remplissage tolérant, dans un onglet laissé ouvert, qui rend les champs laissés vides, sans clic sur « Publier ».
- `UnfilledField` (champ laissé vide) : `ListingFormField` et identifiant d'option éventuel.
- `ManualRepublicationReport` : aperçu à jour et champs laissés vides.
- `ListingRepublication` existant, issue `FAILED` ; raison de journal `MANUAL_REPUBLICATION_PREPARED` ; raison de refus `MANUAL_REPUBLICATION_IN_DRY_RUN`.
- Glossaire (`docs/glossary.md`) : `FailureKind` (type d'échec), `PrepareManualRepublication` (republication manuelle), `UnfilledField` (champ laissé vide), `ManualRepublicationReport`, `JournaledAction.REPUBLICATION_VERIFICATION`.
- Schéma : seule la contrainte `CHECK` du journal change (changeset `017`).

## Tests unitaires et d'intégration attendus
- Domaine : `FailureKind` de chaque issue ; remplissage tolérant (champ impossible → laissé vide, listé).
- Application : `PrepareManualRepublication` (contrôles, jamais de publication, journal, cadence).
- Adaptateur navigateur : remplissage sans clic sur « Publier », champs laissés vides (`@Tag("browser")`, page locale) : `PlaywrightListingPublisherTest.Manual_republication`.
- Adaptateur persistance : `JpaActionJournalTest.every_journaled_action_can_be_written()` (`@Tag("docker")`).
- Adaptateur web : contrôleurs (contrat).
- Interface : Vitest pour le groupement ; `FailedListingsPageTest` et `ListingDetailPageTest` (Playwright, API simulée).
