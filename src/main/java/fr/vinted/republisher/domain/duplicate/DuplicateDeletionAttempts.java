package fr.vinted.republisher.domain.duplicate;

import java.time.Instant;
import java.util.List;

/** The deletions of duplicates that may have clicked on Vinted, which the write cadence counts as deletions. */
public interface DuplicateDeletionAttempts {

    /** The dates of the deletions of duplicates succeeded or in doubt after {@code since}. */
    List<Instant> duplicateDeletionsSince(Instant since);
}
