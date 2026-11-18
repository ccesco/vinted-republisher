package fr.vinted.republisher.adapter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reads once, at startup, how many listing actions may run at the same time (US-15), each in its own tab of Chrome:
 * 1 by default, at most 4. A missing or doubtful value stops the tool.
 */
@Configuration
@EnableConfigurationProperties(ParallelismConfiguration.ParallelismProperties.class)
public class ParallelismConfiguration {

    /** Beyond, the anti-bot risk grows while the write cadence leaves extra tabs idle. */
    static final int MAXIMUM_LISTING_ACTIONS = 4;

    @Bean
    ParallelListingActions parallelListingActions(ParallelismProperties parallelism) {
        Integer listingActions = parallelism.listingActions();
        if (listingActions == null) {
            throw new InvalidParallelismConfiguration("missing value");
        }
        if (listingActions < 1 || listingActions > MAXIMUM_LISTING_ACTIONS) {
            throw new InvalidParallelismConfiguration(
                    "listing-actions must be from 1 to " + MAXIMUM_LISTING_ACTIONS + ": " + listingActions);
        }
        return new ParallelListingActions(listingActions);
    }

    @ConfigurationProperties("parallelism")
    record ParallelismProperties(Integer listingActions) {
    }

    /** How many listing actions may run at the same time. */
    record ParallelListingActions(int count) {
    }

    static class InvalidParallelismConfiguration extends RuntimeException {

        InvalidParallelismConfiguration(String reason) {
            super("parallelism : " + reason);
        }
    }
}
