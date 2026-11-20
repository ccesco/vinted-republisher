package fr.vinted.republisher.adapter.out.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

interface ActionJournalJpaRepository extends JpaRepository<ActionJournalJpaEntity, Long> {

    List<ActionJournalJpaEntity> findByOrderByIdDesc(Limit limit);

    @Query("""
            SELECT entry.occurredAt FROM ActionJournalJpaEntity entry
            WHERE entry.action = fr.vinted.republisher.domain.journal.JournaledAction.DUPLICATE_DELETION
              AND entry.outcome IN (fr.vinted.republisher.domain.journal.ActionOutcome.SUCCEEDED,
                                    fr.vinted.republisher.domain.journal.ActionOutcome.IN_DOUBT)
              AND entry.occurredAt > :since""")
    List<Instant> findDuplicateDeletionsSince(Instant since);

    List<ActionJournalJpaEntity> findByIdLessThanOrderByIdDesc(long before, Limit limit);

    /** Reads every entry naming a listing: the journal of a single member stays small. */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT ON (listing_id) id, occurred_at, action, outcome, listing_id, reason
            FROM action_journal_entry
            WHERE listing_id IS NOT NULL
              AND NOT (outcome = 'REFUSED' AND COALESCE(reason, '') IN (:waitingReasons))
              AND action <> 'DUPLICATE_DELETION'
            ORDER BY listing_id, id DESC""")
    List<ActionJournalJpaEntity> findLastOfEachListingExcept(Collection<String> waitingReasons);

    List<ActionJournalJpaEntity> findByListingIdOrderByIdDesc(long listingId);
}
