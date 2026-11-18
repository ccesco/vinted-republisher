package fr.vinted.republisher.adapter.out.persistence;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;

import static fr.vinted.republisher.DisposablePostgres.POSTGRES;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-32: listings known before the first-seen date was kept get one. Runs on its own database of the
 * {@link fr.vinted.republisher.DisposablePostgres}, so that the shared one keeps the whole changelog.
 */
@Tag("docker")
class ListingFirstSeenMigrationTest {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";
    private static final String DATABASE = "first_seen_migration";
    /** The changesets 001 to 013, written before this one. */
    private static final int CHANGESETS_BEFORE = 13;

    @Test
    void takes_the_first_seen_date_from_the_oldest_reading_or_the_last_sighting() throws Exception {
        createEmptyDatabase();
        migrate(CHANGESETS_BEFORE);
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            // Étant donné, avant la migration, l'annonce 1 vue pour la dernière fois le 5 novembre et relevée les 2
            //   et 4 novembre, et l'annonce 2 vue pour la dernière fois le 5 novembre et jamais relevée
            statement.execute("INSERT INTO listing (vinted_id, url, title, price_amount, price_currency, present, "
                    + "last_seen_at) VALUES "
                    + "(1, 'https://vinted.test/items/1', 'Pull', 10, 'EUR', true, '2026-11-05T09:00:00Z'), "
                    + "(2, 'https://vinted.test/items/2', 'Jupe', 10, 'EUR', true, '2026-11-05T09:00:00Z')");
            statement.execute("INSERT INTO listing_statistics_reading (listing_id, view_count, favourite_count, "
                    + "read_at) VALUES (1, 5, 1, '2026-11-04T09:00:00Z'), (1, 2, 0, '2026-11-02T09:00:00Z')");
        }

        // Quand la migration passe
        migrate(Integer.MAX_VALUE);

        try (Connection connection = connect()) {
            // Alors la première détection de l'annonce 1 est le 2 novembre, celle de l'annonce 2 le 5 novembre
            assertThat(firstSeenAt(connection, 1)).isEqualTo(Instant.parse("2026-11-02T09:00:00Z"));
            assertThat(firstSeenAt(connection, 2)).isEqualTo(Instant.parse("2026-11-05T09:00:00Z"));
        }
    }

    private static void createEmptyDatabase() throws SQLException {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
            statement.execute("CREATE DATABASE " + DATABASE);
        }
    }

    private static String urlOf(String database) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database;
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(urlOf(DATABASE), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** On a connection of its own: Liquibase turns auto-commit off on the connection it is given. */
    private static void migrate(int changesets) throws LiquibaseException, SQLException {
        try (Connection connection = connect()) {
            Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(),
                    new JdbcConnection(connection));
            liquibase.update(changesets, new Contexts(), new LabelExpression());
        }
    }

    private static Instant firstSeenAt(Connection connection, long listing) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT first_seen_at FROM listing WHERE vinted_id = ?")) {
            query.setLong(1, listing);
            ResultSet found = query.executeQuery();
            assertThat(found.next()).isTrue();
            return found.getObject(1, OffsetDateTime.class).toInstant();
        }
    }
}
