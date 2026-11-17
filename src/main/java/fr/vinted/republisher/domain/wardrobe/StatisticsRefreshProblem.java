package fr.vinted.republisher.domain.wardrobe;

/** Why the last refresh of the statistics did not succeed. */
public enum StatisticsRefreshProblem {
    BROWSER_UNAVAILABLE,
    VINTED_UNREACHABLE,
    WARDROBE_UNREADABLE,
    NO_STATISTICS_SHOWN,
    REFRESH_FAILED
}
