package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.journal.ActionJournal;
import fr.vinted.republisher.domain.journal.ActionRecord;
import fr.vinted.republisher.domain.journal.JournalEntry;
import fr.vinted.republisher.domain.journal.JournalPage;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.OptionalLong;

@Repository
class JpaActionJournal implements ActionJournal {

    private final ActionJournalJpaRepository repository;

    JpaActionJournal(ActionJournalJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void write(ActionRecord record) {
        repository.save(new ActionJournalJpaEntity(record));
    }

    /** One entry more than a page tells whether older ones remain. */
    @Override
    public JournalPage page(OptionalLong before) {
        Limit oneMore = Limit.of(JournalPage.SIZE + 1);
        List<JournalEntry> entries = (before.isPresent()
                ? repository.findByIdLessThanOrderByIdDesc(before.getAsLong(), oneMore)
                : repository.findByOrderByIdDesc(oneMore))
                .stream().map(ActionJournalJpaEntity::toJournalEntry).toList();
        return new JournalPage(entries.stream().limit(JournalPage.SIZE).toList(), entries.size() > JournalPage.SIZE);
    }

    @Override
    public List<JournalEntry> lastOfEachListing() {
        return repository.findLastOfEachListingExcept(ActionRecord.WAITING_REASONS, ActionRecord.NO_NEW_LISTING_REASONS).stream()
                .map(ActionJournalJpaEntity::toJournalEntry).toList();
    }

    @Override
    public List<JournalEntry> of(VintedListingId listing) {
        return repository.findByListingIdOrderByIdDesc(listing.value()).stream()
                .map(ActionJournalJpaEntity::toJournalEntry).toList();
    }
}
