package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.ApplicationWithDatabaseTest;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.cadence.WriteOrigin;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
import fr.vinted.republisher.domain.sale.ListingAlreadySold;
import fr.vinted.republisher.domain.sale.ListingSale;
import fr.vinted.republisher.domain.sale.ListingSaleRepository;
import fr.vinted.republisher.domain.tag.ListingTag;
import fr.vinted.republisher.domain.tag.ListingTagRepository;
import fr.vinted.republisher.domain.tag.Tag;
import fr.vinted.republisher.domain.wardrobe.ArticleId;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.PriceChange;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** US-53 : chaque article a un identifiant stable, que ses annonces successives partagent. */
class ArticleIdentityAcceptanceTest extends ApplicationWithDatabaseTest {
    private static final VintedListingId A = new VintedListingId(1);
    private static final VintedListingId B = new VintedListingId(2);
    private static final Instant NOV_10 = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant NOV_11 = Instant.parse("2026-11-11T10:00:00Z");
    private static final Instant NOV_12 = Instant.parse("2026-11-12T10:00:00Z");
    private static final Instant NOV_13 = Instant.parse("2026-11-13T10:00:00Z");
    private static final Tag SUMMER = new Tag("été");
    private static final Tag DRESS = new Tag("robe");

    @Autowired
    private ListingRepository listings;
    @Autowired
    private ListingBackupRepository listingBackups;
    @Autowired
    private ListingRepublicationRepository republications;
    @Autowired
    private ListingTagRepository tags;
    @Autowired
    private ListingSaleRepository sales;
    @Autowired
    private JdbcTemplate database;

    @Test
    void new_listing_gets_its_own_article() {
        // AT-53-01. Étant donné deux annonces inconnues de l'outil
        // Quand elles sont enregistrées, puis l'une est enregistrée à nouveau
        listings.saveAll(List.of(Listing.seen(anActiveListing(A.value()), NOV_10),
                Listing.seen(anActiveListing(B.value()), NOV_10)));
        ArticleId first = articleOf(A);
        listings.saveAll(List.of(Listing.seen(anActiveListing(A.value()), NOV_11)));

        // Alors elles ont chacune un article, distinct
        assertThat(first).isNotEqualTo(articleOf(B));
        // Et l'annonce enregistrée à nouveau garde son article
        assertThat(articleOf(A)).isEqualTo(first);
        assertThat(database.queryForObject("SELECT count(*) FROM article", Long.class)).isEqualTo(2);
    }

    @Test
    void republished_listing_keeps_its_article() {
        // AT-53-02. Étant donné une annonce taguée « été », vue à 20 € puis à 18 €
        seen(A, "20.00", NOV_10);
        seen(A, "18.00", NOV_11);
        tags.add(SUMMER, List.of(A));
        // Quand sa republication réussie vers une nouvelle annonce à 18 € est enregistrée
        seen(B, "18.00", NOV_12);
        republish(A, B);

        // Alors la nouvelle annonce a le même article que l'ancienne
        assertThat(articleOf(B)).isEqualTo(articleOf(A));
        // Et elle porte le tag « été » et l'historique 18 € puis 20 €
        assertThat(tags.findAll()).containsExactlyInAnyOrder(new ListingTag(A, SUMMER), new ListingTag(B, SUMMER));
        assertThat(listings.priceHistoryOf(B)).extracting(change -> change.price().amount().toPlainString())
                .containsExactly("18.00", "20.00");
        // Et l'ancienne annonce marquée absente au scan suivant n'ajoute aucun prix
        listings.saveAll(List.of(listings.find(A).orElseThrow().absent()));
        assertThat(listings.priceHistoryOf(B)).hasSize(2);
    }

    @Test
    void absorbed_article_brings_all_its_listings() {
        // Étant donné B déjà republiée en C, puis la preuve que B est la republication de A
        seen(A, "20.00", NOV_10);
        seen(B, "20.00", NOV_11);
        seen(new VintedListingId(3), "20.00", NOV_12);
        republish(B, new VintedListingId(3));

        republish(A, B);

        // Alors A, B et C ont l'article de A, et les deux republications aussi
        assertThat(articleOf(B)).isEqualTo(articleOf(A));
        assertThat(articleOf(new VintedListingId(3))).isEqualTo(articleOf(A));
        assertThat(database.queryForList("SELECT DISTINCT article_id FROM listing_republication", UUID.class))
                .containsExactly(articleOf(A).value());
        assertThat(database.queryForObject("SELECT count(*) FROM article", Long.class)).isEqualTo(1);
    }

    @Test
    void republication_saved_again_changes_nothing() {
        seen(A, "20.00", NOV_10);
        seen(B, "20.00", NOV_12);
        tags.add(SUMMER, List.of(B));
        republish(A, B);
        ListingRepublication published = republications.findLatest(A).orElseThrow();

        republications.save(published);

        assertThat(articleOf(B)).isEqualTo(articleOf(A));
        assertThat(tags.findAll()).containsExactlyInAnyOrder(new ListingTag(A, SUMMER), new ListingTag(B, SUMMER));
        assertThat(database.queryForObject("SELECT count(*) FROM article", Long.class)).isEqualTo(1);
    }

    @Test
    void scanned_new_listing_merges_into_article() {
        // AT-53-03. Étant donné une annonce A taguée « été », vue à 20 €
        seen(A, "20.00", NOV_10);
        tags.add(SUMMER, List.of(A));
        ArticleId kept = articleOf(A);
        // Et la nouvelle annonce B, vue par le scan à 20 €, taguée « robe » et notée vendue
        seen(B, "20.00", NOV_12);
        tags.add(DRESS, List.of(B));
        sales.add(ListingSale.noted(B, NOV_12));
        ArticleId absorbed = articleOf(B);

        // Quand la republication réussie de A vers B est enregistrée
        republish(A, B);

        // Alors A et B ont le même article, celui de A
        assertThat(articleOf(A)).isEqualTo(kept);
        assertThat(articleOf(B)).isEqualTo(kept);
        // Et cet article a les tags « été » et « robe », la vente de B et l'historique 20 €
        assertThat(tags.findAll()).containsExactlyInAnyOrder(new ListingTag(A, SUMMER), new ListingTag(A, DRESS),
                new ListingTag(B, SUMMER), new ListingTag(B, DRESS));
        assertThat(sales.findAll()).containsExactly(ListingSale.noted(B, NOV_12));
        assertThat(listings.priceHistoryOf(B)).extracting(PriceChange::listingId, PriceChange::changedAt)
                .containsExactly(tuple(A, NOV_10));
        // Et l'article qu'avait B n'existe plus
        assertThat(database.queryForObject("SELECT count(*) FROM article WHERE id = ?", Long.class,
                absorbed.value())).isZero();
    }

    @Test
    void merge_keeps_the_oldest_sale() {
        // AT-53-04. Étant donné une annonce A notée vendue le 10 novembre et une annonce B notée vendue le 12
        seen(A, "20.00", NOV_10);
        seen(B, "20.00", NOV_12);
        sales.add(ListingSale.noted(A, NOV_10));
        sales.add(ListingSale.noted(B, NOV_12));

        // Quand la republication réussie de A vers B est enregistrée
        republish(A, B);

        // Alors l'article n'a qu'une vente, celle de A du 10 novembre
        assertThat(sales.findAll()).containsExactly(ListingSale.noted(A, NOV_10));
    }

    @Test
    void tag_follows_article() {
        // AT-53-05. Étant donné une annonce A republiée en B
        seen(A, "20.00", NOV_10);
        seen(B, "20.00", NOV_12);
        republish(A, B);

        // Quand le membre tague A « hiver »
        Tag winter = new Tag("hiver");
        tags.add(winter, List.of(A));
        // Alors B porte le tag « hiver »
        assertThat(tags.findAll()).contains(new ListingTag(B, winter));

        // Et quand il retire « hiver » de B, ni A ni B ne le portent
        tags.remove(winter, List.of(B));
        assertThat(tags.findAll()).isEmpty();
    }

    @Test
    void one_sale_per_article() {
        // AT-53-06. Étant donné une annonce A republiée en B, B notée vendue
        seen(A, "20.00", NOV_10);
        seen(B, "20.00", NOV_12);
        republish(A, B);
        sales.add(ListingSale.noted(B, NOV_13));

        // Quand le membre note A vendue, c'est refusé
        assertThatThrownBy(() -> sales.add(ListingSale.noted(A, NOV_13))).isInstanceOf(ListingAlreadySold.class);
        assertThat(sales.findAll()).containsExactly(ListingSale.noted(B, NOV_13));
        // Et A n'a pas de vente à elle : sa vente est celle de B
        assertThat(sales.find(A)).isEmpty();
    }

    @Test
    void database_rejects_republication_across_articles() {
        // AT-53-08. Étant donné deux annonces d'articles différents
        seen(A, "20.00", NOV_10);
        seen(B, "20.00", NOV_12);
        listingBackups.save(aBackup(A.value(), NOV_10));

        // Quand on écrit en base une republication réussie de l'une vers l'autre sans passer par la fusion
        // Alors la base refuse l'écriture
        assertThatThrownBy(() -> database.update("INSERT INTO listing_republication (id, listing_id, article_id, "
                        + "origin, backup_saved_at, attempted_at, outcome, published_at, new_listing_id) "
                        + "VALUES (?, ?, ?, 'RUN', ?, ?, 'PUBLISHED', ?, ?)", UUID.randomUUID(), A.value(),
                articleOf(A).value(), Timestamp.from(NOV_10), Timestamp.from(NOV_12),
                Timestamp.from(NOV_12), B.value()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void seen(VintedListingId listing, String price, Instant at) {
        listings.saveAll(List.of(Listing.seen(aListing(listing.value(), "Robe bleue", price, ACTIVE), at)));
    }

    /** A republished as B: attempted, then proven published. */
    private void republish(VintedListingId listing, VintedListingId newListing) {
        listingBackups.save(aBackup(listing.value(), NOV_10));
        ListingRepublication attempt =
                ListingRepublication.attempted(listingBackups.find(listing).orElseThrow(), NOV_12, WriteOrigin.RUN);
        republications.save(attempt);
        republications.save(attempt.published(newListing, NOV_12));
    }

    private ArticleId articleOf(VintedListingId listing) {
        return listings.find(listing).map(Listing::articleId).orElseThrow();
    }
}
