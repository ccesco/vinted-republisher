package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.application.cadence.CadenceCheck;
import fr.vinted.republisher.application.duplicate.DeleteDuplicate;
import fr.vinted.republisher.application.duplicate.ShowDuplicates;
import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.backup.PhotoStorage;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.deletion.ListingDeleter;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.duplicate.ListingEraser;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
import fr.vinted.republisher.domain.sale.ListingSaleRepository;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the duplicates (US-47): the application layer stays free of Spring. */
@Configuration
public class DuplicateConfiguration {

    @Bean
    ShowDuplicates showDuplicates(ListingRepository listings, ListingBackupRepository backups,
                                  ListingDeletionRepository deletions, ListingRepublicationRepository republications,
                                  ListingSaleRepository sales, DeletionMode mode) {
        return new ShowDuplicates(listings, backups, deletions, republications, sales, mode);
    }

    // Like a deletion, it opens a few pages: it runs within the request.
    @Bean
    DeleteDuplicate deleteDuplicate(VintedSession session, ShowDuplicates showDuplicates, ListingRepository listings,
                                    ListingDeleter deleter, ListingEraser eraser, PhotoStorage photos,
                                    DeletionMode mode, CadenceCheck cadence, JournalWriter journal) {
        return new DeleteDuplicate(session, showDuplicates, listings, deleter, eraser, photos, mode, cadence, journal);
    }
}
