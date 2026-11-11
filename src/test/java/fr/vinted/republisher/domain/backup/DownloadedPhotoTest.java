package fr.vinted.republisher.domain.backup;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DownloadedPhotoTest {

    private static final byte[] ABC = "abc".getBytes(StandardCharsets.US_ASCII);
    private static final String SHA256_OF_ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test
    void is_named_after_its_checksum_and_the_type_served() {
        DownloadedPhoto photo = new DownloadedPhoto(ABC, "image/webp");

        assertThat(photo.sha256()).isEqualTo(SHA256_OF_ABC);
        assertThat(photo.fileName()).isEqualTo(SHA256_OF_ABC + ".webp");
    }

    @Test
    void knows_the_image_types_vinted_serves() {
        assertThat(new DownloadedPhoto(ABC, "image/jpeg").extension()).isEqualTo("jpg");
        assertThat(new DownloadedPhoto(ABC, "image/png").extension()).isEqualTo("png");
        assertThat(new DownloadedPhoto(ABC, "Image/WebP; charset=binary").extension()).isEqualTo("webp");
    }

    @Test
    void keeps_the_bytes_as_served() {
        byte[] served = ABC.clone();
        DownloadedPhoto photo = new DownloadedPhoto(served, "image/webp");
        served[0] = 'z';

        assertThat(photo.content()).isEqualTo(ABC);
    }

    @Test
    void empty_file_is_unreadable() {
        assertThatThrownBy(() -> new DownloadedPhoto(new byte[0], "image/webp"))
                .isInstanceOf(ListingUnreadable.class).hasMessageContaining("empty photo");
    }

    @Test
    void file_that_is_not_an_image_is_unreadable() {
        assertThatThrownBy(() -> new DownloadedPhoto(ABC, "text/html"))
                .isInstanceOf(ListingUnreadable.class).hasMessageContaining("not an image: text/html");
    }

    @Test
    void photos_with_the_same_bytes_and_type_are_equal() {
        assertThat(new DownloadedPhoto(ABC, "image/webp")).isEqualTo(new DownloadedPhoto(ABC.clone(), "image/webp"))
                .isNotEqualTo(new DownloadedPhoto(ABC, "image/png"));
    }
}
