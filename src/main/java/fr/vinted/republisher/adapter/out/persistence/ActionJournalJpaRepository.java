package fr.vinted.republisher.adapter.out.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

interface ActionJournalJpaRepository extends JpaRepository<ActionJournalJpaEntity, Long> {

    List<ActionJournalJpaEntity> findByOrderByIdDesc(Limit limit);

    List<ActionJournalJpaEntity> findByIdLessThanOrderByIdDesc(long before, Limit limit);

    /** Reads every entry naming a listing: the journal of a single member stays small. */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT ON (listing_id) id, occurred_at, action, outcome, listing_id, reason
            FROM action_journal_entry
            WHERE listing_id IS NOT NULL
              AND NOT (outcome = 'REFUSED' AND COALESCE(reason, '') IN (:waitingReasons))
              AND NOT (outcome = 'NOT_DONE' AND COALESCE(reason, '') IN (:noNewListingReasons))
              AND action <> 'DUPLICATE_DELETION'
            ORDER BY listing_id, id DESC""")
    List<ActionJournalJpaEntity> findLastOfEachListingExcept(Collection<String> waitingReasons,
                                                             Collection<String> noNewListingReasons);

    List<ActionJournalJpaEntity> findByListingIdOrderByIdDesc(long listingId);
}
