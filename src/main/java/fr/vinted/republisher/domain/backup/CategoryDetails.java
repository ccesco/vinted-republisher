package fr.vinted.republisher.domain.backup;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** Fields the form only asks for in some categories, such as books or electronics. */
public record CategoryDetails(
        Optional<String> isbn,
        Optional<String> bookTitle,
        Optional<String> author,
        Optional<String> manufacturer,
        Optional<String> manufacturerLabel,
        Optional<String> model,
        Optional<BigDecimal> measurementWidth,
        Optional<BigDecimal> measurementLength,
        Optional<String> measurementUnit) {

    public CategoryDetails {
        Stream.of(isbn, bookTitle, author, manufacturer, manufacturerLabel, model, measurementWidth, measurementLength,
                measurementUnit).forEach(Objects::requireNonNull);
    }

    public static CategoryDetails none() {
        return new CategoryDetails(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
