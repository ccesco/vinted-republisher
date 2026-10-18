# US-XX – Titre

**Statut** : brouillon | validée | implémentée
**Priorité** : MVP | V2
**Dépend de** : US-XX

## Contexte
Pourquoi cette fonctionnalité existe, ce qu'elle apporte.

## Objectif
En tant que …, je veux …, afin de ….

## Comportement attendu
Description du fonctionnement, étape par étape.

## Critères d'acceptation
- [ ] Règle métier vérifiable (chaque critère est couvert par au moins un test d'acceptation).
- [ ] …

## Tests d'acceptation
Scénarios écrits avant le code, dans le langage du métier. Une US est terminée quand tous passent.
Les scénarios sont en français ; le nom du test automatisé est en anglais (termes du glossaire).

### AT-XX-01 – Titre du scénario
```gherkin
Étant donné …
Quand …
Alors …
```
Test : `FeatureAcceptanceTest.scenario_name()` | manuel (expliquer pourquoi)

### AT-XX-02 – Cas d'erreur
```gherkin
Étant donné …
Quand …
Alors …
```
Test : …

## Cas limites et erreurs
- Que se passe-t-il si … ?

## Hors périmètre
- Ce qui est volontairement exclu de cette US.

## Décisions
- Décision prise et sa raison.

## Questions ouvertes
- Point à trancher avant de passer à « validée ».

## Modèle de domaine
- Termes du glossaire (anglais ↔ français) utilisés ou ajoutés, agrégats, value objects, événements, ports.

## Tests unitaires et d'intégration attendus
- Liste de tests (boucle interne du cycle TDD), par couche : domaine, application, adapters.
