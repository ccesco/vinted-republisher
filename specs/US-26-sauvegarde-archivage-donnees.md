# US-26 – Sauvegarde et archivage des données
**Statut** : validée
**Priorité** : V2
**Dépend de** : US-02 (base), US-05 et US-06 (contenu et photos sauvegardés), US-17 (navigateur occupé)

## Contexte
Une fois une annonce supprimée sur Vinted (US-09), sa sauvegarde (contenu en PostgreSQL, photos dans `APP_DATA_DIR/photos`) en est la seule copie. Or ces données ne vivent qu'à deux endroits du même poste : le volume Docker `pgdata` et le dossier `APP_DATA_DIR`. Un disque perdu, un `docker compose down -v` ou un dossier effacé par erreur les perd toutes, sans retour possible. Il faut pouvoir en sortir une copie complète, d'un clic, et la ranger ailleurs (autre disque, cloud).

## Objectif
En tant que membre, je veux télécharger en un clic une archive complète des données de l'outil, afin d'en garder une copie hors du poste et de ne jamais perdre une annonce supprimée de Vinted.

## Comportement attendu
1. **Lancement** : bouton « Télécharger une archive » sur la page du dressing (`GET /api/data-archive`). Le navigateur enregistre le fichier là où le membre le choisit ; l'outil n'en garde aucune copie.
2. **Navigateur occupé** : pendant une action navigateur (scan, sauvegarde, suppression, republication, rafraîchissement), l'archive est refusée (`409`, `BROWSER_BUSY`) : ces actions écrivent photos et lignes, une archive prise au milieu serait incohérente. Entre deux relances d'une série « Tout lancer », le navigateur est libre : l'archive est permise.
3. **Contenu** : un fichier zip nommé `vinted-republisher-archive-<date-heure locale AAAA-MM-JJTHH-MM-SS>.zip` contenant :
   - `data.json` : toutes les tables métier de la base (toutes sauf les tables de Liquibase), lues dans une seule transaction en lecture seule (instantané cohérent). Format : `{"formatVersion": 1, "createdAt": "<instant ISO-8601>", "schemaVersion": "<id du dernier changeset Liquibase appliqué>", "tables": {"<table>": [{"<colonne>": <valeur>, …}, …]}}`, lignes triées par clé primaire ; dates en ISO-8601, nombres en nombres, nul en `null`.
   - `photos/…` : le dossier `APP_DATA_DIR/photos` entier, à l'identique (mêmes sous-dossiers et noms, octets inchangés).
   - Jamais le profil Chrome (`browser-profile`) : il contient la session Vinted (cookies), donc l'accès au compte.
4. **Lecture seule** : l'archive ne modifie ni la base, ni les photos, ni rien sur Vinted. Aucun accès à Vinted.
5. **Échec** : l'archive est construite dans un fichier temporaire avant l'envoi ; toute erreur pendant sa construction (base injoignable, photo illisible, disque plein) donne `500` `ARCHIVE_FAILED` au lieu d'un zip tronqué. Le fichier temporaire est toujours effacé, envoi réussi ou non.
6. **Page du dressing** : le bouton télécharge l'archive (demandée par la page, puis enregistrée sous le nom donné par l'API) ; un refus ou un échec s'affiche à côté (« Une action navigateur est en cours : réessayez quand elle sera finie. » / « L'archive n'a pas pu être créée : rien n'a été téléchargé. Voir les logs. »).
7. **Restauration** : manuelle, documentée dans le README. Base : `scripts/restaurer-archive.sql` recharge `data.json` dans une base vide créée par Liquibase, tout ou rien, et refuse une autre `schemaVersion` ; les identifiants générés reprennent après le plus grand rechargé. Photos : recopier `photos/` dans `APP_DATA_DIR`. Aucun import depuis l'outil dans cette US.

## Contrat d'API
Contract first : `src/main/resources/static/openapi/vinted-republisher.yaml`.
- `GET /api/data-archive`, tag `data-archive`, `operationId: downloadDataArchive`.
- `200` `application/zip` (binaire), en-tête `Content-Disposition: attachment; filename="vinted-republisher-archive-….zip"`.
- `409` `DataArchiveRefusal` : `reason` = `BROWSER_BUSY`.
- `500` `DataArchiveRefusal` : `reason` = `ARCHIVE_FAILED`.
- Nouvel enum `DataArchiveRefusalReason` : `BROWSER_BUSY`, `ARCHIVE_FAILED`.
- `BrowserAction` gagne `ARCHIVING` (l'archive tient le navigateur, sans ouvrir Chrome).

## Critères d'acceptation
- [x] L'archive contient toutes les lignes de toutes les tables métier, et seulement elles (pas les tables Liquibase).
- [x] Elle contient toutes les photos à l'identique, et jamais le profil Chrome.
- [x] Elle porte la version de son format et la version du schéma de la base.
- [x] Elle ne modifie rien (base, photos, Vinted) et l'outil n'en garde aucune copie.
- [x] Elle est refusée pendant une action navigateur.
- [x] Une erreur de construction ne livre jamais de zip tronqué et ne laisse aucun fichier temporaire.
- [x] La page du dressing la propose et affiche refus et échec.
- [x] Le README décrit la restauration manuelle.

## Tests d'acceptation
### AT-26-01 – Archive complète
```gherkin
Étant donné une base avec deux annonces, leur contenu, leurs photos, leurs relevés, une suppression, une republication et des entrées de journal
Et leurs photos dans le dossier des photos
Quand le membre télécharge l'archive
Alors il reçoit un zip dont data.json contient chaque ligne de chaque table métier, et aucune table Liquibase
Et dont photos/ contient chaque photo avec les mêmes octets
Et data.json porte formatVersion 1, sa date de création et l'id du dernier changeset appliqué
```
Test : `DataArchiveAcceptanceTest.archive_contains_every_business_row_and_every_photo()` (`@Tag("docker")`, PostgreSQL)

### AT-26-02 – Jamais la session Vinted
```gherkin
Étant donné un profil Chrome dans le dossier des données
Quand le membre télécharge l'archive
Alors aucune entrée du zip ne vient du profil Chrome
```
Test : `DataArchiveAcceptanceTest.archive_never_contains_browser_profile()`

### AT-26-03 – Lecture seule
```gherkin
Étant donné une base et un dossier de photos
Quand le membre télécharge l'archive
Alors la base et les photos sont inchangées
Et aucun fichier d'archive ne reste dans le dossier des données ni dans le dossier temporaire
```
Test : `DataArchiveAcceptanceTest.archive_changes_nothing_and_keeps_no_copy()`

### AT-26-04 – Navigateur occupé
```gherkin
Étant donné une action navigateur en cours
Quand le membre demande l'archive
Alors la réponse est 409 avec la raison BROWSER_BUSY
Et aucun zip n'est envoyé
```
Test : `DataArchiveAcceptanceTest.archive_refused_while_browser_busy()`

### AT-26-05 – Échec de construction
```gherkin
Étant donné une photo illisible dans le dossier des photos
Quand le membre demande l'archive
Alors la réponse est 500 avec la raison ARCHIVE_FAILED
Et aucun zip tronqué n'est envoyé
Et le fichier temporaire est effacé
```
Test : `DataArchiveAcceptanceTest.archive_failure_sends_no_partial_zip()`

### AT-26-06 – Page du dressing
```gherkin
Étant donné la page du dressing
Quand le membre clique sur « Télécharger une archive »
Alors le navigateur reçoit le zip en téléchargement
Et un refus ou un échec s'affiche à côté du bouton
```
Test : `WardrobePageTest.downloads_data_archive_and_shows_refusal()` (`@Tag("browser")`, page locale)

### AT-26-07 – Restauration manuelle
```gherkin
Étant donné une archive téléchargée
Quand le membre suit la procédure du README dans une base vide et un dossier vide
Alors les annonces, leurs sauvegardes et leurs photos réapparaissent dans l'outil
```
Test : manuel (procédure de reprise après sinistre, faite une fois par le membre sur une base jetable avant d'accepter l'US)

## Cas limites et erreurs
- Base vide, aucune photo : archive valide, tables vides, pas de dossier `photos/`.
- Une sauvegarde ou une republication démarre pendant la construction : impossible, l'archive tient le navigateur pour sa durée (comme une action navigateur, sans ouvrir Chrome), donc l'action est refusée ou attend comme en US-17.
- Photo orpheline (fichier sans ligne) : copiée quand même ; l'archive reflète le dossier tel quel.
- Disque plein pour le fichier temporaire : `ARCHIVE_FAILED`.
- Deux téléchargements simultanés : le second est refusé (`BROWSER_BUSY`), le navigateur étant tenu par le premier.

## Hors périmètre
- Import ou restauration automatique depuis l'outil (future US si besoin).
- Archives planifiées, rotation, conservation sur le serveur, envoi vers un cloud.
- Chiffrement de l'archive : elle ne contient aucun secret (ni session Vinted ni mot de passe) ; le membre la range où il veut.
- Purge ou archivage des anciennes données dans la base : rien ne grossit assez pour le justifier aujourd'hui.

## Décisions
Prises par Claude sur délégation de l'utilisateur (« fais comme tu penses », 2026-11-17) :
- Téléchargement plutôt que stockage sur le poste : une copie sur le même disque ne protège pas de sa perte ; c'est le membre qui la range ailleurs. Pas de rétention à gérer.
- Export générique de toutes les tables métier (pas un format métier écrit à la main) : une future table entre dans l'archive sans rien changer, et la restauration est une réinsertion directe. `schemaVersion` dit quelle structure attendre.
- JSON dans un zip plutôt que `pg_dump` : `pg_dump` n'est pas forcément installé à côté de l'application (hors Docker), et le JSON se lit sans outil pour retrouver une annonce à la main.
- Refus pendant une action navigateur, et archive tenant le navigateur pendant sa construction : les seules écritures de photos et de sauvegardes viennent de ces actions, c'est le moyen le plus simple d'un instantané base + photos cohérent.
- Profil Chrome exclu : il donne accès au compte Vinted.
- Pas d'entrée au journal (US-23) : le journal trace les actions sur Vinted et sur les sauvegardes ; une archive ne change rien.
- `GET` sans garde d'origine : il ne change rien et un autre site ne peut pas lire la réponse.

## Questions ouvertes
- Aucune.

## Modèle de domaine
- Glossaire : `DataArchive` (archive des données) : copie complète des tables métier et des photos, téléchargée par le membre.
- Domaine : `DataArchive` (date de création, nom de fichier à l'heure locale du membre) ; port sortant `DataArchiveWriter` : `write(Instant createdAt, OutputStream)`.
- Application : cas d'usage `DownloadDataArchive` ; tient le navigateur (`VintedSession`, action `ARCHIVING`) pendant l'écriture ; refuse avec `BrowserBusy`.
- Adapter out (`adapter/out/archive`) : `ZipDataArchiveWriter` compose `data.json` (`BusinessTables`, implémenté par `JdbcBusinessTables` : liste des tables depuis le schéma moins `databasechangelog` et `databasechangeloglock`, instantané en lecture seule `REPEATABLE READ`) et la copie du dossier des photos.
- Adapter in : `DataArchiveApiController` (fichier temporaire, effacé à la fermeture du flux envoyé ou à l'échec), `DataArchiveRefusals`.
- Aucune table ni changeset nouveau.

## Tests unitaires et d'intégration attendus
- [x] Domaine : `DataArchiveTest` : nom à l'heure locale, à la seconde.
- [x] Application : `DownloadDataArchiveTest` : navigateur tenu pendant l'écriture ; refus `BROWSER_BUSY` sans écrire ; navigateur toujours libéré, succès ou erreur.
- [x] Adapter out : `JdbcBusinessTablesTest` (`@Tag("docker")`) : toutes les tables métier, aucune table Liquibase, tri par clé primaire, types (dates ISO, nombres, `null`), `schemaVersion` ; le script de restauration recharge un export dans des tables vides et les identifiants reprennent après.
- [x] Adapter out : `ZipDataArchiveWriterTest` : photos à l'identique, profil Chrome absent, sans dossier de photos, flux de l'appelant laissé ouvert.
- [x] Adapter in : `DataArchiveApiControllerTest` : 200 zip avec `Content-Disposition` et `Content-Length`, 409, 500 sans zip partiel, fichier temporaire effacé.
- [x] Page : `WardrobePageTest.downloads_data_archive_and_shows_refusal()` (navigateur, page locale).
