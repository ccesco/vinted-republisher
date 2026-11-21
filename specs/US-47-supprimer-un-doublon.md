# US-47 – Repérer et supprimer un doublon
**Statut** : validée
**Priorité** : V2
**Dépend de** : US-04 (scan), US-09 (suppression sur Vinted), US-10 (dry-run), US-23 (journal), US-27 (cadence), US-18b (fiche de l'annonce)

## Contexte
Une annonce republiée deux fois par erreur donne deux annonces identiques en vente sur Vinted, avec deux identifiants différents (cas réel signalé par le membre, 2026-11-19). L'outil les connaît toutes les deux par le scan.

« Supprimer de Vinted » (US-09) ne convient pas au doublon : il exige une sauvegarde validée de l'annonce supprimée, parce qu'il prépare une republication. Ici on ne republie rien : l'article reste en vente par l'autre annonce, la **jumelle**. Le garde-fou devient donc « une autre annonce identique reste en vente sur Vinted ». Une fois supprimé de Vinted, le doublon n'a plus d'intérêt dans l'outil : il est effacé de la base.

## Objectif
En tant que membre, je veux voir les annonces en double dans mon dressing et supprimer un doublon sur Vinted puis de l'outil, afin de ne garder qu'une annonce par article sans risquer de supprimer la dernière.

## Comportement attendu
1. **Doublons** : deux annonces ou plus **présentes au dernier scan** avec exactement le même titre et le même prix (montant et devise) forment un **groupe de doublons**. Une annonce absente n'entre dans aucun groupe (sans quoi chaque ancienne annonce republiée ferait groupe avec sa nouvelle).
2. **Annonce supprimable comme doublon** : elle appartient à un groupe (donc une jumelle présente existe) et n'a **aucun historique à garder** : ni ancienne ni nouvelle annonce d'une republication (quelle qu'en soit l'issue), ni suppression (US-09), ni vente. Sa sauvegarde éventuelle n'empêche rien : elle sera effacée.
3. **Page « Doublons »** (nouvelle entrée du menu, après « Échecs ») : les groupes, du plus récent (annonce vue en premier le plus récemment) au plus ancien ; pour chaque groupe, titre et prix ; pour chaque annonce, identifiant (lien vers la fiche), lien Vinted, date de première détection, sauvegardée ou non, et le bouton **« Supprimer ce doublon »** si elle est supprimable, sinon « Historique à garder ». Aucun groupe → « Aucun doublon. ». La page lit la base, sans ouvrir Vinted.
4. **Fiche de l'annonce** : une annonce supprimable montre aussi « Supprimer ce doublon », avec la mention « Même titre et même prix que l'annonce N » (lien vers la jumelle).
5. **Confirmation** dans l'outil : « Supprimer de Vinted l'annonce N et l'effacer de l'outil (sauvegarde, photos, relevés, tags). L'annonce M reste en vente. Irréversible. » ; second clic « Supprimer le doublon ». Mode dry-run → la confirmation le dit (« dry-run : rien ne sera supprimé »).
6. **Contrôles hors navigateur** : l'annonce est connue, supprimable (2). Sinon refus, Chrome n'est pas ouvert.
7. **Verrou et cadence** : action navigateur `DELETING` sur l'annonce (comme US-09 ; une autre action en cours → refus). En réel seulement, la cadence des suppressions (US-27) est réservée sous le verrou, et tenue jusqu'à l'écriture du journal. Une suppression de doublon compte ensuite dans cette cadence par sa ligne du journal (réussie ou dans le doute), comme une suppression d'US-09 par sa tentative enregistrée. **Depuis US-49**, plus de cadence : clic du membre, la suppression d'un doublon n'est ni limitée ni comptée dans la cadence de la série.
8. **Jumelle relue sur Vinted** : l'outil ouvre la page des jumelles, une à une par identifiant croissant, jusqu'à en trouver une **active, au titre et au prix du doublon** (tels qu'enregistrés au dernier scan). Aucune → refus « aucune jumelle confirmée sur Vinted », rien n'est cliqué.
9. **Doublon relu** : l'outil ouvre la page du doublon.
   - Vinted ne la montre plus → le doublon est **effacé** (10) sans aucun clic : issue « déjà absente de Vinted, effacée ».
   - Sinon il le lit comme US-09 : non actif, titre ou prix différent de ceux enregistrés → refus avec les écarts, aucun clic.
   - **Dry-run** (US-10, actif par défaut) : arrêt ici, aucun clic, rien n'est effacé : « dry-run : rien supprimé ».
   - Sinon clic sur le bouton de suppression, puis sur la confirmation de Vinted, puis l'outil rouvre la page : Vinted ne la montre plus → **effacé** (10), issue « supprimée de Vinted et effacée » ; encore montrée, erreur après le clic de confirmation, ou effacement en échec après la suppression → issue « suppression non confirmée, vérifiez sur Vinted » : **rien n'est enregistré** ni effacé, une nouvelle demande refait tout (une suppression déjà faite est alors vue en 9 et effacée sans clic).
10. **Effacement** : en une transaction, l'annonce et tout ce qui la concerne : relevés de vues et favoris, historique de prix, tags, sauvegarde (contenu, photos enregistrées, validation, vérifications). Puis les fichiers photos de l'annonce. Le **journal des actions garde ses lignes** (jamais supprimées, US-23). Au scan suivant, le doublon n'est plus sur Vinted : il ne revient pas.
11. **Fin** : l'onglet est fermé, la cadence et le verrou libérés dans tous les cas. Sur la fiche, après un effacement, l'outil ouvre la page « Doublons ». La série automatique ne supprime jamais de doublon.
12. **Journal** (US-23) : une ligne par demande, action `DUPLICATE_DELETION` : `SUCCEEDED` (supprimée, ou déjà absente, puis effacée), `DRY_RUN`, `IN_DOUBT` (non confirmée), `REFUSED`, `FAILED`. La page « Échecs » ignore ces lignes : le doublon reste visible sur la page « Doublons », où le membre relance.

## Contrat d'API
Contract first : `src/main/resources/static/openapi/vinted-republisher.yaml`.

| Méthode et chemin | operationId | Réponse |
| --- | --- | --- |
| `GET /api/duplicates` | `showDuplicates` (tag `duplicates`) | 200 `DuplicateGroups` {`groups` : liste de `DuplicateGroup` {`title`, `price` (`Price`), `listings` : liste de `DuplicateListing` {`listingId`, `url`, `firstSeenAt`, `backedUp` (booléen), `deletable` (booléen)}}} |
| `POST /api/listings/{listingId}/duplicate-deletion` | `deleteDuplicate` (tag `duplicates`), sans corps | 200 `DuplicateDeletionReport` {`outcome` (`DuplicateDeletionOutcome` : `DELETED`, `ALREADY_GONE`, `DRY_RUN_PASSED`, `UNCONFIRMED`), `twinListingId` (jumelle confirmée)} ; 404 annonce inconnue ; 409 `DuplicateDeletionRefusal` (`NOT_A_DUPLICATE`, `HAS_HISTORY`, `BROWSER_BUSY`, `CADENCE_LIMIT_REACHED` avec `allowedAt`) ; 422 `DuplicateDeletionRefusal` (`TWIN_NOT_CONFIRMED`, `LISTING_CHANGED` avec `differences`) ; 502 `DuplicateDeletionRefusal` (`BROWSER_UNAVAILABLE`, `VINTED_UNREACHABLE`, `SESSION_DISCONNECTED`, `LISTING_PAGE_UNREADABLE`) ; 500 `DuplicateDeletionRefusal` (`DELETION_FAILED`, avant le clic de confirmation) ; 403 si la demande vient d'un autre site |

- `ListingDetail` gagne `duplicateTwinIds` (identifiants des jumelles présentes, vide si l'annonce n'est dans aucun groupe) et `deletableAsDuplicate` (booléen), tous deux requis.
- `JournaledAction` gagne `DUPLICATE_DELETION`.
- `differences` reprend `ListingDifference` d'US-09 ; `savedTitle` et `savedPrice` y sont ceux du dernier scan.
- `DuplicateGroups` et `ListingDetail` portent aussi `deletionDryRun` (booléen, requis), pour que la confirmation dise si rien ne sera supprimé.

## Critères d'acceptation
- [x] La page « Doublons » regroupe les annonces présentes de même titre et même prix, sans ouvrir Vinted.
- [x] Seule une annonce d'un groupe, sans republication, suppression ni vente, est supprimable comme doublon ; sinon refus sans ouvrir Chrome.
- [x] Avant tout clic, une jumelle est relue active sur Vinted au même titre et au même prix ; sinon refus.
- [x] Avant tout clic, le doublon est relu actif au titre et au prix enregistrés ; sinon refus avec les écarts.
- [x] Une suppression prouvée (Vinted ne montre plus le doublon) efface l'annonce et toutes ses données, photos comprises ; le journal garde ses lignes.
- [x] Un doublon déjà absent de Vinted est effacé sans aucun clic.
- [x] Une suppression non prouvée n'enregistre et n'efface rien ; une nouvelle demande est possible.
- [x] En dry-run, rien n'est cliqué ni effacé.
- [x] Une action navigateur par annonce, verrou toujours libéré (la cadence ne s'applique plus depuis US-49).
- [x] Chaque demande laisse une ligne `DUPLICATE_DELETION` au journal, ignorée par la page « Échecs ».
- [x] La fiche d'une annonce supprimable propose « Supprimer ce doublon » et nomme sa jumelle.
- [x] Un autre site ouvert dans le navigateur ne peut pas demander une suppression de doublon.
- [x] Aucun test automatisé ne contacte le vrai site Vinted.

## Tests d'acceptation
### AT-47-01 – Groupes de doublons
```gherkin
Étant donné les annonces présentes 1 « Robe bleue » 12,00 €, 2 « Robe bleue » 12,00 €, 3 « Robe bleue » 15,00 €
Et l'annonce absente 4 « Robe bleue » 12,00 €
Quand le membre ouvre la page « Doublons »
Alors il voit un seul groupe « Robe bleue » 12,00 € avec les annonces 1 et 2
```
Test : `DeleteDuplicateAcceptanceTest.groups_present_listings_with_the_same_title_and_price()`

### AT-47-02 – Historique à garder
```gherkin
Étant donné un groupe de doublons dont l'annonce 1 est la nouvelle annonce d'une republication
Quand le membre ouvre la page « Doublons »
Alors l'annonce 1 montre « Historique à garder » et l'annonce 2 « Supprimer ce doublon »
Et une demande de suppression de l'annonce 1 est refusée (« historique à garder ») sans ouvrir Chrome
```
Test : `DeleteDuplicateAcceptanceTest.refuses_a_listing_with_history()`, `DeleteDuplicateAcceptanceTest.history_recorded_while_waiting_for_the_lock_is_checked_again_under_it()`, `DuplicatesPageTest.listing_with_history_cannot_be_deleted()`

### AT-47-03 – Pas un doublon
```gherkin
Étant donné une annonce présente sans autre annonce présente de même titre et même prix
Quand une suppression de doublon est demandée
Alors elle est refusée (« pas un doublon ») sans ouvrir Chrome
```
Test : `DeleteDuplicateAcceptanceTest.refuses_a_listing_without_twin()`

### AT-47-04 – Suppression et effacement
```gherkin
Étant donné les doublons 1 et 2 « Robe bleue » 12,00 €, l'annonce 2 supprimable, sauvegardée, avec tags et relevés
Et le dry-run désactivé
Quand le membre confirme « Supprimer le doublon » sur l'annonce 2
Alors l'outil relit l'annonce 1 active au même titre et au même prix
Et il relit l'annonce 2, clique sur la suppression puis la confirme dans Vinted
Et Vinted ne montre plus l'annonce 2
Et l'annonce 2, sa sauvegarde, ses photos (fichiers compris), tags, relevés et prix sont effacés
Et le journal garde ses lignes de l'annonce 2, plus une ligne « suppression de doublon » réussie
Et la réponse dit « supprimée de Vinted et effacée », jumelle 1
```
Test : `DeleteDuplicateAcceptanceTest.deletes_the_duplicate_then_erases_it()`, `JpaListingEraserTest.erases_the_listing_and_its_data_but_not_its_journal_nor_the_other_listings()`

### AT-47-05 – Jumelle non confirmée sur Vinted
```gherkin
Étant donné les doublons 1 et 2
Et Vinted montre l'annonce 1 réservée, ou à un autre prix, ou ne la montre plus
Quand le membre confirme la suppression de l'annonce 2
Alors elle est refusée (« aucune jumelle confirmée sur Vinted »), sans aucun clic, rien n'est effacé
```
Test : `DeleteDuplicateAcceptanceTest.refuses_when_no_twin_is_confirmed_on_vinted()`

### AT-47-06 – Doublon changé sur Vinted
```gherkin
Étant donné les doublons 1 et 2, la jumelle 1 confirmée
Et Vinted montre l'annonce 2 à 10,00 €
Quand le membre confirme la suppression de l'annonce 2
Alors elle est refusée avec « prix changé : 12,00 € enregistré, 10,00 € lu », sans aucun clic
```
Test : `DeleteDuplicateAcceptanceTest.refuses_a_duplicate_changed_on_vinted()`

### AT-47-07 – Doublon déjà absent de Vinted
```gherkin
Étant donné les doublons 1 et 2, la jumelle 1 confirmée
Et Vinted ne montre plus l'annonce 2
Quand le membre confirme la suppression de l'annonce 2
Alors l'annonce 2 est effacée sans aucun clic
Et la réponse dit « déjà absente de Vinted, effacée »
```
Test : `DeleteDuplicateAcceptanceTest.erases_a_duplicate_already_gone_without_click()`

### AT-47-08 – Suppression non confirmée
```gherkin
Étant donné les doublons 1 et 2, la jumelle 1 confirmée, le dry-run désactivé
Et Vinted montre encore l'annonce 2 après le clic de confirmation, ou une erreur survient après ce clic
Quand le membre confirme la suppression de l'annonce 2
Alors la réponse dit « suppression non confirmée, vérifiez sur Vinted »
Et rien n'est enregistré ni effacé, hors la ligne de journal en doute
Et une nouvelle demande est acceptée
```
Test : `DeleteDuplicateAcceptanceTest.unconfirmed_deletion_erases_nothing()`, `DeleteDuplicateAcceptanceTest.erase_failure_after_the_deletion_on_vinted_is_in_doubt_then_erased_without_click()`

### AT-47-09 – Dry-run
```gherkin
Étant donné les doublons 1 et 2 et le dry-run actif
Quand le membre confirme la suppression de l'annonce 2
Alors les deux pages sont relues, aucun clic n'est fait, rien n'est effacé
Et la réponse dit « dry-run : rien supprimé »
```
Test : `DeleteDuplicateAcceptanceTest.dry_run_clicks_and_erases_nothing()`

### AT-47-10 – Verrou et cadence
```gherkin
Étant donné une autre action navigateur en cours sur l'annonce 2, ou la limite de cadence des suppressions atteinte (réel)
Quand le membre confirme la suppression de l'annonce 2
Alors elle est refusée (« une action est en cours » ou « limite de cadence ») sans aucun clic
Et le verrou et la cadence sont libérés après toute demande, réussie ou non
```
Test : `DeleteDuplicateAcceptanceTest.refuses_while_the_listing_is_busy()` ; la partie cadence est remplacée par US-49 (AT-49-04b) : `DeleteDuplicateAcceptanceTest.ignores_the_cadence_and_does_not_count_in_it()`

### AT-47-11 – Erreurs avant le clic de confirmation
```gherkin
Étant donné les doublons 1 et 2
Et <problème> à l'ouverture d'une des pages
Quand le membre confirme la suppression de l'annonce 2
Alors elle est refusée avec <problème>, rien n'est touché sur Vinted ni effacé
Exemples : navigateur indisponible, Vinted inaccessible, session non connectée, page illisible
```
Test : `DeleteDuplicateAcceptanceTest.refuses_on_browser_problems()`

### AT-47-12 – Journal et page « Échecs »
```gherkin
Étant donné une suppression de doublon refusée sur l'annonce 2
Quand le membre ouvre la page « Échecs »
Alors l'annonce 2 n'y figure pas pour cette ligne
Et le journal montre la ligne « suppression de doublon » refusée
```
Test : `JpaActionJournalTest.last_entry_of_each_listing()`, `DeleteDuplicateAcceptanceTest.journals_every_request()`

### AT-47-13 – Fiche de l'annonce
```gherkin
Étant donné l'annonce 2 supprimable, jumelle de l'annonce 1
Quand le membre ouvre la fiche de l'annonce 2
Alors il voit « Même titre et même prix que l'annonce 1 » et « Supprimer ce doublon »
Et après confirmation et effacement, la page « Doublons » s'ouvre
```
Test : `ListingDetailApiControllerTest.detail_names_the_twins_of_a_duplicate()`, `ListingDetailPageTest.deletes_a_duplicate_from_its_detail()`

### AT-47-14 – Page « Doublons »
```gherkin
Étant donné un groupe de doublons
Quand le membre clique sur « Supprimer ce doublon » puis confirme
Alors la confirmation nomme l'annonce effacée et la jumelle gardée, et dit « irréversible »
Et le groupe disparaît de la page une fois le doublon effacé
```
Test : `DuplicatesPageTest.deletes_a_duplicate_after_confirmation()`

### AT-47-15 – Autre site
```gherkin
Quand un autre site ouvert dans le navigateur demande une suppression de doublon
Alors la demande est refusée (403), Chrome n'est pas ouvert et rien n'est enregistré
```
Test : `DuplicateApiControllerTest.deletion_requested_by_another_site_is_refused()`

### AT-47-16 – Sur le vrai compte
```gherkin
Étant donné un vrai doublon dans le dressing du membre, dry-run désactivé par le membre
Quand le membre supprime le doublon depuis la page « Doublons »
Alors Vinted ne montre plus le doublon, la jumelle est toujours en vente, et le doublon n'est plus dans l'outil
```
Test : manuel (vrai compte Vinted, fait par le membre ; aucun test automatisé ne contacte Vinted)

## Cas limites et erreurs
- Trois annonces identiques : deux sont supprimables (si sans historique) ; après l'effacement de l'une, le groupe reste avec deux.
- Le membre supprime la jumelle à la main sur Vinted entre la page et la demande : la jumelle n'est pas confirmée (8), refus.
- Deux demandes simultanées sur les deux annonces d'un groupe : chacune relit l'autre sur Vinted avant de cliquer, sous son propre verrou ; la seconde peut passer avant que la première ne soit effacée. Risque accepté : la seconde relit la jumelle juste avant le clic et la trouve absente si la première est déjà supprimée sur Vinted ; reste la fenêtre entre les deux clics de confirmation, négligeable pour un usage à un seul membre.
- Suppression faite sur Vinted mais effacement en échec (base indisponible) : « suppression non confirmée », ligne dans le doute (comptée dans la cadence), le doublon reste en base ; une nouvelle demande le voit absent de Vinted et l'efface sans clic (9). Si un scan est passé entre-temps, le doublon est absent, donc hors de tout groupe : il reste en base comme toute annonce disparue (hors périmètre).
- Fichiers photos non effacés (disque) : l'effacement en base est gardé, l'erreur est dans les logs ; des fichiers orphelins sont sans effet.
- Lignes du journal d'une annonce effacée : gardées ; la page « Journal » les affiche, le lien vers la fiche mène à « annonce introuvable ».
- Annonce en cours de traitement par la série : le verrou par annonce refuse la demande (`BROWSER_BUSY`).

## Hors périmètre
- Doublons de titre ou de prix approchants (seule l'égalité exacte compte).
- Effacer de l'outil une annonce absente, sans passer par Vinted.
- Suppression de doublons par la série automatique ou en lot.
- Choisir la jumelle gardée autrement qu'en supprimant l'autre annonce.

## Décisions
- Besoin, précisé par le membre (2026-11-19) : une annonce republiée deux fois par erreur est en vente deux fois sur Vinted, avec deux identifiants ; supprimer le doublon sur Vinted puis l'effacer de la base.
- Garde-fou, décidé par le membre (2026-11-19) : jumelle détectée par l'outil (autre annonce présente, même titre et même prix), relue active sur Vinted juste avant le clic, au lieu de la sauvegarde validée d'US-09.
- Page « Doublons » dans cette US, décidé par le membre (2026-11-19), avec le bouton sur chaque doublon supprimable ; bouton aussi sur la fiche.
- Effacement, décidé par le membre (2026-11-19) : tout ce qui concerne l'annonce, sauf le journal ; refus si l'annonce porte un historique à garder (republication, suppression, vente).
- Doute, décidé par le membre (2026-11-19) : rien n'est enregistré ; une nouvelle demande relit la page, efface sans clic un doublon déjà absent. Supprimer deux fois un doublon est sans risque, la page étant relue avant chaque clic.
- Règles d'US-09 reprises, décidé par le membre (2026-11-19) : confirmation « irréversible », dry-run, cadence des suppressions, verrou `DELETING`, journal, jamais par la série automatique.
- Groupes limités aux annonces présentes : sinon chaque ancienne annonce republiée (absente, même titre et prix) ferait groupe avec sa nouvelle.
- Cadence, décidé par le membre (2026-11-20) : une suppression de doublon compte dans la cadence des suppressions par sa ligne du journal (`SUCCEEDED` ou `IN_DOUBT`), sans nouvelle table. Un doublon déjà absent (`SUCCEEDED` sans clic) compte aussi : prudent, sans effet notable pour un usage rare. Remplacé par US-49 (2026-11-20) : ni limitée ni comptée.
- Effacement en échec après une suppression faite sur Vinted, décidé par le membre (2026-11-20, retour de revue) : issue « non confirmée » et ligne dans le doute, au lieu d'une erreur 500 qui disait à tort « avant la confirmation » et échappait à la cadence.
- Supprimabilité revérifiée sous le verrou (retour de revue) : une autre action peut avoir donné un historique à l'annonce entre le premier contrôle et le verrou.
- Risque accepté : une ligne de journal non écrite (base indisponible à ce moment) ne compte pas dans la cadence.
- Annonce inconnue (404) : rien n'est journalisé, comme une annonce sans sauvegarde pour US-09.
- Page « Échecs » sans les lignes `DUPLICATE_DELETION` : leur relance n'a pas d'étape dans le cycle de vie ; la page « Doublons » sert de point de reprise.

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `DuplicateGroup` (Groupe de doublons) : annonces présentes de même titre et même prix, au moins deux ; `DuplicateGroups.of(listings)` calcule les groupes (domaine pur).
- `Twin` (Jumelle) : autre annonce présente du groupe ; celle relue active sur Vinted est la jumelle confirmée.
- `ListingHistory` (Historique à garder) : republication, suppression ou vente de l'annonce ; empêche de la supprimer comme doublon.
- `DeleteDuplicate` (Suppression d'un doublon) : cas d'usage ; `DuplicateDeletionOutcome` (`DELETED` / `ALREADY_GONE` / `DRY_RUN_PASSED` / `UNCONFIRMED`).
- Refus du domaine : `NotADuplicate`, `ListingHasHistory`, `TwinNotConfirmed` ; `ListingChanged` d'US-09 réutilisé.
- `ListingEraser` (Effacement d'une annonce) : port sortant, efface en une transaction l'annonce et ses données ; les fichiers photos ensuite par `PhotoStorage.removeAllBut(id, [])`, un échec n'étant que journalisé dans les logs.
- `DuplicateDeletionAttempts` : port sortant, dates des suppressions de doublons réussies ou dans le doute, lues dans le journal ; `CadenceCheck` les compte comme des suppressions. Supprimé par US-49.
- Réutilisés : `ListingDeleter` et `DeletionVisit` (US-09), `BrowserAction.DELETING`, `VintedWrite.DELETION`, `DeletionMode`, `JournalWriter`.
- `JournaledAction.DUPLICATE_DELETION` ; changeset Liquibase `018-journal-duplicate-deletion` (contrainte de l'action du journal).

## Tests unitaires et d'intégration attendus
- Domaine : `DuplicateGroupsTest` (même titre et prix, présentes seulement, devise comprise, tri), `ListingDifferenceTest` (comparaison au titre et prix enregistrés).
- Application : `DeleteDuplicateAcceptanceTest` (scénarios ci-dessus, faux navigateur `FakeVintedPages` et faux dépôts).
- Adapters : `JpaListingEraserTest` et `JpaActionJournalTest` (PostgreSQL, tout effacé sauf le journal, autres annonces intactes ; cadence), `DuplicateApiControllerTest` et `ListingDetailApiControllerTest` (codes de réponse, 403), `DuplicatesPageTest` et `ListingDetailPageTest` (`@Tag("browser")`), Vitest des libellés (`duplicates.spec.ts`).
