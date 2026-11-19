package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.domain.run.ParallelismMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Reads once, at startup, how the automatic run relaunches listings (US-46): sequential by default, or by workers. A
 * missing or unknown value stops the tool.
 */
@Configuration
@EnableConfigurationProperties(ParallelismConfiguration.ParallelismProperties.class)
public class ParallelismConfiguration {

    @Bean
    ParallelismMode parallelismMode(ParallelismProperties parallelism) {
        if (parallelism.mode() == null) {
            throw new InvalidParallelismConfiguration();
        }
        return parallelism.mode();
    }

    @ConfigurationProperties("parallelism")
    record ParallelismProperties(ParallelismMode mode) {
    }

    static class InvalidParallelismConfiguration extends RuntimeException {

        InvalidParallelismConfiguration() {
            super("parallelism.mode : missing value, expected one of " + Arrays.stream(ParallelismMode.values())
                    .map(Enum::name).collect(Collectors.joining(", ")));
        }
    }
}
