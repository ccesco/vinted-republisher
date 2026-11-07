package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.wardrobe.WardrobeScan;
import fr.vinted.republisher.domain.wardrobe.WardrobeScanRepository;
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
class JpaWardrobeScanRepositoryTest {

    private static final Instant STARTED_AT = Instant.parse("2026-11-06T10:00:00Z");
    private static final Instant FINISHED_AT = STARTED_AT.plusSeconds(1800);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Autowired
    private WardrobeScanRepository scans;

    @Autowired
    private JdbcTemplate database;

    @BeforeEach
    void startWithoutAnyScan() {
        database.update("DELETE FROM wardrobe_scan");
    }

    @Test
    void nothing_is_found_before_the_first_scan() {
        assertThat(scans.findUnfinished()).isEmpty();
        assertThat(scans.findLastFinished()).isEmpty();
    }

    @Test
    void gives_back_an_unfinished_scan_as_it_was_saved() {
        WardrobeScan scan = WardrobeScan.startedAt(STARTED_AT);

        scans.save(scan);

        assertThat(scans.findUnfinished()).contains(scan);
        assertThat(scans.findLastFinished()).isEmpty();
    }

    @Test
    void finishing_a_scan_updates_it() {
        WardrobeScan scan = WardrobeScan.startedAt(STARTED_AT);
        scans.save(scan);

        scans.save(scan.finished(FINISHED_AT));

        assertThat(scans.findUnfinished()).isEmpty();
        assertThat(scans.findLastFinished()).contains(scan.finished(FINISHED_AT));
    }

    @Test
    void last_finished_scan_is_the_one_that_ended_last() {
        WardrobeScan last = WardrobeScan.startedAt(STARTED_AT.plusSeconds(3600)).finished(FINISHED_AT.plusSeconds(3600));
        scans.save(last);
        scans.save(WardrobeScan.startedAt(STARTED_AT).finished(FINISHED_AT));

        assertThat(scans.findLastFinished()).contains(last);
    }

    @Test
    void refuses_a_second_unfinished_scan() {
        scans.save(WardrobeScan.startedAt(STARTED_AT));

        Throwable failure = catchThrowable(() -> scans.save(WardrobeScan.startedAt(FINISHED_AT)));

        assertThat(failure).isInstanceOf(DataAccessException.class);
    }
}
