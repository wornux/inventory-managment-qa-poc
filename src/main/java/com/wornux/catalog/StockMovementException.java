package com.wornux.catalog;

public class StockMovementException extends RuntimeException {

    public StockMovementException(String message) {
        super(message);
    }

    public StockMovementException(String message, Throwable cause) {
        super(message, cause);
    }
}
