package fr.vinted.republisher.adapter.out.browser;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileSettingsPageTest {

    private final ProfileSettingsPage profileSettings =
            new ProfileSettingsPage(URI.create("https://www.vinted.fr/settings/profile"));

    @Test
    void is_shown_at_its_url() {
        assertThat(profileSettings.isShownAt("https://www.vinted.fr/settings/profile")).isTrue();
    }

    @Test
    void is_shown_at_its_url_with_a_trailing_slash() {
        assertThat(profileSettings.isShownAt("https://www.vinted.fr/settings/profile/")).isTrue();
    }

    @Test
    void is_not_shown_at_the_login_page() {
        assertThat(profileSettings.isShownAt("https://www.vinted.fr/member/signup/select_type")).isFalse();
    }

    @Test
    void is_not_shown_at_a_page_without_path() {
        assertThat(profileSettings.isShownAt("about:blank")).isFalse();
    }

    @Test
    void is_not_shown_at_a_url_java_cannot_parse() {
        assertThat(profileSettings.isShownAt("https://www.vinted.fr/settings/profile|ref")).isFalse();
    }
}
