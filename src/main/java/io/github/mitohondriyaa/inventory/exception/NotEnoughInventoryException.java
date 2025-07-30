package io.github.mitohondriyaa.inventory.exception;

public class NotEnoughInventoryException extends RuntimeException {
    public NotEnoughInventoryException(String message) {
        super(message);
    }
}
