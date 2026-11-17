package fr.vinted.republisher.domain.run;

import fr.vinted.republisher.domain.backup.ListingUnreadable;
import fr.vinted.republisher.domain.deletion.ListingPageUnreadable;
import fr.vinted.republisher.domain.republication.ListingFormUnreadable;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.SessionDisconnected;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.WardrobeUnreadable;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RunStopReasonTest {

    @Test
    void names_the_problem_that_stopped_the_run() {
        assertThat(RunStopReason.of(new SessionDisconnected("login"))).isEqualTo(RunStopReason.SESSION_DISCONNECTED);
        assertThat(RunStopReason.of(new BrowserUnavailable("no Chrome"))).isEqualTo(RunStopReason.BROWSER_UNAVAILABLE);
        assertThat(RunStopReason.of(new VintedUnreachable("HTTP 403"))).isEqualTo(RunStopReason.VINTED_UNREACHABLE);
        Stream.of(new ListingPageUnreadable("a"), new ListingFormUnreadable("b"), new ListingUnreadable("c"),
                        new WardrobeUnreadable("d"))
                .forEach(failure -> assertThat(RunStopReason.of(failure)).isEqualTo(RunStopReason.VINTED_PAGE_UNREADABLE));
        assertThat(RunStopReason.of(new IllegalStateException("unexpected"))).isEqualTo(RunStopReason.RUN_FAILED);
    }
}
