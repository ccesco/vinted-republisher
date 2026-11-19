# US-40 – Vérifier et valider les sauvegardes en lot

**Statut** : implémentée
**Priorité** : V2
**Dépend de** : US-05 (lecture du formulaire), US-08 (vérification, validation), US-14 (étapes), US-18 (liste des annonces), US-19 (série « Tout lancer »), US-23 (journal)

## Contexte
La série « Tout lancer » (US-19) ne prend une annonce encore en ligne qu'une fois sa sauvegarde validée (étape `TO_DELETE`). US-08 impose une validation annonce par annonce, après l'aperçu. Sa vérification ne contrôle que les photos, la présence, le titre et le prix, et elle n'est pas gardée. Résultat : valider tout un dressing oblige à ouvrir chaque aperçu, la liste ne dit pas quelles sauvegardes échouent, et une annonce validée puis modifiée sur Vinted (description, taille…) reste validée.

Cette US revient sur trois décisions d'US-08 du 2026-11-11 : « pas de validation en lot », « aucun résultat de vérification n'est gardé » et « pas d'annulation de validation ».

## Objectif
En tant que membre, je veux faire relire sur Vinted toutes mes sauvegardes à valider ou déjà validées, valider d'un coup celles qui sont identiques et voir celles qui ne le sont pas, afin de lancer la série sur tout mon dressing sans risquer de supprimer une annonce mal sauvegardée.

## Comportement attendu
1. **Bouton « Vérifier et valider »** sur la page du dressing. Il est masqué pendant un scan, une sauvegarde, une série ou une vérification en cours. Pendant une vérification, « Tout lancer », le scan et la sauvegarde sont masqués.
2. **Annonces concernées**, fixées au lancement : les annonces présentes aux étapes « à valider » (`TO_VALIDATE`), « vérification en erreur » (`VERIFICATION_FAILED`) et « prête à supprimer » (`TO_DELETE`), par identifiant Vinted croissant.
3. **Relecture** : pour chacune, une à la fois, dans le Chrome piloté, l'outil ouvre `https://www.vinted.fr/items/<id>/edit` en lecture seule (aucun clic, aucune saisie, aucune soumission). Il lit le contenu avec les règles d'US-05, puis fait une pause aléatoire de 5 à 10 secondes avant l'annonce suivante.
4. **Vérification** : les contrôles d'US-08 (au moins une photo, photos intactes par empreinte, annonce présente), plus la comparaison à l'identique du contenu relu avec la sauvegarde actuelle. Sont comparés par identifiants et textes : titre, description, prix et devise, catégorie, marque, taille, état, couleurs, matières, format du colis et champs propres à la catégorie. Les libellés affichés ne sont pas comparés.
5. **Issue par annonce** :
   - réussie : la sauvegarde actuelle est validée. Si elle l'était déjà, la première date de validation est gardée (US-08). L'annonce passe ou reste à « prête à supprimer » ;
   - échouée : l'échec est gardé avec ses motifs et la date. Une validation existante est annulée. L'annonce passe à « vérification en erreur » et la série l'ignore.
6. **Ligne d'état** : « Vérification en cours : n / N », puis « Vérification terminée : <v> validées, <e> en erreur » ou « Vérification arrêtée : <raison> ». La page relit la liste à la fin.
7. **Étape « vérification en erreur »** : elle est déduite du dernier résultat de vérification de la version actuelle de la sauvegarde. Une nouvelle sauvegarde ramène l'annonce à « à valider ». Elle apparaît dans la colonne Étape et dans le filtre Sauvegarde de la liste (« En erreur de vérification »). La fiche de l'annonce (US-18b) affiche la date et les motifs du dernier échec.
8. **Journal** (US-23) : chaque vérification d'annonce est une action journalisée, avec son issue (réussie ou motifs).

## Critères d'acceptation
- [x] « Vérifier et valider » relit sur Vinted toutes les annonces présentes à valider, en erreur de vérification ou prêtes à supprimer, une à une, avec une pause de 5 à 10 s.
- [x] La relecture ne clique, ne saisit et ne soumet rien sur Vinted.
- [x] Une sauvegarde identique au formulaire relu (identifiants et textes), aux photos intactes, est validée sans aperçu.
- [x] Un écart sur n'importe quel champ comparé, une photo manquante ou modifiée, ou une annonce absente fait échouer la vérification, avec ses motifs.
- [x] Une annonce validée dont la vérification échoue perd sa validation et passe à « vérification en erreur » ; la série ne la prend pas.
- [x] Une nouvelle sauvegarde ramène une annonce en erreur à « à valider ».
- [x] Une session expirée, une réponse HTTP ≥ 400 autre que 404 ou un formulaire illisible arrêtent la vérification ; les résultats déjà obtenus restent.
- [x] Le bouton est exclusif du scan, de la sauvegarde et de la série.
- [x] Les résultats de vérification restent en base après un redémarrage.
- [x] Chaque vérification est inscrite au journal des actions.
- [x] Un autre site ne peut pas lancer la vérification (403).
- [x] Aucun test automatisé ne contacte le vrai site Vinted.
- [x] Une version dont la dernière vérification sur Vinted a échoué ne peut pas être validée depuis son aperçu.

AT-40-14 (relecture réelle, manuel) reste à faire par l'utilisateur sur son vrai compte.

## Tests d'acceptation

### AT-40-01 – Sauvegardes identiques validées
```gherkin
Étant donné les annonces 101 et 102, présentes, sauvegardées, non validées, aux photos intactes
Et Vinted affiche pour chacune exactement le contenu sauvegardé
Quand je lance « Vérifier et valider »
Alors 101 et 102 sont validées et passent à « prête à supprimer »
Et la ligne d'état affiche « Vérification terminée : 2 validées, 0 en erreur »
```
Test : `VerifyAndValidateBackupsAcceptanceTest.identical_backups_are_validated()`

### AT-40-02 – Champ changé sur Vinted
```gherkin
Étant donné l'annonce 101 sauvegardée avec la taille « M » et la description « Robe bleue »
Et Vinted affiche maintenant la taille « L » et la description « Robe bleue, portée une fois »
Quand je lance « Vérifier et valider »
Alors 101 n'est pas validée et passe à « vérification en erreur »
Et ses motifs indiquent la taille et la description, avec la valeur sauvegardée et la valeur lue
```
Test : `VerifyAndValidateBackupsAcceptanceTest.changed_field_fails_the_verification()`

### AT-40-03 – Annonce validée puis changée
```gherkin
Étant donné l'annonce 103 dont la sauvegarde est validée
Et dont le prix a changé sur Vinted
Quand je lance « Vérifier et valider »
Alors sa validation est annulée et elle passe à « vérification en erreur »
Et la série « Tout lancer » ne la prend pas
```
Test : `VerifyAndValidateBackupsAcceptanceTest.validated_listing_changed_on_vinted_loses_its_validation()`

### AT-40-04 – Annonce validée inchangée
```gherkin
Étant donné l'annonce 103 validée le 11/11 à 10 h, inchangée sur Vinted
Quand je lance « Vérifier et valider »
Alors elle reste validée le 11/11 à 10 h
```
Test : `VerifyAndValidateBackupsAcceptanceTest.unchanged_validated_listing_keeps_its_validation()`

### AT-40-05 – Photo manquante
```gherkin
Étant donné l'annonce 101 dont le fichier de la photo de rang 1 a disparu
Et Vinted affiche exactement le contenu sauvegardé
Quand je lance « Vérifier et valider »
Alors 101 passe à « vérification en erreur » avec « photo 1 manquante »
```
Test : `VerifyAndValidateBackupsAcceptanceTest.missing_photo_fails_the_verification()`

### AT-40-06 – Annonce absente de Vinted
```gherkin
Étant donné les annonces 101 et 102 à valider
Et le formulaire de 101 répond 404
Quand je lance « Vérifier et valider »
Alors 101 passe à « vérification en erreur » avec « annonce absente »
Et 102 est vérifiée ensuite
```
Test : `VerifyAndValidateBackupsAcceptanceTest.listing_gone_from_vinted_fails_and_the_verification_goes_on()`

### AT-40-07 – Panne générale
```gherkin
Étant donné les annonces 101, 102 et 103 à valider
Et la session Vinted expire à la lecture de 102
Quand je lance « Vérifier et valider »
Alors 101 est validée
Et la vérification s'arrête avec « Vérification arrêtée : session Vinted expirée »
Et 102 et 103 restent à « à valider »
```
Test : `VerifyAndValidateBackupsAcceptanceTest.expired_session_stops_and_keeps_the_results()`

### AT-40-08 – Nouvelle sauvegarde après un échec
```gherkin
Étant donné l'annonce 101 en « vérification en erreur »
Quand une nouvelle sauvegarde de 101 est enregistrée
Alors 101 revient à « à valider »
```
Test : `ListingStageTest.new_backup_clears_the_verification_failure()` et `VerifyAndValidateBackupsAcceptanceTest.new_backup_brings_the_listing_back_to_validation()`

### AT-40-09 – Rythme et lecture seule
```gherkin
Étant donné trois annonces à valider
Quand je lance « Vérifier et valider »
Alors chaque formulaire est ouvert une fois, avec une pause de 5 à 10 s entre deux
Et aucun clic, aucune saisie, aucune soumission n'a lieu
```
Test : `PlaywrightListingContentReaderTest.Forms_read_again` (page locale, `@Tag("browser")`) et `VerifyAndValidateBackupsAcceptanceTest.pauses_between_two_listings()`

### AT-40-10 – Exclusivité
```gherkin
Étant donné une série « Tout lancer » en cours
Quand j'ouvre la page du dressing
Alors le bouton « Vérifier et valider » est masqué
Et une demande directe à l'API est refusée (409)
```
Test : `BackupVerificationApiControllerTest.refused_while_another_action_runs()`, `VerifyAndValidateBackupsAcceptanceTest.refused_while_another_browser_action_or_a_run_is_in_progress()` et `WardrobePageTest.hides_verify_and_validate_while_a_run_is_in_progress()`

### AT-40-11 – Conservation et journal
```gherkin
Étant donné une vérification terminée avec un échec sur 101
Quand l'application redémarre
Alors 101 est toujours en « vérification en erreur » avec ses motifs
Et le journal des actions montre la vérification de chaque annonce et son issue
```
Test : `JpaBackupVerificationRepositoryTest.failures_survive_a_restart_as_they_were_saved()` (Testcontainers), `JpaActionJournalTest.every_journaled_action_can_be_written()` et `VerifyAndValidateBackupsAcceptanceTest.each_verification_is_journaled_with_its_outcome()`

### AT-40-12 – Pages
```gherkin
Étant donné une annonce validée par la vérification et une autre en erreur
Quand j'ouvre la liste puis la fiche de l'annonce en erreur
Alors la liste montre les étapes « prête à supprimer » et « vérification en erreur », filtrables
Et la fiche affiche la date et les motifs de l'échec
```
Test : `WardrobePageTest.shows_the_verification_progress_and_outcome()`, `ListingDetailPageTest.shows_the_verification_failure()`, `ListingDetailApiControllerTest.shows_the_last_verification_failure_of_the_current_backup()` et `VerifyAndValidateBackupsAcceptanceTest.listings_in_error_of_verification_can_be_filtered()`

### AT-40-13 – Demande venue d'un autre site
```gherkin
Étant donné une page d'un autre site ouverte dans le navigateur
Quand elle demande « Vérifier et valider »
Alors la demande est refusée (403) et rien n'est lancé
```
Test : `BackupVerificationApiControllerTest.request_from_another_site_is_refused()`

### AT-40-14 – Relecture réelle
```gherkin
Étant donné mon vrai dressing, sauvegardé, avec le profil local
Quand je lance « Vérifier et valider »
Alors les annonces non modifiées depuis leur sauvegarde sont validées
Et une annonce que je modifie sur Vinted avant de relancer passe en « vérification en erreur »
```
Test : manuel (vrai compte, lecture seule ; lancé par l'utilisateur).

### AT-40-15 – Pas de validation manuelle après un échec
```gherkin
Étant donné une annonce dont la dernière vérification de sa sauvegarde actuelle a échoué (taille changée sur Vinted)
Quand je demande la validation depuis l'aperçu
Alors la validation est refusée (422) avec les motifs de cet échec et rien n'est enregistré
Mais une nouvelle sauvegarde, vue dans l'aperçu, se valide comme avant
```
Test : `ValidateListingBackupAcceptanceTest.version_failing_online_cannot_be_validated_from_its_preview()` et `failure_of_a_replaced_version_does_not_block_the_new_backup()`

## Cas limites et erreurs
- Formulaire en 404 : l'annonce n'existe plus sur Vinted, échec « annonce absente », la vérification continue.
- Autre réponse HTTP ≥ 400, redirection vers la connexion, formulaire illisible ou attribut inconnu (US-05) : la vérification s'arrête, les résultats déjà obtenus restent, les annonces non lues gardent leur étape.
- Sauvegarde remplacée pendant la vérification : le résultat porte sur la version comparée ; s'il ne s'agit plus de la sauvegarde actuelle, il ne compte pas.
- Navigateur occupé par une autre action : refus au lancement (409).
- Aucune annonce concernée : « Vérification terminée : 0 validées, 0 en erreur ».
- Redémarrage pendant une vérification : elle s'arrête ; les résultats déjà écrits restent.

## Hors périmètre
- Resauvegarde automatique d'une annonce en erreur : le membre relance la sauvegarde.
- Comparaison des photos en ligne (nombre, retéléchargement).
- Comparaison des libellés affichés.
- Pause et arrêt de la vérification par le membre.

## Décisions
- Contenu de la vérification, décidé par l'utilisateur (2026-11-18) : la vérification doit contrôler toutes les informations saisies (description, taille…), pas seulement le titre et le prix. Choix : relecture sur Vinted. Chrome rouvre le formulaire de modification de l'annonce (`/items/<id>/edit`, lecture seule comme US-05) et compare tous les champs à la sauvegarde. Les contrôles d'US-08 (photos présentes et intactes, annonce présente) restent.
- Validation sans aperçu, décidé par l'utilisateur (2026-11-18) : une sauvegarde dont la relecture sur Vinted réussit peut être validée en lot sans que le membre ait ouvert son aperçu. Raison : la relecture prouve, champ par champ, que la sauvegarde est identique à ce que montre Vinted. Remplace la décision « Granularité » d'US-08 ; l'aperçu reste possible annonce par annonce.
- Une seule action, décidé par l'utilisateur (2026-11-18) : bouton « Vérifier et valider ». L'outil relit sur Vinted, une par une avec une pause aléatoire de 5 à 10 s, les annonces concernées. Il valide aussitôt celles dont la vérification réussit et garde l'échec des autres avec ses motifs. Raison : un résultat gardé puis validé plus tard pourrait être périmé.
- Périmètre, décidé par l'utilisateur (2026-11-18) : toutes les annonces présentes à l'étape « à valider » (`TO_VALIDATE`), et aussi celles déjà validées (`TO_DELETE`), pour vérifier qu'elles n'ont pas changé sur Vinted depuis.
- Validée puis changée, décidé par l'utilisateur (2026-11-18) : si la relecture d'une annonce validée échoue, sa validation est annulée, l'échec est gardé avec ses motifs et la série (US-19) ne la prend plus. Pour la reprendre : la resauvegarder, puis relancer « Vérifier et valider ». Revient sur « pas d'annulation de validation » d'US-08.
- Pas de contournement par l'aperçu, décidé par l'utilisateur (2026-11-19, à la revue) : tant que la dernière vérification sur Vinted de la version actuelle a échoué, la validation depuis l'aperçu (US-08) est refusée avec les motifs de cet échec. Raison : sinon l'annonce redeviendrait « prête à supprimer » et la série la supprimerait avec une sauvegarde qu'on sait différente de Vinted.
- Affichage, décidé par l'utilisateur (2026-11-18) : nouvelle étape de traitement « vérification en erreur » (`VERIFICATION_FAILED`), entre « prête à supprimer » et « à valider ». Elle apparaît dans la colonne Étape et le filtre de la liste, et ses motifs sur la fiche. La série l'ignore. Elle porte sur une version de la sauvegarde : une nouvelle sauvegarde ramène l'annonce à « à valider ».
- Comparaison, décidé par l'utilisateur (2026-11-18) : tout le contenu gardé par la sauvegarde (US-05), lu avec les mêmes règles. Sont comparés à l'identique, par identifiants et textes : titre, description, prix et devise, catégorie, marque, taille, état, couleurs, matières, format du colis et champs propres à la catégorie. Les libellés affichés ne sont pas comparés. Les photos restent contrôlées par leur empreinte locale (US-08), sans nouveau téléchargement.
- Déroulement, décidé par l'utilisateur (2026-11-18) : comme la sauvegarde du dressing (US-05). Arrière-plan dans le Chrome piloté, une annonce à la fois, ligne d'état « Vérification en cours : n / N ». Exclusif du scan, de la sauvegarde et de la série. Une annonce absente de Vinted échoue et la vérification continue ; une session expirée, une réponse HTTP ≥ 400 ou un formulaire illisible arrêtent tout, et les résultats déjà obtenus restent. Chaque vérification est inscrite au journal des actions.
- Proposés à la rédaction et acceptés en bloc par l'utilisateur (2026-11-18) :
  - Une réponse 404 du formulaire vaut « annonce absente » (l'annonce continue) ; les autres réponses HTTP ≥ 400 arrêtent tout. Précise la décision « Déroulement ».
  - Les annonces déjà « en erreur » sont reprises par une nouvelle vérification, même sans nouvelle sauvegarde : un échec dû à une annonce absente ou à un changement annulé sur Vinted peut alors se lever.
  - Persistance : table `backup_verification`, une ligne par annonce (identifiant Vinted en clé primaire), remplacée à chaque vérification : `backup_saved_at` (version comparée), `verified_at`, `passed`, `failures` (`jsonb`). Un résultat ne vaut que si `backup_saved_at` est la date de la sauvegarde actuelle, comme `backup_validation`.
  - Annulation d'une validation : suppression de la ligne `backup_validation` de l'annonce, dans la même transaction que l'écriture de l'échec.
  - Journal : nouvelle action `BACKUP_VERIFICATION` (« vérification de la sauvegarde »).
  - API : `POST /api/backup-verifications` (202 lancée ; 409 si un scan, une sauvegarde, une série ou une vérification est en cours ; 403 autre origine) et `GET /api/backup-verifications/current` (état, n / N, validées, en erreur, raison d'arrêt). `ListingDetail` gagne `lastVerification` (`verifiedAt`, `failures`). `ListingStage` gagne `VERIFICATION_FAILED`. `VerificationFailure` gagne `FIELD_CHANGED` (champ, valeur sauvegardée, valeur lue).
  - US-08 et US-14 sont mises à jour dans le même commit que le code.
- Filtre, décidé par l'utilisateur (2026-11-18) pendant l'implémentation : la liste n'ayant pas de filtre d'étape, les annonces en erreur se filtrent par une nouvelle valeur du filtre Sauvegarde, « En erreur de vérification » (`BackupFilter.VERIFICATION_FAILED`).
- Journal : la raison d'une vérification échouée réunit ses codes, séparés par des virgules, un champ changé suivi de son nom (`FIELD_CHANGED:SIZE, PHOTO_MISSING`).
- Ligne d'état d'un arrêt : « session Vinted expirée (connectez-vous, puis relancez) », « navigateur indisponible », « Vinted est inaccessible », « formulaire illisible » ou « échec inattendu ».

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `BackupVerification` (vérification de la sauvegarde) : gagne la comparaison avec le contenu relu ; nouvel échec `FIELD_CHANGED` (champ, valeur sauvegardée, valeur lue).
- `VerificationResult` (résultat de vérification, nouveau) : identifiant Vinted, version de la sauvegarde comparée, date, échecs ; vaut seulement pour cette version.
- `ListingStage` gagne `VERIFICATION_FAILED` (vérification en erreur), déduit après `TO_DELETE` et avant `TO_VALIDATE`.
- Cas d'usage : `VerifyAndValidateBackups` (vérifier et valider), exclusif des autres actions navigateur.
- Ports : `BackupVerificationRepository` (résultats ; enregistrer un échec annule la validation dans la même transaction, d'où pas de méthode d'annulation sur `BackupValidationRepository`) ; `ListingContentReader` gagne `readForms` (lecture du formulaire selon US-05, sans les photos, 404 rendu comme annonce absente) ; `ListingFormReading` (formulaire relu ou absent).
- `ListingFieldDifference` et `ListingFormField` passent de `domain.republication` à `domain.backup` : la comparaison du contenu à la sauvegarde sert à US-13 et à US-40, sans cycle entre paquets.
- Avancement : `BackupVerificationProgress` (`BackupVerificationStatus`, `BackupVerificationStopReason`), gardé en mémoire ; `BrowserAction.VERIFYING_BACKUPS`.
- Glossaire : ajouter `VerifyAndValidateBackups`, `VerificationResult`, `BackupVerificationRepository`, `FIELD_CHANGED`, `VERIFICATION_FAILED`, `BACKUP_VERIFICATION` ; mettre à jour `BackupVerification`, `BackupValidation`, `ListingStage`.

## Tests unitaires et d'intégration attendus
- `domain` : `BackupVerificationTest` (chaque champ comparé, libellés ignorés, cumul), `ListingStageTest` (`VERIFICATION_FAILED`, nouvelle version), `VerificationResultTest`.
- `application` : `VerifyAndValidateBackupsAcceptanceTest` ; faux `InMemoryBackupVerificationRepository`.
- `adapter/out/persistence` : `JpaBackupVerificationRepositoryTest` (annulation de la validation comprise), changeset Liquibase `016` (qui ajoute aussi `BACKUP_VERIFICATION` et `STATISTICS_REFRESH`, oublié jusqu'ici, à la contrainte du journal).
- `adapter/out/browser` : réutilisation de la lecture du formulaire (`@Tag("browser")`, page locale).
- `adapter/in/web` : `BackupVerificationApiControllerTest`, `WardrobePageTest`, `ListingDetailPageTest`, `ListingInventoryApiControllerTest` (filtre d'étape).
