package com.example.kafkaavro.mapper;

import com.example.kafkaavro.exception.DataConversionException;
import com.example.kafkaavro.util.AvroTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class GenericRecordBuilder {

    private final AvroTypeHandler avroTypeHandler;

    @Autowired
    public GenericRecordBuilder(@Lazy AvroTypeHandler avroTypeHandler) {
        this.avroTypeHandler = avroTypeHandler;
    }

    public GenericRecord build(Schema schema, JsonNode json) {
        GenericData.Record record = new GenericData.Record(schema);

        for (Schema.Field field : schema.getFields()) {
            JsonNode fieldNode = json.get(field.name());

            if (fieldNode == null || fieldNode.isNull()) {
                if (field.hasDefaultValue()) {
                    record.put(field.name(), field.defaultVal());
                } else if (isNullable(field.schema())) {
                    record.put(field.name(), null);
                } else {
                    throw new DataConversionException(field.name(),
                        field.schema().getType().name(), null,
                        new IllegalArgumentException("Required field missing from JSON input"));
                }
            } else {
                record.put(field.name(), avroTypeHandler.convert(field, fieldNode));
            }
        }
        return record;
    }

    private boolean isNullable(Schema schema) {
        if (schema.getType() == Schema.Type.UNION) {
            return schema.getTypes().stream()
                .anyMatch(s -> s.getType() == Schema.Type.NULL);
        }
        return false;
    }
}
