package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.adapter.out.browser.ChromeTabs;
import fr.vinted.republisher.adapter.out.browser.PlaywrightWardrobeReader;
import fr.vinted.republisher.adapter.out.browser.WardrobeProperties;
import fr.vinted.republisher.application.wardrobe.ScanWardrobe;
import fr.vinted.republisher.application.wardrobe.ShowWardrobe;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.ScanPace;
import fr.vinted.republisher.domain.wardrobe.WardrobeReader;
import fr.vinted.republisher.domain.wardrobe.LastScanProblem;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.Random;
import java.util.concurrent.Executor;

/** Wires the wardrobe use cases: the application layer stays free of Spring. */
@Configuration
@EnableConfigurationProperties(WardrobeProperties.class)
public class WardrobeConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    LastScanProblem lastProblem() {
        return new LastScanProblem();
    }

    @Bean
    WardrobeReader wardrobeReader(WardrobeProperties wardrobe, ChromeTabs tabs, Clock clock) {
        return new PlaywrightWardrobeReader(wardrobe.wardrobeUrl(), wardrobe.scan().stallTimeout(), tabs, clock);
    }

    @Bean
    ShowWardrobe showWardrobe(VintedSession session, LastScanProblem lastProblem, ListingRepository listings) {
        return new ShowWardrobe(session, lastProblem, listings);
    }

    @Bean
    ScanWardrobe scanWardrobe(VintedSession session, LastScanProblem lastProblem, WardrobeReader reader,
                              ListingRepository listings, WardrobeProperties wardrobe, Clock clock,
                              @Qualifier("applicationTaskExecutor") Executor background) {
        ScanPace pace = new ScanPace(wardrobe.scan().minPause(), wardrobe.scan().maxPause(), new Random());
        return new ScanWardrobe(session, lastProblem, reader, listings, pace, clock,
                new FailureLoggingExecutor(background));
    }
}
