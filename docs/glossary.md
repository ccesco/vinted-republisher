# Glossaire – langage omniprésent

Les termes du code sont en anglais ; les specs restent en français. Un terme anglais est choisi une fois et ne varie plus.

| Terme (code) | Équivalent français | Définition |
|---|---|---|
| `Member` | Membre | Utilisateur inscrit sur Vinted ; ici, le propriétaire du dressing. |
| `Wardrobe` | Dressing | Ensemble des annonces d'un membre ; sa page est publique (elle ne dit pas si le membre est connecté). |
| `VintedAccount` | Compte Vinted | Compte du membre sur Vinted, atteint par un navigateur piloté (port sortant). |
| `VintedSession` | Session Vinted | Connexion de l'outil au compte Vinted, conservée dans le profil navigateur d'un lancement à l'autre. |
| `SessionStatus` (`CONNECTED` / `DISCONNECTED`) | État de session (connecté / non connecté) | Connecté si la page des paramètres du profil s'affiche, non connecté si Vinted redirige vers la connexion. Absent tant qu'aucune vérification n'a eu lieu (« non vérifié »). |
| `BrowserAction` (`CHECKING` / `LOGGING_IN` / `SCANNING` / `BACKING_UP` / `DELETING` / `REPUBLISHING` / `REFRESHING_STATISTICS`) | Action navigateur (vérification / connexion / scan / sauvegarde / suppression / republication / rafraîchissement des statistiques) | Opération qui ouvre le profil navigateur ; une seule à la fois. |
| `SessionCheck` (`checkSession`) | Vérification de session | Ouverture de la page des paramètres du profil dans un onglet de Chrome pour connaître l'état de session ; uniquement sur demande. |
| `ProfileSettings` | Paramètres du profil | Page `/settings/profile` de Vinted, réservée au membre connecté (un visiteur est redirigé vers la connexion). |
| `ManualLogin` (`awaitManualLogin`, `LogInToVinted`) | Connexion manuelle | Connexion faite par le membre lui-même dans le Chrome piloté ; l'outil ne saisit jamais d'identifiants. |
| `SessionProblem` | Problème de session | Raison affichée quand une action navigateur n'a pas abouti : connexion manuelle non terminée, navigateur indisponible. |
| `SessionOverview` | Aperçu de session | État de session, action navigateur en cours et problème éventuel, tels qu'affichés. |
| `BrowserUnavailable` | Navigateur indisponible | Chrome introuvable, ou Chrome qui ne répond pas sur le port de débogage. |
| `VintedUnreachable` | Vinted inaccessible | Vinted ne répond pas, ou répond par une erreur HTTP (cas attendu de la page de blocage anti-robot). |
| `Chrome` | Chrome piloté | Chrome normal démarré par l'outil avec un profil dédié, auquel il s'attache par le port de débogage ; reste ouvert entre deux actions. |
| `Listing` | Annonce | Article du dressing connu de l'outil ; conservé en base même s'il n'est plus présent. |
| `VintedListingId` | Identifiant Vinted | Identifiant numérique d'une annonce sur Vinted. |
| `Price` | Prix | Montant décimal et devise d'une annonce. |
| `PriceChange` (`priceHistoryOf`) | Changement de prix (historique de prix) | Prix d'une annonce et date où l'outil l'a vue à ce prix pour la première fois : ajouté à chaque enregistrement de l'annonce dont le prix (montant ou devise) diffère du dernier de son historique, ou qui n'en a pas encore ; jamais modifié ni supprimé. L'historique d'une annonce connue avant lui commence à son prochain enregistrement. |
| `WardrobeScan` (`ScanWardrobe`) | Scan du dressing | Parcours du dressing et lecture de chaque annonce par le Chrome piloté, uniquement sur demande ; persisté avec ses dates de début et de fin, il reste non terminé après un échec et se reprend au scan suivant. |
| `WardrobeListing` | Relevé du dressing | Résultat du parcours du dressing : total affiché et liens des annonces. |
| `ListingLink` | Lien d'annonce | Identifiant et adresse d'une annonce, relevés sur le dressing, avec les statistiques que montre sa carte, s'il y en a. |
| `ListingStatistics` | Statistiques d'une annonce (vues et favoris) | Nombre de vues et de favoris (les « likes » de Vinted) d'une annonce, lus sur sa carte du dressing, que Vinted ne montre qu'au membre connecté ; les deux ensemble ou aucun. |
| `StatisticsReading` | Relevé des statistiques | Statistiques d'une annonce à la fin d'un relevé du dressing, datées ; jamais modifié ni supprimé, un par scan ou rafraîchissement qui les lit. |
| `ListingStatisticsRepository` | Historique des statistiques | Tous les relevés de statistiques, y compris des annonces absentes ou non actives ; donne le dernier relevé de chaque annonce. |
| `StatisticsComparison` | Avant / après republication | Sur la fiche d'une annonce mise en vente par une republication : dernier relevé de l'annonce remplacée (avant), le sien (après), date de republication, et total cumulé des derniers relevés de toute la chaîne de republications (une annonce jamais relevée compte pour zéro). |
| `WardrobeVisit` | Visite du dressing | Ce que le scan fait de ce que le lecteur trouve : choisir les annonces à ouvrir, enregistrer chaque annonce lue. |
| `LastScanProblem` | Problème du dernier scan | Problème affiché jusqu'au scan suivant, conservé en mémoire seulement. |
| `ScanProblem` | Problème de scan | Raison affichée quand un scan n'a pas abouti : navigateur indisponible, Vinted inaccessible, dressing illisible, échec du scan. |
| `RefreshStatistics` | Rafraîchissement des statistiques | Nouveau relevé des vues et favoris de toutes les annonces, lus sur les cartes du dressing sans ouvrir aucune annonce, uniquement sur demande ; tout ou rien, sans effet sur les annonces ni les scans. |
| `StatisticsRefreshProblem` / `LastStatisticsRefreshProblem` | Problème du dernier rafraîchissement | Raison affichée jusqu'au rafraîchissement suivant : navigateur indisponible, Vinted inaccessible, dressing illisible, aucune statistique affichée, échec ; en mémoire seulement. |
| `NoStatisticsShown` | Aucune statistique affichée | Aucune carte du dressing ne montre ses vues et favoris (membre déconnecté ou page changée) : un rafraîchissement n'en tire aucun relevé. |
| `WardrobeUnreadable` | Dressing illisible | Total illisible ou incohérent, aucun lien relevé, ou page d'annonce sans données `Product` complètes. |
| `ScannedListing` | Annonce lue | Annonce telle que lue sur sa page lors d'un scan, active si sa disponibilité est `InStock`. |
| `InvalidListing` | Annonce invalide | Annonce lue dont une valeur est inacceptable (identifiant, titre, lien, prix). |
| `ScanPace` (`PauseRange`) | Rythme du scan | Pauses aléatoires entre deux défilements et entre deux annonces, chacune dans ses bornes configurées. |
| `WardrobeOverview` | Aperçu du dressing | Scan en cours, date de fin du dernier scan terminé et problème éventuel, tels qu'affichés ; les annonces sont dans l'inventaire des annonces. |
| `ListingInventory` (`ShowListingInventory`) | Inventaire des annonces | Toutes les annonces connues, présentes ou absentes, chacune avec son dernier relevé de statistiques, sa sauvegarde actuelle et la date de la validation qui vaut pour elle ; lu en base seulement, filtré, trié et fouillé selon les critères d'affichage. |
| `InventoryEntry` | Ligne de l'inventaire | Une annonce de l'inventaire avec son dernier relevé, sa sauvegarde actuelle et sa date de validation éventuels, et son étape de traitement. |
| `ListingStage` | Étape de traitement | Où en est une annonce dans le cycle sauvegarde, validation, suppression, attente, republication : à sauvegarder, à valider, prête à supprimer, suppression à vérifier, en attente avant republication, à republier, republication à vérifier, republiée, absente de Vinted. Déduite des faits en base à chaque lecture, jamais stockée. |
| `RelaunchListing` (`RelaunchAction`) | Relance d'une annonce (action de relance) | Lancement, depuis la liste, de l'action qui suit l'étape de traitement d'une annonce : suppression, revérification de la suppression, republication ou vérification de la republication ; une seule étape, par le cas d'usage de cette étape, seulement si l'annonce est toujours à l'étape vue par le membre. |
| `StageChanged` | Étape changée | Refus d'une relance : l'annonce n'est plus à l'étape vue par le membre ; rien n'est lancé. |
| `NotRelaunchable` | Étape non relançable | Refus d'une relance : l'étape attend le membre (sauvegarde, validation), le délai de republication, ou plus rien ; rien n'est lancé. |
| `RelaunchReport` | Compte rendu de relance | Action lancée par une relance, dry-run ou non, et annonce relue ensuite avec sa nouvelle étape. |
| `FailureHandling` (`WAIT` / `SKIP_LISTING` / `STOP_RUN`) | Conduite après un échec (attendre / passer l'annonce / tout arrêter) | Réponse à l'échec d'une relance : attendre (jusqu'à une date quand elle est connue : cadence, republication), passer l'annonce (refus qui lui est propre), ou tout arrêter (session, navigateur, Vinted inaccessible ou page illisible, échec inattendu). Une écriture n'est jamais retentée. |
| `AutomaticRun` (`LaunchAutomaticRun`) | Série automatique (« Tout lancer ») | Relance de chaque annonce prête, l'une après l'autre, en arrière-plan : vérifications, republications, suppressions, attentes. Attend quand un échec le demande, écarte une annonce refusée pour elle-même ou dont l'étape ne bouge pas, s'arrête sur un problème du compte ou de Vinted. Une seule à la fois ; ne redémarre jamais seule. |
| `RunStatus` (`IDLE` / `RUNNING` / `WAITING` / `PAUSED` / `FINISHED` / `STOPPED`) | État de la série (aucune / en cours / en attente / en pause / terminée / arrêtée) | Où en est la série automatique ; gardé en mémoire, `IDLE` après un redémarrage. |
| `RunStopReason` | Raison d'arrêt de la série | Session non connectée, navigateur indisponible, Vinted inaccessible, page de Vinted illisible, échec inattendu, ou arrêt demandé par le membre (`STOPPED_BY_MEMBER`). |
| `RunWaiting` | Attente de la série | Port sortant : attendre jusqu'à une date (republication, fin de limite de cadence, navigateur occupé, pause), interrompue dans la seconde par une pause ou un arrêt demandé. |
| `InventoryCriteria` | Critères d'affichage | Présence, état de la sauvegarde, recherche, critère et sens du tri demandés par le membre ; par défaut les annonces présentes, les plus récentes d'abord. |
| `PresenceFilter` (`PRESENT` / `ABSENT` / `ALL`) | Filtre de présence (présentes / absentes / toutes) | Annonces dans le dressing au dernier scan terminé, sorties du dressing, ou les deux. |
| `BackupFilter` (`ALL` / `NOT_BACKED_UP` / `NOT_VALIDATED` / `VALIDATED`) | Filtre de sauvegarde (toutes / non sauvegardées / sauvegardées non validées / validées) | État de la sauvegarde actuelle ; seule une validation de cette version compte. |
| `InventorySort` (`RECENCY` / `TITLE` / `PRICE` / `VIEWS` / `FAVOURITES` / `SAVED_AT`) et `SortDirection` (`ASC` / `DESC`) | Tri (mise en ligne / titre / prix / vues / favoris / date de sauvegarde) et sens | Une annonce sans la valeur triée finit la liste quel que soit le sens ; à égalité, la plus récente d'abord. Mise en ligne = identifiant Vinted, le plus haut étant le plus récent. |
| `ListingDetail` (`ShowListingDetail`) | Fiche d'une annonce | La ligne de l'inventaire d'une annonce, la date où le scan l'a vue pour la dernière fois et tous ses relevés de statistiques et tous ses prix, du plus récent au plus ancien, et les lignes du journal de l'annonce ; lue en base seulement. |
| `ActionJournal` (`ShowActionJournal`) | Journal des actions | Toutes les actions de l'outil (scans, sauvegardes du dressing, validations, suppressions, revérifications, republications) avec leur issue, refus, dry-runs et erreurs compris ; jamais purgé, lu par pages de 50, les plus récentes d'abord. |
| `ActionRecord` | Action enregistrée | Ce qu'une ligne du journal contient : date, action, issue, annonce et raison éventuelles (même code que la réponse de l'API). |
| `JournalEntry` | Ligne du journal | Une action enregistrée, numérotée dans l'ordre d'écriture ; jamais modifiée ni supprimée. |
| `JournalPage` | Page du journal | Au plus 50 lignes, les plus récentes d'abord, et s'il en reste de plus anciennes. |
| `JournaledAction` (`SCAN` / `WARDROBE_BACKUP` / `BACKUP_VALIDATION` / `DELETION` / `DELETION_RECHECK` / `REPUBLICATION` / `STATISTICS_REFRESH`) | Action journalisée | Scan du dressing, sauvegarde du dressing, validation, suppression, revérification, republication, rafraîchissement des statistiques. |
| `ActionOutcome` (`STARTED` / `SUCCEEDED` / `DRY_RUN` / `REFUSED` / `IN_DOUBT` / `NOT_DONE` / `FAILED`) | Issue d'une action | Lancée, réussie, dry-run réussi, refusée, dans le doute, non faite, en échec. |
| `JournalWriter` | Écriture du journal | Écrit la ligne de chaque action pour les cas d'usage ; une écriture impossible est seulement notée dans les logs, l'action et sa réponse ne changent pas. |
| `ListingNotFound` | Annonce inconnue | L'outil ne connaît aucune annonce de cet identifiant. |
| `SearchText` | Texte cherché | Titre ou recherche sans casse, accents ni espaces autour : la recherche trouve le titre qui le contient. |
| `ListingContent` | Contenu d'une annonce | Tous les champs du formulaire de mise en vente sauf les photos, tels que lus sur Vinted : de quoi republier l'annonce sans rien ressaisir. |
| `FormOption` / `FormOptions` | Option du formulaire | Choix unique (catégorie, marque, état, taille, format du colis) ou multiple (couleurs, matières) : identifiant Vinted, qui sert à republier, et libellé affiché, vide si le formulaire ne l'affiche pas. |
| `CategoryDetails` | Détails propres à la catégorie | Champs que le formulaire ne demande que dans certaines catégories : ISBN, titre du livre, auteur, fabricant, modèle, mesures. |
| `ListingEditForm` | Formulaire de modification | Page `/items/<id>/edit` de Vinted, réservée au membre connecté ; l'outil ne fait que la lire, sans rien cliquer, saisir ni soumettre. |
| `ListingBackup` | Sauvegarde d'une annonce | Contenu d'une annonce, ses photos gardées dans leur ordre et sa date de sauvegarde ; une seule par annonce, remplacée seulement par une lecture complète. Une sauvegarde faite avant la conservation des photos n'en a aucune. |
| `ListingReading` | Lecture d'une annonce | Ce qu'une visite d'une annonce lit : son contenu (formulaire de modification) et ses photos téléchargées (page publique), au moins une. |
| `DownloadedPhoto` | Photo téléchargée | Fichier d'une photo tel que Vinted l'a servi, à l'octet près, avec son type ; jamais réencodé. |
| `ListingPhoto` | Photo gardée | Photo d'une sauvegarde : son rang dans l'annonce (0 = couverture), l'empreinte SHA-256 et l'extension de son fichier. |
| `ListingGallery` | Galerie de l'annonce | Liste ordonnée des photos pleine taille que donne la page publique d'une annonce ; la page les télécharge elle-même. |
| `PhotoStorage` | Rangement des photos | Fichiers des photos sous `APP_DATA_DIR/photos/<annonce>/<SHA-256>.<extension>`, écrits une fois et jamais modifiés ; relus tels quels pour la vérification et l'aperçu (absent ou illisible = manquant). |
| `WardrobeBackup` (`BackUpWardrobe`) | Sauvegarde du dressing | Lecture du formulaire de modification et des photos de la page publique de chaque annonce présente, uniquement sur demande ; persistée avec ses dates de début et de fin, elle reste non terminée après un échec et se reprend à la sauvegarde suivante. |
| `BackupPace` | Rythme de la sauvegarde | Pause aléatoire entre deux pages ouvertes par la sauvegarde, dans ses bornes configurées. |
| `BackupOverview` | Aperçu de la sauvegarde | Sauvegarde en cours, date de fin de la dernière terminée et problème éventuel ; la sauvegarde de chaque annonce est dans l'inventaire des annonces. |
| `BackupProblem` | Problème de sauvegarde | Raison affichée quand une sauvegarde n'a pas abouti : navigateur indisponible, Vinted inaccessible, session non connectée, annonce illisible, échec de la sauvegarde. |
| `LastBackupProblem` | Problème de la dernière sauvegarde | Problème affiché jusqu'à la sauvegarde suivante, conservé en mémoire seulement. |
| `ListingUnreadable` | Annonce illisible | Formulaire sans données de l'annonce, champ obligatoire absent ou invalide, ou attribut de code inconnu. |
| `SessionDisconnected` | Session non connectée | Vinted a renvoyé l'outil vers sa page de connexion pendant une action qui exige le membre connecté. |
| `BackupVerification` | Vérification de la sauvegarde | Contrôle d'une sauvegarde avant validation et avant suppression : au moins une photo, chaque fichier présent avec l'empreinte enregistrée, annonce présente dans le dressing, titre et prix identiques au dernier scan. Jamais gardée : refaite à chaque fois. |
| `VerificationFailure` | Échec de vérification | Aucune photo, photo manquante, photo modifiée, annonce absente, titre changé ou prix changé (valeurs sauvegardée et lue). |
| `BackupValidation` (`ValidateListingBackup`) | Validation de la sauvegarde | Confirmation par le membre, annonce par annonce, de la sauvegarde qu'il a vue et dont la vérification est réussie ; ne vaut que pour cette version de la sauvegarde (sa date). |
| `BackupValidationRepository` | Validations | Dernière validation de chaque annonce ; une validation dont la version n'est plus la sauvegarde actuelle ne compte pas. |
| `ListingBackupView` (`ShowListingBackup`) | Aperçu de la sauvegarde d'une annonce | Contenu par ses libellés, photos gardées dans l'ordre, résultat de la vérification et date de validation éventuelle. |
| `ListingNotBackedUp` | Annonce non sauvegardée | L'annonce n'a aucune sauvegarde à montrer ni à valider. |
| `BackupReplaced` | Sauvegarde remplacée | La version vue par le membre n'est plus la sauvegarde actuelle : la validation est refusée. |
| `BackupNotVerified` | Sauvegarde non vérifiée | La vérification refaite au moment de valider échoue : la validation est refusée. |
| `ListingDeletion` (`DeleteListing`) | Suppression d'une annonce | Tentative de supprimer une annonce sur Vinted, arrivée au clic de confirmation de Vinted : annonce, version de la sauvegarde, date du clic, issue, date de règlement. Enregistrée non confirmée juste avant le clic, réglée une seule fois ; jamais supprimée. |
| `DeletionOutcome` (`CONFIRMED` / `UNCONFIRMED` / `NOT_DONE`) | Issue de la suppression (confirmée / non confirmée / non faite) | Confirmée : Vinted ne montre plus l'annonce. Non confirmée : doute après le clic, à revérifier. Non faite : doute levé, l'annonce était toujours là. |
| `RecheckListingDeletion` | Revérification de la suppression | Ouverture de la page de l'annonce, sans aucun clic, pour régler une suppression non confirmée. |
| `ListingDifference` | Écart relu sur Vinted | Différence entre l'annonce montrée par Vinted juste avant sa suppression et sa sauvegarde : plus active, titre changé, prix changé. |
| `ListingChanged` | Annonce changée sur Vinted | Refus de suppression : Vinted montre l'annonce autrement que sa sauvegarde. |
| `DeletionVisit` | Décision de suppression | Ce que la suppression décide sur la page de l'annonce, avant tout clic : écarts, dry-run, ou enregistrement de la tentative puis clic. |
| `ListingDeleter` | Suppresseur d'annonce | Port sortant : supprime une annonce sur Vinted comme le membre, dans le Chrome piloté, sans appel direct à l'API de Vinted. |
| `ListingDeletionRepository` | Suppressions | Toutes les tentatives de suppression ; donne la plus récente de chaque annonce. |
| `DeletionMode` (`DRY_RUN` / `REAL`) | Mode de suppression (dry-run / réel) | Fixé au démarrage par `DELETION_DRY_RUN` ; en dry-run, tout est contrôlé et le bouton de suppression trouvé, mais rien n'est cliqué ni enregistré. |
| `DeletionReport` (`ShowListingDeletion`) | Aperçu de la suppression | Aperçu de la sauvegarde d'une annonce avec sa dernière suppression, sa date de republication (suppression confirmée seulement), le mode de suppression et, en réponse d'un dry-run, sa réussite. |
| `RepublicationDelay` | Délai avant republication | Durée minimale entre la preuve d'une suppression et la republication de l'annonce (`republication.minimum-delay`, 5 minutes, strictement positive) ; date de republication calculée depuis la preuve gardée en base, donc inchangée après un redémarrage, et sans attente une fois écoulée. |
| `InvalidRepublicationDelay` | Délai avant republication invalide | Délai nul ou négatif : refusé ; dans la configuration, l'application ne démarre pas. |
| `VintedWrite` | Écriture sur Vinted | Action de l'outil qui modifie le compte Vinted : suppression (`DELETION`) ou republication (`REPUBLICATION`), comptée dès sa tentative enregistrée juste avant le clic, quelle que soit son issue ; ni le dry-run ni les vérifications n'en sont. |
| `WriteCadence` | Cadence d'écriture | Limites des écritures sur Vinted : intervalle minimal entre deux écritures de tout type (`cadence.minimum-interval`, 2 minutes) et plafond par type sur 24 heures glissantes (`cadence.daily-deletions`, `cadence.daily-republications`, 20) ; calculée depuis les tentatives gardées en base. |
| `CadenceLimitReached` | Limite de cadence atteinte | Refus d'une écriture réelle qui dépasserait la cadence, avec la date à partir de laquelle elle redevient permise (`allowedAt`) ; rien n'est touché sur Vinted. |
| `InvalidWriteCadence` | Cadence d'écriture invalide | Intervalle nul, négatif ou d'au moins 24 heures, ou plafond nul ou négatif : refusé ; dans la configuration, l'application ne démarre pas. |
| `ListingRepublication` (`RepublishListing`) | Republication d'une annonce | Tentative de remettre en vente une annonce supprimée, arrivée au clic sur « Publier » : annonce, version de la sauvegarde, date du clic, issue, nouvel identifiant et date de preuve. Enregistrée en erreur juste avant le clic, réussie une fois la nouvelle annonce retrouvée, non publiée si le membre constate sur Vinted qu'elle ne l'est pas ; au plus une réussie ou en erreur par annonce (la dernière fait foi), jamais supprimée. Réussie, elle relie l'ancienne annonce à la nouvelle (« republiée depuis »). |
| `RepublicationOutcome` (`PUBLISHED` / `FAILED` / `NOT_PUBLISHED`) | Issue de la republication (réussie / erreur de republication / non publiée) | Réussie : nouvelle annonce retrouvée seule sur Vinted. Erreur : tout le reste après le clic ; bloque une nouvelle republication jusqu'à la vérification (US-13). Non publiée : le membre a constaté sur Vinted qu'elle n'est pas publiée ; une nouvelle republication est possible. |
| `VerifyListingRepublication` | Vérification de la republication | « Vérifier sur Vinted » : lecture sur Vinted, sans clic, des annonces affichées à l'ouverture du dressing, qui lève le doute d'une erreur de republication (une seule annonce correspondante → réussie ; aucune ou plusieurs → reste en erreur, le membre valide lui-même) et compare une republication réussie à la sauvegarde. |
| `ValidateListingRepublication` | Validation manuelle de la republication | Le membre dit ce que Vinted montre pour une republication en erreur : « publiée » avec le lien de la nouvelle annonce, dont la seule page est lue pour la contrôler (titre et prix) et la comparer, ou « non publiée », sans ouvrir Chrome. |
| `NewListingLink` | Lien de la nouvelle annonce | Lien collé par le membre ; seul l'identifiant (`/items/<identifiant>`) sert, l'outil ouvre l'annonce à son adresse à lui. |
| `NewListingLinkInvalid` | Lien invalide | Refus : lien absent, sans identifiant d'annonce, de l'ancienne annonce ou de la nouvelle annonce d'une autre republication ; Chrome n'est pas ouvert. |
| `NewListingMismatch` | Nouvelle annonce différente | Refus : la page du lien n'a pas le titre et le prix de la sauvegarde ; rien n'est enregistré. |
| `ListingFieldDifference` | Différence avec la sauvegarde | Champ du formulaire dont la valeur lue sur la nouvelle annonce n'est pas celle sauvegardée (valeur sauvegardée, valeur lue) ; options comparées par identifiant Vinted, photos par leur nombre. Affichée seulement, rien n'est corrigé. |
| `NoRepublicationToVerify` | Aucune republication à vérifier | Refus : pas de republication, ou dernière non publiée ; Chrome n'est pas ouvert. |
| `RepublicationMode` (`DRY_RUN` / `REAL`) | Mode de republication (dry-run / réel) | Fixé au démarrage par `REPUBLICATION_DRY_RUN`, indépendant du mode de suppression ; en dry-run, tout est contrôlé et le formulaire entièrement rempli, mais « Publier » n'est pas cliqué et rien n'est enregistré. |
| `RepublicationChecks` | Contrôles avant republication | Sauvegarde vue = sauvegarde actuelle, aucune republication, dernière suppression confirmée de cette version, date de republication atteinte, sauvegarde validée, photos présentes et intactes ; tous avant d'ouvrir Chrome. |
| `ListingPublisher` | Publieur d'annonce | Port sortant : remplit le formulaire de mise en vente et publie comme le membre, dans le Chrome piloté, sans appel direct à l'API de Vinted. |
| `RepublicationVisit` | Décision de publication | Ce que la republication décide une fois le formulaire rempli : dry-run, ou enregistrement de la tentative puis clic sur « Publier ». |
| `ListingForm` | Formulaire de mise en vente | Page de Vinted pour créer une annonce. |
| `ListingFormField` | Champ du formulaire de mise en vente | Photos, titre, description, prix, catégorie, marque, état, taille, couleurs, matières, format du colis, champs propres à la catégorie. |
| `FieldNotFillable` | Champ impossible à remplir | Option introuvable par son identifiant comme par son libellé, ou champ absent du formulaire ; rien publié. |
| `ListingFormUnreadable` | Formulaire de mise en vente illisible | Le formulaire n'est pas celui attendu ; rien publié. |
| `NewListingSearch` | Recherche de la nouvelle annonce | Une lecture du dressing juste après « Publier », qui n'ouvre que les annonces inconnues de l'outil : la nouvelle annonce est la seule au titre et au prix de la sauvegarde. |
| `RepublicationReport` (`ShowListingRepublication`) | Aperçu de la republication | Aperçu de la suppression avec la republication, la nouvelle annonce une fois republiée, le mode de republication et, en réponse d'un dry-run, sa réussite. |
| `ListingNotDeleted` | Annonce non supprimée | Refus de republication : la dernière suppression n'est pas confirmée, ou pas sur la sauvegarde actuelle. |
| `RepublicationTooEarly` | Trop tôt pour republier | Refus de republication : la date de republication n'est pas atteinte. |
| `ListingAlreadyRepublished` | Annonce déjà republiée | Refus de republication : une republication réussie existe. |
| `RepublicationInError` | Erreur de republication en cours | Refus de republication : la tentative précédente est en erreur, à vérifier sur Vinted. |
| `BackupNotValidated` | Sauvegarde non validée | Refus de suppression : la sauvegarde actuelle n'est pas validée par le membre. |
| `ListingAlreadyDeleted` | Annonce déjà supprimée | Refus de suppression : une suppression confirmée existe déjà. |
| `DeletionUnconfirmed` | Suppression non confirmée | Refus d'une nouvelle suppression tant que la dernière reste dans le doute. |
| `NoUnconfirmedDeletion` | Aucune suppression à revérifier | Seule une suppression non confirmée se règle. |
| `ListingPageUnreadable` | Page de l'annonce illisible | La page de l'annonce n'est pas celle attendue (bouton de suppression introuvable) ; rien n'a été cliqué. |
| `BrowserBusy` | Navigateur occupé | Une autre action navigateur est en cours : une seule à la fois. |
