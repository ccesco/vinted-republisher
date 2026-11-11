package fr.vinted.republisher.domain.backup;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingPhotoTest {

    private static final String SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test
    void is_the_photo_kept_at_a_rank_of_the_listing() {
        ListingPhoto photo = ListingPhoto.at(2, new DownloadedPhoto("abc".getBytes(), "image/webp"));

        assertThat(photo).isEqualTo(new ListingPhoto(2, SHA256, "webp"));
        assertThat(photo.fileName()).isEqualTo(SHA256 + ".webp");
    }

    @Test
    void rank_is_never_negative() {
        assertThatThrownBy(() -> new ListingPhoto(-1, SHA256, "webp")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void checksum_is_a_sha256_in_lower_case_hexadecimal() {
        assertThatThrownBy(() -> new ListingPhoto(0, "abc", "webp")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ListingPhoto(0, SHA256.toUpperCase(), "webp"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extension_is_one_of_an_image() {
        assertThatThrownBy(() -> new ListingPhoto(0, SHA256, "html")).isInstanceOf(IllegalArgumentException.class);
    }
}
