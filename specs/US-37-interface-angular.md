# US-37 – Interface Angular
**Statut** : implémentée
**Priorité** : V2
**Dépend de** : US-03, US-18, US-18b, US-19, US-20, US-21, US-23, US-15, US-22, US-26, US-34, US-36 (pages existantes à reprendre)

## Contexte
L'IHM actuelle est faite de six pages HTML statiques avec du JS écrit à la main (`wardrobe.html`, `listing.html`, `journal.html`, `sales.html`, `live-preview.html`, `vinted-session.html`). Elles marchent mais sont austères, peu lisibles sur mobile et de plus en plus lourdes à faire évoluer (environ 1 700 lignes de HTML et JS non typé). La maquette retenue propose Angular avec Angular Material 3 : barre de navigation latérale, cartes de chiffres clés, carte de la série automatique, tableau avec filtres et sélection, fiche d'annonce, thèmes clair et sombre.

## Objectif
En tant qu'utilisateur, je veux une interface plus agréable et plus claire, afin de suivre et piloter mon dressing plus facilement, y compris depuis mon téléphone.

## Comportement attendu
1. Deux applications dans le même dépôt : le back (`mvn spring-boot:run`, API seule sur `http://localhost:8080/api`) et l'interface Angular (`npm start` dans `frontend/`, sur `http://localhost:4200/`, rechargée à chaque modification, qui relaie `/api` vers le back).
2. Une barre de navigation (à gauche sur ordinateur, en bas sur téléphone) mène à six écrans :
   - **Dressing** (`#/`) : reprend `wardrobe.html` (scan, sauvegarde, rafraîchissement des vues et favoris, archive, série automatique avec pause / reprise / arrêt, liste filtrable et triable, sélection et tags).
   - **Annonce** (`#/listings/{listingId}`) : reprend `listing.html` (historique des prix, vues et favoris, journal, sauvegarde et photos, validation, suppression, revérification, republication, vérification, validation manuelle, relance). Ouverte depuis une ligne du Dressing.
   - **Journal** (`#/journal`) : reprend `journal.html`.
   - **Ventes** (`#/sales`) : reprend `sales.html`.
   - **Aperçu live** (`#/live-preview`) : reprend `live-preview.html` (US-22, fusionnée pendant cette US).
   - **Session Vinted** (`#/vinted-session`) : reprend `vinted-session.html`.
3. En haut de chaque écran, une puce donne l'état de la session Vinted.
4. Le Dressing affiche en tête des chiffres clés **calculés à partir des données déjà renvoyées par l'API**, sur les annonces affichées (ils suivent donc les filtres et la recherche) : nombre d'annonces présentes, nombre de sauvegardes validées sur le total, total des vues, total des favoris. Pas de tendance ni de courbe (aucune donnée d'historique agrégée dans l'API).
5. Les actions irréversibles sur Vinted (suppression, republication) gardent leur confirmation explicite, sous forme de boîte de dialogue.
6. Le thème suit le réglage clair ou sombre du système ; un bouton permet de basculer, le choix est retenu dans le navigateur.
7. Les textes de l'interface sont en français.

## Critères d'acceptation
- [x] Chaque scénario couvert aujourd'hui par les tests de pages (`WardrobePageTest`, `ListingDetailPageTest`, `ListingTagsPageTest`, `ActionJournalPageTest`, `SalesPageTest`, `LivePreviewPageTest`, `VintedSessionPageTest`) passe sur l'interface Angular.
- [x] Les anciennes pages HTML et leur JS sont supprimés ; il ne reste qu'une interface.
- [x] L'interface n'appelle que l'API du contrat et ne charge aucune ressource hors de l'application (polices et icônes embarquées).
- [x] Les appels à l'API passent par le client généré depuis le contrat OpenAPI : un écart au contrat ne compile pas.
- [x] Les chiffres clés du Dressing correspondent aux annonces renvoyées par l'API.
- [x] Le thème suit le système et peut être basculé.
- [x] Aucun défilement horizontal de la page à 375 px de large.
- [x] Back et interface se lancent séparément ; `mvn test` ne demande ni Node ni Chrome, l'interface se teste avec `npm test` et ses pages avec `mvn test -Pbrowser` une fois construite.
- [x] Les actions (POST) de l'interface passent le filtre d'origine du back grâce à l'origine `http://localhost:4200` déclarée de confiance dans le profil `local` ; toute autre origine reste refusée.

## Tests d'acceptation
Les tests de pages Playwright existants sont les tests d'acceptation de la refonte : ils restent boîte noire (Chrome installé, API simulée qui répond selon le contrat) et visent désormais l'interface Angular construite. Leurs scénarios ne changent pas ; seuls les sélecteurs et l'URL d'entrée sont adaptés. S'y ajoutent :

### AT-37-01 – Chiffres clés du Dressing
```gherkin
Étant donné que l'API renvoie 3 annonces présentes, dont 2 avec une sauvegarde validée, totalisant 120 vues et 9 favoris
Quand j'ouvre le Dressing
Alors je vois « 3 » annonces en ligne, « 2 / 3 » sauvegardes validées, « 120 » vues et « 9 » favoris
```
Test : `WardrobePageTest.showsKeyFiguresComputedFromListings()`

### AT-37-02 – Navigation entre les écrans
```gherkin
Étant donné que je suis sur le Dressing
Quand je clique sur une annonce
Alors la fiche de cette annonce s'ouvre
Et la barre de navigation me ramène au Dressing, au Journal, aux Ventes, à l'Aperçu live ou à la Session Vinted
```
Test : `NavigationPageTest.navigatesBetweenScreens()`

### AT-37-03 – Aucune ressource externe
```gherkin
Étant donné que j'ouvre chacun des six écrans
Quand la page a fini de charger
Alors aucune requête n'est partie vers un autre hôte que l'application
```
Test : `NavigationPageTest.loadsNothingOutsideTheApplication()`

### AT-37-04 – Thème clair et sombre
```gherkin
Étant donné que mon système est en thème sombre
Quand j'ouvre l'interface
Alors elle s'affiche en sombre
Et quand je clique sur le bouton de thème, elle passe en clair et le reste après rechargement
```
Test : `NavigationPageTest.followsSystemThemeAndRemembersTheChoice()`

### AT-37-05 – Affichage sur téléphone
```gherkin
Étant donné une fenêtre de 375 px de large
Quand j'ouvre chacun des six écrans
Alors la page ne défile pas horizontalement
Et la barre de navigation est en bas
```
Test : `NavigationPageTest.fitsAPhoneScreen()`

### AT-37-06 – API en erreur
```gherkin
Étant donné que l'API répond en erreur à la liste des annonces
Quand j'ouvre le Dressing
Alors un message d'erreur s'affiche à la place de la liste
Et la barre de navigation reste utilisable
```
Test : `WardrobePageTest.showsAnErrorWhenListingsCannotBeLoaded()`

## Cas limites et erreurs
- API injoignable ou en erreur : l'écran affiche l'erreur renvoyée sans perdre ce qui était affiché.
- Lien direct vers une fiche (`#/listings/{listingId}`) d'une annonce inconnue, sans identifiant ou à l'identifiant invalide : message « Annonce inconnue. » (celui de l'ancienne fiche) et lien « Retour à la liste ».
- Anciennes adresses (`wardrobe.html`…) : plus servies (outil local, un seul utilisateur).

## Hors périmètre
- Toute nouvelle fonction métier ; toute modification du contrat OpenAPI ou du back-end Java (hors suppression des pages statiques).
- Tendances et courbes sur 7 jours de la maquette (demanderaient un nouvel endpoint : US à part si besoin).
- Traduction dans d'autres langues.

## Décisions
Prises par délégation de l'utilisateur (« fais comme tu veux »), confirmées à la validation le 18/11/2026.
- **Angular 22** (dernière version stable sur npm au 18/11/2026 : `@angular/core` 22.2.6) et **Angular Material 22** (Material 3), composants autonomes et signaux. Raison : version courante, Material est la bibliothèque officielle, accessible et thémable.
- **Migration en une fois** dans cette US, anciennes pages supprimées à la fin. Raison : six pages seulement ; deux interfaces en parallèle doubleraient le travail et les tests.
- **Deux applications séparées dans le même dépôt** (décision de l'utilisateur après la première livraison, qui imbriquait l'interface dans le jar via `frontend-maven-plugin`) : le back ne sert que l'API ; l'interface, dans `frontend/`, se lance avec `npm start` (`ng serve`, proxy `/api` vers `http://127.0.0.1:8080`) et se construit avec `npm run build`. Node est requis sur le poste. Raison : chaque application se lance, se construit et se teste seule, avec rechargement automatique de l'interface.
- **Origine de confiance** : le filtre qui refuse les actions venues d'un autre site accepte en plus les origines listées dans `web.trusted-origins` (vide par défaut, `http://localhost:4200` dans le profil `local`). Raison : derrière le proxy, le navigateur envoie l'origine de `ng serve` ; aucun autre site ne peut l'usurper.
- **Client d'API généré** par `openapi-generator` (générateur `typescript-angular`, même version 7.25.0 que le plugin Maven) dans `frontend/src/generated` (ignoré par git), par `@openapitools/openapi-generator-cli` (devDependency du front, configuré dans `frontend/openapitools.json`) ; `npm start` et `npm run build` le régénèrent d'abord par `npm run generate-api`. Raison : contract first jusqu'au navigateur, le contrat restant côté back ; le front possède sa propre génération, sans passer par Maven (qui ne génère que le côté Spring).
- **Routage par fragment** (`withHashLocation`). Raison : aucune redirection à ajouter côté Spring pour les liens directs.
- **Tests** : les tests de pages Playwright actuels restent les tests d'acceptation (boîte noire, inchangés sur le fond) ; la logique propre aux composants (calcul des chiffres clés, filtres) a des tests unitaires Angular avec **Vitest** (outil par défaut d'Angular), lancés par `npm test`. Les tests de pages lisent l'interface construite dans `frontend/dist/browser`.
- **Polices et icônes embarquées** via npm (`@fontsource/roboto` 5.3.0, `material-symbols` 0.49.0). Raison : aucun appel hors de l'application, fonctionne hors ligne.
- **Nouvelles dépendances** (accord requis par `CLAUDE.md`, donné en validant cette spec) : npm `@angular/*` (`core`, `common`, `compiler`, `platform-browser`, `router`), `@angular/material`, `@angular/cdk`, `rxjs` et `tslib` (requis par Angular), `@fontsource/roboto`, `material-symbols`, et en développement `@angular/cli`, `@angular/build`, `@angular/compiler-cli`, `typescript`, `vitest`.

## Questions ouvertes
- Aucune.

## Modèle de domaine
- Aucun terme métier nouveau : refonte de l'adapter d'entrée web côté navigateur. Les noms des composants et services Angular reprennent les termes du glossaire (`Wardrobe`, `Listing`, `ActionJournal`, `Sale`, `VintedSession`, `AutomaticRun`…).

## Tests unitaires et d'intégration attendus
- Front (Vitest) : calcul des chiffres clés à partir d'une liste d'annonces ; construction des critères de filtre et de tri envoyés à l'API ; bascule et mémorisation du thème.
- Adapter web (Playwright, `@Tag("browser")`) : tests de pages existants adaptés, plus `NavigationPageTest`.
- Architecture : les règles ArchUnit restent inchangées (aucun code Java nouveau hors suppression des pages).
