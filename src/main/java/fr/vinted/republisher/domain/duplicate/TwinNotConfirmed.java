package fr.vinted.republisher.domain.duplicate;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

/** Vinted shows no twin active with the title and the price of the duplicate: it could be the last one on sale. */
public class TwinNotConfirmed extends RuntimeException {

    public TwinNotConfirmed(VintedListingId duplicate) {
        super("No twin of listing " + duplicate.value() + " confirmed on Vinted");
    }
}
