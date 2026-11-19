# US-27 – Limites de cadence
**Statut** : implémentée
**Priorité** : MVP
**Dépend de** : US-09, US-12

## Contexte
Un compte Vinted qui enchaîne trop vite suppressions et mises en vente risque d'être bloqué. Aujourd'hui, seule la lecture est rythmée : pauses du scan (US-04) et de la sauvegarde (US-05). Les actions qui écrivent sur Vinted (suppression US-09, republication US-12) se lancent à la main, une à la fois, sans autre garde-fou que le verrou du navigateur. Avant d'automatiser (US-19 « Tout lancer »), l'outil doit imposer lui-même des limites de cadence, quel que soit le déclencheur.

## Objectif
En tant que membre, je veux que l'outil refuse toute écriture sur Vinted qui dépasserait une cadence prudente, et me dise quand elle redeviendra possible, afin de ne pas faire bloquer mon compte.

## Comportement attendu
1. **Écritures comptées** : une écriture est une tentative enregistrée par l'outil juste avant son clic décisif sur Vinted, quelle que soit son issue : suppression (US-09) et republication (US-12), à leur date de tentative. Ne comptent pas : le dry-run (aucun clic, rien d'enregistré), les refus avant le clic, la revérification d'une suppression et la vérification d'une republication (lectures), ni ce que le membre fait lui-même sur Vinted.
2. **Limites** :
   - **Intervalle minimal**, propre au type de l'écriture demandée (un pour les suppressions, un pour les republications), compté depuis la dernière écriture **du même type** (US-46 ; auparavant de n'importe quel type) : la nouvelle écriture n'est possible qu'à partir de la date de la dernière écriture de son type plus l'intervalle de ce type. Une suppression ne retarde pas une republication, ni l'inverse.
   - **Plafond par 24 heures glissantes**, propre à chaque type : au plus N suppressions et au plus M republications dans les 24 heures précédant la demande. Plafond atteint : possible de nouveau 24 heures après l'écriture de ce type qui doit sortir de la fenêtre pour libérer une place.
3. **Contrôle** : en mode réel seulement, au lancement d'une suppression ou d'une republication, après les contrôles existants et une fois le verrou du navigateur pris (deux demandes ne peuvent pas passer le contrôle ensemble), avant toute navigation dans Chrome.
4. **Refus** : limite atteinte → refus 409 `CADENCE_LIMIT_REACHED`, avec `allowedAt`, la date la plus tardive imposée par les limites. Rien n'est touché sur Vinted ni enregistré ; le verrou est libéré. La fiche de l'annonce affiche « limite de cadence atteinte (à partir du <date>). »
5. **Persistance** : les limites se calculent à partir des tentatives déjà enregistrées (US-09, US-12) ; rien de nouveau n'est stocké et un redémarrage ne remet aucun compteur à zéro.
6. **Configuration** : `cadence.deletion-interval` (1 minute), `cadence.republication-interval` (1 minute), `cadence.daily-deletions` (140), `cadence.daily-republications` (140), dans `application.yml`. Valeur absente, nulle, négative ou illisible, ou intervalle d'au moins 24 heures (seules les écritures des 24 dernières heures sont lues) → l'application ne démarre pas.

## Critères d'acceptation
- [x] Une écriture demandée avant la fin de l'intervalle minimal de son type depuis la dernière écriture du même type est refusée avec la date à partir de laquelle elle est possible (US-46).
- [x] Une suppression au-delà du plafond de suppressions sur 24 heures glissantes est refusée avec sa date ; de même pour les republications.
- [x] Les plafonds sont propres à chaque type.
- [x] Toute tentative enregistrée compte, quelle que soit son issue ; dry-run, refus et vérifications ne comptent pas.
- [x] Le dry-run et les vérifications ne sont pas soumis aux limites.
- [x] Un refus ne touche rien sur Vinted, n'enregistre rien et libère le verrou.
- [x] Les limites tiennent après un redémarrage.
- [x] La fiche affiche le refus et sa date.
- [x] Une configuration invalide empêche l'application de démarrer.

## Tests d'acceptation
Valeurs des scénarios : intervalle de 30 secondes pour les suppressions et de 2 minutes pour les republications, plafonds de 2 suppressions et 2 republications (les tests fixent leurs propres valeurs).

### AT-27-01 – Intervalle minimal entre deux écritures
```gherkin
Étant donné une suppression tentée à 10:00
Quand le membre lance la suppression d'une autre annonce à 10:00:20
Alors elle est refusée (CADENCE_LIMIT_REACHED, à partir de 10:00:30), rien n'est touché sur Vinted ni enregistré, et le verrou est libéré
Quand il la relance à 10:00:30
Alors elle a lieu
```
Test : `DeleteListingAcceptanceTest.refusesDeletionTooSoonAfterAnotherWrite()`

### AT-27-02 – L'intervalle se compte depuis la dernière écriture du même type (US-46)
```gherkin
Étant donné une suppression tentée à 10:00
Quand le membre lance la republication d'une autre annonce à 10:01
Alors elle a lieu
```
Test : `RepublishListingAcceptanceTest.republishesRightAfterADeletion()`

### AT-27-03 – Plafond de suppressions sur 24 heures glissantes
```gherkin
Étant donné deux suppressions tentées hier à 11:00 et à 13:00
Quand le membre lance une suppression aujourd'hui à 10:00
Alors elle est refusée (CADENCE_LIMIT_REACHED, à partir d'aujourd'hui 11:00)
Quand il la relance aujourd'hui à 11:00
Alors elle a lieu
```
Test : `DeleteListingAcceptanceTest.refusesDeletionOverTheDailyLimit()`

### AT-27-04 – Plafond de republications, propre à son type
```gherkin
Étant donné deux republications tentées depuis moins de 24 heures, la dernière il y a plus de 2 minutes, quelle que soit leur issue
Et aucune suppression tentée
Quand le membre lance une republication
Alors elle est refusée (CADENCE_LIMIT_REACHED, 24 heures après la plus ancienne)
Quand il lance une suppression
Alors elle a lieu
```
Test : `RepublishListingAcceptanceTest.refusesRepublicationOverTheDailyLimitButNotDeletion()` (la suppression est vérifiée par le contrôle de cadence lui-même)

### AT-27-05 – Le dry-run n'est ni limité ni compté
```gherkin
Étant donné le dry-run de la suppression et une suppression tentée il y a une minute
Quand le membre lance une suppression
Alors le dry-run a lieu et rien n'est enregistré
```
Test : `DeleteListingAcceptanceTest.dryRunIsNotLimitedByCadence()`

### AT-27-06 – Les vérifications ne sont ni limitées ni comptées
```gherkin
Étant donné une suppression tentée il y a une minute, non confirmée
Quand le membre la revérifie sur Vinted
Alors la revérification a lieu
Et une suppression lancée 2 minutes après la tentative a lieu
```
Test : `DeleteListingAcceptanceTest.recheck_finds_the_listing_still_there()` (revérification aussitôt après la tentative, nouvelle suppression 2 minutes après)

### AT-27-07 – Les limites tiennent après un redémarrage
```gherkin
Étant donné des tentatives de suppression et de republication enregistrées
Quand l'outil redémarre (tout est relu en base)
Alors leurs dates sont relues et comptent toujours
```
Test : `JpaListingDeletionRepositoryTest.attemptsSinceSurviveRestart()` et `JpaListingRepublicationRepositoryTest.attemptsSinceSurviveRestart()`

### AT-27-08 – Refus affiché sur la fiche
```gherkin
Étant donné une suppression ou une republication refusée pour cadence
Quand la réponse arrive sur la fiche de l'annonce
Alors la fiche affiche « limite de cadence atteinte (à partir du <date>). »
```
Test : `ListingDetailPageTest.Deletion.showsCadenceRefusalWithItsDate()` et `ListingDetailPageTest.Republication.showsCadenceRefusalWithItsDate()`

### AT-27-09 – Configuration invalide
```gherkin
Étant donné un intervalle minimal ou un plafond absent, nul, négatif ou illisible
Quand l'application démarre
Alors elle refuse de démarrer
```
Test : `CadenceConfigurationTest` (`missingValuePreventsStartup`, `invalidValuePreventsStartup`)

### AT-27-10 – L'intervalle est celui du type de l'écriture demandée, compté depuis la dernière écriture de ce type
```gherkin
Étant donné une suppression tentée à 10:00, un intervalle de 2 minutes
Quand une republication est demandée à 10:00:10
Alors elle a lieu
Quand une autre suppression est demandée à 10:01
Alors elle est refusée (CADENCE_LIMIT_REACHED, à partir de 10:02)
```
Test : `WriteCadenceTest.a_write_only_waits_for_the_interval_of_its_own_type()` (AT-46-05)

## Cas limites et erreurs
- Plusieurs limites atteintes : la date annoncée est la plus tardive.
- Demandes simultanées : le contrôle a lieu sous le verrou du navigateur ; la seconde est refusée par le verrou (409 `BROWSER_BUSY`) ou voit la tentative de la première.
- Base indisponible pendant le contrôle → refus « échec » habituel (logs), rien touché sur Vinted, verrou libéré.
- Horloge : tout se calcule avec l'horloge de l'outil, en dates absolues ; pas de jour calendaire, donc ni fuseau ni changement d'heure.
- Écritures faites par le membre directement sur Vinted : invisibles pour l'outil, non comptées.

## Hors périmètre
- Rythme de remplissage du formulaire de mise en vente : relève du diagnostic d'US-12.
- Rythme de lecture du scan et de la sauvegarde : déjà fixé par US-04 et US-05.
- Attente automatique jusqu'à la date permise : US-19.
- Affichage permanent des compteurs ou de la prochaine date permise hors refus.

## Décisions
- Périmètre, décidé par l'utilisateur (2026-11-16) : limites sur les actions qui écrivent sur Vinted (suppression US-09, republication US-12) : intervalle minimal entre deux telles actions et plafond par jour, appliqués aux actions manuelles comme aux futures actions automatiques (US-19).
- Limite atteinte, décidé par l'utilisateur (2026-11-16) : refus immédiat (409), rien n'est touché sur Vinted ni enregistré, avec la date à partir de laquelle l'action redevient possible, affichée sur la fiche ; demandes toujours synchrones. L'attente automatique relève d'US-19.
- Décidé par Claude sur délégation de l'utilisateur (« fait comme tu penses », 2026-11-16) :
  - intervalle commun à tous les types, plafonds propres à chaque type ;
  - « par jour » = 24 heures glissantes, sans fuseau ni jour calendaire ;
  - valeurs par défaut prudentes : 2 minutes, 20 suppressions et 20 republications par 24 heures (146 annonces en 8 jours environ), à revoir à l'usage ;
  - toute tentative enregistrée compte, quelle que soit son issue (une tentative en erreur a pu écrire sur Vinted) ;
  - dry-run, refus et vérifications ni limités ni comptés ;
  - contrôle sous le verrou du navigateur ;
  - aucune table nouvelle : calcul à partir des tentatives enregistrées.
  - intervalle minimal strictement inférieur à 24 heures (relevé par la revue) : seules les écritures des 24 dernières heures sont lues.
- Intervalle propre aux suppressions, décidé par l'utilisateur (2026-11-18) : 30 secondes avant une suppression, 2 minutes avant une republication, comptées depuis la dernière écriture de n'importe quel type (deux écritures ne se suivent donc jamais à moins de 30 secondes ; remplacé par US-46 : depuis la dernière écriture du même type) ; `cadence.minimum-interval` est remplacé par `cadence.deletion-interval` et `cadence.republication-interval`. Plafond de suppressions porté à 140 par 24 heures (tout le dressing en une journée), plafond de republications inchangé (20). Risque anti-robot accepté par l'utilisateur.
- Intervalles alignés, décidé par l'utilisateur (2026-11-18) : 1 minute avant toute écriture, suppression comme republication (`cadence.deletion-interval` et `cadence.republication-interval` à 1 minute) ; les deux réglages restent séparés. Plafond de republications porté à 140 par 24 heures, comme les suppressions.
- Au rebase sur US-23 (2026-11-16), règle du journal appliquée : un refus pour cadence est écrit « refusée » avec le code `CADENCE_LIMIT_REACHED`, comme la réponse de l'API (`DeleteListingAcceptanceTest.refusesDeletionTooSoonAfterAnotherWrite()`).

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `WriteCadence` (cadence d'écriture) : intervalle minimal et plafond propres à chaque type ; à partir des dates des écritures récentes et de l'heure, dit si une écriture d'un type est permise, sinon à partir de quand.
- `VintedWrite` (écriture sur Vinted) : `DELETION` ou `REPUBLICATION`.
- `CadenceLimitReached` (limite de cadence atteinte) : refus, porte `allowedAt`.
- Ports : `ListingDeletionRepository.findAttemptedSince(Instant)` et `ListingRepublicationRepository.findAttemptedSince(Instant)` (dates des tentatives depuis une date).
- Application : `CadenceCheck`, appelé par `DeleteListing` et `RepublishListing` sous le verrou, en mode réel.
- API : raison `CADENCE_LIMIT_REACHED` et champ `allowedAt` dans `DeletionRefusal` et `RepublicationRefusal`.

## Tests unitaires et d'intégration attendus
- Domaine : `WriteCadenceTest` (intervalle, plafond par type, fenêtre glissante, date la plus tardive, valeurs invalides).
- Application : scénarios d'acceptation ci-dessus ; contre-épreuve faite : sans l'appel au contrôle, les 4 scénarios de refus échouent.
- Persistance : `findAttemptedSince` sur PostgreSQL (`@Tag("docker")`).
- Configuration : `CadenceConfigurationTest`.
- Web : `ListingDeletionApiControllerTest`, `ListingRepublicationApiControllerTest` (`cadence_limit_is_a_conflict_with_the_date_it_ends` : 409 et `allowedAt`) ; page `ListingDetailPageTest`.
