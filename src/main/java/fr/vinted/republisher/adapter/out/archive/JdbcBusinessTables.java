package fr.vinted.republisher.adapter.out.archive;

import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Every table of the changelog but Liquibase's own, row by row, in one read-only snapshot: a future table enters the
 * archive without any change here, and a restore is a plain reinsertion into a schema at the same version.
 * Values: dates as ISO-8601 instants, numbers and booleans as such, {@code null} as null, anything else (uuid, text,
 * arrays such as {@code {1,2}}) as PostgreSQL's text form, which the restore script casts back.
 */
public class JdbcBusinessTables implements BusinessTables {

    private static final int FORMAT_VERSION = 1;
    private static final int ROWS_PER_FETCH = 500;

    private final DataSource database;
    private final JsonMapper json = JsonMapper.builder().disable(StreamWriteFeature.AUTO_CLOSE_TARGET).build();

    public JdbcBusinessTables(DataSource database) {
        this.database = database;
    }

    /**
     * One dedicated connection, read-only and REPEATABLE READ: every query sees the same snapshot, whatever the
     * transactions of the rest of the application.
     */
    @Override
    public void writeJson(Instant createdAt, OutputStream out) {
        try (Connection snapshot = database.getConnection()) {
            snapshot.setAutoCommit(false);
            snapshot.setReadOnly(true);
            snapshot.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            try {
                JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(snapshot, true));
                jdbc.setFetchSize(ROWS_PER_FETCH);
                write(jdbc, createdAt, out);
            } finally {
                snapshot.rollback();
            }
        } catch (SQLException failure) {
            throw new CannotGetJdbcConnectionException("Cannot read a snapshot of the database", failure);
        }
    }

    private void write(JdbcTemplate jdbc, Instant createdAt, OutputStream out) {
        try (JsonGenerator generator = json.createGenerator(out)) {
            generator.writeStartObject();
            generator.writeName("formatVersion");
            generator.writeNumber(FORMAT_VERSION);
            generator.writeName("createdAt");
            generator.writeString(createdAt.toString());
            generator.writeName("schemaVersion");
            generator.writeString(jdbc.queryForObject(
                    "SELECT id FROM databasechangelog ORDER BY orderexecuted DESC LIMIT 1", String.class));
            generator.writeName("tables");
            generator.writeStartObject();
            for (String table : tables(jdbc)) {
                generator.writeName(table);
                writeRows(jdbc, table, generator);
            }
            generator.writeEndObject();
            generator.writeEndObject();
        }
    }

    private static List<String> tables(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = current_schema() "
                + "AND tablename NOT IN ('databasechangelog', 'databasechangeloglock') ORDER BY tablename",
                String.class);
    }

    private static void writeRows(JdbcTemplate jdbc, String table, JsonGenerator generator) {
        generator.writeStartArray();
        jdbc.query("SELECT * FROM " + quoted(table) + " ORDER BY " + primaryKeyOf(jdbc, table),
                (ResultSet row) -> writeRow(row, generator));
        generator.writeEndArray();
    }

    /** So that two archives of the same data are identical; every table of the changelog has a primary key. */
    private static String primaryKeyOf(JdbcTemplate jdbc, String table) {
        List<String> key = jdbc.queryForList("SELECT a.attname FROM pg_index i JOIN pg_attribute a "
                + "ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey) "
                + "WHERE i.indrelid = to_regclass(?) AND i.indisprimary "
                + "ORDER BY array_position(i.indkey::int2[], a.attnum)", String.class, quoted(table));
        if (key.isEmpty()) {
            throw new IllegalStateException("Table " + table + " has no primary key");
        }
        return String.join(", ", key.stream().map(JdbcBusinessTables::quoted).toList());
    }

    private static void writeRow(ResultSet row, JsonGenerator generator) throws SQLException {
        ResultSetMetaData columns = row.getMetaData();
        generator.writeStartObject();
        for (int column = 1; column <= columns.getColumnCount(); column++) {
            generator.writeName(columns.getColumnName(column));
            writeValue(row.getObject(column), generator);
        }
        generator.writeEndObject();
    }

    private static void writeValue(Object value, JsonGenerator generator) {
        switch (value) {
            case null -> generator.writeNull();
            case Timestamp timestamp -> generator.writeString(timestamp.toInstant().toString());
            case OffsetDateTime dateTime -> generator.writeString(dateTime.toInstant().toString());
            case BigDecimal decimal -> generator.writeNumber(decimal);
            case Integer number -> generator.writeNumber(number);
            case Long number -> generator.writeNumber(number);
            case Boolean flag -> generator.writeBoolean(flag);
            default -> generator.writeString(value.toString());
        }
    }

    private static String quoted(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
