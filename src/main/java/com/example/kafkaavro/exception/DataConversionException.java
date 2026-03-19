package com.example.kafkaavro.exception;

public class DataConversionException extends RuntimeException {
    private final String fieldName;
    private final String expectedType;
    private final Object actualValue;

    public DataConversionException(String fieldName, String expectedType,
                                    Object actualValue, Throwable cause) {
        super(String.format("Conversion failed for field '%s': expected=%s actual=%s",
            fieldName, expectedType, actualValue), cause);
        this.fieldName = fieldName;
        this.expectedType = expectedType;
        this.actualValue = actualValue;
    }
    
    public String getFieldName() {
        return fieldName;
    }
    
    public String getExpectedType() {
        return expectedType;
    }
    
    public Object getActualValue() {
        return actualValue;
    }
}
