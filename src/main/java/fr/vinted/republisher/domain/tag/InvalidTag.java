package fr.vinted.republisher.domain.tag;

/** A tag blank, or too long once formed. */
public class InvalidTag extends IllegalArgumentException {

    public InvalidTag(String reason) {
        super(reason);
    }
}
