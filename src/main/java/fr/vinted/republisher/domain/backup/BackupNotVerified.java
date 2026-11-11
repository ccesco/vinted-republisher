package fr.vinted.republisher.domain.backup;

/** A backup that fails its verification cannot be validated. */
public class BackupNotVerified extends RuntimeException {

    private final transient BackupVerification verification;

    public BackupNotVerified(BackupVerification verification) {
        super("The backup fails its verification: " + verification.failures());
        this.verification = verification;
    }

    public BackupVerification verification() {
        return verification;
    }
}
