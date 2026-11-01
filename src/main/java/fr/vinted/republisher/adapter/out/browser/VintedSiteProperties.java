package fr.vinted.republisher.adapter.out.browser;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

/**
 * The member's profile settings page: Vinted displays it to a logged-in member and redirects
 * a visitor to the login page. Unlike the wardrobe, which is public.
 */
@Validated
@ConfigurationProperties("vinted")
public record VintedSiteProperties(@NotNull URI profileSettingsUrl) {
}
