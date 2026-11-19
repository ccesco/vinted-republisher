package fr.vinted.republisher.adapter.out.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import fr.vinted.republisher.domain.backup.CategoryDetails;
import fr.vinted.republisher.domain.backup.DownloadedPhoto;
import fr.vinted.republisher.domain.backup.FormOption;
import fr.vinted.republisher.domain.backup.FormOptions;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.backup.ListingFormField;
import fr.vinted.republisher.domain.republication.FieldNotFillable;
import fr.vinted.republisher.domain.republication.ListingFormUnreadable;
import fr.vinted.republisher.domain.republication.ListingPublisher;
import fr.vinted.republisher.domain.republication.RepublicationVisit;
import fr.vinted.republisher.domain.republication.UnfilledField;
import fr.vinted.republisher.domain.session.SessionDisconnected;
import fr.vinted.republisher.domain.wardrobe.Price;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Puts a listing on sale through Vinted's listing form, as the member does, as observed with the member on
 * 2026-11-15 (AT-12-18): every option is chosen by its Vinted identifier, the saved values win over the ones Vinted
 * suggests, and nothing but the fields and the publish button is clicked (never the draft, never the paid boost).
 */
public class PlaywrightListingPublisher implements ListingPublisher {

    private static final String PHOTOS = "input[data-testid='add-photos-input']";
    private static final String PUBLISH_BUTTON = "[data-testid='upload-form-save-button']";
    /** Observed on 2026-11-15 while a photo was sent; gone once its thumbnail showed. */
    private static final String UPLOAD_PROGRESS = "[data-testid='media-upload'] [role='progressbar'], "
            + "[data-testid='media-upload'] [data-testid='progressbar'], [data-testid='upload-button-loader']";
    /** Where Vinted tells why it keeps the form: alerts and the validation message under a field. */
    private static final String REFUSAL = "[role='alert'], [data-testid$='--validation'], [data-testid$='-error']";
    private static final String CHECKED_COLORS = "[role='checkbox'][data-testid^='filter-grid-option-'][aria-checked='true']";
    private static final String CHECKED_MATERIALS = "[role='checkbox'][data-testid^='material-'][aria-checked='true']";
    /** How much of Vinted's answer to the submission is told when it keeps the form. */
    private static final int ANSWER_EXCERPT = 500;

    private final URI listingFormUrl;
    private final Duration controlTimeout;
    private final Duration photoTimeout;
    private final Duration publicationTimeout;
    private final ChromeTabs tabs;

    /**
     * {@code controlTimeout}: how long a field or an option may take to appear; {@code photoTimeout}: how long the
     * photos may take to be sent; {@code publicationTimeout}: how long Vinted may take to leave the form once
     * published.
     */
    public PlaywrightListingPublisher(URI listingFormUrl, Duration controlTimeout, Duration photoTimeout,
                                      Duration publicationTimeout, ChromeTabs tabs) {
        this.listingFormUrl = listingFormUrl;
        this.controlTimeout = controlTimeout;
        this.photoTimeout = photoTimeout;
        this.publicationTimeout = publicationTimeout;
        this.tabs = tabs;
    }

    @Override
    public void publish(ListingContent content, List<DownloadedPhoto> photos, RepublicationVisit visit) {
        unobservedDetails(content.details()).stream().findFirst().ifPresent(unobserved -> {
            throw new FieldNotFillable(unobserved.field(), unobserved.optionId());
        });
        tabs.workInNewTab(page -> {
            // The member watches the form being filled, as in the deletion.
            page.bringToFront();
            openForm(page);
            List<Filled> filled = fill(page, content, photos, Filling.refusing());
            requireStillFilled(page, filled, content.packageSize().id());
            Locator publish = page.locator(PUBLISH_BUTTON);
            if (publish.count() != 1) {
                throw new ListingFormUnreadable(publish.count() + " publish buttons where one was expected");
            }
            if (!visit.readyToPublish()) {
                return;
            }
            List<Response> answers = new CopyOnWriteArrayList<>();
            List<Request> failures = new CopyOnWriteArrayList<>();
            Consumer<Response> answered = response -> {
                if (sentByTheForm(response.request())) {
                    answers.add(response);
                }
            };
            Consumer<Request> failed = request -> {
                if (sentByTheForm(request)) {
                    failures.add(request);
                }
            };
            page.onResponse(answered);
            page.onRequestFailed(failed);
            publish.click();
            awaitLeaving(page, answers, failures);
        });
    }

    /**
     * Fills as {@link #publish} does, in a tab left to the member: what Vinted later suggests over a filled value is
     * for the member to see, as the publish button is for the member to click. The details never observed come last:
     * where the form shows them is unknown.
     */
    @Override
    public List<UnfilledField> prepare(ListingContent content, List<DownloadedPhoto> photos) {
        Filling filling = Filling.leavingToTheMember();
        tabs.workInTabLeftOpen(page -> {
            page.bringToFront();
            openForm(page);
            fill(page, content, photos, filling);
        });
        return Stream.concat(filling.unfilled().stream(), unobservedDetails(content.details()).stream()).toList();
    }

    private void openForm(Page page) {
        ChromeTabs.open(page, listingFormUrl);
        if (!URI.create(page.url()).getPath().equals(listingFormUrl.getPath())) {
            throw new SessionDisconnected("redirected to " + page.url());
        }
        // Like the delete button, the form only reacts once its scripts have run, by the load event.
        page.waitForLoadState(LoadState.LOAD);
    }

    /** What becomes of a field that cannot be filled: a republication is refused, a manual one leaves it empty. */
    private static final class Filling {

        private final boolean leftToTheMember;
        private final List<UnfilledField> unfilled = new ArrayList<>();

        private Filling(boolean leftToTheMember) {
            this.leftToTheMember = leftToTheMember;
        }

        static Filling refusing() {
            return new Filling(false);
        }

        static Filling leavingToTheMember() {
            return new Filling(true);
        }

        /**
         * A list or a search left open by the field would hide the next ones: Escape closes it. A field is told once,
         * though both the wait for the category's fields and the condition itself may miss it. Colours and materials
         * may stay partly ticked: the member sees them in the form.
         */
        void field(Page page, Runnable filling) {
            try {
                filling.run();
            } catch (FieldNotFillable notFillable) {
                if (!leftToTheMember) {
                    throw notFillable;
                }
                if (!unfilled.contains(notFillable.unfilled())) {
                    unfilled.add(notFillable.unfilled());
                }
                page.keyboard().press("Escape");
            }
        }

        List<UnfilledField> unfilled() {
            return List.copyOf(unfilled);
        }
    }

    /** What a field shows once filled: Vinted may still change it with a late suggestion. */
    private record Filled(ListingFormField field, Optional<Long> optionId, String input, String value) {

        static Filled read(Page page, ListingFormField field, Optional<Long> optionId, String input) {
            return new Filled(field, optionId, input, page.locator(input).inputValue());
        }
    }

    private List<Filled> fill(Page page, ListingContent content, List<DownloadedPhoto> photos, Filling filling) {
        visible(page.locator("#title"), ListingFormField.TITLE, Optional.empty());
        filling.field(page, () -> sendPhotos(page, photos));
        List<Filled> filled = new ArrayList<>();
        page.locator("#title").fill(content.title());
        filled.add(Filled.read(page, ListingFormField.TITLE, Optional.empty(), "#title"));
        page.locator("#description").fill(content.description());
        filled.add(Filled.read(page, ListingFormField.DESCRIPTION, Optional.empty(), "#description"));
        filling.field(page, () -> {
            search(page, ListingFormField.CATEGORY, "#category", "#catalog-search-input", content.category(),
                    "catalog-search-" + content.category().id() + "-result");
            filled.add(Filled.read(page, ListingFormField.CATEGORY, Optional.of(content.category().id()), "#category"));
        });
        filling.field(page, () -> awaitCategoryFields(page, content.condition()));
        filling.field(page, () -> chooseBrand(page, content.brand()).ifPresent(filled::add));
        content.size().ifPresent(size -> filling.field(page, () -> {
            choose(page, ListingFormField.SIZE, "#size",
                    page.locator("[data-testid^='size-group-'][data-testid$='-grid-option-" + size.id() + "']"),
                    size.id());
            filled.add(Filled.read(page, ListingFormField.SIZE, Optional.of(size.id()), "#size"));
        }));
        long condition = content.condition().id();
        filling.field(page, () -> {
            choose(page, ListingFormField.CONDITION, "#condition", page.locator(byKey("condition-" + condition)),
                    condition);
            filled.add(Filled.read(page, ListingFormField.CONDITION, Optional.of(condition), "#condition"));
        });
        filling.field(page, () -> tick(page, ListingFormField.COLORS, "#color", CHECKED_COLORS, content.colors(),
                id -> byKey("filter-grid-option-" + id)).ifPresent(filled::add));
        filling.field(page, () -> tick(page, ListingFormField.MATERIALS, "#material", CHECKED_MATERIALS,
                content.materials(), id -> byKey("material-" + id) + ", " + byKey("material-suggestion-" + id))
                .ifPresent(filled::add));
        Locator price = page.locator("#price");
        price.fill(asTyped(content.price()));
        price.press("Tab");
        filled.add(Filled.read(page, ListingFormField.PRICE, Optional.empty(), "#price"));
        long packageSize = content.packageSize().id();
        filling.field(page, () -> visible(page.locator(byKey("package-size-" + packageSize)),
                ListingFormField.PACKAGE_SIZE, Optional.of(packageSize)).click());
        return filled;
    }

    /** The fields beyond the title and the price only show once a category is chosen; the condition always does. */
    private void awaitCategoryFields(Page page, FormOption condition) {
        visible(page.locator("#condition"), ListingFormField.CONDITION, Optional.of(condition.id()));
    }

    /**
     * Right before publishing, every field still shows what was filled in: a suggestion Vinted made afterwards would
     * otherwise be published instead of the saved value.
     */
    private static void requireStillFilled(Page page, List<Filled> filled, long packageSize) {
        filled.stream().filter(field -> !page.locator(field.input()).inputValue().equals(field.value())).findFirst()
                .ifPresent(changed -> {
                    throw new FieldNotFillable(changed.field(), changed.optionId());
                });
        if (!page.locator("[data-testid='package_type_selector_" + packageSize + "--input']").isChecked()) {
            throw new FieldNotFillable(ListingFormField.PACKAGE_SIZE, Optional.of(packageSize));
        }
    }

    /** Sent at once, in their saved order: the first one becomes the cover. */
    private void sendPhotos(Page page, List<DownloadedPhoto> photos) {
        page.locator(PHOTOS).setInputFiles(IntStream.range(0, photos.size())
                .mapToObj(rank -> new FilePayload("photo-" + (rank + 1) + "." + photos.get(rank).extension(),
                        photos.get(rank).mediaType(), photos.get(rank).content()))
                .toArray(FilePayload[]::new));
        try {
            page.locator("[data-testid='media-select-photo-" + (photos.size() - 1) + "']")
                    .waitFor(waitingFor(WaitForSelectorState.VISIBLE, photoTimeout));
            // Vinted shows a progress bar while it receives the photos, and refuses to publish meanwhile.
            page.locator(UPLOAD_PROGRESS).first().waitFor(waitingFor(WaitForSelectorState.DETACHED, photoTimeout));
        } catch (TimeoutError notSent) {
            throw new FieldNotFillable(ListingFormField.PHOTOS, Optional.empty());
        }
    }

    /** No brand saved: Vinted's suggested brand gives way to « Sans marque », listed without searching. */
    private Optional<Filled> chooseBrand(Page page, Optional<FormOption> brand) {
        if (brand.isEmpty() && page.locator("#brand").count() == 0) {
            return Optional.empty();
        }
        Optional<Long> id = brand.map(FormOption::id);
        if (brand.isPresent()) {
            search(page, ListingFormField.BRAND, "#brand", "#brand-search-input", brand.get(),
                    "brand-" + brand.get().id());
        } else {
            visible(page.locator("#brand"), ListingFormField.BRAND, id).click();
            visible(page.locator(byKey("empty-brand")), ListingFormField.BRAND, id).click();
        }
        return Optional.of(Filled.read(page, ListingFormField.BRAND, id, "#brand"));
    }

    private void search(Page page, ListingFormField field, String opener, String searchInput, FormOption option,
                        String resultKey) {
        Optional<Long> id = Optional.of(option.id());
        visible(page.locator(opener), field, id).click();
        visible(page.locator(searchInput), field, id).fill(option.label());
        visible(page.locator(byKey(resultKey)), field, id).click();
    }

    private void choose(Page page, ListingFormField field, String opener, Locator option, long id) {
        visible(page.locator(opener), field, Optional.of(id)).click();
        visible(option, field, Optional.of(id)).click();
    }

    /** Unticks what Vinted suggested beyond the saved options before ticking them: it limits how many are ticked. */
    private Optional<Filled> tick(Page page, ListingFormField field, String opener, String checked,
                                  Optional<FormOptions> saved, LongFunction<String> option) {
        List<Long> wanted = saved.map(FormOptions::ids).orElse(List.of());
        if (page.locator(opener).count() == 0 && wanted.isEmpty()) {
            return Optional.empty();
        }
        Optional<Long> first = wanted.stream().findFirst();
        visible(page.locator(opener), field, first).click();
        for (long extra : checkedIds(page, checked)) {
            if (!wanted.contains(extra)) {
                visible(page.locator(option.apply(extra)), field, Optional.of(extra)).click();
            }
        }
        for (long id : wanted) {
            if (!checkedIds(page, checked).contains(id)) {
                visible(page.locator(option.apply(id)).first(), field, Optional.of(id)).click();
            }
        }
        Set<Long> ticked = checkedIds(page, checked);
        wanted.stream().filter(id -> !ticked.contains(id)).findFirst().ifPresent(missing -> {
            throw new FieldNotFillable(field, Optional.of(missing));
        });
        if (!ticked.equals(new TreeSet<>(wanted))) {
            throw new FieldNotFillable(field, first);
        }
        page.keyboard().press("Escape");
        return Optional.of(Filled.read(page, field, first, opener));
    }

    /** The identifier ends the test id of a ticked option. */
    private static Set<Long> checkedIds(Page page, String checked) {
        List<?> testIds = (List<?>) page.locator(checked).evaluateAll("options => options.map(o => o.dataset.testid)");
        return testIds.stream().map(String::valueOf)
                .map(testId -> Long.parseLong(testId.substring(testId.lastIndexOf('-') + 1)))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** Vinted puts an option's identifier in its id or in its test id, depending on the field. */
    private static String byKey(String key) {
        return "[id='" + key + "'], [data-testid='" + key + "']";
    }

    /** As the member types it: a decimal comma. */
    private static String asTyped(Price price) {
        return price.amount().toPlainString().replace('.', ',');
    }

    private Locator visible(Locator control, ListingFormField field, Optional<Long> optionId) {
        try {
            control.first().waitFor(waitingFor(WaitForSelectorState.VISIBLE, controlTimeout));
        } catch (TimeoutError notThere) {
            throw new FieldNotFillable(field, optionId);
        }
        return control.first();
    }

    /**
     * Vinted leaves the form once the listing is published; still on the form, it has refused it. The error then
     * tells what the form shows and how Vinted answered what the form sent, the only trace of a silent refusal.
     */
    private void awaitLeaving(Page page, List<Response> answers, List<Request> failures) {
        try {
            page.waitForURL(address -> !URI.create(address).getPath().equals(listingFormUrl.getPath()),
                    new Page.WaitForURLOptions().setTimeout(publicationTimeout.toMillis()));
        } catch (TimeoutError stillThere) {
            List<String> shown = page.locator(REFUSAL).allInnerTexts().stream().map(String::strip)
                    .filter(text -> !text.isEmpty()).toList();
            List<String> sent = new ArrayList<>(answers.stream().map(PlaywrightListingPublisher::described).toList());
            failures.forEach(request -> sent.add(describedFailure(request)));
            throw new ListingFormUnreadable("still on the listing form once published, showing " + shown
                    + ", Vinted answering " + sent);
        }
    }

    /**
     * What the form wrote to Vinted itself: third parties of the page (anti-bot, analytics) are left out, their
     * answers may carry session tokens.
     */
    private boolean sentByTheForm(Request request) {
        return !request.method().equals("GET") && request.url().startsWith(origin());
    }

    private String origin() {
        return listingFormUrl.getScheme() + "://" + listingFormUrl.getRawAuthority() + "/";
    }

    /** Method, path without its query (it may carry tokens), status and the start of a text or JSON body. */
    private static String described(Response response) {
        return request(response.request()) + " " + response.status() + " " + bodyExcerpt(response);
    }

    private static String describedFailure(Request request) {
        return request(request) + " failed: " + request.failure();
    }

    private static String request(Request request) {
        String url = request.url();
        int query = url.indexOf('?');
        String withoutQuery = query < 0 ? url : url.substring(0, query);
        int path = withoutQuery.indexOf('/', withoutQuery.indexOf("://") + 3);
        return request.method() + " " + (path < 0 ? "/" : withoutQuery.substring(path));
    }

    private static String bodyExcerpt(Response response) {
        String type = Optional.ofNullable(response.headerValue("content-type")).orElse("");
        if (!type.startsWith("text/") && !type.contains("json")) {
            return "(" + (type.isEmpty() ? "no body type" : type) + ")";
        }
        try {
            String body = response.text().strip();
            return body.substring(0, Math.min(body.length(), ANSWER_EXCERPT));
        } catch (PlaywrightException unreadable) {
            return "(body unreadable)";
        }
    }

    /**
     * The fields of books and objects were not shown during the observation: a listing needing them is not filled
     * rather than filled blindly, or they are left to the member.
     */
    private static List<UnfilledField> unobservedDetails(CategoryDetails details) {
        Map<ListingFormField, Optional<?>> unobserved = new LinkedHashMap<>();
        unobserved.put(ListingFormField.ISBN, details.isbn());
        unobserved.put(ListingFormField.BOOK_TITLE, details.bookTitle());
        unobserved.put(ListingFormField.AUTHOR, details.author());
        unobserved.put(ListingFormField.MANUFACTURER, details.manufacturer().or(details::manufacturerLabel));
        unobserved.put(ListingFormField.MODEL, details.model());
        unobserved.put(ListingFormField.MEASUREMENTS, details.measurementWidth().map(Object.class::cast)
                .or(details::measurementLength).or(details::measurementUnit));
        return unobserved.entrySet().stream().filter(entry -> entry.getValue().isPresent())
                .map(entry -> new UnfilledField(entry.getKey(), Optional.empty())).toList();
    }

    private static Locator.WaitForOptions waitingFor(WaitForSelectorState state, Duration timeout) {
        return new Locator.WaitForOptions().setState(state).setTimeout(timeout.toMillis());
    }
}
