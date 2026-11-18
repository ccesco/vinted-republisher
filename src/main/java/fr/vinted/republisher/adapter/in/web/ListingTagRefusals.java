package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.domain.inventory.ListingNotFound;
import fr.vinted.republisher.domain.tag.InvalidTag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns the refusals of a tag edit into the statuses the contract declares; nothing has been changed. */
@RestControllerAdvice(assignableTypes = ListingTagApiController.class)
public class ListingTagRefusals {

    @ExceptionHandler
    ResponseEntity<Void> unknownListing(ListingNotFound refusal) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler
    ResponseEntity<Void> invalidTag(InvalidTag refusal) {
        return ResponseEntity.badRequest().build();
    }
}
