package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.PauseRange;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Invented listing contents: never real data from a wardrobe. */
public final class BackupFixtures {

    private BackupFixtures() {
    }

    public static ListingContent aContent(long id) {
        return aContent(id, "Pull rayé " + id, "Porté deux fois");
    }

    public static ListingContent aContent(long id, String title, String description) {
        return aContent(id, title, description, "12.50");
    }

    public static ListingContent aContent(long id, String title, String description, String price) {
        return new ListingContent(new VintedListingId(id), title, description,
                new Price(new BigDecimal(price), "EUR"),
                new FormOption(1653, "Chaussures bébé"),
                Optional.of(new FormOption(60, "Kiabi")),
                new FormOption(2, "Très bon état"),
                Optional.of(new FormOption(588, "")),
                Optional.of(new FormOptions(List.of(12L), "Blanc")),
                Optional.empty(),
                new FormOption(1, "Petit"),
                CategoryDetails.none());
    }

    /** Invented bytes, served as WebP. */
    public static DownloadedPhoto aPhoto(String name) {
        return new DownloadedPhoto(("photo " + name).getBytes(StandardCharsets.UTF_8), "image/webp");
    }

    /** The content of the listing and one photo of its own. */
    public static ListingReading aReading(long id) {
        return new ListingReading(aContent(id), List.of(aPhoto("of " + id)));
    }

    public static ListingBackup aBackup(long id, Instant savedAt) {
        return ListingBackup.of(aReading(id), savedAt);
    }

    public static BackupPace noBackupPause() {
        return new BackupPace(new PauseRange(Duration.ZERO, Duration.ZERO), new Random(42));
    }
}
