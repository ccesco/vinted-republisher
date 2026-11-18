package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.domain.backup.ListingFieldDifference;
import fr.vinted.republisher.domain.backup.ListingFormField;
import fr.vinted.republisher.domain.backup.VerificationFailure;
import fr.vinted.republisher.domain.backup.VerificationFailure.FieldChanged;
import fr.vinted.republisher.domain.backup.VerificationFailure.ListingAbsent;
import fr.vinted.republisher.domain.backup.VerificationFailure.NoPhoto;
import fr.vinted.republisher.domain.backup.VerificationFailure.PhotoAltered;
import fr.vinted.republisher.domain.backup.VerificationFailure.PhotoMissing;
import fr.vinted.republisher.domain.backup.VerificationFailure.PriceChanged;
import fr.vinted.republisher.domain.backup.VerificationFailure.TitleChanged;
import fr.vinted.republisher.domain.wardrobe.Price;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

/**
 * The failures of a verification as the {@code failures} column keeps them: one object per failure, in order, its
 * kind named as in the API, amounts as written so that 12.50 stays 12.50.
 */
final class VerificationFailuresJson {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<Row>> ROWS = new TypeReference<>() {
    };

    /** A failure flattened; only the fields of its kind are set. */
    record Row(String kind, Integer rank, String field, String saved, String read, String savedAmount,
               String savedCurrency, String readAmount, String readCurrency) {

        static Row of(String kind) {
            return new Row(kind, null, null, null, null, null, null, null, null);
        }

        static Row ofRank(String kind, int rank) {
            return new Row(kind, rank, null, null, null, null, null, null, null);
        }

        static Row ofTexts(String kind, String field, String saved, String read) {
            return new Row(kind, null, field, saved, read, null, null, null, null);
        }
    }

    private VerificationFailuresJson() {
    }

    static String write(List<VerificationFailure> failures) {
        return JSON.writeValueAsString(failures.stream().map(VerificationFailuresJson::rowOf).toList());
    }

    static List<VerificationFailure> read(String json) {
        return JSON.readValue(json, ROWS).stream().map(VerificationFailuresJson::failureOf).toList();
    }

    private static Row rowOf(VerificationFailure failure) {
        return switch (failure) {
            case NoPhoto _ -> Row.of("NO_PHOTO");
            case PhotoMissing missing -> Row.ofRank("PHOTO_MISSING", missing.rank());
            case PhotoAltered altered -> Row.ofRank("PHOTO_ALTERED", altered.rank());
            case ListingAbsent _ -> Row.of("LISTING_ABSENT");
            case TitleChanged changed -> Row.ofTexts("TITLE_CHANGED", null, changed.saved(), changed.scanned());
            case PriceChanged changed -> new Row("PRICE_CHANGED", null, null, null, null,
                    changed.saved().amount().toPlainString(), changed.saved().currency(),
                    changed.scanned().amount().toPlainString(), changed.scanned().currency());
            case FieldChanged changed -> Row.ofTexts("FIELD_CHANGED", changed.difference().field().name(),
                    changed.difference().saved(), changed.difference().read());
        };
    }

    private static VerificationFailure failureOf(Row row) {
        return switch (row.kind()) {
            case "NO_PHOTO" -> new NoPhoto();
            case "PHOTO_MISSING" -> new PhotoMissing(row.rank());
            case "PHOTO_ALTERED" -> new PhotoAltered(row.rank());
            case "LISTING_ABSENT" -> new ListingAbsent();
            case "TITLE_CHANGED" -> new TitleChanged(row.saved(), row.read());
            case "PRICE_CHANGED" -> new PriceChanged(new Price(new BigDecimal(row.savedAmount()), row.savedCurrency()),
                    new Price(new BigDecimal(row.readAmount()), row.readCurrency()));
            case "FIELD_CHANGED" -> new FieldChanged(
                    new ListingFieldDifference(ListingFormField.valueOf(row.field()), row.saved(), row.read()));
            default -> throw new IllegalStateException("Unknown verification failure " + row.kind());
        };
    }
}
