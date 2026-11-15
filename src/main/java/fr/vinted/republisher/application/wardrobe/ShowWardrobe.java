package fr.vinted.republisher.application.wardrobe;

import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.LastScanProblem;
import fr.vinted.republisher.domain.wardrobe.WardrobeOverview;
import fr.vinted.republisher.domain.wardrobe.WardrobeScan;
import fr.vinted.republisher.domain.wardrobe.WardrobeScanRepository;

public class ShowWardrobe {

    private final VintedSession session;
    private final LastScanProblem lastProblem;
    private final WardrobeScanRepository scans;

    public ShowWardrobe(VintedSession session, LastScanProblem lastProblem, WardrobeScanRepository scans) {
        this.session = session;
        this.lastProblem = lastProblem;
        this.scans = scans;
    }

    public WardrobeOverview show() {
        boolean scanInProgress = session.overview().currentAction().filter(BrowserAction.SCANNING::equals).isPresent();
        return new WardrobeOverview(
                scanInProgress,
                scans.findLastFinished().flatMap(WardrobeScan::finishedAt),
                lastProblem.current());
    }
}
