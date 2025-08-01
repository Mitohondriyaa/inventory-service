package io.github.mitohondriyaa.inventory.exception;

import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler
    public ResponseEntity<Info> handleNotEnoughInventoryExceptionException(
        NotEnoughInventoryException exception
    ) {
        Info info = new Info(exception.getMessage());

        return new ResponseEntity<>(info, HttpStatus.CONFLICT);
    }

    @ExceptionHandler
    public ResponseEntity<Info> handleNotFoundException(
        NotFoundException exception
    ) {
        Info info = new Info(exception.getMessage());

        return new ResponseEntity<>(info, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler
    public ResponseEntity<MultipleInfo> handleValidationException(
        MethodArgumentNotValidException exception
    ) {
        MultipleInfo multipleInfo = new MultipleInfo(
            exception.getBindingResult().getFieldErrors().stream().collect(
                Collectors.toMap(
                    FieldError::getField,
                    DefaultMessageSourceResolvable::getDefaultMessage,
                    (existing, duplicate) -> existing + ", " +  duplicate
                )
            )
        );

        return new ResponseEntity<>(multipleInfo, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler
    public ResponseEntity<Info> handleOtherExceptions(
        Exception exception
    ) {
        Info info = new Info(exception.getMessage());

        return new ResponseEntity<>(info, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}