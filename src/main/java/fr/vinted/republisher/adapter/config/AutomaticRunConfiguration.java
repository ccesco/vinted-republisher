package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.adapter.out.time.SleepingRunWaiting;
import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.application.relaunch.RelaunchListing;
import fr.vinted.republisher.application.run.LaunchAutomaticRun;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.Executor;

@Configuration
class AutomaticRunConfiguration {

    // The run waits minutes, even hours: it goes on in the background, like scans and backups.
    @Bean
    LaunchAutomaticRun launchAutomaticRun(ShowListingInventory showListingInventory, RelaunchListing relaunchListing,
                                          Clock clock, @Qualifier("applicationTaskExecutor") Executor background) {
        return new LaunchAutomaticRun(showListingInventory, relaunchListing, new SleepingRunWaiting(clock), clock,
                new FailureLoggingExecutor(background));
    }
}
