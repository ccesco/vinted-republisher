package fr.vinted.republisher.domain.sale;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

/** Only a sale noted by the member can be cancelled; one noticed on the wardrobe never is. */
public class SaleNotNoted extends RuntimeException {

    public SaleNotNoted(VintedListingId listing) {
        super("The sale of listing " + listing.value() + " was noticed on the wardrobe, not noted");
    }
}
