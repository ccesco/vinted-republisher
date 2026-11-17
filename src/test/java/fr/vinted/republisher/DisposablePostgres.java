package fr.vinted.republisher;

import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * One disposable PostgreSQL, same image as docker-compose.yml, for the whole test JVM: never the real data.
 * Starting one container per test class cost several seconds each; every test starts by emptying it instead.
 */
public final class DisposablePostgres {

    public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static {
        POSTGRES.start();
    }

    private DisposablePostgres() {
    }

    /** Empties every table the changelog created, leaving Liquibase's own tables as they are. */
    public static void empty() {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            List<String> tables = new ArrayList<>();
            try (ResultSet found = statement.executeQuery("SELECT tablename FROM pg_tables "
                    + "WHERE schemaname = 'public' AND tablename NOT LIKE 'databasechangelog%'")) {
                while (found.next()) {
                    tables.add(found.getString(1));
                }
            }
            if (!tables.isEmpty()) {
                statement.execute("TRUNCATE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot empty the test database", failure);
        }
    }
}
