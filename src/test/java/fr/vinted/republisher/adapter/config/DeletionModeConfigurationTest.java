package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.domain.deletion.DeletionMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Only a deliberate DELETION_DRY_RUN=false allows real deletions; anything doubtful stops the application. */
class DeletionModeConfigurationTest {

    // The same placeholder as application.yml.
    private final ApplicationContextRunner application = new ApplicationContextRunner()
            .withPropertyValues("deletion.dry-run=${DELETION_DRY_RUN:true}")
            .withUserConfiguration(DeletionModeConfiguration.class);

    @Test
    void dry_run_without_the_variable() {
        application.run(context -> assertThat(context).getBean(DeletionMode.class).isEqualTo(DeletionMode.DRY_RUN));
    }

    @Test
    void dry_run_with_true() {
        application.withSystemProperties("DELETION_DRY_RUN=true")
                .run(context -> assertThat(context).getBean(DeletionMode.class).isEqualTo(DeletionMode.DRY_RUN));
    }

    @Test
    void real_deletions_with_false() {
        application.withSystemProperties("DELETION_DRY_RUN=false")
                .run(context -> assertThat(context).getBean(DeletionMode.class).isEqualTo(DeletionMode.REAL));
    }

    @Test
    void does_not_start_with_another_value() {
        application.withSystemProperties("DELETION_DRY_RUN=non")
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("DELETION_DRY_RUN").hasMessageContaining("true ou false"));
    }

    // Values the context runner cannot pass as they are (it trims them, and drops an empty one).
    @ParameterizedTest
    @ValueSource(strings = {"False", "TRUE", "0", "false ", " true", ""})
    void refuses_any_value_but_true_or_false_exactly(String value) {
        assertThatThrownBy(() -> new DeletionModeConfiguration().deletionMode(value))
                .hasMessageContaining("« " + value + " »").hasMessageContaining("true ou false");
    }
}
