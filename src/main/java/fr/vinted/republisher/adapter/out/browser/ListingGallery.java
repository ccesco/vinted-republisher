package fr.vinted.republisher.adapter.out.browser;

import com.microsoft.playwright.Page;
import fr.vinted.republisher.domain.backup.DownloadedPhoto;
import fr.vinted.republisher.domain.backup.ListingUnreadable;
import fr.vinted.republisher.domain.session.SessionDisconnected;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The public page of a listing: the only one giving its photos in full size, in the gallery block of the data the
 * page streams in its scripts. The page itself downloads them, from the member's Chrome, as it would to show them.
 */
final class ListingGallery {

    private static final Set<String> WEB = Set.of("https", "http");
    private static final String DOWNLOAD = """
            async ([address, timeout]) => {
              const controller = new AbortController();
              const timer = setTimeout(() => controller.abort(), timeout);
              try {
                const response = await fetch(address, { signal: controller.signal });
                const bytes = new Uint8Array(await response.arrayBuffer());
                let binary = '';
                for (let start = 0; start < bytes.length; start += 0x8000) {
                  binary += String.fromCharCode(...bytes.subarray(start, start + 0x8000));
                }
                return { status: response.status, type: response.headers.get('content-type') || '',
                         base64: btoa(binary) };
              } catch (failure) {
                return { error: String(failure) };
              } finally {
                clearTimeout(timer);
              }
            }""";

    private ListingGallery() {
    }

    static List<DownloadedPhoto> read(Page page, VintedListingId id, URI pageUrl, Duration timeout) {
        ChromeTabs.open(page, pageUrl);
        requireOnTheListing(page, pageUrl);
        List<URI> addresses = photoAddresses(id, page.locator("script").allTextContents());
        List<DownloadedPhoto> photos = new ArrayList<>();
        for (URI address : addresses) {
            photos.add(download(page, id, photos.size() + 1, address, timeout));
        }
        return photos;
    }

    static List<URI> photoAddresses(VintedListingId id, List<String> scripts) {
        return ListingEditForm.unexpectedDataIsUnreadable(id, () -> {
            FlightPayload payload = FlightPayload.of(scripts);
            Set<JsonNode> galleries = payload.objects(node -> isGallery(node)
                    && ListingEditForm.isNumber(node.path("data").path("item_id"), id.value()));
            Set<JsonNode> photoLists = new LinkedHashSet<>();
            galleries.forEach(gallery -> photoLists.add(gallery.path("data").path("photos")));
            if (photoLists.size() != 1) {
                throw new ListingUnreadable(photoLists.isEmpty()
                        ? "no photo gallery on " + id.value() : photoLists.size() + " photo galleries on " + id.value());
            }
            JsonNode photos = photoLists.iterator().next();
            if (!photos.isArray()) {
                throw new IllegalArgumentException("photos are not a list");
            }
            if (photos.isEmpty()) {
                throw new ListingUnreadable("no photo on " + id.value());
            }
            return photos.valueStream().map(photo -> fullSize(payload, photo)).toList();
        });
    }

    private static boolean isGallery(JsonNode node) {
        return node.path("name").isString() && "gallery".equals(node.path("name").stringValue());
    }

    private static URI fullSize(FlightPayload payload, JsonNode photo) {
        JsonNode address = photo.path("full_size_url");
        if (!address.isString()) {
            throw new IllegalArgumentException("photo without full size address");
        }
        URI uri = URI.create(payload.text(address.stringValue()));
        if (!uri.isAbsolute() || !WEB.contains(uri.getScheme())) {
            throw new IllegalArgumentException("photo address not on the web: " + uri);
        }
        return uri;
    }

    /**
     * Vinted adds the title to the address of a listing ({@code /items/<id>-<title>}), and sends a visitor elsewhere,
     * its login page for one, when it does not show the listing.
     */
    private static void requireOnTheListing(Page page, URI pageUrl) {
        String path = URI.create(page.url()).getPath();
        String listing = pageUrl.getPath().replaceFirst("/$", "");
        if (path == null || !Pattern.matches(Pattern.quote(listing) + "(-[^/]*)?/?", path)) {
            throw new SessionDisconnected("redirected to " + page.url());
        }
    }

    @SuppressWarnings("unchecked")
    private static DownloadedPhoto download(Page page, VintedListingId id, int number, URI address, Duration timeout) {
        Map<String, Object> answer = (Map<String, Object>) page.evaluate(DOWNLOAD,
                List.of(address.toString(), Math.toIntExact(timeout.toMillis())));
        String photo = "photo " + number + " of " + id.value();
        if (answer.containsKey("error")) {
            throw new ListingUnreadable(photo + " not downloaded: " + answer.get("error"));
        }
        int status = ((Number) answer.get("status")).intValue();
        if (status != 200) {
            throw new ListingUnreadable(photo + " answered HTTP " + status);
        }
        try {
            return new DownloadedPhoto(Base64.getDecoder().decode((String) answer.get("base64")),
                    (String) answer.get("type"));
        } catch (ListingUnreadable notAnImage) {
            throw new ListingUnreadable(photo + ": " + notAnImage.getMessage(), notAnImage);
        }
    }
}
