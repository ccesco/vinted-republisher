# US-06 – Sauvegarder les images (l'original n'est jamais modifié)
**Statut** : implémentée
**Priorité** : MVP
**Dépend de** : US-05

## Contexte
Pour republier une annonce à l'identique (US-12), l'outil doit avoir gardé ses photos. US-05 sauvegarde tout le contenu du formulaire sauf les photos. US-06 les sauvegarde sur disque, sous `APP_DATA_DIR` (emplacement renvoyé ici par US-02), sans jamais modifier le fichier gardé : le recadrage (US-28 à US-31) travaillera sur des copies.

## Objectif
En tant que membre, je veux que l'outil garde les photos de chaque annonce de mon dressing, telles que Vinted les sert, afin de pouvoir republier l'annonce avec les mêmes photos.

## Comportement attendu
La sauvegarde d'US-05 (bouton « Sauvegarder », verrou `BACKING_UP`, reprise, problèmes) est étendue ; aucune nouvelle action.
1. **Lecture d'une annonce** : dans l'onglet de la sauvegarde, l'outil ouvre le formulaire de modification (`/items/<id>/edit`) et en lit le contenu comme en US-05. Il ouvre ensuite la page publique de l'annonce (`/items/<id>`), après la même pause aléatoire de 10 à 15 secondes (une pause sépare ainsi deux pages ouvertes), et y lit la liste ordonnée des photos dans leur version pleine taille.
2. **Téléchargement** : la page publique ouverte télécharge elle-même (`fetch` dans le vrai Chrome) chaque photo, l'une après l'autre, sans pause entre elles. L'outil reçoit les octets tels quels et le type servi. Il ne clique sur rien, ne saisit rien, ne soumet rien.
3. **Écriture des fichiers** : chaque photo est écrite dans `APP_DATA_DIR/photos/<identifiant Vinted de l'annonce>/<SHA-256>.<extension>`, d'abord dans un fichier temporaire du même dossier, puis renommée une fois complète. Un fichier déjà présent sous ce nom n'est pas réécrit. Un fichier écrit n'est jamais modifié.
4. **Enregistrement** : une lecture complète (contenu et toutes les photos) remplace d'un seul coup la sauvegarde de l'annonce en base : contenu, et pour chaque photo son rang (0 = couverture) et son empreinte. Ensuite seulement, les fichiers du dossier de l'annonce qui ne servent plus sont supprimés.
5. **Page du dressing** : chaque annonce sauvegardée affiche « sauvegardée le … · N photos ».
6. **Échec** : comme en US-05, la sauvegarde s'arrête, reste non terminée et affiche le problème ; la sauvegarde de l'annonce en cours, contenu et photos, n'est pas touchée.

## Contrat d'API
Contract first : `src/main/resources/static/openapi/vinted-republisher.yaml`.

| Méthode et chemin | operationId | Changement |
|---|---|---|
| `GET /api/wardrobe` | `showWardrobe` | chaque `Listing` gagne `photoCount` (entier ≥ 1, absent = jamais sauvegardée) |
| `POST /api/wardrobe/backup` | `backUpWardrobe` | inchangé |

Les photos elles-mêmes ne sont pas exposées par l'API (validation de la sauvegarde : US-08).

## Critères d'acceptation
- [x] Chaque annonce sauvegardée a toutes les photos de sa page publique, dans leur ordre, chacune dans sa version pleine taille.
- [x] Chaque fichier gardé est identique, à l'octet près, à ce que Vinted a servi, et son nom est son empreinte SHA-256.
- [x] Un fichier gardé n'est jamais réécrit ni modifié.
- [x] Le contenu et les photos d'une annonce sont remplacés ensemble, et seulement par une lecture complète ; sinon l'ancienne version reste intacte, fichiers compris.
- [x] Les fichiers d'une ancienne version qui ne servent plus sont supprimés après l'enregistrement de la nouvelle, jamais avant.
- [x] Aucune donnée Vinted attachée aux photos (adresse, identifiant, orientation) n'est gardée.
- [x] Les photos sont téléchargées par la page publique dans le vrai Chrome ; la sauvegarde ne fait toujours aucun clic, aucune saisie, aucune soumission.
- [x] Une pause de 10 à 15 secondes sépare deux pages ouvertes par la sauvegarde.
- [x] La page du dressing affiche le nombre de photos de chaque annonce sauvegardée.
- [x] Aucun test automatisé ne contacte le vrai site Vinted.

## Tests d'acceptation
### AT-06-01 – Photos sauvegardées avec l'annonce
```gherkin
Étant donné une annonce présente dont la page publique montre 3 photos
Quand je lance la sauvegarde
Alors l'annonce est sauvegardée avec 3 photos, de rang 0 à 2 dans l'ordre de la page
Et chaque fichier est dans photos/<annonce>/<empreinte>.<extension>, identique à l'octet près à ce qui a été servi
```
Test : `BackUpWardrobeAcceptanceTest.backup_keeps_every_photo_of_the_listing_in_order()`

### AT-06-02 – Remplacement des photos
```gherkin
Étant donné une annonce sauvegardée avec les photos A et B
Et sa page publique montre maintenant les photos B et C
Quand je lance la sauvegarde
Alors sa sauvegarde a les photos B puis C
Et le fichier de B n'est pas réécrit
Et le fichier de A est supprimé
```
Test : `BackUpWardrobeAcceptanceTest.new_photos_replace_the_previous_ones()`

### AT-06-03 – Photo non téléchargée
```gherkin
Étant donné une annonce sauvegardée avec ses photos
Et l'une de ses photos ne se télécharge pas (réponse autre que 200, fichier vide, type qui n'est pas une image ou délai dépassé)
Quand la sauvegarde atteint cette annonce
Alors la sauvegarde s'arrête et reste non terminée
Et le problème indique une annonce illisible
Et l'ancienne sauvegarde de l'annonce, contenu et fichiers, est intacte
```
Test : `BackUpWardrobeAcceptanceTest.photo_not_downloaded_stops_and_keeps_the_previous_backup()`, `PlaywrightListingContentReaderTest.Local_public_page.photo_refused_makes_the_listing_unreadable()` et `DownloadedPhotoTest`

### AT-06-04 – Annonce sans photo
```gherkin
Étant donné une page publique d'annonce sans photo
Quand la sauvegarde atteint cette annonce
Alors la sauvegarde s'arrête avec le problème « annonce illisible »
```
Test : `ListingGalleryTest.gallery_without_photo_is_unreadable()` et `ListingBackupTest.reading_without_photo_is_unreadable()`

### AT-06-05 – Page publique redirigée vers la connexion
```gherkin
Étant donné une page publique d'annonce qui redirige vers la connexion
Quand la sauvegarde atteint cette annonce
Alors la sauvegarde s'arrête et l'état de session est « non connecté »
```
Test : `PlaywrightListingContentReaderTest.Local_public_page.public_page_sending_to_the_login_means_the_session_is_disconnected()`

### AT-06-06 – Écriture impossible
```gherkin
Étant donné un dossier des photos où l'écriture est impossible
Quand la sauvegarde atteint une annonce
Alors la sauvegarde s'arrête avec le problème « échec de la sauvegarde »
Et aucun fichier temporaire ne reste
Et l'ancienne sauvegarde de l'annonce est intacte
```
Test : `FilePhotoStorageTest.failed_write_leaves_no_temporary_file()` et `BackUpWardrobeAcceptanceTest.failed_write_stops_the_backup_and_keeps_the_previous_one()`

### AT-06-07 – Fichier jamais modifié
```gherkin
Étant donné une photo déjà gardée
Quand une nouvelle sauvegarde lit la même photo
Alors son fichier n'est pas réécrit et son contenu est inchangé
```
Test : `FilePhotoStorageTest.same_photo_is_not_written_again()`

### AT-06-08 – Lecture d'une fausse page publique locale
```gherkin
Étant donné une fausse page publique d'annonce servie localement, avec 2 photos servies localement
Quand l'outil la lit avec le vrai Chrome
Alors il rend les 2 photos dans l'ordre, octets et type servis
Et aucun clic, aucune saisie ni aucune soumission n'a eu lieu sur la page
```
Test : `PlaywrightListingContentReaderTest.Local_public_page`

### AT-06-09 – Rythme de lecture
```gherkin
Étant donné une sauvegarde en cours
Quand l'outil ouvre le formulaire puis la page publique d'une annonce
Alors une pause de 10 à 15 secondes sépare la page publique du formulaire, et chaque annonce de la précédente
```
Test : `PlaywrightListingContentReaderTest.Local_public_page` (ordre des pages) et `BackupPaceTest` (bornes)

### AT-06-10 – Conservation en base
```gherkin
Étant donné une annonce sauvegardée avec ses photos
Quand l'application redémarre
Alors le rang et l'empreinte de chaque photo sont relus à l'identique
```
Test : `JpaListingBackupRepositoryTest` (Testcontainers)

### AT-06-11 – Page du dressing
```gherkin
Étant donné une annonce sauvegardée avec 3 photos
Quand j'ouvre la page du dressing
Alors je vois « sauvegardée le … · 3 photos »
```
Test : `WardrobePageTest.shows_backup_state()` et `WardrobeApiControllerTest`

### AT-06-12 – Sauvegarde réelle des photos
```gherkin
Étant donné l'application lancée avec le profil local, une session connectée et ma validation explicite
Quand je lance la sauvegarde de mon vrai dressing
Alors chaque annonce sauvegardée a autant de photos que sur Vinted, dans le même ordre
Et les fichiers s'ouvrent et sont en pleine taille
Et aucune annonce n'a été modifiée sur Vinted
```
Test : manuel (vrai compte Vinted, non automatisable). Vérifie aussi une annonce à plusieurs photos (ordre) et la page publique d'une annonce réservée.
Non fait à la clôture (2026-11-11) : l'utilisateur a choisi de passer l'US en « implémentée » avec ce test encore ouvert. Tout écart constaté se traite par une mise à jour de cette spec.

## Cas limites et erreurs
- Aucune photo sur la page publique, ou photo non téléchargée (réponse autre que 200, fichier vide, type servi qui n'est pas une image, délai dépassé) : « annonce illisible », la sauvegarde s'arrête (règle d'US-05).
- Page publique redirigée vers la connexion : « session non connectée », arrêt, état de session « non connecté ».
- Réponse HTTP ≥ 400 sur la page publique elle-même : « Vinted inaccessible », arrêt, comme pour le formulaire.
- Écriture sur disque impossible (disque plein, droits) : « échec de la sauvegarde », arrêt ; le fichier temporaire est supprimé.
- Deux photos identiques dans une annonce : un seul fichier, deux rangs qui le désignent.
- Application arrêtée pendant l'écriture : au pire un fichier temporaire reste ; il est ignoré et supprimé à la prochaine sauvegarde de l'annonce. Une photo déjà renommée mais pas encore enregistrée en base est supprimée avec les fichiers inutiles à la sauvegarde suivante de l'annonce.
- Sauvegardes d'US-05 faites avant US-06 (sans photos) : elles restent affichées « sauvegardée le … » sans nombre de photos jusqu'à la prochaine sauvegarde, qui les complète.
- Le contenu (formulaire) et les photos (page publique) sont lus à quelques dizaines de secondes d'écart : une modification faite entre les deux sur Vinted n'est pas détectée (décidé par l'utilisateur).

## Hors périmètre
- Recadrage et versions recadrées (US-28 à US-31).
- Vues et likes (US-07), validation de la sauvegarde et affichage des photos gardées (US-08).

## Décisions
- Photos gardées, décidé par l'utilisateur : toutes les photos de l'annonce, dans l'ordre de l'annonce (la première est la couverture), chacune dans le plus grand fichier que Vinted sert. « Original » = le fichier tel que téléchargé depuis Vinted, enregistré à l'octet près (aucun réencodage, redimensionnement ni retouche), avec son empreinte SHA-256 qui prouve qu'il n'a pas changé. Le fichier du téléphone du membre n'est pas accessible : Vinted ne sert que ses propres versions. Les versions recadrées (US-28 à US-31) seront des fichiers à part.
- Moment du téléchargement, décidé par l'utilisateur (2026-11-11) : pendant la sauvegarde US-05, au même passage sur chaque annonce (pas de seconde tournée). La sauvegarde d'une annonce n'est complète que si son contenu et toutes ses photos sont lus ; sinon l'annonce n'est pas sauvegardée et son ancienne version (contenu et photos ensemble) reste intacte. Reprise, verrou de session et problèmes de sauvegarde : ceux d'US-05.
- Observation du formulaire de modification réel (2026-11-11, annonce 10208778166, lecture seule) : les données React Flight du formulaire (`initialItemUploadData`) portent `photos`, une liste ordonnée d'objets `{id, url, highResolution: {id, timestamp, orientation}}`, plus `photoOrientations` `[{id, orientation}]`. L'`url` est signée (`?s=…`) et désigne une version `f800` en WebP (600 × 800 ici) ; c'est le fichier que le formulaire affiche (`data-testid="media-select-photo-0--img"`). Aucune adresse pleine taille pour les photos de l'annonce dans le formulaire. Cette annonce n'a qu'une photo.
- Données gardées, décidé par l'utilisateur (2026-11-11, « prends les photos sans les annotations ») : seulement les fichiers image. Rien de ce que Vinted joint à une photo n'est gardé (adresse signée, identifiant de photo, orientation, référence haute résolution). L'outil garde ses propres données : rang de la photo dans l'annonce (0 = couverture) et empreinte SHA-256 du fichier.
- Observation de la page publique de l'annonce (2026-11-11, `/items/10208778166`, lecture seule) : elle affiche la même version `f800` (600 × 800, `data-testid="item-photo-1--img"`), mais ses données donnent aussi une version sans taille dans l'adresse, `tc/<highResolution.id>/<timestamp>.webp?s=…`, de 1200 × 1600 : quatre fois plus de pixels. Cette adresse est signée (`s=` propre à chaque version) : elle ne se déduit pas du formulaire, il faut la lire sur la page publique. Relevé du début de l'implémentation (2026-11-11, lecture seule) : dans les lignes React Flight de la page, la liste se trouve dans le bloc `{"name":"gallery","type":"gallery",…,"data":{"item_id":<id>,"photos":[…]}}` ; chaque photo porte `full_size_url` (adresse pleine taille signée), `image_no` (1 = couverture) et `is_main`. La même liste figure aussi dans d'autres blocs (`item_status`, objet de l'annonce). L'outil lit le bloc `gallery` de l'annonce, dans l'ordre de la liste.
- Source des photos, décidé par l'utilisateur (2026-11-11) : la version pleine taille lue sur la page publique de l'annonce. La sauvegarde d'une annonce ouvre donc deux pages, le formulaire de modification puis la page publique, avec la pause aléatoire de la sauvegarde (US-05) avant chacune ; la durée d'une sauvegarde complète double environ.
- Concordance, décidé par l'utilisateur (2026-11-11) : les photos (liste, ordre, fichiers) se prennent seulement sur la page publique, sans rapprochement avec le formulaire de modification.
- Rangement, décidé par l'utilisateur (2026-11-11) : `APP_DATA_DIR/photos/<identifiant Vinted de l'annonce>/<SHA-256>.<extension servie>`. Rang et empreinte de chaque photo enregistrés en base avec la sauvegarde de l'annonce. Chaque fichier est écrit une fois (fichier temporaire puis renommage une fois complet), jamais modifié ensuite ; une photo identique d'une sauvegarde à l'autre n'est pas réécrite. Les fichiers de l'ancienne version qui ne servent plus sont supprimés seulement après l'enregistrement de la nouvelle en base (une seule version par annonce, comme US-05).
- Observation du téléchargement (2026-11-11, lecture seule) : depuis la page publique ouverte, un `fetch` de l'adresse pleine taille, fait par la page elle-même, répond 200, `image/webp`, 343 606 octets.
- Téléchargement, décidé par l'utilisateur (2026-11-11) : par la page publique elle-même (`fetch` dans le vrai Chrome, mêmes cookies et même empreinte réseau), une photo après l'autre, sans pause entre les photos d'une annonce ; les octets reçus sont transmis tels quels à l'outil, qui calcule l'empreinte et écrit le fichier. Pas de client HTTP côté Java, pas de navigation par photo.
- Cas d'erreur, décidé par l'utilisateur (2026-11-11), puis aligné sur US-05 le même jour (« arrêt ») : aucune photo sur la page publique, ou une photo non téléchargée (réponse autre que 200, fichier qui n'est pas une image, fichier vide, délai dépassé) → annonce illisible (`LISTING_UNREADABLE`), la sauvegarde s'arrête, comme pour un contenu illisible. Page publique redirigée vers la connexion → session déconnectée, arrêt. Écriture sur disque impossible (disque plein, droits) → arrêt en `BACKUP_FAILED`. Dans tous les cas l'ancienne version de l'annonce (contenu et photos) reste intacte, un fichier temporaire à moitié écrit est supprimé, et la sauvegarde suivante reprend les annonces non sauvegardées.
- Affichage, décidé par l'utilisateur (2026-11-11) : la page du dressing affiche, pour chaque annonce sauvegardée, le nombre de photos gardées à côté de la date (« sauvegardée le … · 3 photos ») ; l'API ajoute `photoCount` à chaque `Listing`. Les photos elles-mêmes ne sont pas affichées (US-08).
- Pause, précisé à l'implémentation (2026-11-11) : la pause aléatoire sépare deux pages ouvertes (formulaire → page publique, page publique → formulaire suivant) ; aucune avant la toute première page, comme en US-05. Les clés de configuration deviennent `min-page-pause` / `max-page-pause`, avec `listing-page-url` et `photo-timeout` (30 s).
- Extension du fichier : déduite du type servi (`image/webp` → `webp`, `image/jpeg` → `jpg`, `image/png` → `png`) ; tout autre type est « pas une image ».

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `ListingPhoto` (photo d'une annonce, value object) : rang (≥ 0), empreinte SHA-256 et extension.
- `DownloadedPhoto` (photo téléchargée) : octets tels que servis et type servi ; non vide, type image connu, sinon `ListingUnreadable`.
- `ListingReading` (lecture d'une annonce) : `ListingContent` et liste ordonnée non vide de `DownloadedPhoto` ; ce que rend désormais `ListingContentReader`.
- `ListingBackup` gagne la liste ordonnée de ses `ListingPhoto`, rangées de 0 sans trou ; vide pour une sauvegarde faite avant US-06.
- Port sortant `PhotoStorage` (rangement des photos) : écrit une photo téléchargée et rend son `ListingPhoto` ; supprime les fichiers d'une annonce hors d'une liste donnée. Adaptateur `FilePhotoStorage` (`adapter/out/storage`), sous `app.data-dir`.
- Adaptateur navigateur : `ListingGallery` (galerie de la page publique d'une annonce ; `ListingPage` désigne déjà la page lue par le scan d'US-04) lit la liste ordonnée des adresses pleine taille et fait télécharger chaque photo par la page.
- Persistance : table `listing_photo` (identifiant Vinted de l'annonce, rang, empreinte `char(64)`, extension), clé (annonce, rang), clé étrangère vers `listing_content`, remplacée dans la même transaction que le contenu.
- Glossaire : ajouter `ListingPhoto`, `DownloadedPhoto`, `ListingReading`, `PhotoStorage`, `ListingGallery` ; compléter `ListingBackup` et `BackupPace`.

## Tests unitaires et d'intégration attendus
- `domain` : `ListingPhotoTest`, `DownloadedPhotoTest`, `ListingBackupTest` (photos non vides).
- `application` : `BackUpWardrobeTest` (photos écrites puis enregistrées, fichiers inutiles supprimés après l'enregistrement seulement, écriture impossible, photo illisible) ; `BackUpWardrobeAcceptanceTest` ; faux `InMemoryPhotoStorage`.
- `adapter/out/browser` : `ListingGalleryTest` (adresses pleine taille lues sans navigateur : ordre, aucune photo, données inattendues) et `PlaywrightListingContentReaderTest` contre des pages et des images locales avec le vrai Chrome (deux pages, téléchargement, réponse en erreur, redirection, aucune interaction).
- `adapter/out/storage` : `FilePhotoStorageTest` (nom = empreinte, octets identiques, pas de réécriture, fichier temporaire supprimé sur échec, suppression des fichiers inutiles).
- `adapter/out/persistence` : `JpaListingBackupRepositoryTest` (photos relues, remplacement).
- `adapter/in/web` : `WardrobeApiControllerTest`, `WardrobePageTest`.
