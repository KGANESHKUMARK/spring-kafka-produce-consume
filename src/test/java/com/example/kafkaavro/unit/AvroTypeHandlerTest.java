package com.example.kafkaavro.unit;

import com.example.kafkaavro.mapper.GenericRecordBuilder;
import com.example.kafkaavro.util.AvroTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.avro.JsonProperties;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SpringExtension.class)
@Import({AvroTypeHandler.class, GenericRecordBuilder.class})
@ActiveProfiles("test")
class AvroTypeHandlerTest {

    @Autowired
    private AvroTypeHandler avroTypeHandler;

    @Test
    void convertDecimal_shouldReturnByteBufferWithDynamicScale() {
        Schema decimalSchema = LogicalTypes.decimal(10, 4)
            .addToSchema(Schema.create(Schema.Type.BYTES));
        Schema.Field field = new Schema.Field("amount", decimalSchema, null, null);

        JsonNode value = new TextNode("123.4500");
        Object result = avroTypeHandler.convert(field, value);

        assertThat(result).isInstanceOf(ByteBuffer.class);
        ByteBuffer buf = (ByteBuffer) result;
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        BigDecimal decoded = new BigDecimal(new BigInteger(bytes), 4);
        assertThat(decoded).isEqualByComparingTo(new BigDecimal("123.4500"));
    }

    @Test
    void convertDate_shouldReturnIntDaysSinceEpoch() {
        Schema dateSchema = LogicalTypes.date().addToSchema(Schema.create(Schema.Type.INT));
        Schema.Field field = new Schema.Field("tradeDate", dateSchema, null, null);

        Object result = avroTypeHandler.convert(field, new TextNode("2026-03-19"));

        assertThat(result).isInstanceOf(Integer.class);
        LocalDate decoded = LocalDate.ofEpochDay((int) result);
        assertThat(decoded).isEqualTo(LocalDate.of(2026, 3, 19));
    }

    @Test
    void convertTimestampMillis_shouldReturnLong() {
        Schema tsSchema = LogicalTypes.timestampMillis().addToSchema(Schema.create(Schema.Type.LONG));
        Schema.Field field = new Schema.Field("createdAt", tsSchema, null, null);
        Instant expected = Instant.parse("2026-03-19T10:00:00Z");

        Object result = avroTypeHandler.convert(field, new TextNode("2026-03-19T10:00:00Z"));

        assertThat(result).isInstanceOf(Long.class);
        assertThat(Instant.ofEpochMilli((long) result)).isEqualTo(expected);
    }

    @Test
    void convertTimeMillis_shouldReturnIntMillisSinceMidnight() {
        Schema timeSchema = LogicalTypes.timeMillis().addToSchema(Schema.create(Schema.Type.INT));
        Schema.Field field = new Schema.Field("timeField", timeSchema, null, null);

        Object result = avroTypeHandler.convert(field, new TextNode("10:00:00"));

        assertThat(result).isInstanceOf(Integer.class);
        LocalTime decoded = LocalTime.ofNanoOfDay((long)(int) result * 1_000_000L);
        assertThat(decoded).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void convertNestedRecord_shouldReturnGenericRecord() {
        Schema nested = SchemaBuilder.record("Nested").namespace("test").fields()
            .name("name").type().stringType().noDefault()
            .endRecord();
        Schema.Field field = new Schema.Field("nested", nested, null, null);
        ObjectNode json = new ObjectMapper().createObjectNode().put("name", "hello");

        Object result = avroTypeHandler.convert(field, json);

        assertThat(result).isInstanceOf(GenericRecord.class);
        assertThat(((GenericRecord) result).get("name").toString()).isEqualTo("hello");
    }

    @Test
    void convertArray_shouldReturnList() {
        Schema arraySchema = Schema.createArray(Schema.create(Schema.Type.STRING));
        Schema.Field field = new Schema.Field("tags", arraySchema, null, null);
        ArrayNode json = new ObjectMapper().createArrayNode().add("a").add("b");

        Object result = avroTypeHandler.convert(field, json);

        assertThat(result).isInstanceOf(List.class);
        assertThat((List<String>) result).containsExactlyElementsOf(List.of("a", "b"));
    }

    @Test
    void convertMap_shouldReturnMap() {
        Schema mapSchema = Schema.createMap(Schema.create(Schema.Type.STRING));
        Schema.Field field = new Schema.Field("meta", mapSchema, null, null);
        ObjectNode json = new ObjectMapper().createObjectNode().put("key1", "val1");

        Object result = avroTypeHandler.convert(field, json);

        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result).get("key1")).isEqualTo("val1");
    }

    @Test
    void convertUnion_nullValue_shouldReturnNull() {
        Schema union = Schema.createUnion(
            Schema.create(Schema.Type.NULL), Schema.create(Schema.Type.STRING));
        Schema.Field field = new Schema.Field("desc", union, null, JsonProperties.NULL_VALUE);

        Object result = avroTypeHandler.convert(field, NullNode.getInstance());

        assertThat(result).isNull();
    }

    @Test
    void convertUnion_nonNullValue_shouldConvertToCorrectType() {
        Schema union = Schema.createUnion(
            Schema.create(Schema.Type.NULL), Schema.create(Schema.Type.STRING));
        Schema.Field field = new Schema.Field("desc", union, null, JsonProperties.NULL_VALUE);

        Object result = avroTypeHandler.convert(field, new TextNode("hello"));

        assertThat(result).isEqualTo("hello");
    }
}
