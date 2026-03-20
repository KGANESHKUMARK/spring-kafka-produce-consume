package com.example.kafkaavro.util;

import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class TestSchemaFactory {

    public static Schema buildOrderKeySchema() {
        return SchemaBuilder.record("OrderKey")
            .namespace("com.example")
            .fields()
                .name("orderId").type().stringType().noDefault()
            .endRecord();
    }

    public static Schema buildOrderValueSchema() {
        Schema decimalSchema = LogicalTypes.decimal(10, 4)
            .addToSchema(Schema.create(Schema.Type.BYTES));
        Schema dateSchema = LogicalTypes.date()
            .addToSchema(Schema.create(Schema.Type.INT));
        Schema timestampSchema = LogicalTypes.timestampMillis()
            .addToSchema(Schema.create(Schema.Type.LONG));
        Schema timeSchema = LogicalTypes.timeMillis()
            .addToSchema(Schema.create(Schema.Type.INT));

        Schema nestedSchema = SchemaBuilder.record("Metadata")
            .namespace("com.example")
            .fields()
                .name("source").type().stringType().noDefault()
                .name("region").type().stringType().noDefault()
            .endRecord();

        return SchemaBuilder.record("OrderValue")
            .namespace("com.example")
            .fields()
                .name("amount").type(decimalSchema).noDefault()
                .name("tradeDate").type(dateSchema).noDefault()
                .name("createdAt").type(timestampSchema).noDefault()
                .name("timeField").type(timeSchema).noDefault()
                .name("active").type().booleanType().noDefault()
                .name("tags").type().array().items().stringType().noDefault()
                .name("metadata").type(nestedSchema).noDefault()
                .name("description")
                    .type().unionOf().nullType().and().stringType().endUnion()
                    .nullDefault()
            .endRecord();
    }

    public static GenericRecord buildOrderKeyRecord(Schema keySchema, String orderId) {
        GenericData.Record record = new GenericData.Record(keySchema);
        record.put("orderId", orderId);
        return record;
    }

    public static GenericRecord buildOrderValueRecord(Schema valueSchema) {
        Schema nestedSchema = valueSchema.getField("metadata").schema();
        GenericData.Record metadata = new GenericData.Record(nestedSchema);
        metadata.put("source", "web");
        metadata.put("region", "APAC");

        BigDecimal amount = new BigDecimal("123.4500");
        byte[] amountBytes = amount.unscaledValue().toByteArray();

        GenericData.Record record = new GenericData.Record(valueSchema);
        record.put("amount", ByteBuffer.wrap(amountBytes));
        record.put("tradeDate", (int) LocalDate.of(2026, 3, 19).toEpochDay());
        record.put("createdAt", Instant.parse("2026-03-19T10:00:00Z").toEpochMilli());
        record.put("timeField", (int) (LocalTime.of(10, 0).toSecondOfDay() * 1000));
        record.put("active", true);
        record.put("tags", List.of("urgent", "retail"));
        record.put("metadata", metadata);
        record.put("description", null);
        return record;
    }
}
