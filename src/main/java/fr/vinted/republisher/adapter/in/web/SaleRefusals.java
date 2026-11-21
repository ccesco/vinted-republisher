package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.adapter.in.web.api.model.SaleRefusalDto;
import fr.vinted.republisher.domain.inventory.ListingNotFound;
import fr.vinted.republisher.domain.sale.ListingAlreadySold;
import fr.vinted.republisher.domain.sale.ListingNotSold;
import fr.vinted.republisher.domain.sale.SaleNotNoted;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns the refusals of noting or cancelling a sale into the statuses the contract declares. */
@RestControllerAdvice(assignableTypes = SalesApiController.class)
public class SaleRefusals {

    @ExceptionHandler
    ResponseEntity<Void> unknownListing(ListingNotFound refusal) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler
    ResponseEntity<Void> noSale(ListingNotSold refusal) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler
    ResponseEntity<SaleRefusalDto> alreadySold(ListingAlreadySold refusal) {
        return conflict(SaleRefusalDto.ReasonEnum.ALREADY_SOLD);
    }

    @ExceptionHandler
    ResponseEntity<SaleRefusalDto> notNoted(SaleNotNoted refusal) {
        return conflict(SaleRefusalDto.ReasonEnum.NOT_NOTED);
    }

    @ExceptionHandler
    ResponseEntity<Void> outsideTheContract(ConstraintViolationException refusal) {
        return ResponseEntity.badRequest().build();
    }

    private static ResponseEntity<SaleRefusalDto> conflict(SaleRefusalDto.ReasonEnum reason) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new SaleRefusalDto().reason(reason));
    }
}
