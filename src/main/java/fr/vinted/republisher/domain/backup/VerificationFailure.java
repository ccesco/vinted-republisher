package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.Price;

import java.util.Objects;

/** A reason why a backup cannot be trusted to republish its listing. */
public sealed interface VerificationFailure {

    /** A backup made before photos were kept. */
    record NoPhoto() implements VerificationFailure {
    }

    record PhotoMissing(int rank) implements VerificationFailure {
    }

    /** The file no longer has the SHA-256 it was kept with. */
    record PhotoAltered(int rank) implements VerificationFailure {
    }

    /** No longer in the wardrobe: there is nothing to delete on Vinted. */
    record ListingAbsent() implements VerificationFailure {
    }

    /** The listing changed on Vinted since its backup. */
    record TitleChanged(String saved, String scanned) implements VerificationFailure {

        public TitleChanged {
            Objects.requireNonNull(saved);
            Objects.requireNonNull(scanned);
        }
    }

    /** The listing changed on Vinted since its backup. */
    record PriceChanged(Price saved, Price scanned) implements VerificationFailure {

        public PriceChanged {
            Objects.requireNonNull(saved);
            Objects.requireNonNull(scanned);
        }
    }
}
