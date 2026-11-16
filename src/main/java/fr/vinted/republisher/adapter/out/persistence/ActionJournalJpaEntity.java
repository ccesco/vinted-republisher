package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.ActionRecord;
import fr.vinted.republisher.domain.journal.JournalEntry;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Optional;

@Entity
@Table(name = "action_journal_entry")
class ActionJournalJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private JournaledAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    private ActionOutcome outcome;

    @Column(name = "listing_id")
    private Long listingId;

    @Column(name = "reason")
    private String reason;

    protected ActionJournalJpaEntity() {
    }

    ActionJournalJpaEntity(ActionRecord record) {
        occurredAt = record.occurredAt();
        action = record.action();
        outcome = record.outcome();
        listingId = record.listingId().map(VintedListingId::value).orElse(null);
        reason = record.reason().orElse(null);
    }

    JournalEntry toJournalEntry() {
        return new JournalEntry(id, new ActionRecord(occurredAt, action, outcome,
                Optional.ofNullable(listingId).map(VintedListingId::new), Optional.ofNullable(reason)));
    }
}
