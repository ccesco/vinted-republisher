package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.domain.inventory.ListingNotFound;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns the refusals of the listing detail into the statuses the contract declares. */
@RestControllerAdvice(assignableTypes = ListingDetailApiController.class)
public class ListingDetailRefusals {

    @ExceptionHandler
    ResponseEntity<Void> notFound(ListingNotFound refusal) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler
    ResponseEntity<Void> outsideTheContract(ConstraintViolationException refusal) {
        return ResponseEntity.badRequest().build();
    }
}
