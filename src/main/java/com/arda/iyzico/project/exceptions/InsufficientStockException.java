package com.arda.iyzico.project.exceptions;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(Long itemId, int available, int requested) {
        super("Item " + itemId + " has " + available + " in stock, " + requested + " requested.");
    }
}