package fr.vinted.republisher.acceptance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationStartupAcceptanceTest extends ApplicationWithDatabaseTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void application_starts_when_database_is_available() {
        // Étant donné une base PostgreSQL disponible
        // Quand l'application démarre
        // Alors le contexte Spring se charge sans erreur
        assertThat(context.getBeanDefinitionCount()).isPositive();
    }
}
