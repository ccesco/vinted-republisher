package fr.vinted.republisher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static fr.vinted.republisher.DisposablePostgres.POSTGRES;

/**
 * The whole application on the {@link DisposablePostgres}: every test class sharing this configuration shares
 * one Spring context too, and each test starts from empty tables.
 */
@Tag("docker")
@SpringBootTest(properties = {
        "app.data-dir=target/test-data",
        "chrome.path=not-started-by-these-tests",
        "chrome.debug-port=9222",
        "vinted.wardrobe-url=http://127.0.0.1/member/1"})
public abstract class ApplicationWithDatabaseTest {

    @DynamicPropertySource
    static void connectToPostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void startFromEmptyTables() {
        DisposablePostgres.empty();
    }
}
