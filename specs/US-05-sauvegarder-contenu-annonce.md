# US-05 – Sauvegarder le contenu d'une annonce
**Statut** : implémentée
**Priorité** : MVP
**Dépend de** : US-03, US-04

## Contexte
Pour republier une annonce après sa suppression (US-12), l'outil doit en avoir gardé le contenu. US-04 ne conserve que l'identifiant, l'URL, le titre et le prix. US-05 sauvegarde tout ce que demande le formulaire de mise en vente, sauf les photos (US-06). La sauvegarde lit le formulaire de modification de chaque annonce, en session connectée, sans jamais le modifier ni le soumettre.

## Objectif
En tant que membre, je veux lancer la sauvegarde du contenu de toutes les annonces de mon dressing, afin de pouvoir les republier à l'identique sans rien ressaisir.

## Comportement attendu
1. La page `/wardrobe.html` gagne un bouton « Sauvegarder », la date de fin de la dernière sauvegarde terminée, un éventuel problème de sauvegarde, et pour chaque annonce sa date de sauvegarde (« non sauvegardée » sinon). Tant qu'une sauvegarde est en cours, elle affiche « sauvegarde en cours » et relit l'état toutes les 2 secondes.
2. « Sauvegarder » lance la sauvegarde en arrière-plan. Elle partage le verrou « une seule action navigateur » d'US-03 (nouvelle action `BACKING_UP`) : une demande reçue pendant une action en cours est ignorée. Aucune vérification de session préalable.
3. **Reprise** : s'il existe une sauvegarde non terminée, la nouvelle la reprend ; sinon une sauvegarde est créée avec sa date de début. Les annonces traitées sont les annonces présentes de l'inventaire ; celles déjà sauvegardées depuis le début de la sauvegarde en cours sont sautées. Inventaire vide : la sauvegarde se termine aussitôt.
4. **Lecture** : dans un seul onglet du Chrome piloté, passé au premier plan, l'outil ouvre une à une `https://www.vinted.fr/items/<id>/edit`, avec une pause aléatoire de 10 à 15 secondes entre deux annonces. Il lit l'objet de l'annonce dans les scripts de la page (identifiants), le libellé de la catégorie dans l'arbre des catégories de ces mêmes scripts, et les autres libellés affichés par le formulaire. Il attend au plus 10 secondes que chaque champ renseigné affiche son libellé ; passé ce délai, le libellé est enregistré vide. Il ne clique sur rien, ne saisit rien et ne soumet rien.
5. **Enregistrement** : une lecture complète remplace aussitôt la sauvegarde de l'annonce (`listing_content`), avec `saved_at` à l'instant de la lecture.
6. **Fin** : quand toutes les annonces sont traitées, l'outil ferme l'onglet et termine la sauvegarde.
7. **Échec** : la sauvegarde s'arrête, reste non terminée, le problème est affiché. Les sauvegardes déjà écrites restent ; celle de l'annonce en cours n'est pas touchée.

## Contrat d'API
Contract first : `src/main/resources/static/openapi/vinted-republisher.yaml`.

| Méthode et chemin | operationId | Réponse |
|---|---|---|
| `GET /api/wardrobe` | `showWardrobe` | 200, `WardrobeOverview` gagne `backupInProgress` (booléen, requis), `lastBackupAt` (date de fin de la dernière sauvegarde terminée, absente = aucune), `backupProblem` (`BROWSER_UNAVAILABLE` / `VINTED_UNREACHABLE` / `SESSION_DISCONNECTED` / `LISTING_UNREADABLE` / `BACKUP_FAILED`, absent = aucun) ; chaque `Listing` gagne `savedAt` (absent = jamais sauvegardée) |
| `POST /api/wardrobe/backup` | `backUpWardrobe` | 202 + `WardrobeOverview` juste après la demande, sauvegarde lancée en arrière-plan (ou ignorée si une action navigateur est en cours) ; 403 si la demande vient d'un autre site |

`SessionOverview.currentAction` gagne `BACKING_UP`, affiché « sauvegarde en cours » sur la page de la session. Le contenu sauvegardé n'est pas exposé par l'API (fiche détail : US-18b).

## Critères d'acceptation
- [x] La sauvegarde ne fait que des navigations de lecture : aucun clic, aucune saisie, aucune soumission sur le formulaire, aucune requête directe à l'API de Vinted.
- [x] Chaque annonce présente est sauvegardée avec tous les champs du formulaire sauf les photos : identifiants et libellés affichés.
- [x] Une sauvegarde n'est remplacée que par une lecture complète ; une lecture incomplète, illisible ou avec un attribut inconnu arrête la sauvegarde et laisse l'ancienne version intacte.
- [x] Chaque annonce est sauvegardée dès sa lecture.
- [x] Une sauvegarde interrompue se reprend sans relire les annonces déjà sauvegardées par elle.
- [x] Une redirection vers la connexion arrête la sauvegarde et passe l'état de session à « non connecté ».
- [x] Toute réponse HTTP ≥ 400 sur un formulaire arrête la sauvegarde, sans nouvelle tentative automatique.
- [x] La pause entre deux annonces est comprise entre 10 et 15 secondes.
- [x] Une seule action navigateur a lieu à la fois.
- [x] Les sauvegardes restent en base après un redémarrage de l'application, une colonne typée par donnée.
- [x] Un autre site ouvert dans le navigateur ne peut pas lancer de sauvegarde.
- [x] Aucun test automatisé ne contacte le vrai site Vinted.

## Tests d'acceptation
### AT-05-01 – Sauvegarde d'un dressing
```gherkin
Étant donné un inventaire de 3 annonces présentes
Quand je lance la sauvegarde
Alors le contenu des 3 annonces est sauvegardé avec ses identifiants et ses libellés
Et la date de fin de la sauvegarde est enregistrée
```
Test : `BackUpWardrobeAcceptanceTest.backup_saves_the_content_of_every_present_listing()`

### AT-05-02 – Remplacement par une lecture complète
```gherkin
Étant donné une annonce déjà sauvegardée dont la description a changé sur Vinted
Quand je lance la sauvegarde
Alors sa sauvegarde contient la nouvelle description et une nouvelle date
Et elle n'a qu'une sauvegarde
```
Test : `BackUpWardrobeAcceptanceTest.complete_reading_replaces_the_previous_content()`

### AT-05-03 – Lecture incomplète
```gherkin
Étant donné une annonce déjà sauvegardée dont le formulaire n'a pas d'état
Quand la sauvegarde atteint cette annonce
Alors la sauvegarde s'arrête et reste non terminée
Et le problème indique une annonce illisible
Et l'ancienne sauvegarde de l'annonce est intacte
```
Test : `BackUpWardrobeAcceptanceTest.incomplete_reading_stops_and_keeps_the_previous_content()` et `ListingEditFormTest`

### AT-05-04 – Attribut inconnu
```gherkin
Étant donné un formulaire dont un attribut a un code inconnu
Quand la sauvegarde atteint cette annonce
Alors la sauvegarde s'arrête avec le problème « annonce illisible »
Et le code inconnu est écrit dans les logs
```
Test : `ListingEditFormTest.unknown_attribute_code_makes_the_reading_incomplete()` et `BackUpWardrobeAcceptanceTest.unknown_attribute_stops_the_backup()`

### AT-05-05 – Session non connectée
```gherkin
Étant donné un formulaire de modification qui redirige vers la connexion
Quand la sauvegarde atteint cette annonce
Alors la sauvegarde s'arrête et reste non terminée
Et le problème indique une session non connectée
Et l'état de session est « non connecté »
```
Test : `BackUpWardrobeAcceptanceTest.login_redirect_stops_the_backup_and_disconnects_the_session()` et `PlaywrightListingContentReaderTest.Login_redirect`

### AT-05-06 – Blocage en cours de sauvegarde
```gherkin
Étant donné un formulaire que Vinted refuse par une erreur HTTP
Quand la sauvegarde atteint cette annonce
Alors la sauvegarde s'arrête sans nouvelle tentative
Et le problème indique que Vinted est inaccessible
Et les annonces déjà sauvegardées le restent
```
Test : `BackUpWardrobeAcceptanceTest.http_error_stops_the_backup_and_keeps_what_was_saved()` et `PlaywrightListingContentReaderTest.Blocked_form`

### AT-05-07 – Reprise d'une sauvegarde interrompue
```gherkin
Étant donné une sauvegarde interrompue après 2 annonces sur 3
Quand je lance la sauvegarde
Alors seule la troisième annonce est ouverte
Et la sauvegarde se termine
```
Test : `BackUpWardrobeAcceptanceTest.interrupted_backup_resumes_where_it_stopped()`

### AT-05-08 – Navigateur indisponible
```gherkin
Étant donné un navigateur injoignable
Quand je lance la sauvegarde
Alors le problème indique que le navigateur est indisponible
Et aucune sauvegarde n'est modifiée
```
Test : `BackUpWardrobeAcceptanceTest.unavailable_browser_is_reported()`

### AT-05-09 – Une seule action navigateur à la fois
```gherkin
Étant donné une action navigateur en cours
Quand je demande une sauvegarde
Alors la demande est ignorée
```
Test : `BackUpWardrobeAcceptanceTest.request_during_a_browser_action_is_ignored()`

### AT-05-10 – Lecture d'un faux formulaire local
```gherkin
Étant donné un faux formulaire de modification servi localement, aux libellés affichés avec retard
Quand l'outil le lit avec le vrai Chrome
Alors il rend les identifiants et les libellés attendus
Et aucun clic, aucune saisie ni aucune soumission n'a eu lieu sur la page
```
Test : `PlaywrightListingContentReaderTest.Local_form`

### AT-05-11 – Rythme de lecture
```gherkin
Étant donné une sauvegarde en cours
Quand l'outil passe d'une annonce à la suivante
Alors il attend entre 10 et 15 secondes
```
Test : `BackUpWardrobeTest.pauses_between_two_listings()`

### AT-05-12 – Conservation en base
```gherkin
Étant donné une sauvegarde terminée
Quand l'application redémarre
Alors le contenu sauvegardé de chaque annonce est relu à l'identique, colonne par colonne
```
Test : `JpaListingBackupRepositoryTest` (Testcontainers)

### AT-05-13 – Page du dressing
```gherkin
Étant donné une annonce sauvegardée et une annonce jamais sauvegardée
Quand j'ouvre la page du dressing
Alors je vois la date de sauvegarde de la première et « non sauvegardée » pour la seconde
Et je vois la date de la dernière sauvegarde terminée et le bouton « Sauvegarder »
```
Test : `WardrobePageTest.shows_backup_state()`

### AT-05-14 – API conforme au contrat
```gherkin
Étant donné le contrat OpenAPI du dressing
Quand j'appelle GET /api/wardrobe et POST /api/wardrobe/backup
Alors les réponses respectent le contrat
```
Test : `WardrobeApiControllerTest` et `ContractEnumsTest`

### AT-05-15 – Demande venue d'un autre site
```gherkin
Étant donné un autre site ouvert dans mon navigateur
Quand il envoie POST /api/wardrobe/backup
Alors la réponse est 403 et aucune sauvegarde n'est lancée
```
Test : `WardrobeApiControllerTest.backup_from_another_origin_is_forbidden()`

### AT-05-16 – Inventaire vide
```gherkin
Étant donné un inventaire sans annonce présente
Quand je lance la sauvegarde
Alors la sauvegarde se termine aussitôt sans problème
Et aucun onglet n'est ouvert
```
Test : `BackUpWardrobeTest.empty_inventory_finishes_at_once()`

### AT-05-17 – Sauvegarde réelle
```gherkin
Étant donné l'application lancée avec le profil local, une session connectée et ma validation explicite
Quand je lance la sauvegarde de mon vrai dressing et que je la regarde
Alors la sauvegarde se termine
Et le contenu sauvegardé de quelques annonces correspond à leur formulaire de modification
Et aucune annonce n'a été modifiée sur Vinted
```
Test : manuel (vrai compte Vinted, non automatisable). Commencer par arrêter la sauvegarde après quelques annonces pour vérifier la reprise. Tout attribut inconnu rencontré se traite par une mise à jour de cette spec. Ce test vérifie aussi : une description longue sauvegardée en entier ; le comportement d'une annonce non modifiable (vendue, réservée) sur `/items/<id>/edit` (aujourd'hui, toute redirection hors du formulaire est traitée en « session non connectée ») ; le libellé du format du colis.
Non fait à la clôture (2026-11-11) : l'utilisateur a choisi de passer l'US en « implémentée » avec ce test encore ouvert. Tout écart constaté se traite par une mise à jour de cette spec.
Recette du 2026-11-11 (10 annonces présentes, scan partiel) : sauvegarde terminée deux fois ; la seconde, arrêtée par un dépassement de délai de chargement (« Vinted inaccessible », voir US-04), a repris sans refaire les annonces déjà sauvegardées. Reste ouvert : comparaison du contenu au formulaire de modification par l'utilisateur, et sauvegarde du dressing complet.

## Cas limites et erreurs
- Objet de l'annonce absent des scripts, `id` différent de l'annonce ouverte, champ obligatoire absent ou invalide : « annonce illisible », la sauvegarde s'arrête.
- Identifiant renseigné dont le libellé ne s'affiche pas après 10 secondes : l'identifiant est sauvegardé avec un libellé vide (décidé par l'utilisateur le 2026-11-07).
- Champ facultatif non renseigné (taille, matière, marque…) : enregistré nul, avec un libellé nul.
- Annonce vendue ou retirée entre le scan et la sauvegarde (HTTP 404) : « Vinted inaccessible », arrêt ; un nouveau scan la marquera absente.
- Annonce marquée absente par un scan pendant une interruption de la sauvegarde : elle n'est plus traitée à la reprise ; sa sauvegarde existante est conservée.
- Erreur HTTP sur une ressource tierce de la page : ignorée.
- Erreur inattendue, ou base indisponible : « échec de la sauvegarde » (détail dans les logs), verrou libéré, sauvegarde reprenable.
- Application arrêtée pendant une sauvegarde : elle reste non terminée et se reprend à la suivante.
- Scan et sauvegarde ne peuvent pas tourner ensemble (verrou commun).

## Hors périmètre
- Photos (US-06), vues et likes (US-07), validation de la sauvegarde (US-08).
- Affichage du contenu sauvegardé (fiche détail, US-18b).
- Historique des versions d'une sauvegarde.
- Sauvegarde déclenchée automatiquement après un scan.

## Décisions
- Contenu sauvegardé, décidé par l'utilisateur : tous les champs du formulaire de mise en vente sauf les photos (US-06) : titre, description, prix, catégorie, marque, taille, état, couleurs, matière, format du colis, et les champs propres à la catégorie. But : republier à l'identique sans ressaisie.
- Source, décidée par l'utilisateur : le formulaire de modification de l'annonce (`/items/<id>/edit`), en session connectée, en lecture seule : l'outil ne soumet jamais le formulaire et le quitte sans rien modifier. Lui seul montre tous les champs avec les valeurs attendues par le formulaire (format du colis compris).
- Observations du 2026-11-07, session connectée, Chrome piloté, annonce 10208778166, lecture seule (aucun clic, aucune saisie) : `/items/<id>/edit` s'affiche (« Modifier l'annonce | Vinted »). Titre et description sont des champs `name="title"` et `name="description"` avec leur valeur. Le prix est affiché « 1,00 € » (`price-input--input`). Catégorie, marque, taille, état, couleur et matière sont des champs cachés vides ; la valeur affichée est dans `[data-testid$="--value"]` : marque « Kiabi », état « Très bon état », couleur « Blanc », matière « Sélectionne un matériau » (non renseignée). La catégorie n'affiche que sa feuille (« Chaussures bébé »). Format du colis : trois boutons radio Petit / Moyen / Grand (`package_type_selector_<n>`), Petit coché. Pas de `__NEXT_DATA__`. Les champs proposés dépendent de la catégorie.
- Seconde lecture du même formulaire, 2026-11-07, sans aucun clic : les scripts de la page (charge utile Next.js) contiennent l'objet de l'annonce avec ses identifiants : `id`, `title`, `description`, `catalogId` (1653), `brandId` (60) et `brand` (`id`, `title` « Kiabi »), `colorIds` ([12]), `packageSizeId` (1), `price` (1) et `currency` (« EUR »), `itemAttributes` (liste de `code` + `ids` : `condition` [2], `size` [588]), et des champs selon la catégorie, ici nuls : `isbn`, `bookTitle`, `author`, `measurementWidth`, `measurementLength`, `measurementUnit`, `manufacturer`, `manufacturerLabel`, `model`. La taille, affichée « Sélectionne une taille » à la première lecture, est bien renseignée (588) : l'affichage n'était pas encore complet, d'où l'attente des libellés. Format interne non documenté, susceptible de changer.
- Déclenchement, décidé par l'utilisateur : action séparée « Sauvegarder », à la demande, sur les annonces présentes de l'inventaire. Le scan US-04 reste en visiteur et ne s'allonge pas. Elle partage le verrou « une seule action navigateur » d'US-03 (nouvelle action `BACKING_UP`).
- Périmètre, décidé par l'utilisateur : chaque sauvegarde relit toutes les annonces présentes, pour prendre en compte les modifications faites sur Vinted. Elle est persistée (dates de début et de fin) ; une sauvegarde interrompue est reprise par la suivante, sans relire les annonces déjà sauvegardées par elle.
- Version, décidée par l'utilisateur : une seule sauvegarde par annonce, avec sa date. Elle n'est remplacée que par une lecture complète. Une lecture incomplète ou illisible arrête la sauvegarde avec un problème affiché, l'ancienne version reste intacte. Pas d'historique (aucun usage prévu).
- Représentation, décidée par l'utilisateur : les identifiants de l'objet de l'annonce lu dans les scripts du formulaire, plus les libellés affichés pour la lecture humaine. Les identifiants servent à republier, les libellés à afficher et contrôler.
- Lecture complète, décidée par l'utilisateur : l'objet de l'annonce est trouvé, son `id` est celui de l'annonce ouverte, et sont présents et valides : titre non vide, `catalogId`, `price` positif, `currency`, `packageSizeId`, attribut `condition`. Tous les autres champs sont enregistrés tels que lus, même nuls : leur caractère obligatoire dépend de la catégorie. Seule exception : une description nulle est enregistrée vide (colonne non nulle).
- Décodage des données de la page, à la suite de la revue : ce sont des lignes React Flight. React échappe en `$$` un texte qui commence par `$` et envoie un long texte (description) à part, dans une ligne `<clé>:T<longueur en octets>,<texte>` que la valeur désigne par `"$<clé>"`. L'outil dés-échappe et résout ces textes ; toute autre valeur en `$…` (référence introuvable, date, etc.) rend l'annonce illisible plutôt que d'enregistrer un texte faux. Toute donnée inattendue, y compris avant la lecture des libellés, donne « annonce illisible ».
- Stockage, décidé par l'utilisateur : une colonne par donnée avec un type approprié, sans `jsonb`. Table `listing_content`, une ligne par annonce (clé = `listing_id`, référence `listing`), remplacée à chaque lecture complète :
  - `saved_at` `timestamptz` ;
  - `title`, `description` `text` ;
  - `catalog_id`, `brand_id`, `package_size_id`, `condition_id`, `size_id` `bigint` ;
  - `color_ids`, `material_ids` `bigint[]` ;
  - `price` `numeric(10,2)`, `currency` `char(3)` ;
  - `isbn`, `book_title`, `author`, `manufacturer`, `manufacturer_label`, `model`, `measurement_unit` `text` ; `measurement_width`, `measurement_length` `numeric` (nullables) ;
  - libellés affichés, `text` : `category_label`, `brand_label`, `condition_label`, `size_label`, `color_label`, `material_label`, `package_size_label`.
  Les `itemAttributes` sont répartis par `code` dans leurs colonnes (`condition`, `size`, `material`).
- Attribut inconnu, décidé par l'utilisateur : un `itemAttributes` dont le `code` n'a pas de colonne rend la lecture incomplète ; la sauvegarde s'arrête (« annonce illisible », code écrit dans les logs), l'ancienne version reste intacte. On ajoute alors la colonne.
- Session non connectée, décidé par l'utilisateur : pas de vérification préalable. Si un formulaire de modification redirige vers la connexion, la sauvegarde s'arrête (reprenable), affiche « session non connectée » et l'état de session passe à `DISCONNECTED` (US-03).
- Règles reprises d'US-04, décidé par l'utilisateur : un seul onglet au premier plan, pause aléatoire de 10 à 15 secondes entre deux annonces (configuration) ; annonces présentes au début de la sauvegarde ; table `wardrobe_backup` (`id`, `started_at`, `finished_at`), une annonce est déjà traitée si son `saved_at` n'est pas antérieur au début de la sauvegarde en cours ; réponse HTTP ≥ 400 du formulaire → « Vinted inaccessible », arrêt sans nouvelle tentative ; ressources tierces ignorées ; navigateur injoignable → « navigateur indisponible » ; erreur inattendue ou base indisponible → « échec de la sauvegarde » ; verrou toujours libéré ; `POST` d'un autre site → 403.
- Page et API, laissées à Claude par l'utilisateur : la sauvegarde s'ajoute à `wardrobe.html` et à `WardrobeOverview` (bouton, état, date, problème, date par annonce), sans exposer le contenu. Le problème de sauvegarde est distinct de celui du scan (`backupProblem`), affiché jusqu'à la sauvegarde suivante, en mémoire seulement. Attente des libellés : 10 secondes au plus. Aucune nouvelle dépendance.
- Libellé non affiché, décidé par l'utilisateur après une troisième lecture du formulaire (2026-11-07, aucun clic) : l'annonce 10208778166 a `size` 588, mais le formulaire affiche « Sélectionne une taille » même entièrement chargé. Un identifiant dont le libellé ne s'affiche pas sous 10 secondes est donc sauvegardé avec un libellé vide : les identifiants servent à republier, les libellés seulement à lire. Pour US-12 : une taille non affichée n'est peut-être plus proposée par Vinted pour la catégorie.
- Même lecture : l'objet de l'annonce se trouve dans les scripts `self.__next_f.push([1,"…"])`, dont les chaînes décodées et mises bout à bout forment des lignes `<clé>:<JSON>` ; un seul objet porte `catalogId` et l'`id` de l'annonce. L'arbre des catégories y figure aussi (`{"id":1653,"title":"Chaussures bébé",…}`) : le libellé de la catégorie en est tiré. `self.__next_f` lui-même est vide une fois la page chargée. Les autres libellés sont dans `[data-testid="<champ>-…-input--value"]` ; celui du format du colis est le titre du bouton radio coché.

## Questions ouvertes
- Aucune. Les points inconnus (autres codes d'attribut, catégories à champs propres) sont vérifiés par AT-05-17.

## Modèle de domaine
- `ListingContent` (contenu d'une annonce, value object) : identifiant, titre, description, `Price`, catégorie, marque, état, taille, couleurs, matières, format du colis, `CategoryDetails`. Chaque choix unique du formulaire est une `FormOption`, chaque choix multiple une `FormOptions`.
- `FormOption` (option du formulaire, value object) : identifiant Vinted et libellé affiché (vide s'il ne s'affiche pas). `FormOptions` : identifiants et libellé affiché d'un choix multiple (couleurs, matières).
- `CategoryDetails` (détails propres à la catégorie) : ISBN, titre du livre, auteur, fabricant, libellé du fabricant, modèle, mesures et leur unité, tous facultatifs.
- `ListingBackup` (sauvegarde d'une annonce) : `ListingContent` et date de sauvegarde ; une par annonce.
- `ListingEditForm` (formulaire de modification) : ce que l'adaptateur navigateur lit d'une annonce ; produit un `ListingContent` ou signale `ListingUnreadable`.
- `WardrobeBackup` (sauvegarde du dressing, agrégat) : date de début, date de fin ; dit si une annonce est déjà sauvegardée par elle.
- `BackupProblem` (`BROWSER_UNAVAILABLE` / `VINTED_UNREACHABLE` / `SESSION_DISCONNECTED` / `LISTING_UNREADABLE` / `BACKUP_FAILED`), `LastBackupProblem` (en mémoire), `ListingUnreadable`, `SessionDisconnected` ; `BrowserAction` gagne `BACKING_UP` ; `WardrobeOverview` gagne l'état de sauvegarde.
- `BackupPace` (rythme de la sauvegarde) : pause aléatoire entre deux annonces.
- `BackupOverview` (aperçu de la sauvegarde) : sauvegarde en cours, date de la dernière terminée, problème, date de sauvegarde par annonce ; l'API la joint à l'aperçu du dressing.
- Ports sortants : `ListingContentReader` (lit le formulaire des annonces demandées, dans un onglet), `ListingBackupRepository`, `WardrobeBackupRepository`. Cas d'usage : `BackUpWardrobe`, `ShowBackup` (paquet `backup`, qui dépend de `wardrobe` sans l'inverse).
- Glossaire : ajouter `ListingContent`, `FormOption`, `FormOptions`, `CategoryDetails`, `ListingBackup`, `ListingEditForm`, `WardrobeBackup` (`BackUpWardrobe`), `BackupPace`, `BackupOverview`, `BackupProblem`, `LastBackupProblem`, `ListingUnreadable`, `SessionDisconnected`, et `BACKING_UP` à `BrowserAction`.

## Tests unitaires et d'intégration attendus
- `domain` : `ListingContentTest`, `FormOptionTest`, `WardrobeBackupTest`, `BackupPaceTest`, `VintedSessionTest` (action `BACKING_UP`).
- `application` : `BackUpWardrobeTest` (lecture, enregistrement au fil de l'eau, reprise, chaque problème, session déconnectée, verrou libéré, rythme, inventaire vide), `ShowBackupTest` ; `BackUpWardrobeAcceptanceTest`.
- `adapter/out/browser` : `ListingEditFormTest` (objet de l'annonce et libellés, sans navigateur : complet, champ manquant, `id` différent, attribut inconnu, libellé vide) et `PlaywrightListingContentReaderTest` contre des pages locales avec le vrai Chrome (formulaire, libellés retardés, redirection vers la connexion, erreur HTTP, aucune interaction).
- `adapter/out/persistence` : tests Testcontainers de `JpaListingBackupRepository` et `JpaWardrobeBackupRepository`.
- `adapter/in/web` : `WardrobeApiControllerTest`, `ContractEnumsTest`, `WardrobePageTest`.
