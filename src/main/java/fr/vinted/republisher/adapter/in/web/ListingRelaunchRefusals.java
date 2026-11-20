package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.model.ListingStageDto;
import fr.vinted.republisher.adapter.in.web.api.model.RelaunchRefusalDto;
import fr.vinted.republisher.adapter.in.web.api.model.RelaunchRefusalDto.ReasonEnum;
import fr.vinted.republisher.domain.backup.BackupNotVerified;
import fr.vinted.republisher.domain.backup.BackupReplaced;
import fr.vinted.republisher.domain.backup.ListingNotBackedUp;
import fr.vinted.republisher.domain.backup.ListingUnreadable;
import fr.vinted.republisher.domain.deletion.BackupNotValidated;
import fr.vinted.republisher.domain.deletion.DeletionUnconfirmed;
import fr.vinted.republisher.domain.deletion.ListingAlreadyDeleted;
import fr.vinted.republisher.domain.deletion.ListingChanged;
import fr.vinted.republisher.domain.deletion.ListingPageUnreadable;
import fr.vinted.republisher.domain.deletion.NoUnconfirmedDeletion;
import fr.vinted.republisher.domain.inventory.ListingNotFound;
import fr.vinted.republisher.domain.relaunch.NotRelaunchable;
import fr.vinted.republisher.domain.relaunch.StageChanged;
import fr.vinted.republisher.domain.republication.FieldNotFillable;
import fr.vinted.republisher.domain.republication.ListingAlreadyRepublished;
import fr.vinted.republisher.domain.republication.ListingFormUnreadable;
import fr.vinted.republisher.domain.republication.ListingNotDeleted;
import fr.vinted.republisher.domain.republication.NoRepublicationToVerify;
import fr.vinted.republisher.domain.republication.RepublicationInError;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.SessionDisconnected;
import fr.vinted.republisher.domain.session.VintedUnreachable;
import fr.vinted.republisher.domain.wardrobe.WardrobeUnreadable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolationException;

/**
 * Turns the refusals of a relaunch, and of the use case it launched, into the statuses and bodies the contract
 * declares. A refusal of the launched use case only says so: its detail is on the listing's page and in the journal.
 */
@RestControllerAdvice(assignableTypes = ListingRelaunchApiController.class)
public class ListingRelaunchRefusals {

    private static final Logger LOG = LoggerFactory.getLogger(ListingRelaunchRefusals.class);

    @ExceptionHandler
    ResponseEntity<Void> notFound(ListingNotFound refusal) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler
    ResponseEntity<RelaunchRefusalDto> stageChanged(StageChanged refusal) {
        return conflict(new RelaunchRefusalDto().reason(ReasonEnum.STAGE_CHANGED)
                .stage(ListingStageDto.valueOf(refusal.current().name())));
    }

    @ExceptionHandler
    ResponseEntity<RelaunchRefusalDto> notRelaunchable(NotRelaunchable refusal) {
        return conflict(new RelaunchRefusalDto().reason(ReasonEnum.NOT_RELAUNCHABLE)
                .stage(ListingStageDto.valueOf(refusal.stage().name())));
    }

    @ExceptionHandler
    ResponseEntity<RelaunchRefusalDto> busy(BrowserBusy refusal) {
        return conflict(new RelaunchRefusalDto().reason(ReasonEnum.BROWSER_BUSY));
    }

    @ExceptionHandler({ListingNotBackedUp.class, BackupReplaced.class, BackupNotValidated.class,
            BackupNotVerified.class, ListingAlreadyDeleted.class, DeletionUnconfirmed.class,
            NoUnconfirmedDeletion.class, ListingChanged.class, ListingNotDeleted.class,
            ListingAlreadyRepublished.class, RepublicationInError.class, FieldNotFillable.class,
            NoRepublicationToVerify.class})
    ResponseEntity<RelaunchRefusalDto> actionRefused(RuntimeException refusal) {
        LOG.info("Relaunched action refused: {}", refusal.getMessage());
        return conflict(new RelaunchRefusalDto().reason(ReasonEnum.ACTION_REFUSED));
    }

    @ExceptionHandler
    ResponseEntity<RelaunchRefusalDto> browserUnavailable(BrowserUnavailable problem) {
        return failed(ReasonEnum.BROWSER_UNAVAILABLE, problem);
    }

    @ExceptionHandler
    ResponseEntity<RelaunchRefusalDto> vintedUnreachable(VintedUnreachable problem) {
        return failed(ReasonEnum.VINTED_UNREACHABLE, problem);
    }

    @ExceptionHandler
    ResponseEntity<RelaunchRefusalDto> disconnected(SessionDisconnected problem) {
        return failed(ReasonEnum.SESSION_DISCONNECTED, problem);
    }

    @ExceptionHandler({ListingPageUnreadable.class, ListingFormUnreadable.class, ListingUnreadable.class,
            WardrobeUnreadable.class})
    ResponseEntity<RelaunchRefusalDto> pageUnreadable(RuntimeException problem) {
        return failed(ReasonEnum.VINTED_PAGE_UNREADABLE, problem);
    }

    /** Spring's own refusals of a malformed request, which the generic handler below would turn into failures. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class})
    ResponseEntity<Void> invalid(RuntimeException refusal) {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler
    ResponseEntity<RelaunchRefusalDto> unexpected(RuntimeException failure) {
        LOG.error("Relaunch failed", failure);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new RelaunchRefusalDto().reason(ReasonEnum.RELAUNCH_FAILED));
    }

    private static ResponseEntity<RelaunchRefusalDto> failed(ReasonEnum reason, RuntimeException problem) {
        LOG.warn("Relaunch stopped on Vinted's side", problem);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new RelaunchRefusalDto().reason(reason));
    }

    private static ResponseEntity<RelaunchRefusalDto> conflict(RelaunchRefusalDto refusal) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(refusal);
    }
}
