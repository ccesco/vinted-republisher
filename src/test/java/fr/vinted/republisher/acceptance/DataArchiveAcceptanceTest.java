package fr.vinted.republisher.acceptance;

import fr.vinted.republisher.ApplicationWithDatabaseTest;
import fr.vinted.republisher.DataInEveryTable;
import fr.vinted.republisher.domain.archive.DataArchiveWriter;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.VintedSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/** US-26, on a disposable PostgreSQL and a test data folder, never the real data. */
@AutoConfigureMockMvc
class DataArchiveAcceptanceTest extends ApplicationWithDatabaseTest {

    private static final Path DATA = Path.of("target/test-data");
    private static final Path PHOTOS = DATA.resolve("photos");
    private static final Path BROWSER_PROFILE = DATA.resolve("browser-profile");
    private static final byte[] COVER = {1, 2, 3, 4};
    private static final byte[] SECOND = {5, 6, 7};

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private VintedSession session;
    @Autowired
    private ApplicationContext application;
    @MockitoSpyBean
    private DataArchiveWriter writer;

    @BeforeEach
    void startFromKnownFolders() throws IOException {
        deleteRecursively(PHOTOS);
        deleteRecursively(BROWSER_PROFILE);
    }

    @Test
    void archive_contains_every_business_row_and_every_photo() throws IOException {
        // Étant donné une base avec deux annonces, leur contenu, leurs photos, leurs relevés, une suppression, une
        // republication et des entrées de journal (au moins une ligne dans chaque table)
        DataInEveryTable.fill(application);
        // Et leurs photos dans le dossier des photos
        write(PHOTOS.resolve("1/0.webp"), COVER);
        write(PHOTOS.resolve("1/1.jpg"), SECOND);

        // Quand le membre télécharge l'archive
        MvcTestResult result = mvc.get().uri("/api/data-archive").exchange();

        // Alors il reçoit un zip dont data.json contient chaque ligne de chaque table métier, et aucune table Liquibase
        assertThat(result).hasStatusOk().hasContentType("application/zip");
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .matches("attachment; filename=\"vinted-republisher-archive-\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2}"
                        + "\\.zip\"");
        Map<String, byte[]> entries = unzip(result.getResponse().getContentAsByteArray());
        JsonNode data = JsonMapper.builder().build().readTree(entries.get("data.json"));
        assertThat(fieldNames(data.get("tables"))).isEqualTo(businessTables());
        for (String table : businessTables()) {
            assertThat(data.get("tables").get(table).size()).as(table).isPositive()
                    .isEqualTo(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class));
        }
        JsonNode tables = data.get("tables");
        assertThat(tables.get("listing").valueStream().map(row -> row.get("vinted_id").asLong()).toList())
                .containsExactly(DataInEveryTable.DELETED_LISTING, DataInEveryTable.NEW_LISTING);
        assertThat(tables.get("listing").get(0).get("present").asBoolean()).isFalse();
        assertThat(tables.get("listing_content").get(0).get("listing_id").asLong())
                .isEqualTo(DataInEveryTable.DELETED_LISTING);
        assertThat(tables.get("listing_deletion").get(0).get("listing_id").asLong())
                .isEqualTo(DataInEveryTable.DELETED_LISTING);
        assertThat(tables.get("listing_republication").get(0).get("new_listing_id").asLong())
                .isEqualTo(DataInEveryTable.NEW_LISTING);
        JsonNode reading = tables.get("listing_statistics_reading").get(0);
        assertThat(reading.get("view_count").asInt()).isEqualTo(42);
        assertThat(reading.get("read_at").asString()).isEqualTo("2026-11-17T10:00:00Z");
        assertThat(tables.get("action_journal_entry").get(0).get("listing_id").isNull()).isTrue();
        // Et dont photos/ contient chaque photo avec les mêmes octets
        assertThat(entries.get("photos/1/0.webp")).isEqualTo(COVER);
        assertThat(entries.get("photos/1/1.jpg")).isEqualTo(SECOND);
        // Et data.json porte formatVersion 1, sa date de création et l'id du dernier changeset appliqué
        assertThat(data.get("formatVersion").asInt()).isEqualTo(1);
        assertThat(Instant.parse(data.get("createdAt").asString())).isBeforeOrEqualTo(Instant.now());
        assertThat(data.get("schemaVersion").asString()).isEqualTo(jdbc.queryForObject(
                "SELECT id FROM databasechangelog ORDER BY orderexecuted DESC LIMIT 1", String.class));
    }

    @Test
    void archive_never_contains_browser_profile() throws IOException {
        // Étant donné un profil Chrome dans le dossier des données
        write(BROWSER_PROFILE.resolve("Default/Cookies"), new byte[]{9});
        write(PHOTOS.resolve("1/0.webp"), COVER);

        // Quand le membre télécharge l'archive
        MvcTestResult result = mvc.get().uri("/api/data-archive").exchange();

        // Alors aucune entrée du zip ne vient du profil Chrome
        assertThat(result).hasStatusOk();
        assertThat(unzip(result.getResponse().getContentAsByteArray()).keySet())
                .containsExactlyInAnyOrder("data.json", "photos/1/0.webp");
    }

    @Test
    void archive_changes_nothing_and_keeps_no_copy() throws IOException {
        // Étant donné une base et un dossier de photos
        jdbc.update("INSERT INTO listing VALUES (1, 'https://www.vinted.fr/items/1', 'Bonnet', 12.50, 'EUR', true, "
                + "'2026-11-17T10:00:00Z')");
        write(PHOTOS.resolve("1/0.webp"), COVER);
        List<Map<String, Object>> listingsBefore = jdbc.queryForList("SELECT * FROM listing");
        Set<Path> dataBefore = filesUnder(DATA);
        Set<Path> temporaryBefore = archivesInTemporaryFolder();

        // Quand le membre télécharge l'archive
        MvcTestResult result = mvc.get().uri("/api/data-archive").exchange();

        // Alors la base et les photos sont inchangées
        assertThat(result).hasStatusOk();
        assertThat(jdbc.queryForList("SELECT * FROM listing")).isEqualTo(listingsBefore);
        assertThat(Files.readAllBytes(PHOTOS.resolve("1/0.webp"))).isEqualTo(COVER);
        // Et aucun fichier d'archive ne reste dans le dossier des données ni dans le dossier temporaire
        assertThat(filesUnder(DATA)).isEqualTo(dataBefore);
        assertThat(archivesInTemporaryFolder()).isEqualTo(temporaryBefore);
    }

    @Test
    void archive_refused_while_browser_busy() {
        // Étant donné une action navigateur en cours
        session.start(BrowserAction.SCANNING);
        try {
            // Quand le membre demande l'archive
            MvcTestResult result = mvc.get().uri("/api/data-archive").exchange();

            // Alors la réponse est 409 avec la raison BROWSER_BUSY
            // Et aucun zip n'est envoyé
            assertThat(result).hasStatus(409).hasContentType("application/json")
                    .bodyJson().extractingPath("$.reason").isEqualTo("BROWSER_BUSY");
        } finally {
            session.finish();
        }
    }

    @Test
    void archive_failure_sends_no_partial_zip() {
        // Étant donné une photo illisible dans le dossier des photos (la construction échoue après un début d'écriture)
        doAnswer(invocation -> {
            OutputStream archive = invocation.getArgument(1);
            archive.write(new byte[]{'P', 'K', 3, 4});
            throw new UncheckedIOException(new IOException("Photo unreadable"));
        }).when(writer).write(any(), any());
        Set<Path> temporaryBefore = archivesInTemporaryFolder();

        // Quand le membre demande l'archive
        MvcTestResult result = mvc.get().uri("/api/data-archive").exchange();

        // Alors la réponse est 500 avec la raison ARCHIVE_FAILED
        // Et aucun zip tronqué n'est envoyé
        assertThat(result).hasStatus(500).hasContentType("application/json")
                .bodyJson().extractingPath("$.reason").isEqualTo("ARCHIVE_FAILED");
        // Et le fichier temporaire est effacé
        assertThat(archivesInTemporaryFolder()).isEqualTo(temporaryBefore);
        // Et le navigateur est libéré
        assertThat(session.overview().currentAction()).isEmpty();
    }

    private Set<String> businessTables() {
        return Set.copyOf(jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'public' "
                + "AND tablename NOT LIKE 'databasechangelog%'", String.class));
    }

    private static Set<String> fieldNames(JsonNode node) {
        return Set.copyOf(node.propertyNames());
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.put(entry.getName(), in.readAllBytes());
            }
        }
        return entries;
    }

    private static void write(Path file, byte[] content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }

    private static Set<Path> filesUnder(Path folder) throws IOException {
        try (Stream<Path> files = Files.walk(folder)) {
            return files.collect(Collectors.toSet());
        }
    }

    private static Set<Path> archivesInTemporaryFolder() {
        try (Stream<Path> files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(file -> file.getFileName().toString().startsWith("vinted-republisher-archive"))
                    .collect(Collectors.toSet());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void deleteRecursively(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path file : files.sorted((a, b) -> b.compareTo(a)).toList()) {
                Files.delete(file);
            }
        }
    }
}
