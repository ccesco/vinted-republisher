package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.wardrobe.Listing;

/** Whether the listings shown are in the wardrobe, gone from it, or both. */
public enum PresenceFilter {
    PRESENT, ABSENT, ALL;

    boolean accepts(Listing listing) {
        return switch (this) {
            case PRESENT -> listing.present();
            case ABSENT -> !listing.present();
            case ALL -> true;
        };
    }
}
