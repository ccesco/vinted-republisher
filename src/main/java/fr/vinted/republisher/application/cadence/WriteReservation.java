package fr.vinted.republisher.application.cadence;

import fr.vinted.republisher.domain.cadence.VintedWrite;

import java.time.Instant;

/** A write that passed the cadence and is still in progress: it counts as made at the time of the check. */
public record WriteReservation(VintedWrite write, Instant at) {
}
