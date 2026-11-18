package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.adapter.out.archive.JdbcBusinessTables;
import fr.vinted.republisher.adapter.out.archive.ZipDataArchiveWriter;
import fr.vinted.republisher.application.archive.DownloadDataArchive;
import fr.vinted.republisher.domain.archive.DataArchiveWriter;
import fr.vinted.republisher.domain.session.VintedSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;

/** Wires the download of a data archive: the application layer stays free of Spring. */
@Configuration
public class DataArchiveConfiguration {

    @Bean
    DataArchiveWriter dataArchiveWriter(DataSource database, @Value("${app.data-dir}") Path dataDirectory) {
        return new ZipDataArchiveWriter(new JdbcBusinessTables(database), dataDirectory.resolve("photos"));
    }

    /** The archive's name is in the member's local time, while the clock is in UTC. */
    @Bean
    DownloadDataArchive downloadDataArchive(VintedSession session, DataArchiveWriter writer, Clock clock) {
        return new DownloadDataArchive(session, writer, clock, ZoneId.systemDefault());
    }
}
