package fr.vinted.republisher.domain.backup;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aPhoto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingBackupTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    @Test
    void keeps_the_photos_of_a_reading_ranked_in_their_order() {
        ListingReading reading = new ListingReading(aContent(1), List.of(aPhoto("cover"), aPhoto("back")));

        ListingBackup backup = ListingBackup.of(reading, NOW);

        assertThat(backup.photos()).containsExactly(
                ListingPhoto.at(0, aPhoto("cover")), ListingPhoto.at(1, aPhoto("back")));
        assertThat(backup.content()).isEqualTo(aContent(1));
        assertThat(backup.savedAt()).isEqualTo(NOW);
    }

    @Test
    void reading_without_photo_is_unreadable() {
        assertThatThrownBy(() -> new ListingReading(aContent(1), List.of()))
                .isInstanceOf(ListingUnreadable.class).hasMessageContaining("no photo on 1");
    }

    @Test
    void backup_made_before_photos_were_kept_has_none() {
        assertThat(new ListingBackup(aContent(1), List.of(), NOW).photos()).isEmpty();
    }

    @Test
    void photos_are_ranked_from_zero_without_gap() {
        assertThatThrownBy(() -> new ListingBackup(aContent(1), List.of(ListingPhoto.at(1, aPhoto("back"))), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
