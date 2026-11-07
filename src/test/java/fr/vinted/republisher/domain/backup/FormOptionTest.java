package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.InvalidListing;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class FormOptionTest {

    @Test
    void keeps_its_identifier_and_displayed_label() {
        FormOption option = new FormOption(60, " Kiabi ");

        assertThat(option).isEqualTo(new FormOption(60, "Kiabi"));
    }

    @Test
    void may_have_an_empty_label_when_the_form_does_not_display_it() {
        assertThat(new FormOption(588, "").label()).isEmpty();
    }

    @Test
    void rejects_an_identifier_that_is_not_positive() {
        Throwable attempt = catchThrowable(() -> new FormOption(0, "Kiabi"));

        assertThat(attempt).isInstanceOf(InvalidListing.class).hasMessage("Invalid listing: option identifier 0");
    }

    @Test
    void several_options_need_at_least_one_identifier() {
        Throwable attempt = catchThrowable(() -> new FormOptions(List.of(), "Blanc"));

        assertThat(attempt).isInstanceOf(InvalidListing.class).hasMessage("Invalid listing: no option identifier");
    }

    @Test
    void several_options_reject_an_identifier_that_is_not_positive() {
        Throwable attempt = catchThrowable(() -> new FormOptions(List.of(12L, -1L), "Blanc"));

        assertThat(attempt).isInstanceOf(InvalidListing.class).hasMessage("Invalid listing: option identifier -1");
    }

    @Test
    void several_options_are_immutable() {
        List<Long> ids = new ArrayList<>(List.of(12L));
        FormOptions colors = new FormOptions(ids, "Blanc");

        ids.add(13L);

        assertThat(colors.ids()).containsExactly(12L);
    }
}
