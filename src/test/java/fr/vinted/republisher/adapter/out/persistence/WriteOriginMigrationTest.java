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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static fr.vinted.republisher.DisposablePostgres.POSTGRES;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-49: the attempts recorded before their origin was kept count for the run. Runs on its own database of the
 * {@link fr.vinted.republisher.DisposablePostgres}, so that the shared one keeps the whole changelog.
 */
@Tag("docker")
class WriteOriginMigrationTest {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";
    private static final String DATABASE = "write_origin_migration";
    /** The changesets 001 to 019, written before this one. */
    private static final int CHANGESETS_BEFORE = 19;

    @Test
    void existing_attempts_are_run_writes() throws Exception {
        createEmptyDatabase();
        migrate(CHANGESETS_BEFORE);
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            // Étant donné une suppression et une republication enregistrées avant US-49 (AT-49-06)
            statement.execute("INSERT INTO listing (vinted_id, url, title, price_amount, price_currency, present, "
                    + "last_seen_at, first_seen_at) VALUES (1, 'https://vinted.test/items/1', 'Pull', 10, 'EUR', "
                    + "false, '2026-11-05T09:00:00Z', '2026-11-01T09:00:00Z')");
            statement.execute("INSERT INTO listing_content (listing_id, saved_at, title, description, price, "
                    + "currency, catalog_id, category_label, condition_id, condition_label, package_size_id, "
                    + "package_size_label) VALUES (1, '2026-11-04T09:00:00Z', 'Pull', 'Laine', 10, 'EUR', 1, "
                    + "'Pulls', 1, 'Très bon état', 1, 'Petit')");
            statement.execute("INSERT INTO listing_deletion (id, listing_id, backup_saved_at, attempted_at, "
                    + "outcome, settled_at) VALUES ('00000000-0000-0000-0000-000000000001', 1, "
                    + "'2026-11-04T09:00:00Z', '2026-11-05T09:00:00Z', 'CONFIRMED', '2026-11-05T09:00:05Z')");
            statement.execute("INSERT INTO listing_republication (id, listing_id, backup_saved_at, attempted_at, "
                    + "outcome) VALUES ('00000000-0000-0000-0000-000000000002', 1, '2026-11-04T09:00:00Z', "
                    + "'2026-11-05T10:00:00Z', 'FAILED')");
        }

        // Quand la migration passe
        migrate(Integer.MAX_VALUE);

        try (Connection connection = connect()) {
            // Alors elles sont comptées pour la série
            assertThat(origins(connection, "listing_deletion")).containsExactly("RUN");
            assertThat(origins(connection, "listing_republication")).containsExactly("RUN");
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

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(
                "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + DATABASE,
                POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** On a connection of its own: Liquibase turns auto-commit off on the connection it is given. */
    private static void migrate(int changesets) throws LiquibaseException, SQLException {
        try (Connection connection = connect()) {
            Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(),
                    new JdbcConnection(connection));
            liquibase.update(changesets, new Contexts(), new LabelExpression());
        }
    }

    /** The table is one of the two attempt tables, named by the test itself. */
    private static List<String> origins(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet found = statement.executeQuery("SELECT origin FROM " + table)) {
            List<String> origins = new ArrayList<>();
            while (found.next()) {
                origins.add(found.getString(1));
            }
            return origins;
        }
    }
}
