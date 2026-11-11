package fr.vinted.republisher.adapter.out.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import fr.vinted.republisher.domain.backup.CategoryDetails;
import fr.vinted.republisher.domain.backup.FormOption;
import fr.vinted.republisher.domain.backup.FormOptions;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.backup.ListingUnreadable;
import fr.vinted.republisher.domain.session.SessionDisconnected;
import fr.vinted.republisher.domain.wardrobe.Price;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The edit form of a listing, only ever read: nothing is clicked, typed or submitted. Its identifiers come from the
 * data the page streams in its scripts ({@link FlightPayload}), steadier than its layout; its labels, for the
 * member, from the form. Anything missing or unexpected makes the listing unreadable, rather than being guessed.
 */
final class ListingEditForm {

    private static final String TITLE_FIELD = "input[name='title']";
    // A field with nothing displayed shows a hint starting so, as "Sélectionne une taille" (observed 2026-11-07).
    private static final String PLACEHOLDER = "Sélectionne";
    private static final Set<String> KNOWN_ATTRIBUTES = Set.of("condition", "size", "material");

    // The badge "Recommandé" sits inside the title of a package size: it is not part of its label.
    private static final String LABELS = """
            () => {
              const text = id => {
                const element = document.querySelector(`[data-testid="${id}"]`);
                return element ? element.innerText.trim() : null;
              };
              const checked = document.querySelector('input[data-testid^="package_type_selector_"]:checked');
              let packageSize = null;
              if (checked) {
                const size = checked.dataset.testid.replace(/^package_type_selector_(\\d+)--input$/, '$1');
                const title = document.querySelector(`[data-testid="${size}-package-size--cell--title"]`);
                if (title) {
                  const copy = title.cloneNode(true);
                  copy.querySelectorAll('[data-testid^="package-size-suggestion-badge"]').forEach(badge => badge.remove());
                  packageSize = copy.textContent.trim();
                }
              }
              return {
                condition: text('category-condition-single-list-input--value'),
                size: text('category-size-single-grid_chips-input--value'),
                color: text('color-select-dropdown-input--value'),
                material: text('category-material-multi-list-input--value'),
                packageSize
              };
            }""";
    private static final String LABELS_SHOWN = "([awaited, placeholder]) => { const labels = (" + LABELS + ")();"
            + " return awaited.every(field => labels[field] && !labels[field].startsWith(placeholder)); }";

    /** What the form displays for each choice, null where it displays nothing. */
    record DisplayedLabels(String condition, String size, String color, String material, String packageSize) {
    }

    private ListingEditForm() {
    }

    /** The form may take a while to display its labels: one still missing after the timeout is saved empty. */
    static ListingContent read(Page page, VintedListingId id, URI formUrl, Duration timeout) {
        ChromeTabs.open(page, formUrl);
        awaitForm(page, id, formUrl, timeout);
        List<String> scripts = page.locator("script").allTextContents();
        awaitLabels(page, awaitedLabels(id, scripts), timeout);
        return parse(id, scripts, displayedLabels(page));
    }

    static ListingContent parse(VintedListingId id, List<String> scripts, DisplayedLabels labels) {
        return unexpectedDataIsUnreadable(id, () -> content(id, itemOf(id, FlightPayload.of(scripts)), labels));
    }

    static Set<String> awaitedLabels(VintedListingId id, List<String> scripts) {
        return unexpectedDataIsUnreadable(id, () -> {
            Item item = itemOf(id, FlightPayload.of(scripts));
            Set<String> awaited = new LinkedHashSet<>(List.of("condition", "packageSize"));
            Map<String, List<Long>> attributes = item.attributes(id);
            if (attributes.containsKey("size")) {
                awaited.add("size");
            }
            if (attributes.containsKey("material")) {
                awaited.add("material");
            }
            if (!item.ids("colorIds").isEmpty()) {
                awaited.add("color");
            }
            return awaited;
        });
    }

    static <T> T unexpectedDataIsUnreadable(VintedListingId id, Supplier<T> reading) {
        try {
            return reading.get();
        } catch (JacksonException | IllegalArgumentException unexpected) {
            throw new ListingUnreadable("unexpected data on " + id.value(), unexpected);
        }
    }

    /** Vinted sends a visitor to its login page, sometimes by script once the page is loaded. */
    private static void awaitForm(Page page, VintedListingId id, URI formUrl, Duration timeout) {
        try {
            page.locator(TITLE_FIELD).waitFor(new Locator.WaitForOptions().setTimeout(timeout.toMillis()));
        } catch (TimeoutError noForm) {
            requireStillOnTheForm(page, formUrl);
            throw new ListingUnreadable("no edit form on " + id.value(), noForm);
        }
        requireStillOnTheForm(page, formUrl);
    }

    private static void requireStillOnTheForm(Page page, URI formUrl) {
        String path = URI.create(page.url()).getPath();
        if (path == null || !withoutTrailingSlash(path).equals(withoutTrailingSlash(formUrl.getPath()))) {
            throw new SessionDisconnected("redirected to " + page.url());
        }
    }

    private static String withoutTrailingSlash(String path) {
        return path.replaceFirst("/$", "");
    }

    private static void awaitLabels(Page page, Set<String> awaited, Duration timeout) {
        try {
            page.waitForFunction(LABELS_SHOWN, List.of(List.copyOf(awaited), PLACEHOLDER),
                    new Page.WaitForFunctionOptions().setTimeout(timeout.toMillis()));
        } catch (TimeoutError notAllShown) {
            // Decided with the member: an identifier whose label the form never displays is saved with an empty one.
        }
    }

    @SuppressWarnings("unchecked")
    private static DisplayedLabels displayedLabels(Page page) {
        Map<String, String> labels = (Map<String, String>) page.evaluate(LABELS);
        return new DisplayedLabels(labels.get("condition"), labels.get("size"), labels.get("color"),
                labels.get("material"), labels.get("packageSize"));
    }

    private static Item itemOf(VintedListingId id, FlightPayload payload) {
        Set<JsonNode> items = objects(payload, node -> node.has("catalogId") && isNumber(node.path("id"), id.value()));
        if (items.size() != 1) {
            throw new ListingUnreadable((items.isEmpty() ? "no" : items.size()) + " listing data on " + id.value());
        }
        return new Item(items.iterator().next(), payload);
    }

    private static String categoryTitle(VintedListingId id, Item item) {
        long catalogId = item.number("catalogId");
        Set<String> titles = objects(item.payload(), node -> node.has("catalogs")
                && isNumber(node.path("id"), catalogId) && node.path("title").isString()).stream()
                .map(node -> new Item(node, item.payload()).text("title")).collect(Collectors.toSet());
        if (titles.size() != 1) {
            throw new ListingUnreadable("no category " + catalogId + " on " + id.value());
        }
        return titles.iterator().next();
    }

    private static Set<JsonNode> objects(FlightPayload payload, Predicate<JsonNode> wanted) {
        return payload.objects(wanted);
    }

    static boolean isNumber(JsonNode node, long value) {
        return node.isIntegralNumber() && node.longValue() == value;
    }

    private static ListingContent content(VintedListingId id, Item item, DisplayedLabels labels) {
        Map<String, List<Long>> attributes = item.attributes(id);
        List<Long> colors = item.ids("colorIds");
        return new ListingContent(id,
                item.text("title"),
                // The column holds no null: a listing without description has an empty one.
                item.optionalText("description").orElse(""),
                new Price(item.decimal("price"), item.text("currency")),
                new FormOption(item.number("catalogId"), categoryTitle(id, item)),
                item.brand(),
                new FormOption(single(attributes, "condition")
                        .orElseThrow(() -> new ListingUnreadable("no condition on " + id.value())),
                        label(labels.condition())),
                single(attributes, "size").map(size -> new FormOption(size, label(labels.size()))),
                colors.isEmpty() ? Optional.empty() : Optional.of(new FormOptions(colors, label(labels.color()))),
                Optional.ofNullable(attributes.get("material"))
                        .map(materials -> new FormOptions(materials, label(labels.material()))),
                new FormOption(item.number("packageSizeId"), label(labels.packageSize())),
                new CategoryDetails(item.optionalText("isbn"), item.optionalText("bookTitle"),
                        item.optionalText("author"), item.optionalText("manufacturer"),
                        item.optionalText("manufacturerLabel"), item.optionalText("model"),
                        item.optionalDecimal("measurementWidth"), item.optionalDecimal("measurementLength"),
                        item.optionalText("measurementUnit")));
    }

    private static Optional<Long> single(Map<String, List<Long>> attributes, String code) {
        List<Long> ids = attributes.get(code);
        if (ids == null) {
            return Optional.empty();
        }
        if (ids.size() != 1) {
            throw new IllegalArgumentException(code + " has " + ids.size() + " values");
        }
        return Optional.of(ids.getFirst());
    }

    private static String label(String displayed) {
        return displayed == null || displayed.startsWith(PLACEHOLDER) ? "" : displayed.strip();
    }

    /** An object of the page data, whose texts may be escaped or sent apart by React. */
    private record Item(JsonNode node, FlightPayload payload) {

        /** Without a column for it, an attribute would be lost when republishing: it makes the reading incomplete. */
        Map<String, List<Long>> attributes(VintedListingId id) {
            Map<String, List<Long>> attributes = new HashMap<>();
            for (JsonNode attribute : node.path("itemAttributes").valueStream().toList()) {
                Item read = new Item(attribute, payload);
                String code = read.text("code");
                if (!KNOWN_ATTRIBUTES.contains(code)) {
                    throw new ListingUnreadable("unknown attribute code " + code + " on " + id.value());
                }
                attributes.put(code, read.ids("ids"));
            }
            return attributes;
        }

        Optional<FormOption> brand() {
            if (node.required("brandId").isNull()) {
                return Optional.empty();
            }
            JsonNode brand = node.path("brand");
            String title = brand.isObject() ? new Item(brand, payload).optionalText("title").orElse("") : "";
            return Optional.of(new FormOption(number("brandId"), title));
        }

        List<Long> ids(String field) {
            JsonNode ids = node.path(field);
            if (ids.isMissingNode() || ids.isNull()) {
                return List.of();
            }
            if (!ids.isArray()) {
                throw new IllegalArgumentException(field + " is not a list");
            }
            return ids.valueStream().map(Item::identifier).toList();
        }

        String text(String field) {
            return optionalText(field).orElseThrow(() -> new IllegalArgumentException(field + " is missing"));
        }

        Optional<String> optionalText(String field) {
            JsonNode value = node.required(field);
            if (value.isNull()) {
                return Optional.empty();
            }
            if (!value.isString()) {
                throw new IllegalArgumentException(field + " is not a text");
            }
            return Optional.of(payload.text(value.stringValue()));
        }

        long number(String field) {
            return identifier(node.required(field));
        }

        BigDecimal decimal(String field) {
            return optionalDecimal(field).orElseThrow(() -> new IllegalArgumentException(field + " is missing"));
        }

        Optional<BigDecimal> optionalDecimal(String field) {
            JsonNode value = node.required(field);
            if (value.isNull()) {
                return Optional.empty();
            }
            if (!value.isNumber()) {
                throw new IllegalArgumentException(field + " is not a number");
            }
            return Optional.of(value.decimalValue());
        }

        private static long identifier(JsonNode value) {
            if (!value.isIntegralNumber()) {
                throw new IllegalArgumentException(value + " is not an identifier");
            }
            return value.longValue();
        }
    }
}
