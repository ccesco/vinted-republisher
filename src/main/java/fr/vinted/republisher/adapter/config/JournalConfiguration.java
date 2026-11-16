package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.application.journal.ShowActionJournal;
import fr.vinted.republisher.domain.journal.ActionJournal;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class JournalConfiguration {

    @Bean
    JournalWriter journalWriter(ActionJournal journal, Clock clock) {
        return new JournalWriter(journal, clock);
    }

    @Bean
    ShowActionJournal showActionJournal(ActionJournal journal, ListingRepository listings) {
        return new ShowActionJournal(journal, listings);
    }
}
