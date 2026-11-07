package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.backup.WardrobeBackup;
import fr.vinted.republisher.domain.backup.WardrobeBackupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** On a disposable PostgreSQL with the real changelog, never the real data. */
@Tag("docker")
@Testcontainers
@SpringBootTest(properties = {
        "app.data-dir=target/test-data",
        "chrome.path=not-started-by-these-tests",
        "chrome.debug-port=9222",
        "vinted.wardrobe-url=http://127.0.0.1/member/1"})
class JpaWardrobeBackupRepositoryTest {

    private static final Instant STARTED_AT = Instant.parse("2026-11-06T10:00:00Z");
    private static final Instant FINISHED_AT = STARTED_AT.plusSeconds(1800);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Autowired
    private WardrobeBackupRepository backups;

    @Autowired
    private JdbcTemplate database;

    @BeforeEach
    void startWithoutAnyBackup() {
        database.update("DELETE FROM wardrobe_backup");
    }

    @Test
    void nothing_is_found_before_the_first_backup() {
        assertThat(backups.findUnfinished()).isEmpty();
        assertThat(backups.findLastFinished()).isEmpty();
    }

    @Test
    void gives_back_an_unfinished_backup_as_it_was_saved() {
        WardrobeBackup backup = WardrobeBackup.startedAt(STARTED_AT);

        backups.save(backup);

        assertThat(backups.findUnfinished()).contains(backup);
        assertThat(backups.findLastFinished()).isEmpty();
    }

    @Test
    void finishing_a_backup_updates_it() {
        WardrobeBackup backup = WardrobeBackup.startedAt(STARTED_AT);
        backups.save(backup);

        backups.save(backup.finished(FINISHED_AT));

        assertThat(backups.findUnfinished()).isEmpty();
        assertThat(backups.findLastFinished()).contains(backup.finished(FINISHED_AT));
    }

    @Test
    void last_finished_backup_is_the_one_that_ended_last() {
        WardrobeBackup last = WardrobeBackup.startedAt(STARTED_AT.plusSeconds(3600)).finished(FINISHED_AT.plusSeconds(3600));
        backups.save(last);
        backups.save(WardrobeBackup.startedAt(STARTED_AT).finished(FINISHED_AT));

        assertThat(backups.findLastFinished()).contains(last);
    }

    @Test
    void refuses_a_second_unfinished_backup() {
        backups.save(WardrobeBackup.startedAt(STARTED_AT));

        Throwable failure = catchThrowable(() -> backups.save(WardrobeBackup.startedAt(FINISHED_AT)));

        assertThat(failure).isInstanceOf(DataAccessException.class);
    }
}
