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
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The edit form of a listing, only ever read: nothing is clicked, typed or submitted. Its identifiers come from the
 * data the page streams in its scripts, steadier than its layout; its labels, for the member, from the form.
 * Anything missing or unexpected makes the listing unreadable, rather than being guessed.
 */
final class ListingEditForm {

    // Next.js streams the page data in chunks: decoded and put end to end, they are rows "<key>:<JSON>".
    private static final String CHUNK_PREFIX = "self.__next_f.push([1,";
    private static final String CHUNK_SUFFIX = "])";
    private static final String TITLE_FIELD = "input[name='title']";
    // A field with nothing displayed shows a hint starting so, as "Sélectionne une taille" (observed 2026-11-07).
    private static final String PLACEHOLDER = "Sélectionne";
    private static final Set<String> KNOWN_ATTRIBUTES = Set.of("condition", "size", "material");
    private static final JsonMapper JSON = JsonMapper.builder().build();

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
        ListingData data = dataOf(id, scripts);
        try {
            return content(id, data, labels);
        } catch (JacksonException | IllegalArgumentException unexpected) {
            throw new ListingUnreadable("unexpected data on " + id.value(), unexpected);
        }
    }

    static Set<String> awaitedLabels(VintedListingId id, List<String> scripts) {
        JsonNode item = dataOf(id, scripts).item();
        Set<String> awaited = new LinkedHashSet<>(List.of("condition", "packageSize"));
        Map<String, List<Long>> attributes = attributes(id, item);
        if (attributes.containsKey("size")) {
            awaited.add("size");
        }
        if (attributes.containsKey("material")) {
            awaited.add("material");
        }
        if (!ids(item.path("colorIds")).isEmpty()) {
            awaited.add("color");
        }
        return awaited;
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

    private record ListingData(JsonNode item, String categoryTitle) {
    }

    private static ListingData dataOf(VintedListingId id, List<String> scripts) {
        List<JsonNode> rows = rows(scripts);
        Set<JsonNode> items = objects(rows, node -> node.has("catalogId") && isNumber(node.path("id"), id.value()));
        if (items.size() != 1) {
            throw new ListingUnreadable((items.isEmpty() ? "no" : items.size()) + " listing data on " + id.value());
        }
        JsonNode item = items.iterator().next();
        long catalogId = item.path("catalogId").asLong();
        Set<String> titles = objects(rows, node -> node.has("catalogs") && isNumber(node.path("id"), catalogId)
                && node.path("title").isString()).stream().map(node -> node.path("title").stringValue())
                .collect(Collectors.toSet());
        if (titles.size() != 1) {
            throw new ListingUnreadable("no category " + catalogId + " on " + id.value());
        }
        return new ListingData(item, titles.iterator().next());
    }

    /** A row that is not JSON, such as a text row spread over several lines, holds no listing data. */
    private static List<JsonNode> rows(List<String> scripts) {
        String payload = scripts.stream().map(String::strip)
                .filter(script -> script.startsWith(CHUNK_PREFIX) && script.endsWith(CHUNK_SUFFIX))
                .map(script -> JSON.readValue(script.substring(CHUNK_PREFIX.length(),
                        script.length() - CHUNK_SUFFIX.length()), String.class))
                .collect(Collectors.joining());
        List<JsonNode> rows = new ArrayList<>();
        for (String row : payload.split("\n")) {
            int separator = row.indexOf(':');
            if (separator > 0) {
                parsed(row.substring(separator + 1)).ifPresent(rows::add);
            }
        }
        return rows;
    }

    private static Optional<JsonNode> parsed(String json) {
        try {
            return Optional.of(JSON.readTree(json));
        } catch (JacksonException notJson) {
            return Optional.empty();
        }
    }

    private static Set<JsonNode> objects(List<JsonNode> rows, Predicate<JsonNode> wanted) {
        Set<JsonNode> found = new LinkedHashSet<>();
        rows.forEach(row -> collect(row, wanted, found));
        return found;
    }

    private static void collect(JsonNode node, Predicate<JsonNode> wanted, Set<JsonNode> found) {
        if (node.isObject() && wanted.test(node)) {
            found.add(node);
        }
        if (node.isContainer()) {
            node.valueStream().forEach(child -> collect(child, wanted, found));
        }
    }

    private static boolean isNumber(JsonNode node, long value) {
        return node.isIntegralNumber() && node.longValue() == value;
    }

    private static ListingContent content(VintedListingId id, ListingData data, DisplayedLabels labels) {
        JsonNode item = data.item();
        Map<String, List<Long>> attributes = attributes(id, item);
        List<Long> colors = ids(item.path("colorIds"));
        return new ListingContent(id,
                text(item, "title"),
                optionalText(item, "description").orElse(""),
                new Price(decimal(item, "price"), text(item, "currency")),
                new FormOption(number(item, "catalogId"), data.categoryTitle()),
                brand(item),
                new FormOption(single(attributes, "condition")
                        .orElseThrow(() -> new ListingUnreadable("no condition on " + id.value())),
                        label(labels.condition())),
                single(attributes, "size").map(size -> new FormOption(size, label(labels.size()))),
                colors.isEmpty() ? Optional.empty() : Optional.of(new FormOptions(colors, label(labels.color()))),
                Optional.ofNullable(attributes.get("material"))
                        .map(materials -> new FormOptions(materials, label(labels.material()))),
                new FormOption(number(item, "packageSizeId"), label(labels.packageSize())),
                new CategoryDetails(optionalText(item, "isbn"), optionalText(item, "bookTitle"),
                        optionalText(item, "author"), optionalText(item, "manufacturer"),
                        optionalText(item, "manufacturerLabel"), optionalText(item, "model"),
                        optionalDecimal(item, "measurementWidth"), optionalDecimal(item, "measurementLength"),
                        optionalText(item, "measurementUnit")));
    }

    /** Without a column for it, an attribute would be lost when republishing: it makes the reading incomplete. */
    private static Map<String, List<Long>> attributes(VintedListingId id, JsonNode item) {
        Map<String, List<Long>> attributes = new HashMap<>();
        for (JsonNode attribute : item.path("itemAttributes").valueStream().toList()) {
            String code = text(attribute, "code");
            if (!KNOWN_ATTRIBUTES.contains(code)) {
                throw new ListingUnreadable("unknown attribute code " + code + " on " + id.value());
            }
            attributes.put(code, ids(attribute.required("ids")));
        }
        return attributes;
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

    private static Optional<FormOption> brand(JsonNode item) {
        JsonNode brandId = item.required("brandId");
        if (brandId.isNull()) {
            return Optional.empty();
        }
        return Optional.of(new FormOption(number(item, "brandId"),
                optionalText(item.path("brand"), "title").orElse("")));
    }

    private static List<Long> ids(JsonNode ids) {
        if (ids.isMissingNode() || ids.isNull()) {
            return List.of();
        }
        if (!ids.isArray()) {
            throw new IllegalArgumentException("identifiers are not a list");
        }
        return ids.valueStream().map(ListingEditForm::number).toList();
    }

    private static String label(String displayed) {
        return displayed == null || displayed.startsWith(PLACEHOLDER) ? "" : displayed.strip();
    }

    private static String text(JsonNode parent, String field) {
        return optionalText(parent, field).orElseThrow(() -> new IllegalArgumentException(field + " is missing"));
    }

    private static Optional<String> optionalText(JsonNode parent, String field) {
        JsonNode value = parent.required(field);
        if (value.isNull()) {
            return Optional.empty();
        }
        if (!value.isString()) {
            throw new IllegalArgumentException(field + " is not a text");
        }
        return Optional.of(value.stringValue());
    }

    private static long number(JsonNode parent, String field) {
        return number(parent.required(field));
    }

    private static long number(JsonNode value) {
        if (!value.isIntegralNumber()) {
            throw new IllegalArgumentException(value + " is not an identifier");
        }
        return value.longValue();
    }

    private static BigDecimal decimal(JsonNode parent, String field) {
        return optionalDecimal(parent, field).orElseThrow(() -> new IllegalArgumentException(field + " is missing"));
    }

    private static Optional<BigDecimal> optionalDecimal(JsonNode parent, String field) {
        JsonNode value = parent.required(field);
        if (value.isNull()) {
            return Optional.empty();
        }
        if (!value.isNumber()) {
            throw new IllegalArgumentException(field + " is not a number");
        }
        return Optional.of(value.decimalValue());
    }
}
