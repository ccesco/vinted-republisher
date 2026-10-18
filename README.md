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
| Recadrage | rectangulaire, proposé par une IA, l'original est conservé |
| Interface | dashboard web : une vue de toutes les annonces, un bouton de lancement automatique, un bouton pour relancer chaque annonce, et un visuel par sous-agent (navigateur ou onglet) |
| Premier test | avec un agent Claude complet, sans code, avant de développer |
| Méthode | spec-driven development (SDD), TDD avec tests d'acceptation, DDD, Software Craftsmanship |
| Tests d'architecture | ArchUnit (`archunit-junit6`) |

## Ce que contient le dépôt

- `pom.xml` : Spring Boot 4.1, Java 25, JPA, PostgreSQL, Liquibase, Thymeleaf, validation, actuator, Testcontainers, ArchUnit.
- `docker-compose.yml` : PostgreSQL 17, port exposé en local uniquement.
- `src/main/resources/application.yml` : configuration commune (production), sans valeur par défaut.
- `src/main/resources/application-local.yml` et `.env.example` : profil `local` du poste de dev, qui lit `.env`.
- `src/main/resources/db/changelog/db.changelog-master.yaml` : changelog Liquibase vide, le schéma reste à définir.
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
```

Dans IntelliJ, renseigner `local` dans *Active profiles* de la configuration de lancement.

`.env` contient des secrets et n'est pas versionné ; `.env.example` en est le modèle. Il n'est lu que dans le profil `local`.

En production (profil `local` inactif), les variables viennent du système et sont toutes obligatoires : `DB_URL` (ex. `jdbc:postgresql://hote:5432/appdb`), `DB_USER`, `DB_PASSWORD`, `APP_DATA_DIR`.

| Variable | Rôle | Défaut (profil `local` uniquement) |
|---|---|---|
| `DB_NAME` | nom de la base | `appdb` |
| `DB_USER` | utilisateur PostgreSQL | `appuser` |
| `DB_PASSWORD` | mot de passe PostgreSQL | obligatoire |
| `APP_DATA_DIR` | dossier des données locales (images) | `./data` |
| `DB_URL` | URL JDBC, production uniquement | construite à partir de `localhost:5432` et `DB_NAME` |

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
- US-15 `MVP` Traitement en parallèle (plusieurs navigateurs)
- US-16 `MVP` Reprise après plantage
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

### Epic 9 – Photos
- US-28 `V2` Conserver l'original et la version recadrée
- US-29 `V2` Recadrage rectangulaire manuel
- US-30 `V2` Suggestion de recadrage par IA, avec validation
- US-31 `V2` Republier avec la version recadrée

### Epic 10 – Gestion d'annonces `V2`
- US-32 Cycle de vie des annonces
- US-33 Historique de prix
- US-34 Édition en masse et tags
- US-35 Règles d'aide à la décision
- US-36 Suivi des ventes
