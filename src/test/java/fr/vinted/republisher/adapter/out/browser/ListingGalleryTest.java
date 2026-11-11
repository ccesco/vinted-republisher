package fr.vinted.republisher.adapter.out.browser;

import fr.vinted.republisher.domain.backup.ListingUnreadable;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static fr.vinted.republisher.adapter.out.browser.EditFormFixtures.galleryRow;
import static fr.vinted.republisher.adapter.out.browser.EditFormFixtures.scriptsWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingGalleryTest {

    private static final VintedListingId ID = new VintedListingId(10);
    private static final String FRONT = "https://images1.vinted.net/tc/05_a/1.webp?s=a1";
    private static final String BACK = "https://images1.vinted.net/tc/05_b/2.webp?s=b2";

    @Test
    void reads_the_full_size_photos_of_the_gallery_in_their_order() {
        assertThat(ListingGallery.photoAddresses(ID, scriptsWith(galleryRow(10, FRONT, BACK))))
                .containsExactly(URI.create(FRONT), URI.create(BACK));
    }

    @Test
    void same_gallery_given_twice_is_read_once() {
        List<String> scripts = scriptsWith(galleryRow(10, FRONT, BACK), galleryRow(10, FRONT, BACK).replace("c8:", "c9:"));

        assertThat(ListingGallery.photoAddresses(ID, scripts)).containsExactly(URI.create(FRONT), URI.create(BACK));
    }

    @Test
    void gallery_of_another_listing_only_is_unreadable() {
        assertThatThrownBy(() -> ListingGallery.photoAddresses(ID, scriptsWith(galleryRow(11, FRONT))))
                .isInstanceOf(ListingUnreadable.class).hasMessageContaining("no photo gallery on 10");
    }

    @Test
    void two_different_galleries_are_unreadable() {
        List<String> scripts = scriptsWith(galleryRow(10, FRONT), galleryRow(10, BACK).replace("c8:", "c9:"));

        assertThatThrownBy(() -> ListingGallery.photoAddresses(ID, scripts))
                .isInstanceOf(ListingUnreadable.class).hasMessageContaining("2 photo galleries on 10");
    }

    @Test
    void gallery_without_photo_is_unreadable() {
        assertThatThrownBy(() -> ListingGallery.photoAddresses(ID, scriptsWith(galleryRow(10))))
                .isInstanceOf(ListingUnreadable.class).hasMessageContaining("no photo on 10");
    }

    @Test
    void photo_without_full_size_address_is_unreadable() {
        String noFullSize = galleryRow(10, FRONT).replace("\"full_size_url\":\"" + FRONT + "\"", "\"full_size_url\":null");

        assertThatThrownBy(() -> ListingGallery.photoAddresses(ID, scriptsWith(noFullSize)))
                .isInstanceOf(ListingUnreadable.class).hasMessageContaining("unexpected data on 10");
    }

    @Test
    void address_that_is_not_web_is_unreadable() {
        assertThatThrownBy(() -> ListingGallery.photoAddresses(ID, scriptsWith(galleryRow(10, "file:///etc/passwd"))))
                .isInstanceOf(ListingUnreadable.class).hasMessageContaining("unexpected data on 10");
    }
}
