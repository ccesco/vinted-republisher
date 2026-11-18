package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.adapter.config.ParallelismConfiguration.ParallelListingActions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** US-15: the parallelism comes from application.yml, 1 by default; a doubtful value stops the application. */
class ParallelismConfigurationTest {

    private final ApplicationContextRunner application = new ApplicationContextRunner()
            .withUserConfiguration(ParallelismConfiguration.class);

    @Test
    void defaultsToOneListingActionAtATime() {
        application.withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> assertThat(context).getBean(ParallelListingActions.class)
                        .isEqualTo(new ParallelListingActions(1)));
    }

    @Test
    void isConfigurableUpToFour() {
        application.withPropertyValues("parallelism.listing-actions=4")
                .run(context -> assertThat(context).getBean(ParallelListingActions.class)
                        .isEqualTo(new ParallelListingActions(4)));
    }

    @Test
    void missingValuePreventsStartup() {
        application.run(context -> assertThat(context).hasFailed().getFailure()
                .hasStackTraceContaining("parallelism"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "5", "deux"})
    void invalidValuePreventsStartup(String invalid) {
        application.withPropertyValues("parallelism.listing-actions=" + invalid)
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasStackTraceContaining("parallelism"));
    }
}
