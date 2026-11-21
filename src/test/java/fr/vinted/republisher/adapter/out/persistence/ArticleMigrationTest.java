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
 * US-53: each chain of published republications becomes one article. Runs on its own database of the
 * {@link fr.vinted.republisher.DisposablePostgres}, so that the shared one keeps the whole changelog.
 */
@Tag("docker")
class ArticleMigrationTest {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";
    private static final String DATABASE = "article_migration";
    /** The changesets written before this one (001 to 019, some files holding two). */
    private static final int CHANGESETS_BEFORE = 20;

    @Test
    void chains_become_articles() throws Exception {
        createEmptyDatabase();
        migrate(CHANGESETS_BEFORE);
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            // Étant donné, avant la migration, une chaîne 1 → 2 → 3 et une annonce 4 seule
            statement.execute("INSERT INTO listing (vinted_id, url, title, price_amount, price_currency, present, "
                    + "first_seen_at, last_seen_at) SELECT id, 'https://vinted.test/items/' || id, 'Robe', 20, 'EUR', "
                    + "id IN (3, 4), timestamptz '2026-11-01T09:00:00Z' + id * interval '1 day', "
                    + "timestamptz '2026-11-01T09:00:00Z' + id * interval '1 day' FROM generate_series(1, 4) id");
            statement.execute("INSERT INTO listing_content (listing_id, saved_at, title, description, price, currency, "
                    + "catalog_id, category_label, condition_id, condition_label, package_size_id, "
                    + "package_size_label) SELECT id, '2026-11-01T09:00:00Z', 'Robe', 'Portée', 20, 'EUR', 1, 'Robes',"
                    + " 1, 'Très bon état', 1, 'Petit' FROM generate_series(1, 2) id");
            statement.execute("INSERT INTO listing_republication (id, listing_id, backup_saved_at, attempted_at, "
                    + "outcome, published_at, new_listing_id) VALUES "
                    + "(gen_random_uuid(), 1, '2026-11-01T09:00:00Z', '2026-11-02T09:00:00Z', 'PUBLISHED', "
                    + "'2026-11-02T09:00:00Z', 2), "
                    + "(gen_random_uuid(), 2, '2026-11-01T09:00:00Z', '2026-11-03T09:00:00Z', 'PUBLISHED', "
                    + "'2026-11-03T09:00:00Z', 3)");
            //   et des tags sur 1 et 3, une vente sur 3, des prix sur 1 (20 € puis 18 €) et 3 (18 € puis 15 €)
            statement.execute("INSERT INTO listing_tag (listing_id, tag) VALUES (1, 'été'), (3, 'robe'), (3, 'été')");
            statement.execute("INSERT INTO listing_sale (listing_id, noticed_at, origin) "
                    + "VALUES (3, '2026-11-05T09:00:00Z', 'NOTICED')");
            statement.execute("INSERT INTO listing_price_change (listing_id, price_amount, price_currency, changed_at) "
                    + "VALUES (1, 20, 'EUR', '2026-11-01T09:00:00Z'), (1, 18, 'EUR', '2026-11-02T08:00:00Z'), "
                    + "(3, 18, 'EUR', '2026-11-03T09:00:00Z'), (3, 15, 'EUR', '2026-11-04T09:00:00Z')");
        }

        // Quand la migration passe
        migrate(Integer.MAX_VALUE);

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            // Alors 1, 2 et 3 partagent un article, 4 en a un autre
            assertThat(strings(statement, "SELECT count(DISTINCT article_id) FROM listing WHERE vinted_id <= 3"))
                    .containsExactly("1");
            assertThat(strings(statement, "SELECT count(*) FROM article")).containsExactly("2");
            // Et l'article de 1, 2, 3 a les tags de 1 et de 3, la vente de 3 et les prix de 1 et 3, sans répétition
            assertThat(strings(statement, "SELECT t.tag FROM article_tag t JOIN listing l ON l.article_id = "
                    + "t.article_id WHERE l.vinted_id = 2")).containsExactlyInAnyOrder("robe", "été");
            assertThat(strings(statement, "SELECT s.listing_id FROM article_sale s JOIN listing l ON l.article_id = "
                    + "s.article_id WHERE l.vinted_id = 1")).containsExactly("3");
            assertThat(strings(statement, "SELECT c.price_amount FROM listing_price_change c JOIN listing l ON "
                    + "l.article_id = c.article_id WHERE l.vinted_id = 2 ORDER BY c.changed_at"))
                    .containsExactly("20.00", "18.00", "15.00");
            // Et les republications restent dans leur article
            assertThat(strings(statement, "SELECT count(*) FROM listing_republication r JOIN listing l ON "
                    + "l.vinted_id = r.listing_id WHERE l.article_id = r.article_id")).containsExactly("2");
        }
    }

    private static List<String> strings(Statement statement, String query) throws SQLException {
        List<String> values = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery(query)) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
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
        return DriverManager.getConnection("jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(5432) + "/" + DATABASE, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** On a connection of its own: Liquibase turns auto-commit off on the connection it is given. */
    private static void migrate(int changesets) throws LiquibaseException, SQLException {
        try (Connection connection = connect()) {
            Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(),
                    new JdbcConnection(connection));
            liquibase.update(changesets, new Contexts(), new LabelExpression());
        }
    }
}
