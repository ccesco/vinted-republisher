# US-18b – Fiche détail d'une annonce
**Statut** : implémentée
**Priorité** : MVP
**Dépend de** : US-18 (liste), US-07 (historique des relevés), US-08/US-09/US-12 (aperçu de la sauvegarde, suppression, republication)

## Contexte
Depuis la liste (US-18), le membre n'atteint aujourd'hui que `listing-backup.html?id=…`, et seulement pour une annonce sauvegardée. Cette page montre déjà le contenu sauvegardé par ses libellés, les photos gardées, la vérification, la validation, la suppression et la republication (`GET /api/listings/{id}/backup`).

Contraintes déjà posées par d'autres specs :
- L'historique des relevés de vues et favoris est gardé (US-07) mais n'est exposé nulle part : son affichage est renvoyé ici.
- Le contenu sauvegardé n'est exposé que par l'aperçu de la sauvegarde (US-05, US-08).
- Les états de traitement (US-14) ne sont pas encore spécifiés.
- Les textes venus de Vinted ne passent jamais par `innerHTML` ; les liens d'annonce ne sont que `http`/`https` (`ListingLink`).

## Objectif
En tant que membre, je veux ouvrir la fiche d'une annonce depuis la liste, afin de voir tout ce que l'outil sait d'elle : l'annonce, ses vues et favoris dans le temps, sa sauvegarde, sa suppression et sa republication.

## Comportement attendu
1. **Accès** : dans le tableau de `wardrobe.html`, le titre de chaque annonce (présente ou absente, sauvegardée ou non) ouvre `listing.html?id=<identifiant Vinted>`. Le lien « Voir la sauvegarde » disparaît de la liste ; le lien vers Vinted n'est plus que sur la fiche.
2. **Fiche** (`listing.html`), de haut en bas :
   - **Annonce** : titre (en titre de page), prix, lien « Voir sur Vinted », présence : « présente dans le dressing » ou « absente du dressing, vue pour la dernière fois le <date> » (date de la dernière fois que le scan l'a vue).
   - **Vues et favoris** : tableau des relevés, du plus récent au plus ancien, colonnes date, vues, favoris ; tous les relevés gardés ; « Aucun relevé » s'il n'y en a pas.
   - **Sauvegarde** : la section actuelle de `listing-backup.html`, inchangée (vérification, date, validation et bouton « Valider », suppression, republication, contenu, photos). Si l'annonce n'a pas de sauvegarde (`GET /api/listings/{id}/backup` en 404) : « Non sauvegardée », sans aucune action (depuis US-52, seul « Supprimer de Vinted » reste en en-tête, grisé avec sa raison).
   - Un lien « Retour à la liste » vers `wardrobe.html`.
3. **Annonce inconnue** (identifiant absent, non numérique, ou inconnu de l'outil) : « Annonce inconnue. » et un lien de retour à la liste, rien d'autre.
4. `listing-backup.html` est supprimée : son contenu et ses tests passent dans `listing.html`.
5. Rien n'est lu sur Vinted à l'ouverture de la fiche : seules les données en base (et la vérification locale des photos, inchangée) sont montrées.

## Contrat d'API
| Requête | Opération | Réponse |
| --- | --- | --- |
| `GET /api/listings/{listingId}` | `showListingDetail` | 200, `ListingDetail` : `listing` (`Listing` d'US-18 : identifiant, titre, prix, lien, présence, dernier relevé, sauvegarde, nombre de photos, validation), `lastSeenAt` (date-time, dernière fois que le scan l'a vue), `statisticsHistory` (tableau de `ListingStatistics`, du plus récent au plus ancien, vide si jamais relevée). 404 si l'annonce est inconnue de l'outil. 400 si l'identifiant n'est pas un entier ≥ 1 (paramètre `ListingId` existant). |
| `GET /api/listings/{listingId}/backup` et suivantes | inchangées | La fiche les appelle comme le faisait `listing-backup.html`. |

## Critères d'acceptation
- [x] Le titre de chaque annonce de la liste ouvre sa fiche, qu'elle soit présente ou absente, sauvegardée ou non.
- [x] La fiche montre le titre, le prix, le lien Vinted et la présence ; une annonce absente montre la date où elle a été vue pour la dernière fois.
- [x] La fiche montre tous les relevés de vues et favoris, du plus récent au plus ancien, ou « Aucun relevé ».
- [x] La fiche montre la section sauvegarde / suppression / republication d'une annonce sauvegardée, avec ses actions inchangées ; « Non sauvegardée » sinon, sans action.
- [x] Une annonce inconnue donne 404 dans l'API et « Annonce inconnue. » sur la fiche.
- [x] Les textes venus de Vinted sont affichés comme texte, jamais interprétés comme HTML.

## Tests d'acceptation
### AT-18b-01 – Fiche d'une annonce relevée et sauvegardée
```gherkin
Étant donné l'annonce 101 « Pull rayé », 12,50 €, présente, relevée à 5 vues et 1 favori le 1er novembre puis 12 vues et 3 favoris le 2
  et sauvegardée
Quand le membre ouvre sa fiche
Alors il voit son titre, son prix, son lien Vinted et « présente dans le dressing »
  et les relevés du 2 (12 vues, 3 favoris) puis du 1er (5 vues, 1 favori)
  et la section de sa sauvegarde
```
Test : `ShowListingDetailAcceptanceTest.shows_listing_statistics_history_most_recent_first()` et `ListingDetailPageTest.shows_listing_history_and_backup()`

### AT-18b-02 – Annonce jamais relevée ni sauvegardée
```gherkin
Étant donné l'annonce 102 présente, jamais relevée ni sauvegardée
Quand le membre ouvre sa fiche
Alors il voit « Aucun relevé » et « Non sauvegardée », sans bouton
```
Test : `ShowListingDetailAcceptanceTest.listing_never_read_has_empty_history()` et `ListingDetailPageTest.listing_never_read_nor_backed_up()`

### AT-18b-03 – Annonce absente
```gherkin
Étant donné l'annonce 103 vue au scan du 3 novembre puis absente
Quand le membre ouvre sa fiche
Alors il voit « absente du dressing, vue pour la dernière fois le 03/11/2026 »
```
Test : `ShowListingDetailAcceptanceTest.absent_listing_tells_when_it_was_last_seen()` et `ListingDetailPageTest.absent_listing()`

### AT-18b-04 – Annonce inconnue
```gherkin
Étant donné aucune annonce 999
Quand la page demande GET /api/listings/999
Alors la réponse est 404
Quand le membre ouvre listing.html?id=999, ou listing.html sans identifiant
Alors il voit « Annonce inconnue. » et le lien de retour à la liste
```
Test : `ListingDetailApiControllerTest.unknown_listing_is_not_found()` et `ListingDetailPageTest.unknown_listing()`

### AT-18b-05 – Accès depuis la liste
```gherkin
Étant donné la liste avec une annonce sauvegardée et une annonce absente non sauvegardée
Quand le membre regarde le tableau
Alors le titre de chacune mène à listing.html?id=<son identifiant>
  et aucune ligne n'a de lien « Voir la sauvegarde »
```
Test : `WardrobePageTest.title_opens_the_listing_detail()`

### AT-18b-06 – Actions de la sauvegarde inchangées
```gherkin
Étant donné une annonce sauvegardée
Quand le membre ouvre sa fiche
Alors la validation, la suppression et la republication se font comme sur l'ancienne page listing-backup.html
```
Test : `ListingDetailPageTest`, scénarios repris de `ListingBackupPageTest` (renommé), chacun avec son nom d'origine

### AT-18b-07 – Titre affiché comme texte
```gherkin
Étant donné une annonce au titre "Pull <b>rayé</b>"
Quand le membre ouvre sa fiche
Alors il voit "Pull <b>rayé</b>" tel quel
```
Test : `ListingDetailPageTest.shows_the_title_as_text_never_as_html()`

## Cas limites et erreurs
- Base indisponible : l'API répond 500 ; la fiche affiche « L'application ne répond pas. » (message existant de `listing-backup.html`).
- Annonce connue mais sauvegarde en 404 : « Non sauvegardée » (pas une erreur).
- Annonce republiée (US-12) : sa section de republication montre le lien vers la nouvelle annonce, comme aujourd'hui ; la nouvelle annonce a sa propre fiche.

## Hors périmètre
- Graphique de l'évolution des vues et favoris.
- Cumul des vues et favoris avec l'annonce dont celle-ci est la republication, et comparaison avant / après (US-24).
- Actions de traitement automatique (US-19 à US-21), journal des actions (US-23), états de traitement (US-14).
- Navigation annonce précédente / suivante depuis la fiche.

## Décisions
- Page, décidé par l'utilisateur (2026-11-15) : une fiche unique `listing.html?id=…` remplace `listing-backup.html` (supprimée, ses tests repris). De haut en bas : l'annonce (titre, prix, lien Vinted, présence), l'historique des relevés, puis la section sauvegarde / suppression / republication actuelle, ou « non sauvegardée ». Raison : une page par annonce, accessible même sans sauvegarde.
- Décisions suivantes : recommandations de Claude, acceptées par délégation de l'utilisateur (2026-11-15, « fais comme tu penses être le mieux »).
- Accès : le titre ouvre la fiche pour toutes les annonces ; plus de « Voir la sauvegarde » dans la liste ; lien Vinted sur la fiche seulement. Raison : un seul lien par ligne, vers tout ce que l'outil sait.
- Historique en tableau, du plus récent au plus ancien, tous les relevés (un par scan qui les lit : quelques centaines au plus), sans graphique (YAGNI).
- Nouvelle ressource `GET /api/listings/{listingId}` pour l'annonce et son historique ; la section sauvegarde garde ses ressources existantes, inchangées. Raison : aucune régression sur l'aperçu de la sauvegarde, déjà couvert.
- Pas de cumul avec l'annonce d'origine d'une republication : il attend le lien d'US-12 et la spec d'US-24.
- Ordre de réalisation : `listing-backup.html` est aussi modifiée par US-12 (republication, autre session). La fiche se code après la fusion d'US-12 dans `main`, en renommant le fichier avec `git mv` pour garder son historique et limiter le conflit.

## Questions ouvertes
(aucune)

## Modèle de domaine
- `ListingDetail` (fiche d'une annonce) : la ligne de l'inventaire de l'annonce (`InventoryEntry`, US-18) et tout son historique de relevés, du plus récent au plus ancien.
- `ShowListingDetail` (cas d'usage) : lit l'annonce, son historique, sa sauvegarde et sa validation ; refuse une annonce inconnue (`ListingNotFound`).
- Port `ListingStatisticsRepository` : gagne `historyOf(VintedListingId)` (historique d'une annonce, du plus récent au plus ancien), requête paramétrée sur l'index unique (identifiant Vinted, `read_at`) existant ; aucune modification de schéma.
- `Listing.lastSeenAt` existant fournit la date de dernière vue.

## Tests unitaires et d'intégration attendus
- Domaine / application : `ShowListingDetailAcceptanceTest` avec les fakes (AT-18b-01 à 03, annonce inconnue refusée).
- Persistance : `JpaListingStatisticsRepositoryTest` (`@Tag("docker")`) : historique d'une seule annonce, ordre, aucun relevé.
- Web : `ListingDetailApiControllerTest` (200 avec historique, 404, 400 sur identifiant invalide).
- Pages (`@Tag("browser")`) : `ListingDetailPageTest` (AT-18b-01 à 04, 06, 07, reprise de `ListingBackupPageTest`), `WardrobePageTest` (AT-18b-05).
