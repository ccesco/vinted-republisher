# US-10 – Mode dry-run
**Statut** : validée
**Priorité** : MVP
**Dépend de** : US-09 (suppression), livrée avec elle

## Contexte
La suppression (US-09) est la première action irréversible sur le vrai compte Vinted. Le dry-run permet de faire tourner toute la mécanique (contrôles, ouverture de la page, recherche du bouton) sans rien supprimer, pour vérifier l'outil avant la première vraie suppression. Décidé avec US-09 : actif par défaut.

## Objectif
En tant que membre, je veux pouvoir lancer une suppression « à blanc », qui fait tout sauf supprimer, afin de vérifier l'outil sans risque pour mon dressing.

## Comportement attendu
1. **Démarrage** : l'application lit `DELETION_DRY_RUN`. Absente → dry-run actif ; `true` → dry-run actif ; `false` → suppressions réelles ; toute autre valeur → l'application ne démarre pas (message : valeurs acceptées `true` ou `false`). Le mode ne change plus jusqu'au prochain démarrage.
2. **Affichage** : l'aperçu de la sauvegarde affiche en permanence « Dry-run actif : aucune suppression réelle » ou « Suppressions réelles activées ».
3. **Suppression en dry-run** : « Supprimer définitivement » déroule US-09 à l'identique (contrôles hors navigateur, verrou `DELETING`, page de l'annonce relue, session connectée, bouton de suppression trouvé), puis s'arrête avant le moindre clic de suppression. L'onglet est fermé, le verrou libéré.
4. **Résultat** : l'aperçu affiche « Dry-run réussi : contrôles réussis, annonce relue sur Vinted, bouton de suppression trouvé. Rien n'a été supprimé. » Un échec donne le même refus qu'une vraie suppression. Rien n'est enregistré : l'annonce reste dans son état d'avant.
5. **Revérification** : « Revérifier sur Vinted » (US-09) ne fait que lire ; elle fonctionne de la même façon dans les deux modes.

## Contrat d'API
Contract first : `src/main/resources/static/openapi/vinted-republisher.yaml`.

| Méthode et chemin | operationId | Changement |
| --- | --- | --- |
| `GET /api/listings/{listingId}/backup` | `showListingBackup` | `ListingBackupView` gagne `deletionDryRun` (booléen, toujours présent) |
| `POST /api/listings/{listingId}/deletion` | `deleteListing` | en dry-run réussi : 200 `ListingBackupView` avec `dryRunPassed: true` (absent sinon), `deletion` inchangé ; refus identiques à US-09 |

Aucun moyen de changer le mode par l'API.

## Critères d'acceptation
- [ ] Sans `DELETION_DRY_RUN`, ou avec `true`, aucune suppression réelle n'est possible.
- [ ] Seule la valeur `false` active les suppressions réelles ; toute autre valeur empêche le démarrage.
- [ ] En dry-run, aucun clic de suppression n'est fait sur Vinted, et tous les contrôles d'US-09 sont faits.
- [ ] Un dry-run réussi ou refusé ne laisse aucune trace en base : l'annonce reste dans son état d'avant.
- [ ] L'aperçu affiche toujours le mode actif.
- [ ] Le mode ne se change ni par l'interface ni par l'API.

## Tests d'acceptation
### AT-10-01 – Dry-run réussi
```gherkin
Étant donné le dry-run actif
Et une annonce présente, active sur Vinted, dont la sauvegarde actuelle est validée et vérifiée
Quand je confirme « Supprimer définitivement »
Alors l'outil relit l'annonce et trouve le bouton de suppression
Et aucun clic de suppression n'est fait
Et la réponse indique « dry-run réussi, rien supprimé »
Et aucune suppression n'est enregistrée, l'annonce reste présente et validée
```
Test : `DeleteListingAcceptanceTest.dry_run_checks_everything_and_deletes_nothing()`

### AT-10-02 – Dry-run refusé
```gherkin
Étant donné le dry-run actif
Et une annonce validée dont le prix a changé sur Vinted
Quand je confirme « Supprimer définitivement »
Alors la demande est refusée avec « prix changé », comme une vraie suppression
Et aucune suppression n'est enregistrée
```
Test : `DeleteListingAcceptanceTest.dry_run_refuses_like_a_real_deletion()`

### AT-10-03 – Mode selon la configuration
```gherkin
Étant donné DELETION_DRY_RUN <valeur>
Quand l'application démarre
Alors <résultat>

Exemples :
| valeur   | résultat                                    |
| absente  | dry-run actif                               |
| true     | dry-run actif                               |
| false    | suppressions réelles activées               |
| False    | l'application ne démarre pas, message clair |
| non      | l'application ne démarre pas, message clair |
```
Test : `DeletionModeConfigurationTest` (paramétré)

### AT-10-04 – Mode affiché
```gherkin
Étant donné le dry-run actif, puis les suppressions réelles activées
Quand j'ouvre l'aperçu d'une sauvegarde
Alors je vois « Dry-run actif : aucune suppression réelle », puis « Suppressions réelles activées »
Et après un dry-run réussi, « Dry-run réussi … Rien n'a été supprimé. »
```
Test : `ListingBackupPageTest` (mode et résultat du dry-run)

### AT-10-05 – Adaptateur en dry-run sur une fausse page locale
```gherkin
Étant donné une fausse page d'annonce locale avec son bouton de suppression
Quand l'adaptateur navigateur la parcourt en dry-run
Alors il trouve le bouton sans cliquer sur rien
Et la page ne reçoit aucune demande de suppression
```
Test : `PlaywrightListingDeleterTest` (dry-run, `@Tag("browser")`)

### AT-10-06 – Dry-run réel
```gherkin
Étant donné le dry-run actif, ma session connectée, et quelques annonces validées que je choisis
Quand je lance « Supprimer définitivement » sur chacune
Alors l'outil affiche « dry-run réussi » pour chacune
Et rien n'a changé sur mon dressing Vinted
```
Test : manuel (vrai compte, après AT-09-20, avant AT-09-21).

## Cas limites et erreurs
- Variable modifiée pendant que l'application tourne : sans effet jusqu'au redémarrage.
- Variable vide (`DELETION_DRY_RUN=`) : valeur invalide, l'application ne démarre pas.
- Dry-run sur une annonce dont la suppression est non confirmée ou confirmée : même refus qu'US-09, Chrome n'est pas ouvert.
- Passage du dry-run aux suppressions réelles : aucune trace de dry-run à reprendre ; la première vraie suppression refait tous les contrôles.

## Hors périmètre
- Dry-run de la republication (US-12) : à décider dans sa spec.
- Journal des passages en dry-run (US-23).
- Changement du mode depuis l'interface.

## Décisions
- Repris d'US-09 (2026-11-11) : actif par défaut ; en dry-run, la suppression fait tous les contrôles, ouvre la page, trouve le bouton de suppression, puis s'arrête avant le moindre clic de suppression (« dry-run : rien supprimé ») ; rien n'est enregistré dans `listing_deletion`.
- Réglage, décidé par l'utilisateur (2026-11-11) : configuration seulement, `DELETION_DRY_RUN` (`true` par défaut et dans `.env.example`) ; passer en suppressions réelles = modifier `.env` et redémarrer. Aucun moyen de le changer depuis l'interface ou l'API. L'aperçu affiche l'état en permanence : « Dry-run actif : aucune suppression réelle » ou « Suppressions réelles activées ».
- Valeur par défaut, décidé par l'utilisateur (2026-11-11) : la variable est facultative, y compris en production (contrairement aux autres) ; absente → dry-run actif, suppressions réelles non activées. Raison : sécuriser au maximum, un oubli ne mène jamais à une suppression réelle.
- Valeur invalide, décidé par l'utilisateur (2026-11-11) : seules `true` et `false` sont acceptées ; toute autre valeur empêche l'application de démarrer, avec un message qui donne les valeurs acceptées.
- Résultat, décidé par l'utilisateur (2026-11-11) : dry-run réussi → « Dry-run réussi : contrôles réussis, annonce relue sur Vinted, bouton de suppression trouvé. Rien n'a été supprimé. » ; échec → même refus qu'une vraie suppression. Rien n'est gardé : résultat renvoyé et affiché seulement, l'annonce reste dans son état d'avant (historique : US-23).
- Proposés à la rédaction et validés par l'utilisateur avec la spec (2026-11-11) : la valeur est comparée exactement (`False` est invalide) ; une variable vide est invalide ; « Revérifier sur Vinted » fonctionne pareil dans les deux modes (lecture seule) ; contrat d'API ci-dessus (`deletionDryRun`, `dryRunPassed`).

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `DeletionMode` (mode de suppression) : `DRY_RUN` ou `REAL`, fixé au démarrage.
- `DeleteListing` (US-09) reçoit le mode ; en `DRY_RUN`, le port `ListingDeleter` est appelé pour relire l'annonce et trouver le bouton, jamais pour supprimer.
- Glossaire : ajouter `DeletionMode` (mode de suppression : dry-run ou réel).

## Tests unitaires et d'intégration attendus
- `domain` / `application` : `DeleteListingAcceptanceTest` (AT-10-01, AT-10-02) avec le faux `FakeListingDeleter`, qui échoue si on lui demande de supprimer en dry-run.
- `adapter/config` : `DeletionModeConfigurationTest` (absente, `true`, `false`, invalide, vide).
- `adapter/out/browser` : `PlaywrightListingDeleterTest` (dry-run : aucun clic, aucune requête de suppression).
- `adapter/in/web` : `ListingBackupApiControllerTest` (`deletionDryRun`, `dryRunPassed`), `ListingBackupPageTest` (bandeau, résultat).
