package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.adapter.config.ParallelismConfiguration.ParallelListingActions;
import fr.vinted.republisher.adapter.out.time.SleepingRunWaiting;
import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.application.relaunch.RelaunchListing;
import fr.vinted.republisher.application.run.LaunchAutomaticRun;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
class AutomaticRunConfiguration {

    // The run waits minutes, even hours: it goes on in the background, like scans and backups.
    // The relaunches of a round (US-15) get threads of their own: the run's thread waits for them.
    @Bean
    LaunchAutomaticRun launchAutomaticRun(ShowListingInventory showListingInventory, RelaunchListing relaunchListing,
                                          Clock clock, @Qualifier("applicationTaskExecutor") Executor background,
                                          ParallelListingActions parallelism, RelaunchThreads relaunchThreads) {
        return new LaunchAutomaticRun(showListingInventory, relaunchListing,
                new SleepingRunWaiting(clock, Duration.ofSeconds(1)), clock, new FailureLoggingExecutor(background),
                parallelism.count(), relaunchThreads.threads);
    }

    @Bean
    RelaunchThreads relaunchThreads(ParallelListingActions parallelism) {
        return new RelaunchThreads(parallelism.count());
    }

    /**
     * One thread per place. Not an Executor bean: one would replace Spring Boot's applicationTaskExecutor. Closed by
     * shutdown, not shutdownNow: a relaunch in progress is never cut, even when the application stops.
     */
    static final class RelaunchThreads implements AutoCloseable {

        private final ExecutorService threads;

        private RelaunchThreads(int count) {
            threads = Executors.newFixedThreadPool(count, Thread.ofPlatform().name("relaunch-", 1).factory());
        }

        @Override
        public void close() {
            threads.shutdown();
        }
    }
}
