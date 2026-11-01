# Glossaire – langage omniprésent

Les termes du code sont en anglais ; les specs restent en français. Un terme anglais est choisi une fois et ne varie plus.

| Terme (code) | Équivalent français | Définition |
|---|---|---|
| `Member` | Membre | Utilisateur inscrit sur Vinted ; ici, le propriétaire du dressing. |
| `Wardrobe` | Dressing | Ensemble des annonces d'un membre ; sa page est publique (elle ne dit pas si le membre est connecté). |
| `VintedAccount` | Compte Vinted | Compte du membre sur Vinted, atteint par un navigateur piloté (port sortant). |
| `VintedSession` | Session Vinted | Connexion de l'outil au compte Vinted, conservée dans le profil navigateur d'un lancement à l'autre. |
| `SessionStatus` (`CONNECTED` / `DISCONNECTED`) | État de session (connecté / non connecté) | Connecté si la page des paramètres du profil s'affiche, non connecté si Vinted redirige vers la connexion. Absent tant qu'aucune vérification n'a eu lieu (« non vérifié »). |
| `BrowserAction` (`CHECKING` / `LOGGING_IN`) | Action navigateur (vérification / connexion) | Opération qui ouvre le profil navigateur ; une seule à la fois. |
| `SessionCheck` (`checkSession`) | Vérification de session | Ouverture de la page des paramètres du profil dans un onglet de Chrome pour connaître l'état de session ; uniquement sur demande. |
| `ProfileSettings` | Paramètres du profil | Page `/settings/profile` de Vinted, réservée au membre connecté (un visiteur est redirigé vers la connexion). |
| `ManualLogin` (`awaitManualLogin`, `LogInToVinted`) | Connexion manuelle | Connexion faite par le membre lui-même dans le Chrome piloté ; l'outil ne saisit jamais d'identifiants. |
| `SessionProblem` | Problème de session | Raison affichée quand une action navigateur n'a pas abouti : connexion manuelle non terminée, navigateur indisponible. |
| `SessionOverview` | Aperçu de session | État de session, action navigateur en cours et problème éventuel, tels qu'affichés. |
| `BrowserUnavailable` | Navigateur indisponible | Chrome introuvable, ou Chrome qui ne répond pas sur le port de débogage. |
| `VintedUnreachable` | Vinted inaccessible | Vinted ne répond pas, ou répond par une erreur HTTP (cas attendu de la page de blocage anti-robot). |
| `Chrome` | Chrome piloté | Chrome normal démarré par l'outil avec un profil dédié, auquel il s'attache par le port de débogage ; reste ouvert entre deux actions. |
