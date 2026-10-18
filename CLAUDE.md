# Vinted Republisher – consignes pour Claude

Outil pour sauvegarder puis republier les annonces d'un dressing Vinted (voir `README.md`).

## Méthode : spec-driven development
- Aucune implémentation sans spec validée dans `specs/` (statut `validée`).
- Une spec par user story, créée à partir de `specs/_template.md`.
- Chaque spec contient ses tests d'acceptation (scénarios Gherkin) ; une US est terminée quand ils passent tous.
- Si le comportement change, mettre la spec à jour dans le même commit que le code.
- Ne pas inventer de décisions : toute question ouverte d'une spec se pose à l'utilisateur.

## Principes de développement (non négociables)
Software Craftsmanship, TDD et DDD s'appliquent à **tout** code de production.
- Pour implémenter une US : utiliser le skill `implement-user-story`, qui enchaîne les trois suivants.
- `tdd` : Rouge → Vert → Refactor, aucun code de production sans test rouge préalable.
- `ddd` : langage omniprésent en anglais (`docs/glossary.md`) : tous les termes du code sont en anglais, domaine pur Java sans framework, dépendances `adapter → application → domain`.
- Les tests ArchUnit (`src/test/java/.../architecture`) ne se désactivent pas et ne s'assouplissent pas sans accord : si une règle bloque, revoir le design.
- `craftsmanship` : simple design, YAGNI, petits commits verts, checklist de revue avant de rendre la main.

## Stack décidée
Java 25, Spring Boot 4.1 (Maven), PostgreSQL 17 dans Docker, Liquibase, architecture hexagonale vérifiée par ArchUnit.

## Règles
- Ne jamais committer `.env`, `data/` ni de secrets.
- Ne pas ajouter de dépendance sans en parler.
- Ne rien supprimer ni republier sur le vrai compte Vinted sans validation explicite de l'utilisateur.
- Répondre et rédiger en français.

## Commandes
```bash
docker compose up -d          # PostgreSQL
mvn test
mvn spring-boot:run           # profil local (lit .env) actif par défaut
```

## Configuration
- `application.yml` : commun à tous les environnements, aucune valeur propre au poste de dev.
- `application-local.yml` : profil `local` (lecture de `.env`, valeurs par défaut localhost). Toute config de dev va ici.
