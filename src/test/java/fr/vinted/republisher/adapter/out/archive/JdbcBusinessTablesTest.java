package fr.vinted.republisher.adapter.out.archive;

import fr.vinted.republisher.ApplicationWithDatabaseTest;
import fr.vinted.republisher.DataInEveryTable;
import fr.vinted.republisher.DisposablePostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.testcontainers.images.builder.Transferable;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** On a disposable PostgreSQL with the real changelog, never the real data. */
class JdbcBusinessTablesTest extends ApplicationWithDatabaseTest {

    private static final Instant CREATED_AT = Instant.parse("2026-11-17T13:05:09Z");

    @Autowired
    private DataSource database;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ApplicationContext application;

    @Test
    void holds_every_business_table_and_no_liquibase_table() {
        JsonNode data = export();

        assertThat(data.get("tables").propertyNames()).containsExactlyElementsOf(businessTables());
        assertThat(data.get("formatVersion").asInt()).isEqualTo(1);
        assertThat(data.get("createdAt").asString()).isEqualTo("2026-11-17T13:05:09Z");
        assertThat(data.get("schemaVersion").asString()).isEqualTo(jdbc.queryForObject(
                "SELECT id FROM databasechangelog ORDER BY orderexecuted DESC LIMIT 1", String.class));
    }

    @Test
    void sorts_rows_by_primary_key() {
        insertListing(3);
        insertListing(1);
        insertListing(2);

        JsonNode listings = export().get("tables").get("listing");

        assertThat(listings.valueStream().map(row -> row.get("vinted_id").asLong()).toList())
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void keeps_dates_in_iso_numbers_as_numbers_and_null_as_null() {
        insertListing(1);
        jdbc.update("INSERT INTO action_journal_entry (occurred_at, action, outcome, listing_id, reason) "
                + "VALUES ('2026-11-17T10:00:00.123456Z', 'DELETION', 'REFUSED', NULL, 'NOT_VALIDATED')");

        JsonNode tables = export().get("tables");

        JsonNode listing = tables.get("listing").get(0);
        assertThat(listing.get("price_amount").isNumber()).isTrue();
        assertThat(listing.get("price_amount").decimalValue()).isEqualTo(new BigDecimal("12.50"));
        assertThat(listing.get("present").isBoolean()).isTrue();
        assertThat(listing.get("last_seen_at").asString()).isEqualTo("2026-11-17T10:00:00Z");
        JsonNode entry = tables.get("action_journal_entry").get(0);
        assertThat(entry.get("id").isIntegralNumber()).isTrue();
        assertThat(entry.get("occurred_at").asString()).isEqualTo("2026-11-17T10:00:00.123456Z");
        assertThat(entry.get("listing_id").isNull()).isTrue();
        assertThat(entry.get("reason").asString()).isEqualTo("NOT_VALIDATED");
    }

    @Test
    void reads_every_table_on_one_read_only_repeatable_read_connection() {
        List<String> states = new ArrayList<>();

        new JdbcBusinessTables(new RecordingDataSource(database, states))
                .writeJson(CREATED_AT, new ByteArrayOutputStream());

        assertThat(states).hasSizeGreaterThan(businessTables().size())
                .containsOnly("connection 1, autoCommit false, readOnly true, isolation "
                        + Connection.TRANSACTION_REPEATABLE_READ);
    }

    @Test
    void restore_script_reloads_an_export_of_every_table_into_empty_tables() throws IOException {
        DataInEveryTable.fill(application);
        Map<String, List<Map<String, Object>>> before = rowsOfEveryTable();
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        new JdbcBusinessTables(database).writeJson(CREATED_AT, json);
        DisposablePostgres.empty();
        DisposablePostgres.POSTGRES.copyFileToContainer(Transferable.of(json.toByteArray()), "/tmp/data.json");

        jdbc.execute(Files.readString(Path.of("scripts/restaurer-archive.sql")));

        assertThat(rowsOfEveryTable()).isEqualTo(before);
        assertThat(before.values()).allSatisfy(rows -> assertThat(rows).isNotEmpty());
        long lastEntry = jdbc.queryForObject("SELECT max(id) FROM action_journal_entry", Long.class);
        jdbc.update("INSERT INTO action_journal_entry (occurred_at, action, outcome) "
                + "VALUES ('2026-11-17T11:00:00Z', 'SCAN', 'STARTED')");
        assertThat(jdbc.queryForObject("SELECT max(id) FROM action_journal_entry", Long.class))
                .isEqualTo(lastEntry + 1);
    }

    private List<String> businessTables() {
        return jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'public' "
                + "AND tablename NOT LIKE 'databasechangelog%' ORDER BY tablename", String.class);
    }

    /** Arrays compared by their text, which is what equality of two reads of the same row needs. */
    private Map<String, List<Map<String, Object>>> rowsOfEveryTable() {
        Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
        for (String table : businessTables()) {
            rows.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1").stream()
                    .map(JdbcBusinessTablesTest::comparable).toList());
        }
        return rows;
    }

    private static Map<String, Object> comparable(Map<String, Object> row) {
        Map<String, Object> values = new LinkedHashMap<>();
        row.forEach((column, value) -> values.put(column, value instanceof java.sql.Array ? value.toString() : value));
        return values;
    }

    private void insertListing(long id) {
        jdbc.update("WITH new_article AS (INSERT INTO article VALUES (gen_random_uuid(), '2026-11-17T10:00:00Z') "
                + "RETURNING id) INSERT INTO listing SELECT ?, 'https://www.vinted.fr/items/' || ?, 'Bonnet', 12.50, 'EUR', "
                + "true, '2026-11-17T10:00:00Z', '2026-11-17T10:00:00Z', id FROM new_article", id, id);
    }

    private JsonNode export() {
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        new JdbcBusinessTables(database).writeJson(CREATED_AT, json);
        return JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build()
                .readTree(json.toByteArray());
    }

    /** Records, for each statement, which connection runs it and in which transaction settings. */
    private static final class RecordingDataSource extends DelegatingDataSource {

        private final List<String> states;
        private int connections;

        RecordingDataSource(DataSource target, List<String> states) {
            super(target);
            this.states = states;
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection real = super.getConnection();
            int number = ++connections;
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().startsWith("prepareStatement")
                                || method.getName().equals("createStatement")) {
                            states.add("connection " + number + ", autoCommit " + real.getAutoCommit()
                                    + ", readOnly " + real.isReadOnly()
                                    + ", isolation " + real.getTransactionIsolation());
                        }
                        try {
                            return method.invoke(real, arguments);
                        } catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
        }
    }
}
