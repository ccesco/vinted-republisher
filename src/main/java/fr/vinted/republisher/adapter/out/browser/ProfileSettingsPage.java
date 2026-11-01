package fr.vinted.republisher.adapter.out.browser;

import java.net.URI;

/** Recognizes the member's profile settings page among the URLs a tab goes through. */
record ProfileSettingsPage(URI url) {

    /**
     * Tolerates a trailing slash. A page without path (about:blank, chrome-error://…) or a URL that
     * java.net.URI rejects is not the profile settings.
     */
    boolean isShownAt(String pageUrl) {
        try {
            return withoutTrailingSlash(url.getPath()).equals(withoutTrailingSlash(URI.create(pageUrl).getPath()));
        } catch (IllegalArgumentException unparsable) {
            return false;
        }
    }

    private static String withoutTrailingSlash(String path) {
        return path == null ? "" : path.replaceFirst("/$", "");
    }
}
