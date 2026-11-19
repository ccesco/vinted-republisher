// Codes du contrat OpenAPI -> libellés affichés sur la fiche d'une annonce.

export const LIFECYCLE_LABELS: Record<string, string> = {
  FIRST_SEEN: 'vue pour la première fois',
  DELETED: "supprimée par l'outil",
  LEFT_WARDROBE: "sortie du dressing (vendue ou retirée hors de l'outil, vue pour la dernière fois à cette date)"
};
export const CONTENT_LABELS: [string, string][] = [
  ['title', 'Titre'], ['description', 'Description'], ['price', 'Prix'], ['category', 'Catégorie'],
  ['brand', 'Marque'], ['condition', 'État'], ['size', 'Taille'], ['colors', 'Couleurs'],
  ['materials', 'Matières'], ['packageSize', 'Format du colis'], ['isbn', 'ISBN'],
  ['bookTitle', 'Titre du livre'], ['author', 'Auteur'], ['manufacturer', 'Fabricant'],
  ['manufacturerLabel', 'Libellé du fabricant'], ['model', 'Modèle'], ['measurementWidth', 'Largeur'],
  ['measurementLength', 'Longueur'], ['measurementUnit', 'Unité de mesure']
];
export const VALIDATION_REFUSALS: Record<string, string> = {
  BACKUP_REPLACED: "La sauvegarde a été remplacée depuis l'ouverture de cette page : rechargez-la pour voir la nouvelle version avant de la valider.",
  VERIFICATION_FAILED: "La vérification a échoué : rien n'a été validé."
};
export const APPLICATION_DOWN_MESSAGE = "L'application ne répond pas : rechargez la page.";
export const DELETION_REFUSALS: Record<string, string> = {
  BACKUP_REPLACED: "la sauvegarde a été remplacée depuis l'ouverture de cette page : rechargez-la.",
  NOT_VALIDATED: "la sauvegarde actuelle n'est pas validée.",
  ALREADY_DELETED: "l'annonce est déjà supprimée de Vinted.",
  DELETION_UNCONFIRMED: "la dernière suppression n'est pas confirmée : revérifiez-la sur Vinted.",
  NO_UNCONFIRMED_DELETION: 'aucune suppression à revérifier.',
  BROWSER_BUSY: 'une autre action est en cours dans le navigateur : réessayez quand elle sera finie.',
  VERIFICATION_FAILED: 'la vérification de la sauvegarde a échoué.',
  LISTING_CHANGED: "Vinted montre l'annonce autrement que sa sauvegarde",
  BROWSER_UNAVAILABLE: 'le navigateur est indisponible.',
  VINTED_UNREACHABLE: 'Vinted est inaccessible.',
  SESSION_DISCONNECTED: "la session Vinted n'est pas connectée : connectez-vous depuis la page de session.",
  LISTING_PAGE_UNREADABLE: "la page de l'annonce sur Vinted n'est pas celle attendue.",
  CADENCE_LIMIT_REACHED: 'limite de cadence atteinte'
};
export const DELETION_FAILED_MESSAGE = "Échec de la suppression : vérifiez l'annonce sur Vinted (détail dans les logs).";
export const RECHECK_FAILED_MESSAGE = 'Revérification impossible, le doute reste : ';
export const DRY_RUN_PASSED = 'Dry-run réussi : contrôles réussis, annonce relue sur Vinted, bouton de suppression'
  + " trouvé. Rien n'a été supprimé.";
export const REPUBLICATION_REFUSALS: Record<string, string> = {
  BACKUP_REPLACED: "la sauvegarde a été remplacée depuis l'ouverture de cette page : rechargez-la.",
  NOT_VALIDATED: "la sauvegarde actuelle n'est pas validée.",
  NOT_DELETED: "l'annonce n'a pas de suppression confirmée de cette sauvegarde.",
  TOO_EARLY: "la date de republication n'est pas atteinte",
  ALREADY_REPUBLISHED: "l'annonce est déjà republiée.",
  REPUBLICATION_IN_ERROR: 'la republication précédente est en erreur : vérifiez sur Vinted.',
  BROWSER_BUSY: 'une autre action est en cours dans le navigateur : réessayez quand elle sera finie.',
  PHOTOS_NOT_VERIFIED: 'les photos gardées ne sont pas intactes',
  FIELD_NOT_FILLABLE: 'champ impossible à remplir',
  BROWSER_UNAVAILABLE: 'le navigateur est indisponible.',
  VINTED_UNREACHABLE: 'Vinted est inaccessible.',
  SESSION_DISCONNECTED: "la session Vinted n'est pas connectée : connectez-vous depuis la page de session.",
  FORM_UNREADABLE: "le formulaire de mise en vente de Vinted n'est pas celui attendu.",
  NO_REPUBLICATION_TO_VERIFY: 'aucune republication en erreur ou réussie à vérifier.',
  LISTING_UNREADABLE: "la page de la nouvelle annonce sur Vinted n'est pas celle attendue.",
  CADENCE_LIMIT_REACHED: 'limite de cadence atteinte',
  NEW_LISTING_LINK_INVALID: "ce lien n'est pas celui d'une nouvelle annonce sur Vinted.",
  NEW_LISTING_MISMATCH: "la page de ce lien n'a pas le titre et le prix de la sauvegarde.",
  MANUAL_REPUBLICATION_IN_DRY_RUN: 'indisponible en dry-run.'
};
export const VALIDATION_REFUSED_MESSAGE = "Validation refusée, rien n'a été enregistré : ";
// Une levée du doute peut être enregistrée avant l'échec de la comparaison : l'aperçu relu fait foi.
export const VERIFICATION_FAILED_MESSAGE = "Vérification interrompue, l'état affiché est celui enregistré : ";
export const UNEXPECTED_FAILURE = 'échec inattendu (détail dans les logs).';
// Seules les annonces affichées à l'ouverture du dressing sont lues : rien ou plusieurs, le membre tranche.
export const STILL_IN_ERROR = 'Introuvable parmi les annonces récentes de votre dressing, ou plusieurs annonces'
  + " correspondent : vérifiez sur Vinted (supprimez les doublons s'il y en a), puis validez vous-même"
  + ' ci-dessous.';
export const FORM_FIELDS: Record<string, string> = {
  PHOTOS: 'Photos', TITLE: 'Titre', DESCRIPTION: 'Description', PRICE: 'Prix', CATEGORY: 'Catégorie',
  BRAND: 'Marque', CONDITION: 'État', SIZE: 'Taille', COLORS: 'Couleurs', MATERIALS: 'Matières',
  PACKAGE_SIZE: 'Format du colis', ISBN: 'ISBN', BOOK_TITLE: 'Titre du livre', AUTHOR: 'Auteur',
  MANUFACTURER: 'Fabricant', MODEL: 'Modèle', MEASUREMENTS: 'Mesures'
};
// Un échec inattendu peut survenir après le clic sur « Publier » (base tombée pendant la réponse) : ne rien affirmer.
export const REPUBLICATION_FAILED_MESSAGE = 'Échec de la republication : vérifiez sur Vinted avant de relancer'
  + ' (détail dans les logs).';
export const REPUBLICATION_DRY_RUN_PASSED = 'Dry-run réussi : contrôles réussis, formulaire de mise en vente entièrement'
  + " rempli. Rien n'a été publié.";
export const UNKNOWN_LISTING_MESSAGE = 'Annonce inconnue.';
