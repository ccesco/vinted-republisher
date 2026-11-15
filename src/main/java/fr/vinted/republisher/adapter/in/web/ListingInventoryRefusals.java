package fr.vinted.republisher.adapter.in.web;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** A criterion outside the contract (a search too long) is the page's mistake: 400, as the contract declares. */
@RestControllerAdvice(assignableTypes = ListingInventoryApiController.class)
public class ListingInventoryRefusals {

    @ExceptionHandler
    ResponseEntity<Void> outsideTheContract(ConstraintViolationException refusal) {
        return ResponseEntity.badRequest().build();
    }
}
