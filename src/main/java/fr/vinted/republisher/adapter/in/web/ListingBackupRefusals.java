package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.model.ValidationRefusalDto;
import fr.vinted.republisher.domain.backup.BackupNotVerified;
import fr.vinted.republisher.domain.backup.BackupReplaced;
import fr.vinted.republisher.domain.backup.ListingNotBackedUp;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns the refusals of the backup use cases into the statuses and bodies the contract declares. */
@RestControllerAdvice(assignableTypes = ListingBackupApiController.class)
public class ListingBackupRefusals {

    @ExceptionHandler
    ResponseEntity<Void> notBackedUp(ListingNotBackedUp refusal) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler
    ResponseEntity<ValidationRefusalDto> replaced(BackupReplaced refusal) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ValidationRefusalDto().reason(ValidationRefusalDto.ReasonEnum.BACKUP_REPLACED));
    }

    @ExceptionHandler
    ResponseEntity<ValidationRefusalDto> notVerified(BackupNotVerified refusal) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ValidationRefusalDto().reason(ValidationRefusalDto.ReasonEnum.VERIFICATION_FAILED)
                        .verification(ListingBackupApiController.toDto(refusal.verification())));
    }
}
