package com.example.kafkaavro.exception;

public class SchemaFetchException extends RuntimeException {
    private final String subject;
    
    public SchemaFetchException(String subject, Throwable cause) {
        super("Failed to fetch schema for subject: " + subject, cause);
        this.subject = subject;
    }
    
    public String getSubject() { 
        return subject; 
    }
}
