package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.model.DataArchiveRefusalDto;
import fr.vinted.republisher.adapter.in.web.api.model.DataArchiveRefusalReasonDto;
import fr.vinted.republisher.domain.session.BrowserBusy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns the refusals and failures of a data archive into the statuses and bodies the contract declares. */
@RestControllerAdvice(assignableTypes = DataArchiveApiController.class)
public class DataArchiveRefusals {

    private static final Logger LOG = LoggerFactory.getLogger(DataArchiveRefusals.class);

    @ExceptionHandler
    ResponseEntity<DataArchiveRefusalDto> busy(BrowserBusy refusal) {
        return refusal(HttpStatus.CONFLICT, DataArchiveRefusalReasonDto.BROWSER_BUSY);
    }

    /** Whatever broke the archive (database, photo, disk), nothing was sent: the details are in the logs. */
    @ExceptionHandler
    ResponseEntity<DataArchiveRefusalDto> failed(RuntimeException failure) {
        LOG.error("Data archive failed", failure);
        return refusal(HttpStatus.INTERNAL_SERVER_ERROR, DataArchiveRefusalReasonDto.ARCHIVE_FAILED);
    }

    private static ResponseEntity<DataArchiveRefusalDto> refusal(HttpStatus status,
                                                               DataArchiveRefusalReasonDto reason) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(new DataArchiveRefusalDto().reason(reason));
    }
}
