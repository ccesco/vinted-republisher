package fr.vinted.republisher.adapter.out.persistence;

/** The number of attempts of a listing found not published, as counted by the database. */
record NotPublishedCount(long listingId, long attempts) {
}
