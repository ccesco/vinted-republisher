// Journal des actions (US-23) : libellés des codes du contrat OpenAPI et ligne de tableau, partagés par journal.html
// et la fiche listing.html. Les titres viennent de Vinted : textContent seulement, jamais d'innerHTML.
const JOURNAL_ACTIONS = {
    SCAN: 'Scan du dressing', WARDROBE_BACKUP: 'Sauvegarde du dressing', BACKUP_VALIDATION: 'Validation',
    DELETION: 'Suppression', DELETION_RECHECK: 'Revérification', REPUBLICATION: 'Republication'
};
const JOURNAL_OUTCOMES = {
    STARTED: 'lancée', SUCCEEDED: 'réussie', DRY_RUN: 'dry-run réussi', REFUSED: 'refusée',
    IN_DOUBT: 'dans le doute', NOT_DONE: 'non faite', FAILED: 'en échec'
};
// Refus, doutes et échecs : mis en évidence.
const JOURNAL_HIGHLIGHTED = ['REFUSED', 'IN_DOUBT', 'FAILED'];
const JOURNAL_REASONS = {
    BACKUP_REPLACED: 'sauvegarde remplacée', VERIFICATION_FAILED: 'vérification échouée',
    NOT_VALIDATED: 'sauvegarde non validée', ALREADY_DELETED: 'déjà supprimée',
    DELETION_UNCONFIRMED: 'suppression précédente non confirmée', NO_UNCONFIRMED_DELETION: 'aucune suppression à revérifier',
    LISTING_CHANGED: 'annonce différente sur Vinted', LISTING_PAGE_UNREADABLE: "page de l'annonce illisible",
    NOT_DELETED: 'pas de suppression confirmée', TOO_EARLY: 'trop tôt', ALREADY_REPUBLISHED: 'déjà republiée',
    REPUBLICATION_IN_ERROR: 'republication précédente en erreur', FIELD_NOT_FILLABLE: 'champ impossible à remplir',
    FORM_UNREADABLE: 'formulaire de mise en vente illisible', BROWSER_BUSY: 'autre action en cours',
    BROWSER_UNAVAILABLE: 'navigateur indisponible', VINTED_UNREACHABLE: 'Vinted inaccessible',
    SESSION_DISCONNECTED: 'session non connectée', WARDROBE_UNREADABLE: 'dressing illisible',
    LISTING_UNREADABLE: 'annonce illisible', SCAN_FAILED: 'échec du scan', BACKUP_FAILED: 'échec de la sauvegarde',
    DELETION_FAILED: 'échec de la suppression', REPUBLICATION_FAILED: 'échec de la republication'
};

function journalCell(...content) {
    const td = document.createElement('td');
    td.append(...content);
    return td;
}

// withListing : colonne « Annonce » (page du journal), absente dans la fiche.
function journalRow(entry, withListing) {
    const outcome = JOURNAL_OUTCOMES[entry.outcome] ?? entry.outcome;
    let shownOutcome = outcome;
    if (JOURNAL_HIGHLIGHTED.includes(entry.outcome)) {
        shownOutcome = document.createElement('strong');
        shownOutcome.textContent = outcome;
    }
    const row = document.createElement('tr');
    row.append(
        journalCell(new Date(entry.occurredAt).toLocaleString('fr-FR')),
        journalCell(JOURNAL_ACTIONS[entry.action] ?? entry.action),
        journalCell(shownOutcome),
        journalCell(entry.reason ? (JOURNAL_REASONS[entry.reason] ?? entry.reason) : ''));
    if (withListing) {
        if (entry.listingId) {
            const link = document.createElement('a');
            link.href = 'listing.html?id=' + encodeURIComponent(entry.listingId);
            link.textContent = entry.listingTitle ?? String(entry.listingId);
            row.append(journalCell(link));
        } else {
            row.append(journalCell(''));
        }
    }
    return row;
}
