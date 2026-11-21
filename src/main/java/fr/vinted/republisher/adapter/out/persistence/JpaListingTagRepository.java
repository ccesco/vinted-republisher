package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.tag.ListingTag;
import fr.vinted.republisher.domain.tag.ListingTagRepository;
import fr.vinted.republisher.domain.tag.Tag;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Tags are borne by articles (US-53): every listing of an article bears them. */
@Repository
class JpaListingTagRepository implements ListingTagRepository {

    private final NamedParameterJdbcTemplate database;

    JpaListingTagRepository(JdbcTemplate database) {
        this.database = new NamedParameterJdbcTemplate(database);
    }

    @Override
    public List<ListingTag> findAll() {
        return database.query("SELECT listing.vinted_id, tag.tag FROM article_tag tag "
                        + "JOIN listing ON listing.article_id = tag.article_id ORDER BY listing.vinted_id, tag.tag",
                (row, _) -> new ListingTag(new VintedListingId(row.getLong(1)), new Tag(row.getString(2))));
    }

    /**
     * One transaction: an unknown listing (no article, refused by the database) rolls back every row added before it.
     * A tag already borne is left as it is, even when two edits add it at the same time.
     */
    @Override
    @Transactional
    public void add(Tag tag, List<VintedListingId> listings) {
        listings.forEach(listing -> database.update("INSERT INTO article_tag (article_id, tag) VALUES "
                        + "((SELECT article_id FROM listing WHERE vinted_id = :listing), :tag) ON CONFLICT DO NOTHING",
                Map.of("listing", listing.value(), "tag", tag.name())));
    }

    @Override
    @Transactional
    public void remove(Tag tag, List<VintedListingId> listings) {
        if (listings.isEmpty()) {
            return;
        }
        database.update("DELETE FROM article_tag WHERE tag = :tag AND article_id IN "
                        + "(SELECT article_id FROM listing WHERE vinted_id IN (:listings))",
                Map.of("tag", tag.name(), "listings", listings.stream().map(VintedListingId::value).toList()));
    }
}
