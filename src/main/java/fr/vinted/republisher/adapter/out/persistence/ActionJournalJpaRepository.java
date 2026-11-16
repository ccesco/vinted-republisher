package fr.vinted.republisher.adapter.out.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface ActionJournalJpaRepository extends JpaRepository<ActionJournalJpaEntity, Long> {

    List<ActionJournalJpaEntity> findByOrderByIdDesc(Limit limit);

    List<ActionJournalJpaEntity> findByIdLessThanOrderByIdDesc(long before, Limit limit);

    List<ActionJournalJpaEntity> findByListingIdOrderByIdDesc(long listingId);
}
