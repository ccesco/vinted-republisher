package fr.vinted.republisher.domain.archive;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DataArchiveTest {

    @Test
    void is_named_after_the_member_local_time_to_the_second() {
        ZonedDateTime now = ZonedDateTime.ofInstant(Instant.parse("2026-11-17T13:05:09.987Z"), ZoneId.of("Europe/Paris"));

        DataArchive archive = DataArchive.createdAt(now);

        assertThat(archive.fileName()).isEqualTo("vinted-republisher-archive-2026-11-17T14-05-09.zip");
        assertThat(archive.createdAt()).isEqualTo(Instant.parse("2026-11-17T13:05:09.987Z"));
    }
}
