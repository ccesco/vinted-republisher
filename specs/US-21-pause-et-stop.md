# US-21 – Pause et stop
**Statut** : implémentée
**Priorité** : MVP
**Dépend de** : US-19 (série automatique)

## Contexte
Une série « Tout lancer » (US-19) peut durer des heures : attentes de republication, limites de cadence. Aujourd'hui, seul un redémarrage de l'application l'arrête, au risque de couper une action en plein milieu. Le membre doit pouvoir la mettre en pause, la reprendre ou l'arrêter depuis la page, sans jamais interrompre une action commencée sur Vinted.

## Objectif
En tant que membre, je veux mettre en pause, reprendre ou arrêter la série en cours, afin de garder la main sur ce qui se passe sur mon compte.

## Comportement attendu
1. **Pause** : demandée pendant une série en cours ou en attente. La relance en cours se termine (jamais coupée), puis la série passe en `PAUSED` et ne relance plus rien. Une attente en cours (cadence, date de republication, navigateur occupé) est interrompue.
2. **Reprise** : depuis `PAUSED` (ou pause demandée et pas encore effective), la série reprend au tour suivant, en relisant l'inventaire. Une attente interrompue est refaite si elle reste nécessaire (la relance la redemande).
3. **Arrêt** : demandé pendant une série en cours, en attente ou en pause. La relance en cours se termine, puis la série s'arrête (`STOPPED`, raison `STOPPED_BY_MEMBER`). Une attente ou une pause est interrompue.
4. **Demandes sans effet** : pause, reprise ou arrêt sans série en cours (aucune, terminée, arrêtée) sont ignorés ; reprise sans pause est ignorée. « Tout lancer » reste ignoré tant qu'une série est en cours, en attente ou en pause.
5. **Aperçu** : nouvel état `PAUSED` ; `pauseRequested` et `stopRequested` tant que la demande attend la fin de la relance en cours.
6. **Page du dressing** : boutons « Pause » et « Arrêter » pendant une série en cours ou en attente ; « Reprendre » et « Arrêter » en pause. Ligne d'état : « Traitement en pause. », « … (pause demandée, à la fin de l'action en cours) », « … (arrêt demandé, à la fin de l'action en cours) », « Traitement arrêté à votre demande. ».
7. **Attente interruptible** : l'attente vérifie au moins chaque seconde si une pause ou un arrêt est demandé.

## Contrat d'API
- `POST /api/automatic-run/pause`, `POST /api/automatic-run/resume`, `POST /api/automatic-run/stop` → `202` `AutomaticRunOverview` (demande prise en compte ou ignorée) ; `403` autre site.
- `RunStatus` gagne `PAUSED` ; `RunStopReason` gagne `STOPPED_BY_MEMBER`.
- `AutomaticRunOverview` gagne `pauseRequested` et `stopRequested` (booléens, obligatoires).

## Critères d'acceptation
- [x] Une pause laisse finir la relance en cours, puis la série ne relance plus rien jusqu'à la reprise.
- [x] Une reprise relance la série au tour suivant.
- [x] Un arrêt laisse finir la relance en cours, puis arrête la série (`STOPPED_BY_MEMBER`).
- [x] Pause et arrêt interrompent une attente.
- [x] Les demandes sans série en cours, et « Tout lancer » pendant une pause, sont ignorés.
- [x] L'API et la page proposent pause, reprise et arrêt selon l'état et montrent les demandes en attente.

## Tests d'acceptation

### AT-21-01 – Pause à la fin de la relance en cours, puis reprise
```gherkin
Étant donné une série en cours sur deux annonces prêtes
Quand le membre demande une pause pendant la suppression de la première
Alors la première suppression se termine, la série passe en pause et la seconde n'est pas touchée
Quand il reprend
Alors la seconde est supprimée et la série continue
```
Test : `AutomaticRunAcceptanceTest.pauses_after_the_current_relaunch_then_resumes()`

### AT-21-02 – Arrêt à la fin de la relance en cours
```gherkin
Étant donné une série en cours sur deux annonces prêtes
Quand le membre demande l'arrêt pendant la suppression de la première
Alors la première suppression se termine, la seconde n'est pas touchée
Et la série est arrêtée, raison STOPPED_BY_MEMBER
```
Test : `AutomaticRunAcceptanceTest.stops_after_the_current_relaunch()`

### AT-21-03 – Arrêt pendant une attente
```gherkin
Étant donné une série qui attend la fin d'une limite de cadence
Quand le membre demande l'arrêt
Alors l'attente est interrompue, rien n'est supprimé, la série est arrêtée (STOPPED_BY_MEMBER)
```
Test : `AutomaticRunAcceptanceTest.stop_interrupts_a_wait()`

### AT-21-04 – Pause pendant une attente
```gherkin
Étant donné une série qui attend la fin d'une limite de cadence
Quand le membre demande une pause, puis reprend après la fin de la limite
Alors la série passe en pause sans rien supprimer, puis supprime l'annonce à la reprise
```
Test : `AutomaticRunAcceptanceTest.pause_interrupts_a_wait()`

### AT-21-05 – Demandes sans effet
```gherkin
Étant donné aucune série en cours, ou une série en pause
Quand le membre demande pause, reprise ou arrêt sans série, ou « Tout lancer » pendant la pause
Alors rien ne change
```
Test : `AutomaticRunAcceptanceTest.requests_without_effect_are_ignored()`, `AutomaticRunAcceptanceTest.a_pause_asked_for_again_while_paused_is_ignored()` ; arrêt et pause demandés ensemble : `AutomaticRunAcceptanceTest.stop_wins_over_a_pause_asked_for_during_the_same_relaunch()`

### AT-21-06 – Attente interruptible
```gherkin
Étant donné une attente d'une heure
Quand une pause ou un arrêt est demandé
Alors l'attente se termine dans la seconde
```
Test : `SleepingRunWaitingTest.returns_early_when_interrupted()`

### AT-21-07 – Contrat
Test : `AutomaticRunApiControllerTest` (pause, reprise, arrêt : 202 et aperçu ; `PAUSED`, demandes en attente, `STOPPED_BY_MEMBER`).

### AT-21-08 – Page
```gherkin
Étant donné une série en cours, puis en pause
Quand le membre clique sur « Pause », « Reprendre » puis « Arrêter »
Alors chaque demande est envoyée, et la page montre les boutons et l'état qui conviennent
```
Test : `WardrobePageTest.pauses_resumes_and_stops_a_run()`

## Cas limites et erreurs
- Pause puis arrêt : l'arrêt l'emporte.
- Arrêt pendant une écriture sur Vinted : l'écriture se termine et s'enregistre normalement ; jamais de coupure entre le clic et l'enregistrement.
- Redémarrage pendant une pause : l'aperçu repart à `IDLE` (US-19).

## Hors périmètre
- Annuler la relance en cours.
- Pause programmée, reprise automatique.

## Décisions
- Décidé par Claude sur délégation de l'utilisateur (« fait le tout comme tu le sens », 2026-11-17) :
  - pause et arrêt prennent effet entre deux relances, jamais pendant : aucune écriture coupée ;
  - pause et arrêt interrompent les attentes, vérifiées chaque seconde ;
  - « Tout lancer » ignoré pendant une pause : reprendre ou arrêter d'abord ;
  - demandes en attente visibles dans l'aperçu.
  - relevé par la revue : la série sort de sa boucle sur ses propres décisions, sans relire son état (un nouveau « Tout lancer » juste après un arrêt ne fait jamais tourner deux séries) ; une reprise demandée juste avant le passage en pause l'annule.

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `RunStatus.PAUSED` (en pause), `RunStopReason.STOPPED_BY_MEMBER` (arrêtée à la demande du membre).
- `AutomaticRun` : demandes de pause et d'arrêt en attente.
- `RunWaiting.until(Instant, BooleanSupplier interrupted)` : attente interruptible.
- Application : `LaunchAutomaticRun.pause()`, `resume()`, `stop()`.

## Tests unitaires et d'intégration attendus
- Application : `AutomaticRunAcceptanceTest`.
- Adaptateur : `SleepingRunWaitingTest`.
- Web : `AutomaticRunApiControllerTest`, page `WardrobePageTest`.
