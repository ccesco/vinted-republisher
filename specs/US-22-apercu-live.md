# US-22 – Aperçu live par navigateur
**Statut** : implémentée
**Priorité** : V2
**Dépend de** : US-03 (Chrome piloté), US-19 (« Tout lancer »), US-21 (pause et stop)

## Contexte
Pendant un traitement automatique (US-19), l'outil travaille dans un onglet du Chrome piloté, souvent dans une fenêtre que le membre ne regarde pas. Le dashboard dit quelle annonce est en cours et à quelle étape, mais pas ce que le navigateur montre réellement : une page de blocage anti-robot, un formulaire bloqué ou une fenêtre de dialogue ne se voient qu'en allant chercher la fenêtre de Chrome. Avec le traitement en parallèle (US-15), il y aura plusieurs navigateurs à surveiller.

## Objectif
En tant que membre, je veux voir depuis le dashboard ce que montre chaque navigateur piloté pendant qu'il travaille, afin de suivre le traitement et de repérer un blocage sans aller chercher les fenêtres de Chrome.

## Comportement attendu
1. **Onglet piloté** : quand l'outil ouvre un onglet de travail dans un Chrome piloté (`ChromeTabs`), il le note comme onglet à montrer ; il l'oublie quand il le ferme, que l'action réussisse ou échoue. Noter l'onglet coûte quelques millisecondes à l'ouverture (une session de débogage le temps de lire son identifiant) : le travail n'est pas ralenti.
2. **Capture à la demande** : l'API capture l'écran de chaque onglet noté au moment où elle est appelée (capture JPEG de la partie visible de la page, qualité 60), par une connexion de débogage à part, ouverte puis fermée pour la capture, qui ne fait que la capture : elle ne navigue pas, ne clique pas, ne saisit rien, n'active aucun domaine de Chrome et ne passe pas par la connexion Playwright du travail. Personne ne regarde : aucune capture.
3. **Un aperçu par onglet piloté**, donc par navigateur (chaque Chrome piloté ne travaille que dans un onglet à la fois) ; il porte le nom de son navigateur, « Chrome (port 9222) » (le port de débogage distingue les navigateurs, US-15 compris), et la date de sa capture. Les aperçus sont triés par nom de navigateur.
4. **Capture impossible** (onglet fermé entre-temps, fenêtre réduite qui ne dessine plus, Chrome qui ne répond pas en 3 secondes) : l'aperçu est rendu sans image ; la cause va aux logs. Les onglets sont capturés en même temps, chacun dans ce délai de 3 secondes, connexion comprise : la réponse ne dépasse pas 3 secondes, quel que soit le nombre de navigateurs. Une capture ratée n'interrompt jamais le travail.
5. **Page « Aperçu live »** (`live-preview.html`, lien « Aperçu live » depuis le dressing) : interroge l'API toutes les 2 secondes tant qu'elle est ouverte et montre, pour chaque navigateur au travail, son nom, l'image de sa page et l'heure de capture ; « Aperçu indisponible. » pour un navigateur sans image. Aucun navigateur au travail : « Aucun navigateur au travail. » API indisponible : « Aperçus indisponibles. », et la page continue d'interroger l'API.

## Contrat d'API
- `GET /api/live-previews` (`operationId: showLivePreviews`, tag `live-preview`) → `200` `LivePreviews`. Ne contacte pas Vinted ; capture les onglets du Chrome piloté.
- `LivePreviews` : `previews` (tableau de `LivePreview`, trié par navigateur ; vide si aucun navigateur ne travaille) ; obligatoire.
- `LivePreview` : `browser` (texte, obligatoire) ; `image` (JPEG en base64, `format: byte`) et `capturedAt` (date-heure), présents ensemble, absents si la capture a échoué.

## Critères d'acceptation
- [x] Tant que l'outil travaille dans un onglet du Chrome piloté, l'aperçu de ce navigateur montre la page de cet onglet, telle qu'elle est au moment de la demande.
- [x] L'onglet fermé, son navigateur n'a plus d'aperçu.
- [x] La capture n'agit pas sur la page : ni navigation, ni clic, ni saisie, ni défilement, et le travail en cours se poursuit.
- [x] Une capture impossible donne un aperçu sans image, sans erreur pour le travail ni pour l'API.
- [x] L'API rend un aperçu par navigateur, trié par nom ; la page les montre et se rafraîchit seule.

## Tests d'acceptation
### AT-22-01 – Aperçu de l'onglet au travail
```gherkin
Étant donné l'outil qui travaille dans un onglet du Chrome piloté, sur une page entièrement rouge
Quand je demande les aperçus live
Alors je reçois un aperçu pour ce navigateur, nommé « Chrome (port <port de débogage>) »
Et son image est une capture de la page rouge, datée du moment de la demande
```
Test : `ChromeLivePreviewsTest.shows_the_page_of_the_tab_at_work()`

### AT-22-02 – Plus d'aperçu une fois l'onglet fermé
```gherkin
Étant donné l'outil qui a fini son travail dans un onglet, réussi ou en échec
Quand je demande les aperçus live
Alors je ne reçois aucun aperçu
```
Test : `ChromeLivePreviewsTest.forgets_the_tab_once_closed()`, `ChromeLivePreviewsTest.forgets_the_tab_closed_on_a_failure()`

### AT-22-03 – La capture n'agit pas sur la page
```gherkin
Étant donné l'outil qui travaille dans un onglet, sur une page qui compte les clics, frappes, défilements et navigations
Quand je demande les aperçus live plusieurs fois
Alors la page n'a compté aucun clic, frappe, défilement ni navigation
Et l'outil continue son travail dans l'onglet
```
Test : `ChromeLivePreviewsTest.capturing_does_not_act_on_the_page()`

### AT-22-04 – Capture impossible
```gherkin
Étant donné un onglet noté que Chrome ne connaît plus
Quand je demande les aperçus live
Alors je reçois l'aperçu de ce navigateur sans image ni date
```
Test : `ChromeLivePreviewsTest.a_failed_capture_gives_a_preview_without_image()`

### AT-22-05 – Un aperçu par navigateur, par l'API
```gherkin
Étant donné « Chrome (port 9333) » et « Chrome (port 9222) » au travail, le premier capturé, le second sans image
Quand je demande GET /api/live-previews
Alors je reçois « Chrome (port 9222) » sans image puis « Chrome (port 9333) » avec son image en base64 et sa date
Et sans navigateur au travail, une liste vide
```
Test : `LivePreviewApiControllerTest.shows_one_preview_per_browser_sorted_by_browser()`, `LivePreviewApiControllerTest.no_browser_at_work()`

### AT-22-06 – Page « Aperçu live »
```gherkin
Quand le membre ouvre la page « Aperçu live »
Alors il voit, pour chaque navigateur au travail, son nom, l'image de sa page et l'heure de capture
Et « Aperçu indisponible. » pour un navigateur sans image
Et « Aucun navigateur au travail. » sans navigateur, « Aperçus indisponibles. » si l'API ne répond pas
Et la page se met à jour seule quand les aperçus changent
```
Test : `LivePreviewPageTest.shows_each_browser_at_work()`, `LivePreviewPageTest.says_when_no_browser_is_at_work()`, `LivePreviewPageTest.says_when_the_previews_are_unavailable()`, `LivePreviewPageTest.refreshes_by_itself()`

## Cas limites et erreurs
- Fenêtre de Chrome réduite ou onglet passé au second plan par le membre : Chrome ne dessine plus la page, la capture dépasse 3 secondes et l'aperçu est sans image ; le travail n'est pas touché.
- Onglet fermé pendant la capture : capture en échec, aperçu sans image ; à la demande suivante, plus d'aperçu.
- Plusieurs pages « Aperçu live » ouvertes : chacune déclenche ses captures ; le coût reste celui d'une capture par onglet toutes les 2 secondes et par page ouverte, côté Chrome uniquement.
- Le Chrome piloté n'est pas lancé : aucun onglet noté, aucun aperçu ; l'aperçu ne démarre jamais Chrome.
- Les captures montrent le compte Vinted du membre : elles ne sont servies que par l'application locale, jamais conservées (ni disque ni base), et ne vont pas aux logs.

## Hors périmètre
- Flux vidéo continu (screencast), historique des captures, enregistrement.
- Agir depuis l'aperçu (clic, saisie à distance) : l'aperçu est en lecture seule.
- Aperçu intégré dans la page du dressing : page à part, pour garder le dressing intact (US-15 le modifie en parallèle).
- Montrer le dernier écran d'un navigateur qui ne travaille plus.
- Plusieurs navigateurs : apportés par US-15, l'aperçu les montre déjà un par un.

## Décisions
Décidé par Claude sur délégation de l'utilisateur (2026-11-18, « rédiger et valider la spec soi-même ») :
- Capture d'écran à la demande plutôt que flux vidéo (screencast) ou capture périodique en tâche de fond : aucun thread ni travail tant que personne ne regarde, et la fréquence suit celle de la page (2 s, comme le dashboard). Le screencast imposerait d'accuser réception de chaque image depuis le fil de travail Playwright, qui seul peut lui parler : il ralentirait le travail.
- Connexion de débogage à part, réduite à `Page.captureScreenshot` (WebSocket du JDK, Jackson déjà présent : aucune dépendance ajoutée), plutôt qu'une seconde connexion Playwright : Playwright n'est pas utilisable depuis un autre fil que celui du travail, et une seconde connexion Playwright s'attacherait à tous les onglets (dialogues fermés d'office, scripts injectés) : elle agirait sur la page.
- Un aperçu par onglet de travail, nommé par le port de débogage de son Chrome : compatible avec plusieurs navigateurs (US-15) sans rien supposer de leur nombre ; avec un seul Chrome, un seul aperçu.
- Image JPEG en base64 dans la réponse JSON plutôt qu'un point d'accès binaire par navigateur : un seul appel, image et date cohérentes, `<img src="data:…">` dans la page.
- Pas d'aperçu quand l'onglet est fermé : l'aperçu montre ce qui se passe, pas ce qui s'est passé (le journal, US-23, le dit).
- Délai de capture de 3 secondes, qualité JPEG 60 : assez pour suivre, léger à transférer en local.
- Page à part « Aperçu live » plutôt qu'un bloc du dressing : localise le changement, la page du dressing étant aussi touchée par US-15.

## Questions ouvertes
- Aucune.

## Modèle de domaine
- `LivePreview` (`domain/preview`, value object) | Aperçu live | Nom du navigateur et, si la capture a réussi, capture JPEG de la page de son onglet de travail (`Screenshot` : image et date de capture).
- `LivePreviews` (port sortant, `domain/preview`) : `captureAll()`, les aperçus des onglets de travail, triés par navigateur.
- `ShowLivePreviews` (`application/preview`) : la page.
- Adaptateur `ChromeLivePreviews` (`adapter/out/browser`) : note les onglets ouverts par `ChromeTabs` et les capture par le WebSocket de débogage de leur Chrome.

## Tests unitaires et d'intégration attendus
- `domain` : `LivePreviewTest` (tri par navigateur).
- `adapter/out/browser` (`@Tag("browser")`, pages locales) : `ChromeLivePreviewsTest`.
- `adapter/in/web` : `LivePreviewApiControllerTest`, `LivePreviewPageTest`.
