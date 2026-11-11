package fr.vinted.republisher.adapter.out.browser;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Where the edit form and the public page of a listing are, and the pace at which the backup reads them. */
@Validated
@ConfigurationProperties("vinted.backup")
public record BackupProperties(@NotBlank String editFormUrl, @NotBlank String listingPageUrl,
                               @NotNull Duration minPagePause, @NotNull Duration maxPagePause,
                               @NotNull Duration labelTimeout, @NotNull Duration photoTimeout) {
}
