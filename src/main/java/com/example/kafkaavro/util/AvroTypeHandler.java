package com.example.kafkaavro.util;

import com.example.kafkaavro.exception.DataConversionException;
import com.example.kafkaavro.mapper.GenericRecordBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AvroTypeHandler {

    private final GenericRecordBuilder genericRecordBuilder;

    @Autowired
    public AvroTypeHandler(@Lazy GenericRecordBuilder genericRecordBuilder) {
        this.genericRecordBuilder = genericRecordBuilder;
    }

    public Object convert(Schema.Field field, JsonNode valueNode) {
        try {
            return convertValue(field.name(), field.schema(), valueNode);
        } catch (DataConversionException e) {
            throw e;
        } catch (Exception e) {
            throw new DataConversionException(field.name(),
                field.schema().getType().name(), valueNode, e);
        }
    }

    private Object convertValue(String fieldName, Schema schema, JsonNode valueNode) {
        if (schema.getType() == Schema.Type.UNION) {
            return convertUnion(fieldName, schema, valueNode);
        }

        LogicalType logicalType = schema.getLogicalType();
        if (logicalType != null) {
            return convertLogicalType(fieldName, schema, logicalType, valueNode);
        }

        return switch (schema.getType()) {
            case STRING  -> valueNode.asText();
            case INT     -> valueNode.asInt();
            case LONG    -> valueNode.asLong();
            case FLOAT   -> (float) valueNode.asDouble();
            case DOUBLE  -> valueNode.asDouble();
            case BOOLEAN -> valueNode.asBoolean();
            case BYTES   -> ByteBuffer.wrap(valueNode.asText().getBytes(StandardCharsets.UTF_8));
            case ENUM    -> new org.apache.avro.generic.GenericData.EnumSymbol(schema, valueNode.asText());
            case RECORD  -> genericRecordBuilder.build(schema, valueNode);
            case ARRAY   -> convertArray(fieldName, schema, valueNode);
            case MAP     -> convertMap(fieldName, schema, valueNode);
            default      -> throw new DataConversionException(fieldName,
                                schema.getType().name(), valueNode, null);
        };
    }

    private Object convertLogicalType(String fieldName, Schema schema,
                                       LogicalType logicalType, JsonNode valueNode) {
        return switch (logicalType.getName()) {
            case "decimal" -> {
                int scale = ((LogicalTypes.Decimal) logicalType).getScale();
                BigDecimal bd;
                if (valueNode.isNumber()) {
                    bd = valueNode.decimalValue().setScale(scale, RoundingMode.HALF_UP);
                } else {
                    bd = new BigDecimal(valueNode.asText()).setScale(scale, RoundingMode.HALF_UP);
                }
                byte[] bytes = bd.unscaledValue().toByteArray();
                yield ByteBuffer.wrap(bytes);
            }
            case "date" -> {
                LocalDate date = LocalDate.parse(valueNode.asText());
                yield (int) date.toEpochDay();
            }
            case "timestamp-millis" -> {
                Instant instant = Instant.parse(valueNode.asText());
                yield instant.toEpochMilli();
            }
            case "timestamp-micros" -> {
                if (valueNode.isNumber()) {
                    yield valueNode.asLong();
                }
                Instant instant = Instant.parse(valueNode.asText());
                yield instant.toEpochMilli() * 1000;
            }
            case "time-millis" -> {
                if (valueNode.isNumber()) {
                    yield valueNode.asInt();
                }
                LocalTime time = LocalTime.parse(valueNode.asText());
                yield (int) (time.toNanoOfDay() / 1_000_000);
            }
            case "time-micros" -> {
                if (valueNode.isNumber()) {
                    yield valueNode.asLong();
                }
                LocalTime time = LocalTime.parse(valueNode.asText());
                yield time.toNanoOfDay() / 1000;
            }
            default -> throw new DataConversionException(fieldName,
                logicalType.getName(), valueNode, null);
        };
    }

    private Object convertUnion(String fieldName, Schema unionSchema, JsonNode valueNode) {
        boolean hasNull = unionSchema.getTypes().stream()
            .anyMatch(s -> s.getType() == Schema.Type.NULL);

        if (valueNode == null || valueNode.isNull()) {
            if (hasNull) return null;
            throw new DataConversionException(fieldName, unionSchema.toString(), null, null);
        }

        Schema nonNullSchema = unionSchema.getTypes().stream()
            .filter(s -> s.getType() != Schema.Type.NULL)
            .findFirst()
            .orElseThrow(() -> new DataConversionException(fieldName,
                "non-null union type", valueNode, null));

        return convertValue(fieldName, nonNullSchema, valueNode);
    }

    private List<Object> convertArray(String fieldName, Schema arraySchema, JsonNode valueNode) {
        List<Object> list = new ArrayList<>();
        Schema elementSchema = arraySchema.getElementType();
        for (JsonNode element : valueNode) {
            list.add(convertValue(fieldName + "[]", elementSchema, element));
        }
        return list;
    }

    private Map<String, Object> convertMap(String fieldName, Schema mapSchema, JsonNode valueNode) {
        Map<String, Object> map = new LinkedHashMap<>();
        Schema valueSchema = mapSchema.getValueType();
        valueNode.fields().forEachRemaining(entry ->
            map.put(entry.getKey(),
                convertValue(fieldName + "." + entry.getKey(), valueSchema, entry.getValue()))
        );
        return map;
    }
}
