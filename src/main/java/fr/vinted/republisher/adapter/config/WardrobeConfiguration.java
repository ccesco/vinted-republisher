package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.adapter.out.browser.ChromeTabs;
import fr.vinted.republisher.adapter.out.browser.PlaywrightWardrobeReader;
import fr.vinted.republisher.adapter.out.browser.WardrobeProperties;
import fr.vinted.republisher.application.wardrobe.ScanWardrobe;
import fr.vinted.republisher.application.wardrobe.ShowWardrobe;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.LastScanProblem;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.PauseRange;
import fr.vinted.republisher.domain.wardrobe.ScanPace;
import fr.vinted.republisher.domain.wardrobe.WardrobeReader;
import fr.vinted.republisher.domain.wardrobe.WardrobeScanRepository;
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
    ShowWardrobe showWardrobe(VintedSession session, LastScanProblem lastProblem, ListingRepository listings,
                              WardrobeScanRepository scans) {
        return new ShowWardrobe(session, lastProblem, listings, scans);
    }

    @Bean
    ScanWardrobe scanWardrobe(VintedSession session, LastScanProblem lastProblem, WardrobeScanRepository scans,
                              WardrobeReader reader, ListingRepository listings, WardrobeProperties wardrobe,
                              Clock clock, @Qualifier("applicationTaskExecutor") Executor background) {
        return new ScanWardrobe(session, lastProblem, scans, reader, listings, paceOf(wardrobe.scan()), clock,
                new FailureLoggingExecutor(background));
    }

    private static ScanPace paceOf(WardrobeProperties.Scan scan) {
        return new ScanPace(new PauseRange(scan.minScrollPause(), scan.maxScrollPause()),
                new PauseRange(scan.minListingPause(), scan.maxListingPause()), new Random());
    }
}
