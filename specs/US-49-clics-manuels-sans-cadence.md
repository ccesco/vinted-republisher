# US-49 – Clics manuels sans cadence ni délai
**Statut** : validée
**Priorité** : V2
**Dépend de** : US-27 (limites de cadence), US-11 (délai avant republication), US-09 (suppression), US-12 (republication), US-20 (relance), US-45 (republication manuelle), US-46 (workers), US-47 (suppression d'un doublon)

## Contexte
US-27 applique les limites de cadence (intervalle d'1 minute, plafond de 140 par 24 heures, par type d'écriture) à toute écriture sur Vinted, manuelle comme automatique. Quand le membre clique lui-même sur une annonce, attendre la minute est une gêne : il est devant l'écran et décide de son propre rythme. US-45 a déjà levé cadence et délai pour « Republier manuellement ».

## Objectif
En tant que membre, je veux que mes clics sur une annonce s'exécutent sans attendre la cadence ni le délai avant republication, afin d'avancer à mon rythme, tandis que « Tout lancer » reste rythmé pour protéger le compte.

## Comportement attendu
1. **Clics manuels** concernés : « Supprimer sur Vinted » (fiche, US-09), « Republier sur Vinted » (fiche, US-12), « Relancer » d'une annonce (liste, page « Échecs », US-20), « Supprimer le doublon » (page « Doublons », US-47), ainsi que « Republier manuellement » (US-45), déjà sans cadence ni délai, qui ne compte plus pour la série.
2. **Ni cadence ni délai** : pour ces clics, ni l'intervalle ni le plafond par 24 heures (US-27, `CADENCE_LIMIT_REACHED`), ni le délai après la suppression (US-11, `TOO_EARLY`) ne les refusent. Tous les autres contrôles restent (sauvegarde validée, photos, verrou du navigateur, dry-run, journal).
3. **Hors cadence de la série** : une écriture faite par un clic manuel ne compte pas dans la cadence des écritures automatiques : la série (« Tout lancer », US-19/US-46) ne tient compte, pour son intervalle et son plafond, que de ses propres écritures.
4. **Automatique inchangé** : la série garde la cadence et le délai d'US-27 et US-11.
5. **Fiche** : « Republier sur Vinted » est actif dès la suppression confirmée, sans compte à rebours.
6. **Relancer à l'étape `WAITING`** : la liste propose le bouton de relance « Republier sur Vinted » aussi pour une annonce `WAITING` ; il lance la republication (`REPUBLICATION`) sans attendre la date. La série, elle, ne republie une annonce `WAITING` qu'une fois la date atteinte.
7. **Écran inchangé** : l'origine d'une écriture est enregistrée mais affichée nulle part (journal, fiche, aperçu).

## Critères d'acceptation
- [ ] Une suppression, une republication, une relance ou une suppression de doublon manuelle n'est refusée ni par l'intervalle ni par le plafond de cadence.
- [ ] Une republication ou une relance manuelle n'est pas refusée par le délai après la suppression.
- [ ] Une écriture manuelle ne retarde pas la prochaine écriture de la série et ne compte pas dans son plafond.
- [ ] La série reste soumise à la cadence et au délai.
- [ ] Sur la fiche, « Republier sur Vinted » est actif dès la suppression confirmée.
- [ ] Dans la liste, une annonce `WAITING` se relance (« Republier sur Vinted »).
- [ ] Les tentatives enregistrées avant US-49 comptent pour la série.

## Tests d'acceptation
### AT-49-01 – Suppression manuelle sans cadence
```gherkin
Étant donné une suppression faite il y a 10 secondes et le plafond de suppressions atteint
Quand le membre supprime une autre annonce depuis sa fiche
Alors elle est supprimée sur Vinted et journalisée
```
Test : `DeleteListingAcceptanceTest.manual_deletion_ignores_the_cadence()`

### AT-49-02 – Republication manuelle sans délai ni cadence
```gherkin
Étant donné une annonce supprimée il y a 5 minutes (délai de republication non écoulé)
Et une republication faite il y a 10 secondes
Quand le membre la republie depuis sa fiche
Alors elle est republiée
```
Test : `RepublishListingAcceptanceTest.manual_republication_ignores_the_delay_and_the_cadence()`

### AT-49-03 – Relance manuelle sans cadence
```gherkin
Étant donné la limite de cadence de suppression atteinte
Quand le membre relance une annonce « Prête à supprimer »
Alors elle est supprimée
```
Test : `RelaunchListingAcceptanceTest.relaunch_ignores_the_cadence()`

### AT-49-03b – Relance d'une annonce en attente
```gherkin
Étant donné une annonce supprimée il y a 5 minutes, à l'étape « En attente »
Quand le membre la relance depuis la liste avec le bouton « Republier sur Vinted »
Alors elle est republiée sans attendre la date de republication
```
Test : `RelaunchListingAcceptanceTest.relaunches_a_waiting_listing_without_waiting()` ; page : test de la liste (bouton pour `WAITING`)

### AT-49-04 – Une écriture manuelle ne compte pas pour la série
```gherkin
Étant donné une suppression manuelle faite il y a 10 secondes
Quand la série cherche à supprimer une annonce
Alors elle la supprime sans attendre
```
Test : `AutomaticRunAcceptanceTest.manual_writes_do_not_count_in_the_run_cadence()`

### AT-49-05 – La série reste rythmée
```gherkin
Étant donné une suppression faite par la série il y a 10 secondes
Quand la série cherche à supprimer une autre annonce
Alors elle attend la fin de l'intervalle
```
Test : `AutomaticRunAcceptanceTest.run_writes_keep_the_cadence()`

### AT-49-04b – Suppression de doublon sans cadence, hors cadence de la série
```gherkin
Étant donné la limite de cadence des suppressions atteinte (réel)
Quand le membre supprime un doublon
Alors il est supprimé sur Vinted
Et la série peut ensuite supprimer une annonce sans attendre l'intervalle
```
Test : `DeleteDuplicateAcceptanceTest`, nom fixé à l'implémentation

### AT-49-04c – Republication manuelle hors cadence de la série
```gherkin
Étant donné une annonce que le membre vient de republier manuellement (US-45)
Quand la série cherche à republier une autre annonce
Alors elle la republie sans attendre l'intervalle
```
Test : `PrepareManualRepublicationAcceptanceTest` (remplace `ignores_the_delay_and_the_cadence_but_counts_in_it()`)

### AT-49-06 – Tentatives antérieures comptées pour la série
```gherkin
Étant donné une suppression enregistrée avant US-49, il y a 10 secondes
Quand la série cherche à supprimer une annonce
Alors elle attend la fin de l'intervalle
```
Test : intégration Liquibase sur PostgreSQL (`@Tag("docker")`), nom fixé à l'implémentation

## Cas limites et erreurs
- Clic manuel pendant qu'une série tourne : le verrou du navigateur (une action par annonce) reste le seul garde-fou ; le clic ne prend ni ne libère aucune réservation de cadence.
- Clics manuels en rafale : rien ne les limite (risque anti-robot accepté).
- Dry-run : inchangé, aucune écriture, rien n'est compté.

## Hors périmètre
- Changer les valeurs de cadence d'US-27.
- Afficher l'origine d'une écriture.

## Décisions
- Périmètre, plafond, comptage et délai US-11 décidés par l'utilisateur (2026-11-20) : les trois clics (supprimer, republier, relancer) ignorent l'intervalle **et** le plafond de cadence, ainsi que le délai US-11 ; une écriture manuelle ne compte pas dans la cadence de la série. Risque anti-robot accepté par l'utilisateur.
- Constaté dans le code (2026-11-20) : la revérification de suppression et la vérification de republication ne sont déjà soumises à aucune cadence (seules les écritures `DELETION` et `REPUBLICATION` le sont) ; la relance de ces étapes ne change donc pas. La cadence compte les tentatives enregistrées en base (`findAttemptedSince`) plus les écritures en cours (réservations en mémoire).
- Origine des écritures, décidé par l'utilisateur (2026-11-20) : chaque tentative de suppression et de republication enregistre son origine, `MANUAL` (clic du membre) ou `RUN` (série) ; la cadence de la série ne compte que les tentatives `RUN` (et ses propres réservations). Les tentatives enregistrées avant US-49 sont `RUN` (choix prudent : au pire une attente de trop dans les 24 heures suivant la mise à jour). Changement de schéma (changeset Liquibase).
- « Republier manuellement » aligné, décidé par l'utilisateur (2026-11-20) : sa republication est enregistrée `MANUAL` et ne compte plus dans la cadence de la série (remplace la règle d'US-45 « elle compte dans la cadence » ; US-45 et AT-45-04a mis à jour dans le même commit).
- `WAITING` relançable, recommandation acceptée par l'utilisateur (2026-11-20) : cohérent avec la fiche, où le délai ne s'applique plus au clic ; US-20 (tableau des étapes relançables) mise à jour dans le même commit.
- Origine non affichée, recommandation acceptée par l'utilisateur (2026-11-20) : enregistrée pour la cadence seulement (YAGNI).
- « Supprimer le doublon » (US-47) aligné, recommandation acceptée par l'utilisateur (2026-11-20) : clic toujours manuel, donc ni cadence ni comptage pour la série ; US-47 mise à jour dans le même commit.
- Statut `validée` : l'utilisateur a demandé (2026-11-20) de suivre les recommandations et de lancer le développement.

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `WriteOrigin` (`MANUAL` / `RUN`) | Origine d'une écriture (clic du membre / série) : portée par chaque tentative de suppression et de republication ; la cadence ne compte que les tentatives `RUN`, et seule la série la consulte.
- `DeleteListing`, `RepublishListing` : appelés avec une origine ; `MANUAL` → ni cadence ni délai US-11 ; `RUN` → inchangé.

## Tests unitaires et d'intégration attendus
- Application : `DeleteListing` / `RepublishListing` en `MANUAL` passent cadence atteinte et (republication) avant la date ; en `RUN`, refus inchangés ; tentative enregistrée avec son origine. Relance en `MANUAL`, `WAITING` → `REPUBLICATION`. `DeleteDuplicate` sans cadence. Série en `RUN`.
- Adapters : changeset Liquibase (colonne d'origine, `NOT NULL`, `RUN` pour l'existant) ; lecture des tentatives pour la cadence filtrée sur `RUN` ; contrôleurs de la fiche, de la relance, des doublons en `MANUAL`.
- Interface : bouton de relance pour `WAITING` ; plus de compte à rebours sur « Republier sur Vinted ».
