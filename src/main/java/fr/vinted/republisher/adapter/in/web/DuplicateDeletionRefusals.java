package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.model.DuplicateDeletionRefusalDto;
import fr.vinted.republisher.adapter.in.web.api.model.DuplicateDeletionRefusalDto.ReasonEnum;
import fr.vinted.republisher.domain.cadence.CadenceLimitReached;
import fr.vinted.republisher.domain.deletion.ListingChanged;
import fr.vinted.republisher.domain.deletion.ListingPageUnreadable;
import fr.vinted.republisher.domain.duplicate.ListingHasHistory;
import fr.vinted.republisher.domain.duplicate.NotADuplicate;
import fr.vinted.republisher.domain.duplicate.TwinNotConfirmed;
import fr.vinted.republisher.domain.inventory.ListingNotFound;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.SessionDisconnected;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.ZoneOffset;

/**
 * Turns the refusals of the deletion of a duplicate into the statuses and bodies the contract declares. The page
 * only gives the reason: details are in the logs.
 */
@RestControllerAdvice(assignableTypes = DuplicatesApiController.class)
public class DuplicateDeletionRefusals {

    private static final Logger LOG = LoggerFactory.getLogger(DuplicateDeletionRefusals.class);

    @ExceptionHandler
    ResponseEntity<Void> unknown(ListingNotFound refusal) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler
    ResponseEntity<DuplicateDeletionRefusalDto> notADuplicate(NotADuplicate refusal) {
        return refused(HttpStatus.CONFLICT, ReasonEnum.NOT_A_DUPLICATE);
    }

    @ExceptionHandler
    ResponseEntity<DuplicateDeletionRefusalDto> hasHistory(ListingHasHistory refusal) {
        return refused(HttpStatus.CONFLICT, ReasonEnum.HAS_HISTORY);
    }

    @ExceptionHandler
    ResponseEntity<DuplicateDeletionRefusalDto> busy(BrowserBusy refusal) {
        return refused(HttpStatus.CONFLICT, ReasonEnum.BROWSER_BUSY);
    }

    @ExceptionHandler
    ResponseEntity<DuplicateDeletionRefusalDto> cadenceLimitReached(CadenceLimitReached refusal) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new DuplicateDeletionRefusalDto()
                .reason(ReasonEnum.CADENCE_LIMIT_REACHED).allowedAt(refusal.allowedAt().atOffset(ZoneOffset.UTC)));
    }

    @ExceptionHandler
    ResponseEntity<DuplicateDeletionRefusalDto> twinNotConfirmed(TwinNotConfirmed refusal) {
        return refused(HttpStatus.UNPROCESSABLE_CONTENT, ReasonEnum.TWIN_NOT_CONFIRMED);
    }

    @ExceptionHandler
    ResponseEntity<DuplicateDeletionRefusalDto> changed(ListingChanged refusal) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new DuplicateDeletionRefusalDto()
                .reason(ReasonEnum.LISTING_CHANGED)
                .differences(refusal.differences().stream().map(ListingDeletionRefusals::toDto).toList()));
    }

    @ExceptionHandler
    ResponseEntity<DuplicateDeletionRefusalDto> browserUnavailable(BrowserUnavailable problem) {
        return failed(ReasonEnum.BROWSER_UNAVAILABLE, problem);
    }

    @ExceptionHandler
    ResponseEntity<DuplicateDeletionRefusalDto> vintedUnreachable(VintedUnreachable problem) {
        return failed(ReasonEnum.VINTED_UNREACHABLE, problem);
    }

    @ExceptionHandler
    ResponseEntity<DuplicateDeletionRefusalDto> disconnected(SessionDisconnected problem) {
        return failed(ReasonEnum.SESSION_DISCONNECTED, problem);
    }

    @ExceptionHandler
    ResponseEntity<DuplicateDeletionRefusalDto> pageUnreadable(ListingPageUnreadable problem) {
        return failed(ReasonEnum.LISTING_PAGE_UNREADABLE, problem);
    }

    /** Spring's own refusals of a malformed request, which the generic handler below would turn into failures. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Void> invalid(RuntimeException refusal) {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler
    ResponseEntity<DuplicateDeletionRefusalDto> unexpected(RuntimeException failure) {
        LOG.error("Deletion of a duplicate failed", failure);
        return refused(HttpStatus.INTERNAL_SERVER_ERROR, ReasonEnum.DELETION_FAILED);
    }

    private static ResponseEntity<DuplicateDeletionRefusalDto> failed(ReasonEnum reason, RuntimeException problem) {
        LOG.warn("Deletion of a duplicate stopped on Vinted's side", problem);
        return refused(HttpStatus.BAD_GATEWAY, reason);
    }

    private static ResponseEntity<DuplicateDeletionRefusalDto> refused(HttpStatus status, ReasonEnum reason) {
        return ResponseEntity.status(status).body(new DuplicateDeletionRefusalDto().reason(reason));
    }
}
