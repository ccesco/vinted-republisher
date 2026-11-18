# Vinted Republisher

Outil pour mettre à jour mon dressing Vinted en republiant toutes mes annonces.

Pour chaque annonce : récupérer les images, la description, toutes les informations saisies et le prix (ainsi que le nombre de vues et de likes), supprimer l'annonce, attendre 5 minutes, puis la republier.

> **Avertissement.** L'automatisation du site Vinted peut contrevenir à ses conditions d'utilisation et exposer le compte à un blocage. Toujours sauvegarder avant de supprimer, et tester sur une annonce peu importante.

## Décisions prises

| Sujet | Décision |
|---|---|
| Langage | Java 25 |
| Framework | Spring Boot (dernière version stable : 4.1), Maven |
| Architecture | hexagonale (packages `domain`, `application`, `adapter`) |
| Base de données | PostgreSQL 17 dans Docker, volume nommé pour ne pas perdre les données (base `appdb`, utilisateur `appuser`) |
| Migrations | Liquibase (changelog vide pour l'instant) |
| Données sauvegardées | contenu de l'annonce, prix, images, nombre de vues et de likes |
| Images | l'image originale est toujours conservée, jamais modifiée |
| Interface | dashboard web : une vue de toutes les annonces, un bouton de lancement automatique, un bouton pour relancer chaque annonce, et un visuel par sous-agent (navigateur ou onglet) |
| API | contract first : contrat OpenAPI écrit avant le code, interfaces générées par openapi-generator, Swagger UI (springdoc) ; l'IHM (HTML statique + JS) n'appelle que l'API |
| Premier test | avec un agent Claude complet, sans code, avant de développer |
| Méthode | spec-driven development (SDD), TDD avec tests d'acceptation, DDD, Software Craftsmanship |
| Tests d'architecture | ArchUnit (`archunit-junit6`) |

## Ce que contient le dépôt

- `pom.xml` : Spring Boot 4.1, Java 25, JPA, PostgreSQL, Liquibase, validation, actuator, Playwright, springdoc (Swagger UI), openapi-generator, Testcontainers, ArchUnit.
- `src/main/resources/static/openapi/vinted-republisher.yaml` : contrat OpenAPI de l'API (contract first), dont sont générées les interfaces des contrôleurs.
- `docker-compose.yml` : PostgreSQL 17, port exposé en local uniquement.
- `src/main/resources/application.yml` : configuration commune (production), sans valeur par défaut.
- `src/main/resources/application-local.yml` et `.env.example` : profil `local` du poste de dev, qui lit `.env`.
- `src/main/resources/db/changelog/db.changelog-master.yaml` : changelog Liquibase, qui inclut les changesets de `changes/`.
- `src/main/java/fr/vinted/republisher` : classe `main` et packages vides `domain`, `application`, `adapter/in`, `adapter/out`.

- `specs/` : une spec par user story (gabarit `_template.md`), méthode SDD.
- `src/test/java/fr/vinted/republisher/architecture` : tests ArchUnit (architecture hexagonale et règles de code).
- `CLAUDE.md` : consignes de travail pour Claude.

Aucun code métier n'est écrit pour l'instant.

## Démarrage

Prérequis : JDK 25, Maven, Docker.

```bash
cp .env.example .env          # puis modifier DB_PASSWORD
docker compose up -d          # PostgreSQL avec volume persistant
mvn spring-boot:run           # profil local actif par défaut, lit .env
mvn test                      # indépendant du système : ni Chrome ni Docker
mvn test -Pdocker             # ajoute les tests sur PostgreSQL (Testcontainers), Docker doit tourner
mvn test -Pbrowser            # ajoute les tests Chrome (CHROME_PATH hors Windows)
mvn test "-Pbrowser,docker"   # tous les tests
```

Dans IntelliJ, renseigner `local` dans *Active profiles* de la configuration de lancement.

`.env` contient des secrets et n'est pas versionné ; `.env.example` en est le modèle. Il n'est lu que dans le profil `local`.

En production (profil `local` inactif), les variables viennent du système et sont toutes obligatoires : `DB_URL` (ex. `jdbc:postgresql://hote:5432/appdb`), `DB_USER`, `DB_PASSWORD`, `APP_DATA_DIR`, `CHROME_PATH`, `CHROME_DEBUG_PORT`, `VINTED_WARDROBE_URL`.

Session Vinted : page `http://localhost:8080/vinted-session.html` (contrat d'API et Swagger UI sur `http://localhost:8080/swagger-ui.html`). L'outil démarre lui-même un Chrome normal (Chrome doit être installé) avec un profil dédié `APP_DATA_DIR/browser-profile` et le pilote par son port de débogage local ; ce Chrome reste ouvert entre deux actions. **Attention** : tant qu'il est ouvert, n'importe quel programme de votre machine peut le piloter par ce port, donc agir sur votre compte Vinted connecté ; fermez-le quand vous n'utilisez pas l'outil. Avec « Se connecter », vous vous connectez vous-même à Vinted dans ce Chrome ; la session est considérée ouverte quand la page `https://www.vinted.fr/settings/profile` s'affiche sans redirection vers la connexion.

Dressing : page `http://localhost:8080/wardrobe.html`. « Scanner » parcourt le dressing `VINTED_WARDROBE_URL` dans ce Chrome, puis ouvre chaque annonce une à une, sans rien modifier sur Vinted : comptez 10 à 15 secondes par annonce, soit environ 30 à 35 minutes pour 146 annonces. Chaque annonce active est enregistrée dès sa lecture. Un scan interrompu garde ce qu'il a lu et reprend au scan suivant ; une annonce n'est marquée absente qu'à la fin d'un scan terminé. L'onglet du scan passe au premier plan : laissez-le visible pendant le relevé, sinon la page ne charge pas la suite des annonces. L'application n'écoute que sur `127.0.0.1` et refuse les demandes venues d'un autre site.

Vues et favoris : « Rafraîchir les vues et favoris » relit seulement les cartes de la page du dressing, sans ouvrir aucune annonce ni rien modifier sur Vinted (quelques minutes au lieu d'un scan complet), et ajoute un relevé par annonce à l'historique. Il faut être connecté à Vinted : les cartes ne montrent ces nombres qu'au membre. Un dressing incomplet n'ajoute aucun relevé. La fiche d'une annonce mise en vente par une republication montre aussi « Avant / après republication » : le dernier relevé de l'annonce remplacée, le sien, et le total cumulé depuis la première mise en ligne.

Conseil : la colonne « Conseil » de la liste, et la fiche, signalent « À republier » une annonce en vente qui ne se voit presque plus : moins de 10 vues gagnées depuis son dernier relevé d'il y a au moins 7 jours (relevé récent de moins de 7 jours, moins de 5 favoris, qu'une republication ferait perdre). Rafraîchissez les vues et favoris régulièrement pour en profiter. Le conseil ne lance rien : c'est au membre de relancer l'annonce.

Fiche et validation : sur la page du dressing, le titre d'une annonce ouvre sa fiche (`listing.html`) : l'annonce, l'historique de ses prix (un prix de plus à chaque changement vu par un scan ou une republication ; pour une annonce connue avant cet historique, il commence au scan suivant), l'historique de ses vues et favoris, puis l'aperçu de sa sauvegarde : photos gardées, contenu par ses libellés et vérification faite à l'ouverture (au moins une photo, fichiers présents et intacts, annonce présente, titre et prix identiques au dernier scan). « Valider » confirme cette version de la sauvegarde ; une nouvelle sauvegarde de l'annonce annule la validation. Rien n'est demandé à Vinted.

Journal : le lien « Journal » de la page du dressing ouvre `journal.html`, qui liste les actions de l'outil (scans, sauvegardes du dressing, validations, suppressions, revérifications, republications) avec leur issue, refus, dry-runs et erreurs compris, 50 par page ; la fiche d'une annonce montre les siennes. Les lignes ne sont jamais supprimées.

Tags : dans la liste de la page du dressing, cochez des annonces (la case de l'en-tête coche toutes les lignes affichées), saisissez un tag puis « Ajouter le tag » ou « Retirer le tag » ; le filtre « Tag » n'affiche que les annonces qui le portent. Les tags restent dans l'outil, rien n'est envoyé à Vinted ; une annonce republiée ne reprend pas les tags de l'ancienne.
Cycle de vie : la fiche d'une annonce indique depuis quand l'outil la connaît (« Vue pour la première fois le … ») et, sous « Cycle de vie », les faits établis de l'article à travers toutes ses annonces successives : première détection de chacune, suppression confirmée, republication (avec un lien vers la nouvelle annonce) et sortie du dressing (annonce absente sans suppression par l'outil : vendue ou retirée, datée de sa dernière vue). Pour les annonces connues avant cette fonctionnalité, la première détection est reprise de leur plus ancien relevé de vues et favoris, ou à défaut de leur dernière vue (approximation).

Ventes : le lien « Ventes » de la page du dressing ouvre `sales.html`, qui liste les annonces vues « Vendu » dans le dressing au scan ou au rafraîchissement des vues et favoris, la plus récente d'abord, avec la date du constat (pas celle de la vente), le dernier prix lu et le total des prix. Seules les annonces déjà connues de l'outil sont comptées ; rien n'est ouvert ni modifié sur Vinted pour cela.

| Variable | Rôle | Défaut (profil `local` uniquement) |
|---|---|---|
| `DB_NAME` | nom de la base | `appdb` |
| `DB_USER` | utilisateur PostgreSQL | `appuser` |
| `DB_PASSWORD` | mot de passe PostgreSQL | obligatoire |
| `APP_DATA_DIR` | dossier des données locales (images) | `./data` |
| `DB_URL` | URL JDBC, production uniquement | construite à partir de `localhost:5432` et `DB_NAME` |
| `CHROME_PATH` | chemin de `chrome.exe` | `C:/Program Files/Google/Chrome/Application/chrome.exe` |
| `CHROME_DEBUG_PORT` | port de débogage de Chrome (127.0.0.1 uniquement) | `9222` |
| `VINTED_WARDROBE_URL` | adresse de votre dressing, `https://www.vinted.fr/member/<id>` | obligatoire |

### Archive des données (US-26)
Une annonce supprimée de Vinted n'existe plus que dans la base et dans `APP_DATA_DIR/photos`. Le bouton « Télécharger une archive » de la page du dressing (`GET /api/data-archive`) donne un zip de toutes les tables (`data.json`) et de toutes les photos (`photos/`) ; rangez-le hors du poste. Il ne contient jamais le profil Chrome (la session Vinted). Refusé pendant une action navigateur.

Restauration, dans une base vide et avec la version de l'application qui a fait l'archive (le script refuse une autre version du schéma) :
1. Base vide : `docker compose down -v` puis `docker compose up -d` (**efface la base actuelle**), démarrer l'application une fois pour que Liquibase crée les tables, puis l'arrêter.
2. Dézipper l'archive, puis recharger `data.json` (tout ou rien) :
   ```bash
   docker compose cp data.json db:/tmp/data.json
   docker compose exec -T db psql -U appuser -d appdb -v ON_ERROR_STOP=1 < scripts/restaurer-archive.sql
   ```
3. Recopier le dossier `photos/` de l'archive dans `APP_DATA_DIR`.

## User stories (proposition de découpage)

Priorités : `MVP` = première version utilisable, `V2` = ensuite. Les critères d'acceptation seront écrits dans les specs.

### Epic 0 – Fondations
- US-01 `MVP` Squelette du projet et structure des packages
- US-02 `MVP` Base PostgreSQL via Docker

### Epic 1 – Session
- US-03 `MVP` Connexion à Vinted

### Epic 2 – Scan et sauvegarde
- US-04 `MVP` Scanner mon dressing
- US-05 `MVP` Sauvegarder le contenu d'une annonce
- US-06 `MVP` Sauvegarder les images (l'original n'est jamais modifié)
- US-07 `MVP` Sauvegarder vues et likes
- US-08 `MVP` Valider la sauvegarde

### Epic 3 – Suppression sécurisée
- US-09 `MVP` Supprimer uniquement après sauvegarde validée
- US-10 `MVP` Mode dry-run

### Epic 4 – Republication
- US-11 `MVP` Attendre 5 minutes de façon persistante
- US-12 `MVP` Republier depuis la sauvegarde
- US-13 `MVP` Vérifier la republication

### Epic 5 – Orchestration
- US-14 `MVP` États d'une annonce en cours de traitement
- US-15 `V2` Traitement en parallèle (plusieurs navigateurs) : les limites de cadence plafonnent déjà les écritures, et plusieurs navigateurs augmentent le risque de blocage anti-robot
- US-16 `MVP` Reprise après plantage (couverte par US-19 : étapes déduites de la base, nouveau « Tout lancer » sans redémarrage automatique)
- US-17 `MVP` Gestion des échecs

### Epic 6 – Dashboard
- US-18 `MVP` Liste complète des annonces (filtres, tri, recherche, vues et likes)
- US-18b `MVP` Fiche détail d'une annonce
- US-19 `MVP` Bouton « Tout lancer » (automatique)
- US-20 `MVP` Bouton « Relancer » par annonce
- US-21 `MVP` Pause et stop
- US-22 `V2` Aperçu live par sous-agent (navigateur ou onglet)
- US-23 `MVP` Journal des actions

### Epic 7 – Statistiques
- US-24 `V2` Comparer vues et likes avant et après republication
- US-25 `V2` Rafraîchir les statistiques

### Epic 8 – Sécurité et exploitation
- US-26 `V2` Sauvegarde et archivage des données
- US-27 `MVP` Limites de cadence

### Epic 9 – Gestion d'annonces `V2`
- [x] US-32 Cycle de vie des annonces
- [x] US-33 Historique de prix
- [x] US-34 Édition en masse et tags
- US-35 Règles d'aide à la décision (implémentée)
- [x] US-36 Suivi des ventes
