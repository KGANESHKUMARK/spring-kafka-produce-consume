package com.example.kafkaavro.util;

import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

public class SafeFieldExtractor {

    private final GenericRecord record;

    public SafeFieldExtractor(GenericRecord record) {
        Objects.requireNonNull(record, "GenericRecord must not be null");
        this.record = record;
    }

    public Object get(String fieldName) {
        return record.get(fieldName);
    }

    public BigDecimal getDecimal(String fieldName) {
        Object raw = record.get(fieldName);
        if (raw == null) return null;
        Schema fieldSchema = resolveSchema(record.getSchema().getField(fieldName).schema());
        int scale = ((LogicalTypes.Decimal) fieldSchema.getLogicalType()).getScale();
        ByteBuffer buffer = (ByteBuffer) raw;
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return new BigDecimal(new java.math.BigInteger(bytes), scale);
    }

    public LocalDate getDate(String fieldName) {
        Object raw = record.get(fieldName);
        if (raw == null) return null;
        return LocalDate.ofEpochDay(((Number) raw).longValue());
    }

    public Instant getTimestamp(String fieldName) {
        Object raw = record.get(fieldName);
        if (raw == null) return null;
        return Instant.ofEpochMilli(((Number) raw).longValue());
    }

    public LocalTime getTime(String fieldName) {
        Object raw = record.get(fieldName);
        if (raw == null) return null;
        return LocalTime.ofNanoOfDay((long) ((Number) raw).intValue() * 1_000_000L);
    }

    public SafeFieldExtractor getNested(String fieldName) {
        Object raw = record.get(fieldName);
        if (raw == null) return null;
        return new SafeFieldExtractor((GenericRecord) raw);
    }

    @SuppressWarnings("unchecked")
    public List<Object> getArray(String fieldName) {
        Object raw = record.get(fieldName);
        if (raw == null) return Collections.emptyList();
        List<Object> result = new ArrayList<>();
        for (Object item : (Iterable<?>) raw) result.add(item);
        return result;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(String fieldName) {
        Object raw = record.get(fieldName);
        if (raw == null) return Collections.emptyMap();
        Map<String, Object> result = new LinkedHashMap<>();
        ((Map<?, ?>) raw).forEach((k, v) -> result.put(k.toString(), v));
        return result;
    }

    public <T> Optional<T> getOptional(String fieldName, Class<T> type) {
        try {
            Object raw = record.get(fieldName);
            if (raw == null) return Optional.empty();
            return Optional.of(type.cast(raw));
        } catch (ClassCastException e) {
            return Optional.empty();
        }
    }

    private Schema resolveSchema(Schema schema) {
        if (schema.getType() == Schema.Type.UNION) {
            return schema.getTypes().stream()
                .filter(s -> s.getType() != Schema.Type.NULL)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No non-null type in union"));
        }
        return schema;
    }
}
