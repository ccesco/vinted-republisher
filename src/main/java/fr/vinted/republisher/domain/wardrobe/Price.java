package fr.vinted.republisher.domain.wardrobe;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.regex.Pattern;

/** Always two decimals, so that 1.5 and 1.50 are the same price. */
public record Price(BigDecimal amount, String currency) {

    private static final int SCALE = 2;
    private static final int MAXIMUM_PRECISION = 10;
    private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");

    public Price {
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        amount = normalized(amount);
        if (!CURRENCY_CODE.matcher(currency).matches()) {
            throw new InvalidListing("currency " + currency);
        }
    }

    private static BigDecimal normalized(BigDecimal amount) {
        try {
            BigDecimal normalized = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
            if (normalized.signum() < 0 || normalized.precision() > MAXIMUM_PRECISION) {
                throw new InvalidListing("price " + amount);
            }
            return normalized;
        } catch (ArithmeticException tooManyDecimals) {
            throw new InvalidListing("price " + amount);
        }
    }
}
