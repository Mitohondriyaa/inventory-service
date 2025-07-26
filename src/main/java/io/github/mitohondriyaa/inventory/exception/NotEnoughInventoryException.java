package io.github.mitohondriyaa.inventory.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class NotEnoughInventoryException extends RuntimeException {
    public NotEnoughInventoryException(String message) {
        super(message);
    }
}
