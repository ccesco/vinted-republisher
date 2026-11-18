package fr.vinted.republisher.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface ListingTagJpaRepository extends JpaRepository<ListingTagJpaEntity, ListingTagJpaEntity.Key> {

    /** A tag already borne is left as it is, even when two edits add it at the same time. */
    @Modifying
    @Query(value = "INSERT INTO listing_tag (listing_id, tag) VALUES (:listingId, :tag) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    void addIfAbsent(@Param("listingId") long listingId, @Param("tag") String tag);

    @Modifying
    @Query("delete from ListingTagJpaEntity t where t.key.tag = :tag and t.key.listingId in :listingIds")
    void removeFrom(@Param("tag") String tag, @Param("listingIds") List<Long> listingIds);
}
