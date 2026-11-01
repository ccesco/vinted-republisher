package fr.vinted.republisher.adapter.out.browser;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

@Validated
@ConfigurationProperties("chrome")
public record ChromeProperties(@NotNull Path path, @Positive int debugPort) {
}
