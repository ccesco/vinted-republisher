package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.adapter.out.browser.ChromeLivePreviews;
import fr.vinted.republisher.application.preview.ShowLivePreviews;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** One registry for every piloted Chrome: each shows its working tab there. */
@Configuration
class LivePreviewConfiguration {

    @Bean
    ChromeLivePreviews chromeLivePreviews(Clock clock) {
        return new ChromeLivePreviews(clock);
    }

    @Bean
    ShowLivePreviews showLivePreviews(ChromeLivePreviews livePreviews) {
        return new ShowLivePreviews(livePreviews);
    }
}
