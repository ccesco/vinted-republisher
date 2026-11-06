package fr.vinted.republisher.acceptance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigrationAcceptanceTest extends ApplicationWithDatabaseTest {

    @Autowired
    private JdbcTemplate database;

    @Test
    void liquibase_runs_with_empty_changelog() {
        // Étant donné une base PostgreSQL vide
        // Quand l'application démarre
        // Alors Liquibase s'exécute sans erreur
        // Et sa table de suivi existe dans la base
        String trackingTable = database.queryForObject(
                "SELECT to_regclass('public.databasechangelog')::text", String.class);

        assertThat(trackingTable).isEqualTo("databasechangelog");
    }
}
