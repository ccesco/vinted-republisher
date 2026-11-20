package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.domain.cadence.WriteOrigin;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.time.Instant;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;

import static org.assertj.core.api.Assertions.assertThat;

/** The minimum delay before a republication comes from application.yml; a doubtful value stops the application. */
class RepublicationConfigurationTest {

    private final ApplicationContextRunner application = new ApplicationContextRunner()
            .withUserConfiguration(RepublicationConfiguration.class);

    @Test
    void defaultMinimumDelayIsFiveMinutes() {
        application.withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> assertThat(context).getBean(RepublicationDelay.class)
                        .isEqualTo(new RepublicationDelay(Duration.ofMinutes(5))));
    }

    @Test
    void minimumDelayIsConfigurable() {
        ListingDeletion provenAt1432 = ListingDeletion.attempted(aBackup(1, Instant.parse("2026-11-10T10:00:00Z")),
                Instant.parse("2026-11-11T14:31:58Z"),
                        WriteOrigin.RUN).confirmed(Instant.parse("2026-11-11T14:32:00Z"));

        application.withPropertyValues("republication.minimum-delay=PT10M")
                .run(context -> assertThat(context.getBean(RepublicationDelay.class).republishableAt(provenAt1432))
                        .contains(Instant.parse("2026-11-11T14:42:00Z")));
    }

    @Test
    void missingMinimumDelayPreventsStartup() {
        application.run(context -> assertThat(context).hasFailed().getFailure()
                .hasStackTraceContaining("republication.minimum-delay"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "-PT5M", "cinq"})
    void invalidMinimumDelayPreventsStartup(String delay) {
        application.withPropertyValues("republication.minimum-delay=" + delay)
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasStackTraceContaining("republication.minimum-delay"));
    }
}
