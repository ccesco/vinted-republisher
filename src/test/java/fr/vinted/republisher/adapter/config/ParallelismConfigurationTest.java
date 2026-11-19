package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.domain.run.ParallelismMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** US-46: the parallelism mode comes from application.yml, sequential by default; a doubtful value stops the tool. */
class ParallelismConfigurationTest {

    private final ApplicationContextRunner application = new ApplicationContextRunner()
            .withUserConfiguration(ParallelismConfiguration.class);

    @Test
    void defaults_to_sequential() {
        // AT-46-01
        application.withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> assertThat(context).getBean(ParallelismMode.class)
                        .isEqualTo(ParallelismMode.SEQUENTIAL));
    }

    @Test
    void workers_are_configurable() {
        application.withPropertyValues("parallelism.mode=WORKERS")
                .run(context -> assertThat(context).getBean(ParallelismMode.class)
                        .isEqualTo(ParallelismMode.WORKERS));
    }

    @Test
    void refuses_a_missing_mode() {
        // AT-46-14
        application.run(context -> assertThat(context).hasFailed().getFailure()
                .hasStackTraceContaining("parallelism.mode")
                .hasStackTraceContaining("SEQUENTIAL, WORKERS"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "TROIS"})
    void refuses_an_empty_or_unknown_mode(String invalid) {
        // AT-46-14
        application.withPropertyValues("parallelism.mode=" + invalid)
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasStackTraceContaining("parallelism.mode"));
    }
}
