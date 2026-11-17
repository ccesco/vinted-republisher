package fr.vinted.republisher.application.wardrobe;

import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.LastStatisticsRefreshProblem;
import fr.vinted.republisher.domain.wardrobe.ListingStatisticsRepository;
import fr.vinted.republisher.domain.wardrobe.StatisticsRefreshOverview;

/** Reads where the refresh of the statistics stands, never Vinted. */
public class ShowStatisticsRefresh {

    private final VintedSession session;
    private final LastStatisticsRefreshProblem lastProblem;
    private final ListingStatisticsRepository statistics;

    public ShowStatisticsRefresh(VintedSession session, LastStatisticsRefreshProblem lastProblem,
                                 ListingStatisticsRepository statistics) {
        this.session = session;
        this.lastProblem = lastProblem;
        this.statistics = statistics;
    }

    public StatisticsRefreshOverview show() {
        boolean inProgress = session.overview().currentAction()
                .filter(BrowserAction.REFRESHING_STATISTICS::equals).isPresent();
        return new StatisticsRefreshOverview(inProgress, statistics.findLastReadAt(), lastProblem.current());
    }
}
