package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.domain.deletion.DeletionMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reads the deletion mode once, at startup. Without DELETION_DRY_RUN the tool stays in dry-run; a doubtful value
 * stops it, rather than being read in one direction or the other.
 */
@Configuration
public class DeletionModeConfiguration {

    @Bean
    DeletionMode deletionMode(@Value("${deletion.dry-run}") String dryRun) {
        return switch (dryRun) {
            case "true" -> DeletionMode.DRY_RUN;
            case "false" -> DeletionMode.REAL;
            default -> throw new InvalidDeletionMode(dryRun);
        };
    }

    static class InvalidDeletionMode extends RuntimeException {

        InvalidDeletionMode(String value) {
            super("DELETION_DRY_RUN vaut « " + value + " » : valeurs acceptées true ou false (true par défaut)");
        }
    }
}
