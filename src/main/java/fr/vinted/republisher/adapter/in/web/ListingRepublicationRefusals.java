package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.model.ListingFormFieldDto;
import fr.vinted.republisher.adapter.in.web.api.model.RepublicationRefusalDto;
import fr.vinted.republisher.adapter.in.web.api.model.RepublicationRefusalDto.ReasonEnum;
import fr.vinted.republisher.domain.backup.BackupNotVerified;
import fr.vinted.republisher.domain.backup.BackupReplaced;
import fr.vinted.republisher.domain.backup.ListingNotBackedUp;
import fr.vinted.republisher.domain.deletion.BackupNotValidated;
import fr.vinted.republisher.domain.republication.FieldNotFillable;
import fr.vinted.republisher.domain.republication.ListingAlreadyRepublished;
import fr.vinted.republisher.domain.republication.ListingFormUnreadable;
import fr.vinted.republisher.domain.republication.ListingNotDeleted;
import fr.vinted.republisher.domain.republication.RepublicationInError;
import fr.vinted.republisher.domain.republication.RepublicationTooEarly;
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
 * Turns the refusals of the republication into the statuses and bodies the contract declares. The page only gives
 * the reason: details are in the logs.
 */
@RestControllerAdvice(assignableTypes = ListingRepublicationApiController.class)
public class ListingRepublicationRefusals {

    private static final Logger LOG = LoggerFactory.getLogger(ListingRepublicationRefusals.class);

    @ExceptionHandler
    ResponseEntity<Void> notBackedUp(ListingNotBackedUp refusal) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler
    ResponseEntity<RepublicationRefusalDto> replaced(BackupReplaced refusal) {
        return refused(HttpStatus.CONFLICT, ReasonEnum.BACKUP_REPLACED);
    }

    @ExceptionHandler
    ResponseEntity<RepublicationRefusalDto> notValidated(BackupNotValidated refusal) {
        return refused(HttpStatus.CONFLICT, ReasonEnum.NOT_VALIDATED);
    }

    @ExceptionHandler
    ResponseEntity<RepublicationRefusalDto> notDeleted(ListingNotDeleted refusal) {
        return refused(HttpStatus.CONFLICT, ReasonEnum.NOT_DELETED);
    }

    @ExceptionHandler
    ResponseEntity<RepublicationRefusalDto> tooEarly(RepublicationTooEarly refusal) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new RepublicationRefusalDto()
                .reason(ReasonEnum.TOO_EARLY).republishableAt(refusal.republishableAt().atOffset(ZoneOffset.UTC)));
    }

    @ExceptionHandler
    ResponseEntity<RepublicationRefusalDto> alreadyRepublished(ListingAlreadyRepublished refusal) {
        return refused(HttpStatus.CONFLICT, ReasonEnum.ALREADY_REPUBLISHED);
    }

    @ExceptionHandler
    ResponseEntity<RepublicationRefusalDto> inError(RepublicationInError refusal) {
        return refused(HttpStatus.CONFLICT, ReasonEnum.REPUBLICATION_IN_ERROR);
    }

    @ExceptionHandler
    ResponseEntity<RepublicationRefusalDto> busy(BrowserBusy refusal) {
        return refused(HttpStatus.CONFLICT, ReasonEnum.BROWSER_BUSY);
    }

    @ExceptionHandler
    ResponseEntity<RepublicationRefusalDto> photosNotVerified(BackupNotVerified refusal) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new RepublicationRefusalDto().reason(ReasonEnum.PHOTOS_NOT_VERIFIED)
                        .verification(ListingBackupApiController.toDto(refusal.verification())));
    }

    @ExceptionHandler
    ResponseEntity<RepublicationRefusalDto> fieldNotFillable(FieldNotFillable refusal) {
        LOG.warn("Republication abandoned before Publish", refusal);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new RepublicationRefusalDto().reason(ReasonEnum.FIELD_NOT_FILLABLE)
                        .field(ListingFormFieldDto.valueOf(refusal.field().name()))
                        .optionId(refusal.optionId().orElse(null)));
    }

    @ExceptionHandler
    ResponseEntity<RepublicationRefusalDto> browserUnavailable(BrowserUnavailable problem) {
        return failed(ReasonEnum.BROWSER_UNAVAILABLE, problem);
    }

    @ExceptionHandler
    ResponseEntity<RepublicationRefusalDto> vintedUnreachable(VintedUnreachable problem) {
        return failed(ReasonEnum.VINTED_UNREACHABLE, problem);
    }

    @ExceptionHandler
    ResponseEntity<RepublicationRefusalDto> disconnected(SessionDisconnected problem) {
        return failed(ReasonEnum.SESSION_DISCONNECTED, problem);
    }

    @ExceptionHandler
    ResponseEntity<RepublicationRefusalDto> formUnreadable(ListingFormUnreadable problem) {
        return failed(ReasonEnum.FORM_UNREADABLE, problem);
    }

    /** Spring's own refusals of a malformed request, which the generic handler below would turn into failures. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Void> invalid(RuntimeException refusal) {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler
    ResponseEntity<RepublicationRefusalDto> unexpected(RuntimeException failure) {
        LOG.error("Republication failed", failure);
        return refused(HttpStatus.INTERNAL_SERVER_ERROR, ReasonEnum.REPUBLICATION_FAILED);
    }

    private static ResponseEntity<RepublicationRefusalDto> failed(ReasonEnum reason, RuntimeException problem) {
        LOG.warn("Republication stopped on Vinted's side", problem);
        return refused(HttpStatus.BAD_GATEWAY, reason);
    }

    private static ResponseEntity<RepublicationRefusalDto> refused(HttpStatus status, ReasonEnum reason) {
        return ResponseEntity.status(status).body(new RepublicationRefusalDto().reason(reason));
    }
}
