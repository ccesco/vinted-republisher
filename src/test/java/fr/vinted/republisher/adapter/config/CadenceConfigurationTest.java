package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.domain.cadence.WriteCadence;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** The write cadence comes from application.yml; a doubtful value stops the application. */
class CadenceConfigurationTest {

    private static final String[] VALID = {"cadence.minimum-interval=PT3M", "cadence.daily-deletions=10",
            "cadence.daily-republications=12"};

    private final ApplicationContextRunner application = new ApplicationContextRunner()
            .withUserConfiguration(CadenceConfiguration.class)
            .withBean(ListingDeletionRepository.class, InMemoryListingDeletionRepository::new)
            .withBean(ListingRepublicationRepository.class, InMemoryListingRepublicationRepository::new)
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    void defaultCadenceIsTwoMinutesAndTwentyOfEachKindPerDay() {
        application.withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> assertThat(context).getBean(WriteCadence.class)
                        .isEqualTo(new WriteCadence(Duration.ofMinutes(2), 20, 20)));
    }

    @Test
    void cadenceIsConfigurable() {
        application.withPropertyValues(VALID)
                .run(context -> assertThat(context).getBean(WriteCadence.class)
                        .isEqualTo(new WriteCadence(Duration.ofMinutes(3), 10, 12)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"cadence.minimum-interval", "cadence.daily-deletions", "cadence.daily-republications"})
    void missingValuePreventsStartup(String missing) {
        String[] others = Arrays.stream(VALID).filter(value -> !value.startsWith(missing + "="))
                .toArray(String[]::new);
        application.withPropertyValues(others)
                .run(context -> assertThat(context).hasFailed().getFailure().hasStackTraceContaining("cadence"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"cadence.minimum-interval=PT0S", "cadence.minimum-interval=-PT2M",
            "cadence.minimum-interval=deux", "cadence.daily-deletions=0", "cadence.daily-republications=-1",
            "cadence.daily-deletions=vingt"})
    void invalidValuePreventsStartup(String invalid) {
        application.withPropertyValues(VALID).withPropertyValues(invalid)
                .run(context -> assertThat(context).hasFailed().getFailure().hasStackTraceContaining("cadence"));
    }
}
