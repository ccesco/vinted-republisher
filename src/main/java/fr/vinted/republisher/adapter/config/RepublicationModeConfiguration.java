package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.domain.republication.RepublicationMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reads the republication mode once, at startup, independently of the deletion mode. Without REPUBLICATION_DRY_RUN
 * the tool stays in dry-run; a doubtful value stops it, rather than being read in one direction or the other.
 */
@Configuration
public class RepublicationModeConfiguration {

    @Bean
    RepublicationMode republicationMode(@Value("${republication.dry-run}") String dryRun) {
        return switch (dryRun) {
            case "true" -> RepublicationMode.DRY_RUN;
            case "false" -> RepublicationMode.REAL;
            default -> throw new InvalidRepublicationMode(dryRun);
        };
    }

    static class InvalidRepublicationMode extends RuntimeException {

        InvalidRepublicationMode(String value) {
            super("REPUBLICATION_DRY_RUN vaut « " + value + " » : valeurs acceptées true ou false (true par défaut)");
        }
    }
}
