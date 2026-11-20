package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.application.cadence.CadenceCheck;
import fr.vinted.republisher.domain.cadence.InvalidWriteCadence;
import fr.vinted.republisher.domain.cadence.WriteCadence;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.duplicate.DuplicateDeletionAttempts;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/** Reads the write cadence once, at startup: a missing or doubtful value stops the tool. */
@Configuration
@EnableConfigurationProperties(CadenceConfiguration.CadenceProperties.class)
public class CadenceConfiguration {

    @Bean
    WriteCadence writeCadence(CadenceProperties cadence) {
        if (cadence.deletionInterval() == null || cadence.republicationInterval() == null
                || cadence.dailyDeletions() == null || cadence.dailyRepublications() == null) {
            throw new InvalidCadenceConfiguration("missing value");
        }
        try {
            return new WriteCadence(cadence.deletionInterval(), cadence.republicationInterval(),
                    cadence.dailyDeletions(), cadence.dailyRepublications());
        } catch (InvalidWriteCadence invalid) {
            throw new InvalidCadenceConfiguration(invalid.getMessage());
        }
    }

    @Bean
    CadenceCheck cadenceCheck(ListingDeletionRepository deletions, ListingRepublicationRepository republications,
                              DuplicateDeletionAttempts duplicateDeletions, WriteCadence cadence, Clock clock) {
        return new CadenceCheck(deletions, republications, duplicateDeletions, cadence, clock);
    }

    @ConfigurationProperties("cadence")
    record CadenceProperties(Duration deletionInterval, Duration republicationInterval, Integer dailyDeletions,
                             Integer dailyRepublications) {
    }

    static class InvalidCadenceConfiguration extends RuntimeException {

        InvalidCadenceConfiguration(String reason) {
            super("cadence : " + reason);
        }
    }
}
