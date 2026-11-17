package fr.vinted.republisher.domain.republication;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The link of a new listing, as the member copied it from Vinted: only a listing page of the Vinted site is accepted,
 * and its address is rebuilt on that site, without query nor fragment, since Chrome may later be sent there.
 */
public record NewListingLink(VintedListingId id, URI url) {

    // As the wardrobe recognises the link of a listing: its page, and none below it.
    private static final Pattern LISTING_PATH = Pattern.compile("/items/([1-9]\\d{0,17})(-[\\w%.~-]*)?/?");

    /**
     * {@code site}: the Vinted site, whose scheme and host the link must have. Throws {@link NewListingLinkInvalid}
     * without a link, or with one that is not the page of a listing of that site.
     */
    public static NewListingLink parse(Optional<String> link, URI site) {
        URI uri = link.map(String::strip).flatMap(NewListingLink::uri).orElseThrow(NewListingLinkInvalid::new);
        if (!sameSite(uri, site) || uri.getRawUserInfo() != null || uri.getRawPath() == null) {
            throw new NewListingLinkInvalid();
        }
        Matcher listing = LISTING_PATH.matcher(uri.getRawPath());
        if (!listing.matches()) {
            throw new NewListingLinkInvalid();
        }
        return new NewListingLink(new VintedListingId(Long.parseLong(listing.group(1))),
                URI.create(site.getScheme() + "://" + site.getRawAuthority() + uri.getRawPath()));
    }

    private static boolean sameSite(URI uri, URI site) {
        return uri.getScheme() != null && uri.getHost() != null
                && uri.getScheme().toLowerCase(Locale.ROOT).equals(site.getScheme().toLowerCase(Locale.ROOT))
                && uri.getHost().toLowerCase(Locale.ROOT).equals(site.getHost().toLowerCase(Locale.ROOT))
                && uri.getPort() == site.getPort();
    }

    private static Optional<URI> uri(String link) {
        try {
            return Optional.of(new URI(link));
        } catch (URISyntaxException notALink) {
            return Optional.empty();
        }
    }
}
