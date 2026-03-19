package com.example.kafkaavro.unit;

import com.example.kafkaavro.exception.DataConversionException;
import com.example.kafkaavro.mapper.GenericRecordBuilder;
import com.example.kafkaavro.util.AvroTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SpringExtension.class)
@Import({GenericRecordBuilder.class, AvroTypeHandler.class})
@ActiveProfiles("test")
class GenericRecordBuilderTest {

    @Autowired
    private GenericRecordBuilder genericRecordBuilder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void build_simpleFlatRecord_shouldMapAllFields() throws Exception {
        Schema schema = SchemaBuilder.record("Order").namespace("test").fields()
            .name("id").type().stringType().noDefault()
            .name("active").type().booleanType().noDefault()
            .endRecord();

        JsonNode json = objectMapper.readTree("{\"id\":\"ORD-001\",\"active\":true}");
        GenericRecord result = genericRecordBuilder.build(schema, json);

        assertThat(result.get("id").toString()).isEqualTo("ORD-001");
        assertThat(result.get("active")).isEqualTo(true);
    }

    @Test
    void build_nestedRecord_shouldRecurse() throws Exception {
        Schema inner = SchemaBuilder.record("Inner").namespace("test").fields()
            .name("value").type().stringType().noDefault()
            .endRecord();
        Schema outer = SchemaBuilder.record("Outer").namespace("test").fields()
            .name("inner").type(inner).noDefault()
            .endRecord();

        JsonNode json = objectMapper.readTree("{\"inner\":{\"value\":\"nested\"}}");
        GenericRecord result = genericRecordBuilder.build(outer, json);

        GenericRecord innerRecord = (GenericRecord) result.get("inner");
        assertThat(innerRecord.get("value").toString()).isEqualTo("nested");
    }

    @Test
    void build_nullableFieldWithNullJson_shouldSetNull() throws Exception {
        Schema schema = SchemaBuilder.record("Test").namespace("test").fields()
            .name("desc").type().unionOf().nullType().and().stringType().endUnion().nullDefault()
            .endRecord();

        JsonNode json = objectMapper.readTree("{\"desc\":null}");
        GenericRecord result = genericRecordBuilder.build(schema, json);

        Object desc = result.get("desc");
assertThat(desc == null || desc == org.apache.avro.JsonProperties.NULL_VALUE).isTrue();
    }

    @Test
    void build_missingFieldWithDefault_shouldUseDefault() throws Exception {
        Schema schema = SchemaBuilder.record("Test").namespace("test").fields()
            .name("status").type().stringType().stringDefault("PENDING")
            .endRecord();

        JsonNode json = objectMapper.readTree("{}");
        GenericRecord result = genericRecordBuilder.build(schema, json);

        assertThat(result.get("status").toString()).isEqualTo("PENDING");
    }

    @Test
    void build_missingRequiredField_shouldThrowDataConversionException() throws Exception {
        Schema schema = SchemaBuilder.record("Test").namespace("test").fields()
            .name("required").type().stringType().noDefault()
            .endRecord();

        JsonNode json = objectMapper.readTree("{}");

        assertThatThrownBy(() -> genericRecordBuilder.build(schema, json))
            .isInstanceOf(DataConversionException.class)
            .hasMessageContaining("required");
    }

    @Test
    void build_arrayField_shouldMapToList() throws Exception {
        Schema schema = SchemaBuilder.record("Test").namespace("test").fields()
            .name("tags").type().array().items().stringType().noDefault()
            .endRecord();

        JsonNode json = objectMapper.readTree("{\"tags\":[\"a\",\"b\"]}");
        GenericRecord result = genericRecordBuilder.build(schema, json);

        assertThat(result.get("tags")).isInstanceOf(List.class);
        assertThat((List<String>) result.get("tags")).containsExactlyElementsOf(List.of("a", "b"));
    }

    @Test
    void build_mapField_shouldMapToMap() throws Exception {
        Schema schema = SchemaBuilder.record("Test").namespace("test").fields()
            .name("meta").type().map().values().stringType().noDefault()
            .endRecord();

        JsonNode json = objectMapper.readTree("{\"meta\":{\"k\":\"v\"}}");
        GenericRecord result = genericRecordBuilder.build(schema, json);

        assertThat(result.get("meta")).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result.get("meta")).get("k")).isEqualTo("v");
    }
}
