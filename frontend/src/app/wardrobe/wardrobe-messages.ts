// Codes du contrat OpenAPI -> libellés affichés sur l'écran du dressing.

export const STAGE_LABELS: Record<string, string> = {
  TO_BACK_UP: 'À sauvegarder', TO_VALIDATE: 'À valider', TO_DELETE: 'Prête à supprimer',
  DELETION_TO_CHECK: 'Suppression à vérifier', WAITING: 'En attente avant republication',
  TO_REPUBLISH: 'À republier', REPUBLICATION_TO_CHECK: 'Republication à vérifier', REPUBLISHED: 'Republiée',
  SOLD: 'Vendue', GONE: 'Absente de Vinted', VERIFICATION_FAILED: 'Vérification en erreur'
};
// Étapes qui demandent de vérifier sur Vinted : mises en évidence.
export const STAGES_TO_CHECK = ['DELETION_TO_CHECK', 'REPUBLICATION_TO_CHECK'];
// Étapes qui se relancent (US-20) -> libellé du bouton : l'action que le serveur en déduit.
// Une annonce en attente se republie sans attendre sa date : un clic n'attend pas (US-49).
export const RELAUNCH_LABELS: Record<string, string> = {
  TO_DELETE: 'Supprimer de Vinted', DELETION_TO_CHECK: 'Revérifier la suppression',
  WAITING: 'Republier sur Vinted', TO_REPUBLISH: 'Republier sur Vinted',
  REPUBLICATION_TO_CHECK: 'Vérifier la republication'
};
// RelaunchAction -> message une fois l'action faite.
export const RELAUNCH_DONE_MESSAGES: Record<string, string> = {
  DELETION: 'Suppression faite', DELETION_RECHECK: 'Revérification faite',
  REPUBLICATION: 'Republication faite', REPUBLICATION_VERIFICATION: 'Vérification faite'
};
export const RELAUNCH_DRY_RUN_MESSAGE = "Dry-run réussi, rien n'a été modifié sur Vinted";
export const RELAUNCH_UNCERTAIN_MESSAGE = 'Issue incertaine : à vérifier sur Vinted';
export const RELAUNCH_FAILED_MESSAGE = 'Échec de la relance : vérifiez la fiche avant de relancer';
// RelaunchRefusal -> message ; ACTION_REFUSED ajoute le lien de la fiche.
export const RELAUNCH_REFUSAL_MESSAGES: Record<string, string> = {
  STAGE_CHANGED: "L'étape a changé depuis l'affichage : relisez la ligne avant de relancer",
  NOT_RELAUNCHABLE: 'Rien à relancer à cette étape',
  BROWSER_BUSY: "Navigateur occupé : relancez une fois l'action en cours finie",
  ACTION_REFUSED: 'Relance refusée : voir la fiche',
  BROWSER_UNAVAILABLE: 'Navigateur indisponible : voir les logs',
  VINTED_UNREACHABLE: 'Vinted est inaccessible : réessayez plus tard',
  SESSION_DISCONNECTED: "La session Vinted n'est pas connectée : connectez-vous depuis la page de la session",
  VINTED_PAGE_UNREADABLE: 'Page de Vinted illisible : voir les logs'
};
// ScanProblem.
export const PROBLEM_MESSAGES: Record<string, string> = {
  BROWSER_UNAVAILABLE: 'Le navigateur est indisponible (Chrome introuvable ou qui ne répond pas) : voir les logs.',
  VINTED_UNREACHABLE: 'Vinted est inaccessible (réseau ou blocage anti-robot) : réessayez plus tard.',
  WARDROBE_UNREADABLE: 'Le dressing est illisible (total incohérent ou page changée) : ce qui a été lu est conservé, le prochain scan reprendra. Voir les logs.',
  SCAN_FAILED: 'Le scan a échoué : ce qui a été lu est conservé, le prochain scan reprendra. Voir les logs.'
};
// BackupProblem.
export const BACKUP_PROBLEM_MESSAGES: Record<string, string> = {
  BROWSER_UNAVAILABLE: 'Le navigateur est indisponible (Chrome introuvable ou qui ne répond pas) : voir les logs.',
  VINTED_UNREACHABLE: 'Vinted est inaccessible (réseau, blocage anti-robot ou annonce disparue) : ce qui a été sauvegardé est conservé, la prochaine sauvegarde reprendra.',
  SESSION_DISCONNECTED: "La session Vinted n'est pas connectée : connectez-vous depuis la page de la session, puis relancez la sauvegarde, qui reprendra là où elle s'est arrêtée.",
  LISTING_UNREADABLE: 'Une annonce est illisible (formulaire changé ou incomplet) : sa sauvegarde précédente est intacte. Voir les logs.',
  BACKUP_FAILED: 'La sauvegarde a échoué : ce qui a été sauvegardé est conservé, la prochaine sauvegarde reprendra. Voir les logs.'
};
// StatisticsRefreshProblem (US-25).
export const STATISTICS_PROBLEM_MESSAGES: Record<string, string> = {
  BROWSER_UNAVAILABLE: 'Le navigateur est indisponible (Chrome introuvable ou qui ne répond pas) : voir les logs.',
  VINTED_UNREACHABLE: 'Vinted est inaccessible (réseau ou blocage anti-robot) : réessayez plus tard.',
  WARDROBE_UNREADABLE: "Le dressing est illisible (total incohérent ou page changée) : aucun relevé n'a été ajouté. Voir les logs.",
  NO_STATISTICS_SHOWN: "Aucune carte du dressing ne montre ses vues et favoris : vérifiez que la session Vinted est connectée. Aucun relevé n'a été ajouté.",
  REFRESH_FAILED: "Le rafraîchissement a échoué : aucun relevé n'a été ajouté. Voir les logs."
};
// RunStopReason (US-19).
export const RUN_STOP_MESSAGES: Record<string, string> = {
  SESSION_DISCONNECTED: "la session Vinted n'est pas connectée (connectez-vous, puis relancez)",
  BROWSER_UNAVAILABLE: 'le navigateur est indisponible (voir les logs)',
  VINTED_UNREACHABLE: 'Vinted est inaccessible (réseau ou blocage anti-robot) : réessayez plus tard',
  VINTED_PAGE_UNREADABLE: 'une page de Vinted est illisible (page changée ?) : voir les logs',
  RUN_FAILED: 'échec inattendu (voir les logs)'
};
// DataArchiveRefusalReason (US-26).
export const ARCHIVE_PROBLEM_MESSAGES: Record<string, string> = {
  BROWSER_BUSY: 'Une action navigateur est en cours : réessayez quand elle sera finie.',
  ARCHIVE_FAILED: "L'archive n'a pas pu être créée : rien n'a été téléchargé. Voir les logs."
};
export const ARCHIVE_UNREACHABLE_MESSAGE = "L'application ne répond pas : rien n'a été téléchargé.";
export const APPLICATION_DOWN_MESSAGE = "L'application ne répond pas : nouvel essai dans 2 secondes.";
// Statuts de POST /api/listings/tag-edits -> message ; rien n'est modifié en cas de refus.
export const TAG_EDIT_REFUSALS: Record<number, string> = {
  400: 'Tag invalide (1 à 30 caractères) : aucun tag modifié',
  404: "Une annonce sélectionnée n'est plus connue : aucun tag modifié"
};
export const TAG_EDIT_FAILED_MESSAGE = 'Échec de la modification des tags : aucun tag modifié';
