# US-17 – Gestion des échecs
**Statut** : implémentée
**Priorité** : MVP
**Dépend de** : US-09, US-12, US-13, US-20 (relance), US-23 (journal), US-27 (cadence) ; prépare US-19

## Contexte
Une relance (US-20) peut échouer de plusieurs façons, qui n'appellent pas la même réponse :
- une écriture dont l'issue est douteuse (clic fait, résultat non prouvé) laisse déjà l'annonce « à vérifier » (US-09, US-12) : jamais de doublon ;
- certains refus ne concernent qu'une annonce (sauvegarde qui ne se vérifie plus, annonce modifiée sur Vinted, champ impossible à remplir) ;
- d'autres touchent tout le compte ou tout l'outil : session perdue, navigateur indisponible, Vinted inaccessible (cas attendu d'un blocage anti-robot), page de Vinted illisible (Vinted a changé sa page) ;
- d'autres enfin demandent seulement d'attendre : limite de cadence (US-27), date de republication pas atteinte (US-11), navigateur occupé.

Avant d'enchaîner tout le dressing (US-19), l'outil doit savoir, pour chaque échec, s'il faut attendre, passer à l'annonce suivante ou tout arrêter. Côté liste, le membre doit aussi distinguer une action faite d'une action dont l'issue reste à vérifier.

## Objectif
En tant que membre, je veux que chaque échec d'une relance soit classé (attendre, passer l'annonce, tout arrêter) et que la liste me dise quand une issue reste à vérifier, afin que l'enchaînement automatique protège mon compte et ne refasse jamais une écriture douteuse.

## Comportement attendu
1. **Conduite à tenir** (`FailureHandling`), déduite de l'échec d'une relance :

| Échec | Conduite | Raison |
|---|---|---|
| limite de cadence atteinte | `WAIT` jusqu'à `allowedAt` | US-27 |
| annonce en attente (`NotRelaunchable` avec date) | `WAIT` jusqu'à la date de republication | US-11 |
| date de republication pas atteinte (`RepublicationTooEarly`) | `WAIT` jusqu'à cette date | US-11 |
| navigateur occupé | `WAIT`, sans date | une autre action navigateur est en cours |
| étape changée | `SKIP_LISTING` | l'annonce sera relue au passage suivant |
| annonce inconnue de l'outil | `SKIP_LISTING` | propre à l'annonce : disparue de l'inventaire entre la liste et la relance |
| étape sans action (`NotRelaunchable` sans date) | `SKIP_LISTING` | attend le membre, ou plus rien à faire |
| refus du cas d'usage lancé (sauvegarde remplacée, non validée, non vérifiée, annonce modifiée, champ impossible à remplir, déjà supprimée ou republiée, republication en erreur…) | `SKIP_LISTING` | propre à l'annonce, le membre tranche sur la fiche |
| session non connectée, navigateur indisponible, Vinted inaccessible | `STOP_RUN` | touche tout le compte ; Vinted inaccessible peut être un blocage anti-robot |
| page de Vinted illisible (annonce, formulaire, dressing, nouvelle annonce) | `STOP_RUN` | Vinted a probablement changé sa page : toutes les annonces échoueraient |
| tout autre échec (inattendu) | `STOP_RUN` | prudence : rien n'est relancé sans comprendre |

2. **Jamais de nouvel essai d'une écriture** : une suppression ou une republication dont l'issue est douteuse reste « à vérifier » ; seule la vérification (lecture) peut se relancer, par l'étape « à vérifier » (US-20). Aucune règle de nouvel essai automatique d'une écriture n'est ajoutée.
3. **Issue incertaine dans la liste** : après une relance réussie côté serveur, si l'annonce relue est « Suppression à vérifier » ou « Republication à vérifier », la ligne affiche « Issue incertaine : à vérifier sur Vinted » au lieu de « <action> faite ».
4. **Dressing illisible pendant une relance** : rendu 502 `VINTED_PAGE_UNREADABLE`, comme les autres pages illisibles (contrat d'US-20), et non plus 500.

## Critères d'acceptation
- [x] Chaque échec du tableau donne sa conduite ; `WAIT` porte sa date quand elle est connue.
- [x] Un échec inattendu donne `STOP_RUN`.
- [x] La liste affiche « Issue incertaine : à vérifier sur Vinted » quand l'annonce relancée reste ou devient « à vérifier ».
- [x] Un dressing illisible pendant une relance est un 502 `VINTED_PAGE_UNREADABLE`.

## Tests d'acceptation

### AT-17-01 – Attendre
```gherkin
Étant donné une relance refusée pour cadence (à partir de 10:02), une annonce en attente (republiable à 10:05), ou un navigateur occupé
Quand l'échec est classé
Alors la conduite est « attendre », jusqu'à 10:02, jusqu'à 10:05, ou sans date
```
Test : `FailureHandlingTest.waits_for_cadence_republication_date_or_busy_browser()`

### AT-17-02 – Passer l'annonce
```gherkin
Étant donné une étape changée, une annonce inconnue, une étape sans action, ou un refus du cas d'usage lancé
Quand l'échec est classé
Alors la conduite est « passer l'annonce »
```
Test : `FailureHandlingTest.skips_the_listing_on_a_refusal_of_its_own()`

### AT-17-03 – Tout arrêter
```gherkin
Étant donné une session non connectée, un navigateur indisponible, Vinted inaccessible, une page de Vinted illisible ou un échec inattendu
Quand l'échec est classé
Alors la conduite est « tout arrêter »
```
Test : `FailureHandlingTest.stops_the_run_on_a_problem_of_the_account_or_of_vinted()`

### AT-17-04 – Issue incertaine dans la liste
```gherkin
Étant donné une annonce « Prête à supprimer » dont la suppression reste non confirmée après la relance
Quand la réponse arrive
Alors la ligne montre « Suppression à vérifier » et « Issue incertaine : à vérifier sur Vinted »
```
Test : `WardrobePageTest.shows_an_uncertain_outcome()`

### AT-17-05 – Dressing illisible pendant une relance
```gherkin
Étant donné une annonce « Republication à vérifier » et un dressing illisible
Quand le membre la relance
Alors la réponse est 502 VINTED_PAGE_UNREADABLE
```
Test : `ListingRelaunchApiControllerTest.unreadable_wardrobe_is_a_bad_gateway()`

## Cas limites et erreurs
- Plusieurs échecs possibles : le cas d'usage lancé s'arrête au premier, c'est lui qui est classé.
- Navigateur occupé : sans date, la durée d'attente relève de l'enchaînement (US-19).

## Hors périmètre
- Enchaîner, attendre réellement, reprendre ou arrêter une série : US-19, US-21.
- Nouvel essai automatique d'une écriture : exclu (risque de doublon).
- Nouvel essai automatique d'une lecture en échec : exclu, l'arrêt protège le compte d'un éventuel blocage.
- Compteur d'échecs par annonce : rien à compter tant qu'aucun nouvel essai n'est fait.
- Dressing illisible depuis la fiche (US-13) : inchangé.

## Décisions
- Décidé par Claude sur délégation de l'utilisateur (« fait le tout comme tu le sens », 2026-11-17) :
  - trois conduites seulement : attendre, passer l'annonce, tout arrêter ;
  - aucune relecture ni réécriture automatique en cas d'échec : un problème du compte ou de Vinted arrête tout, par prudence anti-robot ;
  - page illisible = tout arrêter : un changement de page chez Vinted ferait échouer toutes les annonces ;
  - la classification vit dans le domaine, à partir des refus du domaine ; US-19 l'utilisera pour piloter l'enchaînement ;
  - message d'issue incertaine déduit de l'étape relue, sans changer le contrat.
  - annonce inconnue (`ListingNotFound`) = passer l'annonce, relevé par la revue ; classement par motifs de type dans un seul `switch`.

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `FailureHandling` (conduite après un échec) : `WAIT` (avec date éventuelle), `SKIP_LISTING`, `STOP_RUN` ; `FailureHandling.of(RuntimeException)`.
- Réutilise `CadenceLimitReached`, `NotRelaunchable`, `StageChanged`, `BrowserBusy` et les refus des cas d'usage.

## Tests unitaires et d'intégration attendus
- Domaine : `FailureHandlingTest`.
- Web : `ListingRelaunchApiControllerTest`, page `WardrobePageTest`.
