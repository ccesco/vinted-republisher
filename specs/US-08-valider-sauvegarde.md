# US-08 – Valider la sauvegarde
**Statut** : implémentée
**Priorité** : MVP
**Dépend de** : US-04, US-05, US-06

## Contexte
La suppression d'une annonce sur Vinted (US-09) est irréversible : elle ne doit avoir lieu qu'après une sauvegarde validée. US-05 et US-06 gardent le contenu et les photos de chaque annonce ; rien ne dit encore qu'une sauvegarde est intacte, à jour, ni qu'elle a été vue par le membre. US-06 a renvoyé ici l'affichage des photos gardées.

## Objectif
En tant que membre, je veux voir la sauvegarde de chaque annonce, vérifiée par l'outil, et la valider moi-même, afin de ne supprimer une annonce que si je peux la republier à l'identique.

## Comportement attendu
Aucune action navigateur : US-08 ne lit que la base et les fichiers gardés, n'ouvre pas Chrome et ne contacte pas Vinted.
1. **Page du dressing** : chaque annonce sauvegardée gagne un lien « Voir la sauvegarde » et, si sa sauvegarde actuelle est validée, « validée le … ».
2. **Aperçu** : la page `/listing-backup.html?id=<identifiant Vinted>` affiche, pour cette annonce :
   - le résultat de la vérification automatique, faite à l'ouverture : « vérification réussie », ou la liste des échecs ;
   - toutes les photos gardées, en grand, dans l'ordre (couverture en premier) ;
   - tout le contenu sauvegardé par ses libellés : titre, description, prix, catégorie, marque, état, taille, couleurs, matières, format du colis, détails propres à la catégorie ; pas les identifiants Vinted ;
   - la date de sauvegarde, et « validée le … » si cette sauvegarde est validée ;
   - le bouton « Valider », actif seulement si la vérification a réussi et que la sauvegarde n'est pas déjà validée.
3. **Vérification automatique** : trois contrôles.
   - Photos présentes : la sauvegarde a au moins une photo (le contenu complet est garanti par US-05).
   - Photos intactes : chaque fichier gardé existe et son empreinte SHA-256 est celle enregistrée.
   - Sauvegarde à jour : l'annonce est présente dans le dressing, et le titre et le prix de la sauvegarde sont identiques à ceux du dernier scan.
4. **Validation** : « Valider » envoie la date de la sauvegarde vue. L'outil refait la vérification, puis enregistre la validation de cette version (date de sauvegarde, date de validation). Refusée si la sauvegarde a été remplacée depuis l'ouverture de l'aperçu, ou si la vérification échoue ; rien n'est alors enregistré.
5. **Annulation** : une validation ne vaut que pour la version de la sauvegarde validée. Dès que la sauvegarde de l'annonce est remplacée (US-05/06), l'annonce n'est plus validée.

## Contrat d'API
Contract first : `src/main/resources/static/openapi/vinted-republisher.yaml`.

| Méthode et chemin | operationId | Réponse |
| --- | --- | --- |
| `GET /api/wardrobe` | `showWardrobe` | chaque `Listing` gagne `validatedAt` (date-time ; absent = sauvegarde actuelle non validée) |
| `GET /api/listings/{listingId}/backup` | `showListingBackup` | 200 `ListingBackupView` : `vintedId`, `savedAt`, `validatedAt` (absent = non validée), `verification` {`passed` booléen, `failures` liste de `VerificationFailure`}, `content` (libellés, prix, détails), `photos` liste ordonnée {`rank`, `url`} ; 404 si l'annonce n'a pas de sauvegarde |
| `GET /api/listings/{listingId}/backup/photos/{rank}` | `showListingBackupPhoto` | 200, les octets du fichier gardé avec son type (`image/webp`, `image/jpeg`, `image/png`) ; 404 si absent |
| `PUT /api/listings/{listingId}/backup/validation` | `validateListingBackup` | corps {`savedAt`} ; 200 `ListingBackupView` après validation (renvoyer la même version déjà validée garde la première date de validation) ; 400 sans `savedAt` ; 404 sans sauvegarde ; 409 `BACKUP_REPLACED` si `savedAt` n'est plus la sauvegarde actuelle ; 422 `VERIFICATION_FAILED` avec les échecs ; 403 si la demande vient d'un autre site |

`VerificationFailure` : `NO_PHOTO`, `PHOTO_MISSING` (avec son rang), `PHOTO_ALTERED` (avec son rang), `LISTING_ABSENT`, `TITLE_CHANGED`, `PRICE_CHANGED` (avec la valeur sauvegardée et celle du dernier scan).

- [x] L'aperçu montre toutes les photos gardées dans leur ordre et tout le contenu sauvegardé par ses libellés.
- [x] La vérification signale une sauvegarde sans photo, un fichier manquant, un fichier dont l'empreinte a changé, une annonce absente du dressing, un titre ou un prix différent du dernier scan.
- [x] Une sauvegarde ne se valide qu'annonce par annonce, par le membre, et seulement si la vérification refaite au moment de valider réussit.
- [x] Une validation porte sur la version vue : si la sauvegarde a été remplacée entre-temps, elle est refusée.
- [x] Remplacer la sauvegarde d'une annonce annule sa validation.
- [x] La page du dressing affiche la date de validation de chaque annonce validée.
- [x] Les validations restent en base après un redémarrage.
- [x] US-08 n'ouvre pas Chrome, ne contacte pas Vinted et ne modifie aucun fichier gardé.
- [x] Un autre site ouvert dans le navigateur ne peut pas valider une sauvegarde.
- [x] Aucun test automatisé ne contacte le vrai site Vinted.
 d'acceptation
### AT-08-01 – Aperçu d'une sauvegarde intacte
```gherkin
Étant donné une annonce présente, sauvegardée avec 3 photos, dont le titre et le prix sont ceux du dernier scan
Quand j'ouvre l'aperçu de sa sauvegarde
Alors la vérification est réussie
Et je vois ses 3 photos dans l'ordre et tout son contenu par ses libellés
Et elle n'est pas validée
```
Test : `ValidateListingBackupAcceptanceTest.preview_shows_a_verified_backup()`

### AT-08-02 – Validation
```gherkin
Étant donné l'aperçu d'une sauvegarde dont la vérification est réussie
Quand je clique sur « Valider »
Alors la sauvegarde est validée, avec la date de validation
Et la page du dressing affiche « validée le … » pour cette annonce
```
Test : `ValidateListingBackupAcceptanceTest.member_validates_a_verified_backup()`

### AT-08-03 – Photo manquante
```gherkin
Étant donné une annonce sauvegardée dont le fichier de la photo de rang 1 a disparu
Quand j'ouvre l'aperçu ou que je demande la validation
Alors la vérification échoue avec « photo 1 manquante »
Et la validation est refusée, rien n'est enregistré
```
Test : `ValidateListingBackupAcceptanceTest.missing_photo_fails_the_verification()`

### AT-08-04 – Photo modifiée
```gherkin
Étant donné une annonce sauvegardée dont le fichier de la photo de rang 0 a été modifié
Quand je demande la validation
Alors la vérification échoue avec « photo 0 modifiée »
Et la validation est refusée
```
Test : `ValidateListingBackupAcceptanceTest.altered_photo_fails_the_verification()`

### AT-08-05 – Sauvegarde périmée
```gherkin
Étant donné une annonce sauvegardée au titre « Robe bleue » et au prix 12,00 €
Et le dernier scan l'a lue au titre « Robe bleue M » et au prix 10,00 €
Quand j'ouvre l'aperçu
Alors la vérification échoue sur le titre et sur le prix, avec les deux valeurs de chacun
```
Test : `ValidateListingBackupAcceptanceTest.backup_differing_from_the_last_scan_fails_the_verification()`

### AT-08-06 – Annonce absente
```gherkin
Étant donné une annonce sauvegardée qui n'est plus présente dans le dressing
Quand je demande la validation
Alors la vérification échoue avec « annonce absente du dressing »
```
Test : `ValidateListingBackupAcceptanceTest.absent_listing_fails_the_verification()`

### AT-08-07 – Sauvegarde remplacée pendant l'aperçu
```gherkin
Étant donné l'aperçu ouvert sur la sauvegarde du 10/11
Et une sauvegarde de l'annonce enregistrée le 11/11 depuis
Quand je clique sur « Valider »
Alors la validation est refusée car la sauvegarde a été remplacée
Et aucune version n'est validée
```
Test : `ValidateListingBackupAcceptanceTest.replaced_backup_cannot_be_validated_from_a_stale_preview()`

### AT-08-08 – Nouvelle sauvegarde annule la validation
```gherkin
Étant donné une annonce dont la sauvegarde est validée
Quand une nouvelle sauvegarde de l'annonce est enregistrée
Alors l'annonce n'est plus validée
```
Test : `ValidateListingBackupAcceptanceTest.new_backup_cancels_the_validation()`

### AT-08-09 – Validation répétée
```gherkin
Étant donné une sauvegarde validée le 11/11 à 10 h
Quand je la valide de nouveau
Alors elle reste validée le 11/11 à 10 h
```
Test : `ValidateListingBackupAcceptanceTest.validating_twice_keeps_the_first_validation()`

### AT-08-10 – Annonce sans sauvegarde
```gherkin
Étant donné une annonce jamais sauvegardée
Quand je demande son aperçu ou sa validation
Alors l'outil répond « aucune sauvegarde » (404)
```
Test : `ListingBackupApiControllerTest.listing_without_backup_is_not_found()`

### AT-08-11 – Conservation en base
```gherkin
Étant donné une validation enregistrée
Quand l'application redémarre
Alors elle est relue à l'identique, rattachée à la même version de la sauvegarde
```
Test : `JpaBackupValidationRepositoryTest` (Testcontainers)

### AT-08-12 – Photos servies
```gherkin
Étant donné une photo gardée en WebP
Quand la page la demande
Alors l'outil renvoie le fichier à l'octet près, en image/webp, sans le modifier
```
Test : `ListingBackupApiControllerTest.serves_the_kept_file_byte_for_byte_with_its_type()`, `ShowListingBackupPhotoTest` et `FilePhotoStorageTest.reads_a_kept_photo_byte_for_byte()`

### AT-08-13 – Pages
```gherkin
Étant donné une annonce sauvegardée et validée, et une autre sauvegardée dont la vérification échoue
Quand j'ouvre la page du dressing puis l'aperçu de chacune
Alors le dressing affiche « Voir la sauvegarde » pour les deux et « validée le … » pour la première
Et l'aperçu de la seconde affiche ses échecs et un bouton « Valider » inactif
```
Test : `ListingBackupPageTest` (aperçu, échecs, validation, refus) et `WardrobePageTest.shows_validation()`

### AT-08-14 – Demande venue d'un autre site
```gherkin
Étant donné une page d'un autre site ouverte dans le navigateur
Quand elle demande la validation d'une sauvegarde
Alors la demande est refusée (403) et rien n'est enregistré
```
Test : `ListingBackupApiControllerTest.validation_requested_by_another_site_is_refused()`

### AT-08-15 – Relecture réelle
```gherkin
Étant donné ma vraie sauvegarde, faite après un scan, avec le profil local
Quand j'ouvre l'aperçu de quelques annonces
Alors la vérification est réussie, photos et contenu sont ceux de mes annonces sur Vinted
Et je peux valider l'une d'elles
```
Test : manuel (vraies données, contrôle visuel ; aucun accès à Vinted).
Non fait à la clôture (2026-11-11) : l'utilisateur a choisi de passer l'US en « implémentée » avec ce test encore ouvert. À vérifier en priorité : titre du scan identique à celui du formulaire, sinon toute vérification échoue (décision « comparaison exacte » à rouvrir). Tout écart constaté se traite par une mise à jour de cette spec.
Recette du 2026-11-11 (10 annonces sauvegardées) : vérification réussie pour les 10, la comparaison exacte des titres tient ; validation d'une annonce affichée dans le dressing ; un fichier photo retiré donne « photo 2 manquante », remis, la vérification repasse ; une nouvelle sauvegarde annule la validation. Reste ouvert : contrôle visuel des photos et du contenu par l'utilisateur.
Contrôles restants : passé, confirmé par l'utilisateur le 2026-11-11, sur les 10 annonces de la recette (scan et sauvegarde du dressing complet non menés à terme).

## Cas limites et erreurs
- Sauvegarde faite avant la conservation des photos (aucune photo) : échec « aucune photo », à refaire.
- Annonce absente du dressing : aperçu possible, validation refusée (rien à supprimer sur Vinted).
- Titre ou prix différent du dernier scan (annonce modifiée sur Vinted, ou scan plus ancien que la sauvegarde mais d'une autre valeur) : échec, il faut rescanner puis resauvegarder. La comparaison est exacte (texte et montant + devise).
- Sauvegarde ou scan en cours : l'aperçu et la validation restent possibles ; une sauvegarde qui remplace l'annonce entre-temps fait refuser la validation (version vue).
- Fichier illisible (droits) : traité comme manquant.
- Rang de photo ou identifiant inexistant dans l'URL : 404.

## Hors périmètre
- Suppression (US-09), qui refait la vérification avant d'agir ; dry-run (US-10).
- Fiche détail complète d'une annonce (US-18b), historique des statistiques.
- Annulation manuelle d'une validation, validation en lot.
- Réparation d'une sauvegarde : il suffit de relancer la sauvegarde (US-05/06).

## Décisions
- Nature de la validation, décidé par l'utilisateur (2026-11-11) : les deux. Une vérification automatique de la sauvegarde est obligatoire, puis le membre confirme lui-même après avoir vu la sauvegarde. Raison : la suppression (US-09) est irréversible ; l'automatique repère fichiers manquants ou abîmés, seul le membre voit une photo ou une description qui ne va pas.
- Granularité, décidé par l'utilisateur (2026-11-11) : confirmation annonce par annonce, après avoir vu l'aperçu de sa sauvegarde ; pas de validation en lot. Raison : le membre doit avoir réellement vu chaque sauvegarde avant une suppression irréversible.
- Vérification automatique, décidé par l'utilisateur (2026-11-11) : trois contrôles. (1) Sauvegarde avec au moins une photo (contenu complet garanti par US-05). (2) Photos intactes : chaque fichier gardé existe et son SHA-256 est celui enregistré. (3) Sauvegarde à jour : titre et prix de la sauvegarde identiques à ceux du dernier scan. Raison du (3) : ne pas supprimer une annonce modifiée sur Vinted depuis sa sauvegarde.
- Moment de la vérification, décidé par l'utilisateur (2026-11-11) : annonce par annonce, à l'ouverture de l'aperçu, puis de nouveau côté serveur à la confirmation, refusée si un contrôle échoue. Aucun résultat de vérification n'est gardé ; US-09 la relancera avant de supprimer. La page du dressing affiche seulement la date de validation.
- Annulation, décidé par l'utilisateur (2026-11-11) : une validation porte sur une version de la sauvegarde (sa date de sauvegarde) ; elle cesse de valoir dès que la sauvegarde est remplacée. Une annonce modifiée sur Vinted garde sa validation, mais la vérification relancée avant suppression (US-09) échoue sur le contrôle « à jour ». Pas d'annulation manuelle.
- Aperçu, décidé par l'utilisateur (2026-11-11, « suivant ta recommandation ») : page à part `listing-backup.html?id=…`, ouverte depuis le dressing ; résultat de la vérification, photos en grand dans l'ordre (servies par l'API, lecture seule), tout le contenu par ses libellés sans identifiants, date de sauvegarde, bouton « Valider ».
- Points suivants proposés à la rédaction et acceptés en bloc par l'utilisateur (2026-11-11, « fait suivant ta recommandation ») :
  - Annonce absente du dressing : échec de vérification `LISTING_ABSENT` (une annonce absente n'a rien à supprimer).
  - Version vue : la validation envoie la date de la sauvegarde affichée ; refusée (409) si elle n'est plus la sauvegarde actuelle, pour ne jamais valider une version que le membre n'a pas vue.
  - Validation idempotente (`PUT`) : valider de nouveau la même version garde la première date.
  - Persistance : table `backup_validation` (identifiant Vinted en clé primaire, `backup_saved_at`, `validated_at`, `timestamptz`), une ligne par annonce, remplacée à chaque validation. Une validation vaut seulement si `backup_saved_at` est la date de la sauvegarde actuelle : remplacer la sauvegarde l'annule sans toucher à la sauvegarde elle-même ni à son code.
  - Comparaison titre / prix : exacte. Risque à vérifier au test manuel AT-08-15 : si le titre lu au scan (données `Product`) diffère systématiquement de celui du formulaire (espaces, entités), toutes les vérifications échoueront ; on rouvrira alors cette décision.
- Précisé à l'implémentation (2026-11-11) : la date de validation du dressing est portée par l'aperçu de la sauvegarde (`BackupOverview.validatedAt`, `ShowBackup`), pas par `ShowWardrobe`. Refus de l'API : 404 sans corps, 409 et 422 avec un corps `ValidationRefusal` (`reason`, et `verification` pour 422), traduits par `ListingBackupRefusals`. L'aperçu n'affiche pas un libellé vide (ex. taille sans libellé). Ports : `ListingRepository` et `ListingBackupRepository` gagnent `find(VintedListingId)`.

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `BackupVerification` (vérification de la sauvegarde, value object) : liste des `VerificationFailure` d'une sauvegarde ; réussie si vide. Calculée par le domaine à partir de la sauvegarde (`ListingBackup`), de l'annonce (`Listing`, dernier scan) et de l'état de chaque fichier gardé.
- `VerificationFailure` (échec de vérification) : aucune photo, photo manquante (rang), photo modifiée (rang), annonce absente, titre changé, prix changé (valeurs sauvegardée et lue).
- `BackupValidation` (validation de la sauvegarde) : identifiant Vinted, date de la sauvegarde validée, date de validation ; vaut seulement pour cette version.
- Ports sortants : `BackupValidationRepository` (validations) ; `PhotoStorage` gagne la lecture d'un fichier gardé (octets, ou absent) ; `ListingBackupRepository` gagne la recherche par identifiant.
- Cas d'usage : `ShowListingBackup` (aperçu + vérification), `ValidateListingBackup` (vérification puis validation), `ShowListingBackupPhoto` ; `ShowBackup` ajoute la date de validation de chaque annonce (`BackupOverview`).
- Glossaire : ajouter `BackupVerification`, `VerificationFailure`, `BackupValidation`, `BackupValidationRepository`, `ListingBackupView` (aperçu de la sauvegarde) ; compléter `BackupOverview`, `PhotoStorage`.

## Tests unitaires et d'intégration attendus
- `domain` : `BackupVerificationTest` (chaque échec, cumul des échecs, réussite), `BackupValidationTest` (vaut pour sa version seulement).
- `application` : `ValidateListingBackupAcceptanceTest` (aperçu, validation, refus version remplacée, refus vérification, idempotence, annulation), `ShowListingBackupPhotoTest`, `ShowBackupTest` (date de validation, validation périmée ignorée) ; faux `InMemoryBackupValidationRepository`.
- `adapter/out/storage` : `FilePhotoStorageTest` (lecture d'un fichier, absent).
- `adapter/out/persistence` : `JpaBackupValidationRepositoryTest`, `JpaListingBackupRepositoryTest` et `JpaListingRepositoryTest` (recherche par identifiant).
- `adapter/in/web` : `ListingBackupApiControllerTest`, `WardrobeApiControllerTest`, `ListingBackupPageTest`, `WardrobePageTest`.
