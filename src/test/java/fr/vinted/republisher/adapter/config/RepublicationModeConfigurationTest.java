package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.domain.republication.RepublicationMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Only a deliberate REPUBLICATION_DRY_RUN=false allows real republications; anything doubtful stops the application. */
class RepublicationModeConfigurationTest {

    // The same placeholder as application.yml.
    private final ApplicationContextRunner application = new ApplicationContextRunner()
            .withPropertyValues("republication.dry-run=${REPUBLICATION_DRY_RUN:true}")
            .withUserConfiguration(RepublicationModeConfiguration.class);

    // AT-12-12, absente
    @Test
    void dryRunSetting() {
        application.run(context ->
                assertThat(context).getBean(RepublicationMode.class).isEqualTo(RepublicationMode.DRY_RUN));
    }

    @Test
    void dry_run_with_true() {
        application.withSystemProperties("REPUBLICATION_DRY_RUN=true").run(context ->
                assertThat(context).getBean(RepublicationMode.class).isEqualTo(RepublicationMode.DRY_RUN));
    }

    @Test
    void real_republications_with_false() {
        application.withSystemProperties("REPUBLICATION_DRY_RUN=false").run(context ->
                assertThat(context).getBean(RepublicationMode.class).isEqualTo(RepublicationMode.REAL));
    }

    @Test
    void does_not_start_with_another_value() {
        application.withSystemProperties("REPUBLICATION_DRY_RUN=oui")
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("REPUBLICATION_DRY_RUN").hasMessageContaining("true ou false"));
    }

    // Values the context runner cannot pass as they are (it trims them, and drops an empty one).
    @ParameterizedTest
    @ValueSource(strings = {"oui", "False", "TRUE", "0", "false ", ""})
    void refuses_any_value_but_true_or_false_exactly(String value) {
        assertThatThrownBy(() -> new RepublicationModeConfiguration().republicationMode(value))
                .hasMessageContaining("« " + value + " »").hasMessageContaining("true ou false");
    }
}
