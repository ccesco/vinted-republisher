package fr.vinted.republisher.domain.inventory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepublicationTallyTest {

    @Test
    void a_tally_never_counts_below_zero() {
        assertThatThrownBy(() -> new RepublicationTally(-1, 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A republication tally counts from zero: -1, 0");
        assertThatThrownBy(() -> new RepublicationTally(0, -1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A republication tally counts from zero: 0, -1");
    }
}
