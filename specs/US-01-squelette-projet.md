# US-01 – Squelette du projet et structure des packages

**Statut** : brouillon (partiellement réalisé, à vérifier)
**Priorité** : MVP
**Dépend de** : aucune

## Contexte
Point de départ du projet : une application Spring Boot qui démarre, avec une structure de packages prête à accueillir le métier.

## Objectif
En tant que développeur, je veux un projet Spring Boot structuré en couches, afin de pouvoir développer le métier sans le coupler au navigateur ni à la base.

## Comportement attendu
- Le projet se construit avec Maven (Java 25, Spring Boot 4.1).
- L'application démarre.
- Les packages `domain`, `application`, `adapter/in`, `adapter/out` existent sous `fr.vinted.republisher`.

## Critères d'acceptation
- [ ] `mvn test` s'exécute sans erreur avec un JDK 25.
- [ ] `mvn spring-boot:run` démarre l'application quand la base est disponible (voir US-02).
- [ ] Les quatre packages existent.
- [ ] Le dépôt ne contient aucun code métier.

## Tests d'acceptation

### AT-01-01 – Le projet se construit
```gherkin
Étant donné un poste avec un JDK 25 et Maven
Quand je lance "mvn test"
Alors le build réussit sans erreur
```
Test : le build Maven lui-même (vérifié à chaque exécution).

### AT-01-02 – L'application démarre avec une base disponible
```gherkin
Étant donné une base PostgreSQL disponible
Quand l'application démarre
Alors le contexte Spring se charge sans erreur
```
Test : `ApplicationStartupAcceptanceTest.application_starts_when_database_is_available()` (`@SpringBootTest` + Testcontainers, sous réserve de la question ouverte de US-02).

### AT-01-03 – La structure hexagonale est en place
```gherkin
Étant donné le code source du projet
Quand j'inspecte le package "fr.vinted.republisher"
Alors les packages "domain", "application", "adapter.in" et "adapter.out" existent
Et "domain" ne dépend ni de Spring, ni de JPA, ni de Playwright
```
Test : existence des packages vérifiée manuellement (ArchUnit ne voit pas les packages vides) ; dépendances vérifiées par `HexagonalArchitectureTest` (ArchUnit).

### AT-01-05 – Les règles d'architecture sont vérifiées à chaque build
```gherkin
Étant donné une classe du domaine qui importe Spring, JPA ou Playwright
Quand je lance "mvn test"
Alors le build échoue en désignant la règle d'architecture violée
```
Test : `HexagonalArchitectureTest` et `CodingRulesTest` (ArchUnit), exécutés par `mvn test`.

### AT-01-04 – Aucun code métier
```gherkin
Étant donné le dépôt à la fin de l'US
Quand je relis les sources
Alors aucune classe métier n'existe en dehors de la classe main
```
Test : manuel (revue).

## État actuel du dépôt
Fait : `pom.xml`, classe `VintedRepublisherApplication`, packages vides (fichiers `.gitkeep`).
Non vérifié : la compilation et le démarrage après le passage de Flyway à Liquibase.

## Hors périmètre
- Les ports, les cas d'usage et les adapters : ils seront décrits dans les specs des US qui les utilisent (à partir de US-04).
- Tout code métier.

## Décisions
- ArchUnit (`archunit-junit6`, compatible JUnit 6 de Spring Boot 4.1) vérifie l'architecture hexagonale et des règles de code dès cette US.

## Questions ouvertes
- Nommage des packages : `adapter.in` / `adapter.out`, ou `inbound` / `outbound` ?
- Sous-packages des adapters par technologie (`web`, `persistence`, …) : maintenant ou au fil des US ?
- Dépendances du `pom.xml` : garder tout ce qui y figure aujourd'hui (Thymeleaf, validation, actuator, Testcontainers…) ou ne garder que le strict nécessaire ?

## Tests unitaires et d'intégration attendus
- Aucun en dehors des tests d'acceptation : l'US ne contient pas de code métier.
