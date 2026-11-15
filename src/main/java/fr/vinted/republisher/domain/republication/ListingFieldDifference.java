package fr.vinted.republisher.domain.republication;

import fr.vinted.republisher.domain.backup.CategoryDetails;
import fr.vinted.republisher.domain.backup.FormOption;
import fr.vinted.republisher.domain.backup.FormOptions;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.wardrobe.Price;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A field of the listing form whose value on the new listing is not the saved one. Options are compared by their
 * Vinted identifier, never their label; photos by their number only, as Vinted re-encodes the photos it publishes.
 */
public record ListingFieldDifference(ListingFormField field, String saved, String read) {

    public ListingFieldDifference {
        Stream.of(field, saved, read).forEach(Objects::requireNonNull);
    }

    /** {@code savedPhotos} is the number of photos kept; empty when the new listing is the backup again. */
    public static List<ListingFieldDifference> between(ListingContent saved, int savedPhotos, ListingReading read) {
        ListingContent found = read.content();
        CategoryDetails savedDetails = saved.details();
        CategoryDetails readDetails = found.details();
        Comparison comparison = new Comparison();
        comparison.add(ListingFormField.PHOTOS, String.valueOf(savedPhotos), String.valueOf(read.photos().size()));
        comparison.add(ListingFormField.TITLE, saved.title(), found.title());
        comparison.add(ListingFormField.DESCRIPTION, saved.description(), found.description());
        comparison.add(ListingFormField.PRICE, price(saved.price()), price(found.price()));
        comparison.option(ListingFormField.CATEGORY, Optional.of(saved.category()), Optional.of(found.category()));
        comparison.option(ListingFormField.BRAND, saved.brand(), found.brand());
        comparison.option(ListingFormField.CONDITION, Optional.of(saved.condition()), Optional.of(found.condition()));
        comparison.option(ListingFormField.SIZE, saved.size(), found.size());
        comparison.options(ListingFormField.COLORS, saved.colors(), found.colors());
        comparison.options(ListingFormField.MATERIALS, saved.materials(), found.materials());
        comparison.option(ListingFormField.PACKAGE_SIZE, Optional.of(saved.packageSize()),
                Optional.of(found.packageSize()));
        comparison.add(ListingFormField.ISBN, text(savedDetails.isbn()), text(readDetails.isbn()));
        comparison.add(ListingFormField.BOOK_TITLE, text(savedDetails.bookTitle()), text(readDetails.bookTitle()));
        comparison.add(ListingFormField.AUTHOR, text(savedDetails.author()), text(readDetails.author()));
        comparison.add(ListingFormField.MANUFACTURER, text(savedDetails.manufacturer()),
                text(readDetails.manufacturer()));
        comparison.add(ListingFormField.MODEL, text(savedDetails.model()), text(readDetails.model()));
        comparison.add(ListingFormField.MEASUREMENTS, measurements(savedDetails), measurements(readDetails));
        return List.copyOf(comparison.differences);
    }

    private static String price(Price price) {
        return price.amount().toPlainString() + " " + price.currency();
    }

    private static String text(Optional<String> value) {
        return value.orElse("");
    }

    /** Numbers stripped of trailing zeros: 20 and 20.0 are the same measurement. */
    private static String measurements(CategoryDetails details) {
        Function<Optional<BigDecimal>, String> number =
                value -> value.map(amount -> amount.stripTrailingZeros().toPlainString()).orElse("");
        String width = number.apply(details.measurementWidth());
        String length = number.apply(details.measurementLength());
        return width.isEmpty() && length.isEmpty() ? ""
                : width + " x " + length + " " + text(details.measurementUnit());
    }

    private static final class Comparison {

        private final List<ListingFieldDifference> differences = new ArrayList<>();

        private void add(ListingFormField field, String saved, String read) {
            if (!saved.equals(read)) {
                differences.add(new ListingFieldDifference(field, saved, read));
            }
        }

        private void option(ListingFormField field, Optional<FormOption> saved, Optional<FormOption> read) {
            if (!saved.map(FormOption::id).equals(read.map(FormOption::id))) {
                differences.add(new ListingFieldDifference(field, saved.map(Comparison::shown).orElse(""),
                        read.map(Comparison::shown).orElse("")));
            }
        }

        private void options(ListingFormField field, Optional<FormOptions> saved, Optional<FormOptions> read) {
            if (!saved.map(Comparison::sorted).equals(read.map(Comparison::sorted))) {
                differences.add(new ListingFieldDifference(field, saved.map(Comparison::shown).orElse(""),
                        read.map(Comparison::shown).orElse("")));
            }
        }

        private static String shown(FormOption option) {
            return option.label().isEmpty() ? String.valueOf(option.id())
                    : option.label() + " (" + option.id() + ")";
        }

        private static String shown(FormOptions options) {
            String ids = sorted(options).stream().map(String::valueOf).collect(Collectors.joining(", "));
            return options.label().isEmpty() ? ids : options.label() + " (" + ids + ")";
        }

        private static List<Long> sorted(FormOptions options) {
            return options.ids().stream().sorted().toList();
        }
    }
}
