package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.model.DeletionRefusalDto;
import fr.vinted.republisher.adapter.in.web.api.model.DeletionRefusalDto.ReasonEnum;
import fr.vinted.republisher.adapter.in.web.api.model.ListingDifferenceDto;
import fr.vinted.republisher.domain.backup.BackupNotVerified;
import fr.vinted.republisher.domain.backup.BackupReplaced;
import fr.vinted.republisher.domain.backup.ListingNotBackedUp;
import fr.vinted.republisher.domain.deletion.BackupNotValidated;
import fr.vinted.republisher.domain.deletion.DeletionUnconfirmed;
import fr.vinted.republisher.domain.deletion.ListingAlreadyDeleted;
import fr.vinted.republisher.domain.deletion.ListingChanged;
import fr.vinted.republisher.domain.deletion.ListingDifference;
import fr.vinted.republisher.domain.deletion.ListingPageUnreadable;
import fr.vinted.republisher.domain.deletion.NoUnconfirmedDeletion;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.SessionDisconnected;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns the refusals of the deletion use cases into the statuses and bodies the contract declares. The page only
 * gives the reason: details are in the logs.
 */
@RestControllerAdvice(assignableTypes = ListingDeletionApiController.class)
public class ListingDeletionRefusals {

    private static final Logger LOG = LoggerFactory.getLogger(ListingDeletionRefusals.class);

    @ExceptionHandler
    ResponseEntity<Void> notBackedUp(ListingNotBackedUp refusal) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler
    ResponseEntity<DeletionRefusalDto> replaced(BackupReplaced refusal) {
        return refused(HttpStatus.CONFLICT, ReasonEnum.BACKUP_REPLACED);
    }

    @ExceptionHandler
    ResponseEntity<DeletionRefusalDto> notValidated(BackupNotValidated refusal) {
        return refused(HttpStatus.CONFLICT, ReasonEnum.NOT_VALIDATED);
    }

    @ExceptionHandler
    ResponseEntity<DeletionRefusalDto> alreadyDeleted(ListingAlreadyDeleted refusal) {
        return refused(HttpStatus.CONFLICT, ReasonEnum.ALREADY_DELETED);
    }

    @ExceptionHandler
    ResponseEntity<DeletionRefusalDto> unconfirmed(DeletionUnconfirmed refusal) {
        return refused(HttpStatus.CONFLICT, ReasonEnum.DELETION_UNCONFIRMED);
    }

    @ExceptionHandler
    ResponseEntity<DeletionRefusalDto> nothingToRecheck(NoUnconfirmedDeletion refusal) {
        return refused(HttpStatus.CONFLICT, ReasonEnum.NO_UNCONFIRMED_DELETION);
    }

    @ExceptionHandler
    ResponseEntity<DeletionRefusalDto> busy(BrowserBusy refusal) {
        return refused(HttpStatus.CONFLICT, ReasonEnum.BROWSER_BUSY);
    }

    @ExceptionHandler
    ResponseEntity<DeletionRefusalDto> notVerified(BackupNotVerified refusal) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new DeletionRefusalDto().reason(ReasonEnum.VERIFICATION_FAILED)
                        .verification(ListingBackupApiController.toDto(refusal.verification())));
    }

    @ExceptionHandler
    ResponseEntity<DeletionRefusalDto> changed(ListingChanged refusal) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new DeletionRefusalDto().reason(ReasonEnum.LISTING_CHANGED)
                        .differences(refusal.differences().stream().map(ListingDeletionRefusals::toDto).toList()));
    }

    @ExceptionHandler
    ResponseEntity<DeletionRefusalDto> browserUnavailable(BrowserUnavailable problem) {
        return failed(ReasonEnum.BROWSER_UNAVAILABLE, problem);
    }

    @ExceptionHandler
    ResponseEntity<DeletionRefusalDto> vintedUnreachable(VintedUnreachable problem) {
        return failed(ReasonEnum.VINTED_UNREACHABLE, problem);
    }

    @ExceptionHandler
    ResponseEntity<DeletionRefusalDto> disconnected(SessionDisconnected problem) {
        return failed(ReasonEnum.SESSION_DISCONNECTED, problem);
    }

    @ExceptionHandler
    ResponseEntity<DeletionRefusalDto> pageUnreadable(ListingPageUnreadable problem) {
        return failed(ReasonEnum.LISTING_PAGE_UNREADABLE, problem);
    }

    @ExceptionHandler
    ResponseEntity<DeletionRefusalDto> unexpected(RuntimeException failure) {
        LOG.error("Deletion failed", failure);
        return refused(HttpStatus.INTERNAL_SERVER_ERROR, ReasonEnum.DELETION_FAILED);
    }

    private static ResponseEntity<DeletionRefusalDto> failed(ReasonEnum reason, RuntimeException problem) {
        LOG.warn("Deletion stopped on Vinted's side", problem);
        return refused(HttpStatus.BAD_GATEWAY, reason);
    }

    private static ResponseEntity<DeletionRefusalDto> refused(HttpStatus status, ReasonEnum reason) {
        return ResponseEntity.status(status).body(new DeletionRefusalDto().reason(reason));
    }

    private static ListingDifferenceDto toDto(ListingDifference difference) {
        return switch (difference) {
            case ListingDifference.NotActive notActive ->
                    new ListingDifferenceDto().kind(ListingDifferenceDto.KindEnum.NOT_ACTIVE);
            case ListingDifference.TitleChanged changed ->
                    new ListingDifferenceDto().kind(ListingDifferenceDto.KindEnum.TITLE_CHANGED)
                            .savedTitle(changed.saved()).readTitle(changed.read());
            case ListingDifference.PriceChanged changed ->
                    new ListingDifferenceDto().kind(ListingDifferenceDto.KindEnum.PRICE_CHANGED)
                            .savedPrice(ListingBackupApiController.toDto(changed.saved()))
                            .readPrice(ListingBackupApiController.toDto(changed.read()));
        };
    }
}
