package fr.vinted.republisher.domain.republication;

/** Refusal: no real publication may start from a tool set to dry-run, even one the member makes. */
public class ManualRepublicationInDryRun extends RuntimeException {

    public ManualRepublicationInDryRun() {
        super("A manual republication is not available in dry-run");
    }
}
