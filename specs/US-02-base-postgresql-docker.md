# US-02 – Base PostgreSQL via Docker

**Statut** : brouillon (partiellement réalisé, à vérifier)
**Priorité** : MVP
**Dépend de** : US-01

## Contexte
Les données (annonces, historique) doivent survivre à l'arrêt de l'application. La base tourne dans Docker, l'application sur la machine locale.

## Objectif
En tant qu'utilisateur, je veux une base PostgreSQL dans Docker avec un volume persistant, afin de ne jamais perdre mes données.

## Comportement attendu
- `docker compose up -d` démarre PostgreSQL 17 avec un volume nommé.
- Le nom de la base, l'utilisateur et le mot de passe viennent du fichier `.env` (modèle : `.env.example`).
- Le port n'est accessible que depuis la machine locale (`127.0.0.1`).
- En local, l'application se connecte à la base avec les mêmes variables : le fichier `.env` est la **source unique**, lu par Docker Compose et importé par Spring Boot (`spring.config.import`) **uniquement dans le profil `local`** (`application-local.yml`).
- `mvn spring-boot:run` active le profil `local` par défaut (configuration du plugin Maven) ; le jar packagé, lui, ne l'active pas.
- Hors profil `local` (production), aucune valeur par défaut : `DB_URL`, `DB_USER`, `DB_PASSWORD` et `APP_DATA_DIR` viennent des variables d'environnement du système, et l'absence de l'une d'elles fait échouer le démarrage.
- Liquibase est configuré avec un changelog principal vide ; le schéma sera construit par les US suivantes.

## Critères d'acceptation
- [ ] Après `docker compose down` puis `docker compose up -d`, les données créées précédemment sont toujours là.
- [ ] `.env` est ignoré par git ; `.env.example` est versionné et sans secret.
- [ ] Le port 5432 n'est pas exposé hors de la machine.
- [ ] L'application démarre et se connecte à la base.
- [ ] En local, aucune variable d'environnement à définir à la main : `.env` suffit pour Docker et pour l'application lancée depuis la racine du projet avec le profil `local`.
- [ ] Hors profil `local`, le fichier `.env` n'est jamais lu et aucune valeur de développement (localhost, `appdb`, `appuser`, `./data`) n'est utilisée.
- [ ] Liquibase s'exécute sans erreur avec le changelog vide.

## Tests d'acceptation

### AT-02-01 – Les données survivent au redémarrage du conteneur
```gherkin
Étant donné la base démarrée avec "docker compose up -d"
Et une donnée enregistrée dans la base
Quand je lance "docker compose down" puis "docker compose up -d"
Alors la donnée est toujours présente
```
Test : manuel (redémarrage réel du conteneur et du volume nommé).

### AT-02-02 – Les secrets ne sont pas versionnés
```gherkin
Étant donné le dépôt git
Quand je vérifie les fichiers suivis
Alors ".env" est ignoré par git
Et ".env.example" est versionné et ne contient aucun secret
```
Test : manuel (`git check-ignore .env`, relecture de `.env.example`).

### AT-02-03 – La base n'est accessible que localement
```gherkin
Étant donné la base démarrée avec Docker Compose
Quand j'inspecte les ports publiés du conteneur
Alors le port 5432 n'est lié qu'à 127.0.0.1
```
Test : manuel (`docker compose ps`).

### AT-02-04 – L'application se connecte avec le seul fichier .env
```gherkin
Étant donné un fichier ".env" à la racine du projet
Et aucune variable d'environnement définie à la main
Et la base démarrée avec Docker Compose
Quand je lance "mvn spring-boot:run" depuis la racine (profil local actif par défaut)
Alors l'application démarre et se connecte à la base
```
Test : manuel (dépend de Docker Compose et du `.env` réel).

### AT-02-08 – Hors profil local, la configuration de dev n'est pas utilisée
```gherkin
Étant donné un fichier ".env" à la racine du projet
Et l'application lancée sans le profil "local"
Et la variable "DB_URL" non définie
Quand l'application démarre
Alors le démarrage échoue en signalant la configuration de base de données manquante
Et le fichier ".env" n'a pas été lu
```
Test : manuel (`java -jar target/vinted-republisher-*.jar` depuis la racine).

### AT-02-05 – Liquibase s'exécute avec le changelog vide
```gherkin
Étant donné une base PostgreSQL vide
Quand l'application démarre
Alors Liquibase s'exécute sans erreur
Et sa table de suivi existe dans la base
```
Test : `DatabaseMigrationAcceptanceTest.liquibase_runs_with_empty_changelog()` (`@SpringBootTest` + Testcontainers, sous réserve de la question ouverte).

### AT-02-06 – Mot de passe absent
```gherkin
Étant donné un fichier ".env" sans "DB_PASSWORD"
Quand je lance "docker compose up -d"
Alors PostgreSQL refuse de démarrer
Et le message d'erreur indique que le mot de passe manque
```
Test : manuel.

### AT-02-07 – Base indisponible au démarrage de l'application
```gherkin
Étant donné une base arrêtée
Quand je démarre l'application
Alors le démarrage échoue avec un message explicite sur la connexion à la base
```
Test : manuel.

## Cas limites et erreurs
- `DB_PASSWORD` absent ou vide : PostgreSQL refuse de démarrer ; le message d'erreur doit être compréhensible.
- Base indisponible au démarrage de l'application : échec explicite.
- Application lancée en profil `local` depuis un autre dossier que la racine : `.env` est introuvable (import optionnel), `DB_PASSWORD` n'est pas résolu et le démarrage échoue de façon explicite.
- En profil `local`, une variable d'environnement du système définie avec le même nom l'emporte sur `.env`.
- Production (profil `local` inactif) : `.env` n'est jamais lu, même s'il existe dans le dossier de lancement.
- Changement des noms de base ou d'utilisateur alors qu'un volume existe déjà : l'ancien volume garde les anciennes valeurs (il faut le supprimer avec `docker compose down -v`).

## Hors périmètre
- Le schéma de la base (tables, colonnes) : défini dans les specs des US concernées.
- La sauvegarde et l'archivage des données (US-26).

## Décisions
- PostgreSQL 17 dans Docker, volume nommé.
- Base `appdb`, utilisateur `appuser` (valeurs par défaut modifiables).
- Liquibase pour les migrations, avec un changelog YAML.
- Un seul fichier `.env` pour Docker Compose et Spring Boot (`optional:file:.env[.properties]`), plutôt que des variables définies à la main.
- L'import de `.env` et les valeurs par défaut de développement sont isolés dans le profil `local` (`application-local.yml`) : `application.yml` ne contient que la configuration commune, sans valeur propre au poste de dev.
- `mvn spring-boot:run` active `local` par défaut via la configuration du `spring-boot-maven-plugin`, pour garder une commande unique en développement.

## État actuel du dépôt
Fait : `docker-compose.yml`, `.env.example`, `application.yml`, `application-local.yml`, `db/changelog/db.changelog-master.yaml` (vide).
Non vérifié : démarrage de bout en bout (Docker, puis application, puis Liquibase).

## Questions ouvertes
- Les tests d'intégration utilisent-ils Testcontainers (déjà dans le `pom.xml`) ou la base du `docker-compose` ?
- Où stocker les images sauvegardées (`APP_DATA_DIR`) : à trancher dans US-06.
- `ddl-auto: validate` : à conserver une fois les premières entités JPA ajoutées ?
- Alternative à étudier plus tard : le support Docker Compose de Spring Boot (démarre la base et fournit la connexion automatiquement).

## Tests unitaires et d'intégration attendus
- Aucun en dehors des tests d'acceptation : l'US ne contient que de la configuration.
