# US-01 – Squelette du projet et structure des packages

**Statut** : implémentée
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
- [x] `mvn test` s'exécute sans erreur avec un JDK 25.
- [x] `mvn spring-boot:run` démarre l'application quand la base est disponible (voir US-02).
- [x] Les quatre packages existent.
- [x] Le squelette (cette US) n'introduit aucun code métier ; le métier arrive avec les US suivantes (US-03 et après).

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
Test : `ApplicationStartupAcceptanceTest.application_starts_when_database_is_available()` (`@SpringBootTest` + Testcontainers, `@Tag("docker")`, voir les décisions d'US-02).

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
Étant donné le commit du squelette (b0d2683)
Quand je relis ses sources
Alors aucune classe métier n'existe en dehors de la classe main
```
Test : manuel (revue du commit b0d2683).

## État actuel du dépôt
Fait : `pom.xml`, classe `VintedRepublisherApplication`, packages (peuplés par US-03).
Vérifié (2026-11-06) : `mvn test "-Pbrowser,docker"` vert (91 tests, ArchUnit compris) ; `mvn spring-boot:run` démarre avec la base Docker (Liquibase exécuté, `/actuator/health` à UP) ; violation de `domain` refusée par ArchUnit (AT-01-05) ; le commit b0d2683 ne contient que la classe main (AT-01-04).

## Hors périmètre
- Les ports, les cas d'usage et les adapters : ils seront décrits dans les specs des US qui les utilisent (à partir de US-04).
- Tout code métier.

## Décisions
- ArchUnit (`archunit-junit6`, compatible JUnit 6 de Spring Boot 4.1) vérifie l'architecture hexagonale et des règles de code dès cette US.
- Packages `adapter.in` / `adapter.out` (et non `inbound` / `outbound`) : nommage en place, vérifié par ArchUnit et utilisé par US-03.
- Sous-packages des adapters créés au fil des US, par technologie : `adapter.in.web`, `adapter.out.browser`, `adapter.config` (US-03).
- Thymeleaf retiré du `pom.xml` (US-03 : pages statiques, contract first). Validation (`@Validated` des propriétés) et actuator (`/actuator/health`) conservés.

## Questions ouvertes
- Aucune.

## Tests unitaires et d'intégration attendus
- Aucun en dehors des tests d'acceptation : l'US ne contient pas de code métier.
