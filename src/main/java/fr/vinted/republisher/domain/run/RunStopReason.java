package fr.vinted.republisher.domain.run;

import fr.vinted.republisher.domain.backup.ListingUnreadable;
import fr.vinted.republisher.domain.deletion.ListingPageUnreadable;
import fr.vinted.republisher.domain.republication.ListingFormUnreadable;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.SessionDisconnected;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.WardrobeUnreadable;

/** Why the automatic run stopped: a problem of the account, of the browser or of Vinted, or anything unexpected. */
public enum RunStopReason {
    SESSION_DISCONNECTED,
    BROWSER_UNAVAILABLE,
    VINTED_UNREACHABLE,
    VINTED_PAGE_UNREADABLE,
    RUN_FAILED;

    public static RunStopReason of(RuntimeException failure) {
        return switch (failure) {
            case SessionDisconnected _ -> SESSION_DISCONNECTED;
            case BrowserUnavailable _ -> BROWSER_UNAVAILABLE;
            case VintedUnreachable _ -> VINTED_UNREACHABLE;
            case ListingPageUnreadable _, ListingFormUnreadable _, ListingUnreadable _, WardrobeUnreadable _ ->
                    VINTED_PAGE_UNREADABLE;
            default -> RUN_FAILED;
        };
    }
}
