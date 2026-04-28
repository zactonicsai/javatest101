package com.example.tdd.messaging;

public class PoisonMessageException extends RuntimeException {
    public PoisonMessageException(String message) { super(message); }
}
