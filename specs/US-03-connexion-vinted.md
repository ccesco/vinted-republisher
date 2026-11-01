# US-03 – Connexion à Vinted
**Statut** : implémentée
**Priorité** : MVP
**Dépend de** : US-01, US-02

## Contexte
Toutes les US suivantes (scan, sauvegarde, suppression, republication) agissent sur le compte Vinted de l'utilisateur à travers un navigateur piloté. Il faut donc une session Vinted ouverte, et la garder d'un lancement à l'autre pour ne pas se reconnecter à chaque fois. Vinted protège sa page de connexion (captcha, vérification par e-mail ou SMS) : l'utilisateur se connecte lui-même, l'outil ne saisit jamais d'identifiants.

## Objectif
En tant qu'utilisateur, je veux me connecter une fois à Vinted depuis l'outil et qu'il garde ma session, afin que les traitements suivants puissent accéder à mon dressing sans que je me reconnecte.

## Comportement attendu
1. Une page web minimale (`/vinted-session.html`, HTML statique + JS) affiche l'état de la session Vinted : « non vérifié » (au démarrage), « connecté », « non connecté », « vérification en cours » ou « connexion en cours ». Elle n'appelle que l'API REST décrite par le contrat (voir « Contrat d'API »). Tant qu'une vérification ou une connexion est en cours, elle relit l'état toutes les 2 secondes.
2. **Navigateur** : l'outil pilote un Chrome **normal**, qu'il démarre lui-même (`chrome.exe` avec seulement un port de débogage local et le profil dédié `APP_DATA_DIR/browser-profile`), puis auquel il s'attache par ce port (Playwright `connectOverCDP`). Playwright ne lance jamais Chrome lui-même : aucune marque d'automatisation, aucun mode invisible. Si un Chrome répond déjà sur le port (lancé précédemment, y compris avant un redémarrage de l'application), l'outil s'y attache sans en démarrer un autre. Chrome reste ouvert entre deux actions ; l'outil travaille dans un onglet qu'il ferme après usage.
3. **Vérification** (bouton « Vérifier » ; jamais automatiquement au démarrage ; une connexion se conclut par la même détection) : l'outil ouvre la page des paramètres du profil (`https://www.vinted.fr/settings/profile`) dans un onglet. La session est **ouverte** si la page reste affichée (aucune redirection dans les 5 secondes suivant son chargement), **fermée** si Vinted redirige vers la connexion. Le dressing, public, ne permet pas de le savoir. L'état est mémorisé entre deux vérifications.
   Une seule action navigateur (vérification ou connexion) à la fois : une demande reçue pendant une action en cours est ignorée.
4. Si la session n'est pas connectée, la page propose un bouton « Se connecter ».
5. Le bouton ouvre la page des paramètres du profil dans un onglet de ce Chrome ; si la session est fermée, Vinted redirige vers sa page de connexion, où l'utilisateur se connecte lui-même (identifiants, captcha, code de vérification).
6. L'outil attend que la connexion aboutisse (Vinted ramène l'onglet sur la page des paramètres du profil), dans un délai de 5 minutes, puis ferme l'onglet ; le profil (cookies, stockage local) reste conservé sur disque.
7. La page affiche alors « connecté ».
8. Aux lancements suivants, la session conservée est réutilisée sans nouvelle connexion, tant que Vinted ne l'a pas expirée.
9. **Vinted inaccessible** : si la page des paramètres du profil ne se charge pas (erreur réseau) ou répond par une erreur HTTP (≥ 400, cas attendu d'une page de blocage anti-robot), la session est « non connecté » avec le message « Vinted est inaccessible (réseau ou blocage anti-robot) », distinct de « navigateur indisponible ».

## Contrat d'API
Contract first : `src/main/resources/static/openapi/vinted-republisher.yaml` (OpenAPI 3.0), écrit avant le code et servi par Swagger UI sur `/swagger-ui.html`.

| Méthode et chemin | operationId | Réponse |
|---|---|---|
| `GET /api/vinted-session` | `showVintedSession` | 200, `SessionOverview` : `status` (`CONNECTED` / `DISCONNECTED`, absent = non vérifié), `currentAction` (`CHECKING` / `LOGGING_IN`, absent = aucune), `problem` (`MANUAL_LOGIN_NOT_COMPLETED` / `BROWSER_UNAVAILABLE` / `VINTED_UNREACHABLE`, absent = aucun) |
| `POST /api/vinted-session/check` | `checkVintedSession` | 202 + `SessionOverview` juste après la demande, vérification lancée en arrière-plan (ou ignorée si une action est en cours) |
| `POST /api/vinted-session/login` | `logInToVinted` | 202 + `SessionOverview` juste après la demande, connexion manuelle lancée en arrière-plan (ou ignorée si une action est en cours) |

L'API renvoie des codes, pas de libellés : les libellés français sont dans la page.

## Critères d'acceptation
- [x] L'outil ne lit, ne saisit et ne stocke jamais l'identifiant ni le mot de passe Vinted.
- [x] La page affiche « non connecté » et le bouton « Se connecter » quand Vinted redirige la page des paramètres du profil vers la connexion.
- [x] Après une connexion manuelle réussie, la page affiche « connecté ».
- [x] La session est conservée après un redémarrage de l'application.
- [x] Une session expirée côté Vinted est détectée et affichée « non connecté » à la vérification suivante.
- [x] Si l'utilisateur ne se connecte pas dans le délai imparti (ou ferme l'onglet ou Chrome), la session reste « non connecté » et un message l'explique.
- [x] Le profil navigateur est stocké dans `APP_DATA_DIR/browser-profile`, jamais versionné.
- [x] Chrome est démarré comme un navigateur normal, sans marque d'automatisation ni mode invisible, et n'est jamais ouvert sans action de l'utilisateur.
- [x] Aucun test automatisé ne contacte le vrai site Vinted.

## Tests d'acceptation

### AT-03-01 – Session ouverte détectée
```gherkin
Étant donné une session Vinted conservée dans laquelle le membre est connecté
Quand je vérifie l'état de la session
Alors l'état est "connecté"
```
Test : `VintedSessionAcceptanceTest.open_session_is_reported_as_connected()`

### AT-03-02 – Session fermée détectée
```gherkin
Étant donné aucune session Vinted conservée, ou une session expirée
Quand je vérifie l'état de la session
Alors l'état est "non connecté"
```
Test : `VintedSessionAcceptanceTest.closed_session_is_reported_as_disconnected()`

### AT-03-03 – Connexion manuelle réussie
```gherkin
Étant donné une session fermée
Quand je lance la connexion
Et que je me connecte moi-même dans le navigateur ouvert
Alors la session est ouverte
Et l'état est "connecté"
```
Test : `VintedSessionAcceptanceTest.manual_login_opens_the_session()`

### AT-03-04 – Connexion abandonnée
```gherkin
Étant donné une session fermée
Quand je lance la connexion
Et que je ne me connecte pas avant la fin du délai, ou que je ferme l'onglet ou Chrome
Alors l'état reste "non connecté"
Et un message indique que la connexion n'a pas abouti
```
Test : `VintedSessionAcceptanceTest.abandoned_login_leaves_the_session_closed()`

### AT-03-05 – Page d'état de la session
```gherkin
Étant donné une session fermée
Quand j'ouvre la page de la session Vinted
Alors je vois "non connecté"
Et un bouton "Se connecter"
Et un bouton "Vérifier"
```
Test : `VintedSessionPageTest.closed_session_offers_to_log_in_and_to_check()` (page statique dans Chrome, API simulée)

### AT-03-06 – Détection sur des pages locales
```gherkin
Étant donné un faux site Vinted servi localement (page des paramètres du profil affichée, ou redirigée vers la connexion par le serveur ou par un script)
Quand l'adaptateur navigateur vérifie la session
Alors il répond "ouverte" si la page reste affichée, "fermée" s'il est redirigé vers la connexion
```
Test : `PlaywrightVintedAccountTest.Session_check` et `.Manual_login` (pages HTML locales servies par le serveur HTTP du JDK, aucun accès au vrai site ; nécessite Chrome sur le poste)

### AT-03-08 – Une seule action navigateur à la fois
```gherkin
Étant donné une connexion manuelle en cours
Quand je demande une vérification ou une nouvelle connexion
Alors la demande est ignorée
Et aucun second navigateur n'est ouvert
```
Test : `VintedSessionAcceptanceTest.request_during_a_browser_action_is_ignored()`

### AT-03-09 – Navigateur indisponible
```gherkin
Étant donné un navigateur injoignable (Chrome introuvable, ou qui ne répond pas sur le port de débogage)
Quand je vérifie l'état de la session
Alors l'état est "non connecté"
Et un message indique que le navigateur est indisponible
```
Test : `VintedSessionAcceptanceTest.unavailable_browser_leaves_the_session_disconnected()` et `PlaywrightVintedAccountTest.reports_an_unavailable_browser_when_chrome_is_not_found()`

### AT-03-10 – Rien n'est ouvert au démarrage
```gherkin
Étant donné l'application qui vient de démarrer
Quand j'ouvre la page de la session Vinted
Alors je vois "non vérifié"
Et un bouton "Se connecter"
Et un bouton "Vérifier"
Et aucun Chrome n'a été démarré
```
Test : `VintedSessionAcceptanceTest.session_is_unchecked_until_asked()` et `VintedSessionPageTest.unchecked_session_offers_to_log_in_and_to_check()`

### AT-03-11 – Chrome normal, démarré une fois et réutilisé
```gherkin
Étant donné aucun Chrome à l'écoute sur le port de débogage
Quand l'adaptateur navigateur vérifie la session
Alors il démarre un Chrome avec le profil dédié et s'y attache
Et ce Chrome ne porte pas la marque d'automatisation ("navigator.webdriver" faux)
Et ce Chrome reste ouvert après la vérification, sans l'onglet utilisé
Et une vérification suivante réutilise ce Chrome sans en démarrer un autre
```
Test : `PlaywrightVintedAccountTest.Normal_chrome` (Chrome réel, profil et port de test)

### AT-03-12 – Vinted inaccessible
```gherkin
Étant donné Vinted qui ne répond pas, ou qui répond par une erreur HTTP (page de blocage anti-robot)
Quand je vérifie l'état de la session
Alors l'état est "non connecté"
Et un message indique que Vinted est inaccessible
```
Test : `VintedSessionAcceptanceTest.unreachable_vinted_leaves_the_session_disconnected()` et `PlaywrightVintedAccountTest.Unreachable_vinted`

### AT-03-13 – API conforme au contrat
```gherkin
Étant donné le contrat OpenAPI de la session Vinted
Quand j'appelle GET /api/vinted-session, POST /api/vinted-session/check et POST /api/vinted-session/login
Alors les réponses ont les codes et le format JSON du contrat
Et les actions sont lancées en arrière-plan
```
Test : `VintedSessionApiControllerTest` (`@WebMvcTest`) ; la conformité de signature est garantie à la compilation (interface générée depuis le contrat)

### AT-03-07 – Connexion réelle et conservation après redémarrage
```gherkin
Étant donné l'application lancée avec le profil local
Quand je me connecte à mon vrai compte Vinted via le bouton "Se connecter"
Et que je redémarre l'application
Alors la page affiche toujours "connecté"
```
Test : manuel (vrai compte Vinted, captcha et vérification non automatisables).
Historique : un premier essai avec un Chrome lancé par Playwright (profil persistant, vérification invisible au démarrage) a été bloqué par l'anti-robot de Vinted (« Ta session a été bloquée », blocage temporaire de l'adresse IP) ; d'où le pilotage d'un Chrome normal.
Deuxième essai : la session était détectée « non connecté » alors que le membre était connecté, car la détection reposait sur l'en-tête du dressing (public) ; remplacée par la page des paramètres du profil.
Troisième essai (2026-11-01, API contract first) : « Vérifier » affiche « connecté », puis encore « connecté » après redémarrage de l'application, Chrome étant réutilisé. Validé.

## Cas limites et erreurs
- Délai de connexion dépassé : l'onglet est fermé, la session reste fermée, message explicite.
- Onglet ou Chrome fermé par l'utilisateur pendant la connexion : même comportement que le délai dépassé.
- Chrome fermé par l'utilisateur entre deux actions : l'action suivante le redémarre.
- Clic sur « Se connecter » ou « Vérifier » alors qu'une action navigateur est déjà en cours : demande ignorée, pas de second navigateur.
- Chrome introuvable (`CHROME_PATH`), ou Chrome qui ne répond pas sur le port de débogage : la session reste « non connecté » et la page affiche un message indiquant que le navigateur est indisponible (détail dans les logs).
- Page des paramètres du profil en erreur réseau ou HTTP ≥ 400 (blocage anti-robot) : « non connecté » avec le message « Vinted inaccessible ». Non vérifié sur le vrai site : on ignore encore si la page de blocage de Vinted renvoie bien une erreur HTTP.
- Erreur inattendue pendant une action navigateur : l'action se termine quand même (« non connecté », message « navigateur indisponible », détail dans les logs) ; la session n'est jamais bloquée « en cours ».
- Application arrêtée ou en erreur pendant que la page est ouverte : la page affiche « L'application ne répond pas » et réessaie toutes les 2 secondes jusqu'à ce qu'elle réponde.
- Page des paramètres du profil servie avec un `/` final : traitée comme la même page.

## Hors périmètre
- Saisie automatique des identifiants, contournement de captcha.
- Déconnexion depuis l'outil.
- Plusieurs comptes Vinted.
- Le dashboard complet (US ultérieures) : seule une page minimale d'état est livrée ici.

## Décisions
- Connexion manuelle par l'utilisateur dans un Chrome visible ; l'outil conserve ensuite la session (profil persistant). Il ne saisit jamais d'identifiants.
- Playwright for Java (nouvelle dépendance `com.microsoft.playwright:playwright`, validée), utilisé uniquement pour s'attacher à Chrome (`connectOverCDP`) ; aucun navigateur téléchargé.
- Chrome normal démarré par l'outil : `chrome.exe --remote-debugging-port=<port> --user-data-dir=APP_DATA_DIR/browser-profile` (plus `--no-first-run --no-default-browser-check`), aucune autre option. Un profil dédié est obligatoire : Chrome (≥ 136) refuse le port de débogage sur le profil par défaut.
- Chrome reste ouvert entre deux actions ; réutilisé s'il répond déjà sur le port (y compris après un redémarrage de l'application) ; l'outil ferme ses onglets après usage.
- Port de débogage configurable : `CHROME_DEBUG_PORT` (défaut 9222 dans le profil `local`), écoute sur 127.0.0.1 uniquement (comportement par défaut de Chrome).
- Risque accepté : tant que ce Chrome est ouvert, tout programme de la machine peut le piloter par ce port, donc agir sur le compte Vinted connecté. Parade : fermer ce Chrome quand l'outil ne sert pas (signalé dans le README). Écarté : `--remote-debugging-pipe`, qui imposerait que Playwright lance Chrome (détecté par l'anti-robot) et empêcherait de le réutiliser après un redémarrage.
- Chemin de Chrome configurable : `CHROME_PATH` (défaut `C:\Program Files\Google\Chrome\Application\chrome.exe` dans le profil `local`).
- Session ouverte = la page des paramètres du profil (`vinted.profile-settings-url`, `https://www.vinted.fr/settings/profile` dans `application.yml`) s'affiche sans redirection vers la connexion (règle donnée par l'utilisateur après le premier essai : le dressing est visible même déconnecté). Délai de 5 secondes après chargement pour laisser à une redirection par script le temps de se produire.
- Connexion : l'outil ouvre la page des paramètres du profil ; Vinted redirige vers la connexion puis ramène le membre sur cette page une fois connecté.
- Retirés car devenus inutiles : `VINTED_BASE_URL`, `VINTED_WARDROBE_URL` (à réintroduire avec l'US-04 si besoin) et les sélecteurs de l'en-tête.
- Vérification sur demande (bouton « Vérifier »), jamais automatiquement au démarrage : moins d'accès à Vinted. État « non vérifié » au démarrage ; état mémorisé entre deux vérifications.
- Délai de connexion manuelle : 5 minutes.
- Profil navigateur dans `APP_DATA_DIR/browser-profile`.
- Rien n'est stocké en base pour cette US.
- Contract first (contrainte projet ajoutée en cours d'US) : API REST décrite d'abord dans `vinted-republisher.yaml` ; `openapi-generator-maven-plugin` (validé) génère les interfaces Spring et les DTO (`interfaceOnly`, sans dépendance supplémentaire : `openApiNullable` et annotations Swagger désactivés) ; Swagger UI via `springdoc-openapi-starter-webmvc-ui` (validé), qui affiche le contrat statique (`springdoc.swagger-ui.url`) et non le contrat déduit du code ; springdoc n'activant Swagger UI qu'avec `/v3/api-docs`, celui-ci reste exposé mais inutilisé.
- Page web minimale : HTML statique + JS (`static/vinted-session.html`), libellés français dans la page, qui relit l'état toutes les 2 secondes pendant une action. Le starter Thymeleaf est retiré du `pom.xml` (plus aucun écran ne l'utilise).
- Vérification et connexion en arrière-plan ; états d'affichage « vérification en cours » et « connexion en cours ».
- API : réponses 202 pour les actions, même si la demande est ignorée parce qu'une action est en cours. Le 202 porte l'aperçu de la session juste après la demande (action lancée, ou action déjà en cours si la demande est ignorée) : la page l'affiche sans relire l'état. Vérification et connexion lancées sur l'exécuteur de tâches de Spring Boot (`applicationTaskExecutor`).
- Termes du code : `SessionStatus` vaut `CONNECTED` / `DISCONNECTED`.
- Test de slice web : dépendance de test `spring-boot-starter-webmvc-test` ajoutée (validée).

## Questions ouvertes
- Aucune.

## Tests unitaires et d'intégration attendus
- `domain` : `VintedSessionTest` (non vérifié, action navigateur unique, problèmes).
- `application` : `CheckVintedSessionTest`, `LogInToVintedTest` avec un faux `VintedAccount` en mémoire (connexion réussie, délai de 5 minutes, action déjà en cours, navigateur indisponible, Vinted inaccessible).
- `adapter/out` : `PlaywrightVintedAccountTest` contre des pages HTML locales, avec le vrai Chrome (démarrage, réutilisation, onglet fermé, absence de marque d'automatisation, Chrome introuvable, Vinted inaccessible : erreur HTTP ou absence de réponse).
- `adapter/in` : `VintedSessionApiControllerTest` (`@WebMvcTest`, JSON du contrat), `ContractEnumsTest` (toute valeur du domaine existe dans le contrat), `VintedSessionPageTest` (page statique dans Chrome sans interface, API simulée).
