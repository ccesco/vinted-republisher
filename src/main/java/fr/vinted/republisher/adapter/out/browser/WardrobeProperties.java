package fr.vinted.republisher.adapter.out.browser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

/** The member's wardrobe and the pace at which it is read. */
@Validated
@ConfigurationProperties("vinted")
public record WardrobeProperties(@NotNull URI wardrobeUrl, @NotNull @Valid Scan scan) {

    public record Scan(@NotNull Duration minPause, @NotNull Duration maxPause, @NotNull Duration stallTimeout) {
    }
}
