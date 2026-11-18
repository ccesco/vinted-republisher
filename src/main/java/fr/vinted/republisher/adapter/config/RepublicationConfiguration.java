package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.domain.republication.InvalidRepublicationDelay;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** Reads the minimum delay before a republication once, at startup: a missing or doubtful value stops the tool. */
@Configuration
@EnableConfigurationProperties(RepublicationConfiguration.RepublicationProperties.class)
public class RepublicationConfiguration {

    @Bean
    RepublicationDelay republicationDelay(RepublicationProperties republication) {
        if (republication.minimumDelay() == null) {
            throw new InvalidRepublicationConfiguration("missing");
        }
        try {
            return new RepublicationDelay(republication.minimumDelay());
        } catch (InvalidRepublicationDelay invalid) {
            throw new InvalidRepublicationConfiguration(invalid.getMessage());
        }
    }

    @ConfigurationProperties("republication")
    record RepublicationProperties(Duration minimumDelay, Duration proofDelay) {
    }

    static class InvalidRepublicationConfiguration extends RuntimeException {

        InvalidRepublicationConfiguration(String reason) {
            super("republication.minimum-delay : " + reason);
        }
    }
}
