package fr.vinted.republisher.domain.inventory;

/**
 * How many times the article was put on sale again ({@code published}) and how many attempts the member found not
 * published on Vinted ({@code notPublished}), over its whole chain of republications.
 */
public record RepublicationTally(int published, int notPublished) {

    public static final RepublicationTally NONE = new RepublicationTally(0, 0);

    public RepublicationTally {
        if (published < 0 || notPublished < 0) {
            throw new IllegalArgumentException("A republication tally counts from zero: " + published + ", "
                    + notPublished);
        }
    }
}
