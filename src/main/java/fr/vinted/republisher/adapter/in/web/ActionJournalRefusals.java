package fr.vinted.republisher.adapter.in.web;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = ActionJournalApiController.class)
public class ActionJournalRefusals {

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Void> outsideTheContract(RuntimeException refusal) {
        return ResponseEntity.badRequest().build();
    }
}
