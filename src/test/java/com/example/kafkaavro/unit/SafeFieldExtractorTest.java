package com.example.kafkaavro.unit;

import com.example.kafkaavro.mapper.GenericRecordBuilder;
import com.example.kafkaavro.util.AvroTypeHandler;
import com.example.kafkaavro.util.SafeFieldExtractor;
import com.example.kafkaavro.util.TestSchemaFactory;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@ExtendWith(SpringExtension.class)
@Import({AvroTypeHandler.class, GenericRecordBuilder.class})
@ActiveProfiles("test")
class SafeFieldExtractorTest {

    @Test
    void getDecimal_shouldReturnBigDecimalWithCorrectScale() {
        Schema schema = TestSchemaFactory.buildOrderValueSchema();
        GenericRecord record = TestSchemaFactory.buildOrderValueRecord(schema);
        SafeFieldExtractor extractor = new SafeFieldExtractor(record);

        BigDecimal result = extractor.getDecimal("amount");

        assertThat(result).isNotNull();
        assertThat(result.scale()).isEqualTo(4);
        assertThat(result).isEqualByComparingTo(new BigDecimal("123.4500"));
    }

    @Test
    void getDate_shouldReturnLocalDate() {
        Schema schema = TestSchemaFactory.buildOrderValueSchema();
        GenericRecord record = TestSchemaFactory.buildOrderValueRecord(schema);
        SafeFieldExtractor extractor = new SafeFieldExtractor(record);

        assertThat(extractor.getDate("tradeDate")).isEqualTo(LocalDate.of(2026, 3, 19));
    }

    @Test
    void getTimestamp_shouldReturnInstant() {
        Schema schema = TestSchemaFactory.buildOrderValueSchema();
        GenericRecord record = TestSchemaFactory.buildOrderValueRecord(schema);
        SafeFieldExtractor extractor = new SafeFieldExtractor(record);

        assertThat(extractor.getTimestamp("createdAt"))
            .isEqualTo(Instant.parse("2026-03-19T10:00:00Z"));
    }

    @Test
    void getTime_shouldReturnLocalTime() {
        Schema schema = TestSchemaFactory.buildOrderValueSchema();
        GenericRecord record = TestSchemaFactory.buildOrderValueRecord(schema);
        SafeFieldExtractor extractor = new SafeFieldExtractor(record);

        assertThat(extractor.getTime("timeField")).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void getNested_shouldReturnWrappedExtractor() {
        Schema schema = TestSchemaFactory.buildOrderValueSchema();
        GenericRecord record = TestSchemaFactory.buildOrderValueRecord(schema);
        SafeFieldExtractor extractor = new SafeFieldExtractor(record);

        SafeFieldExtractor nested = extractor.getNested("metadata");
        assertThat(nested).isNotNull();
        assertThat(nested.get("source").toString()).isEqualTo("web");
    }

    @Test
    void getArray_shouldReturnList() {
        Schema schema = TestSchemaFactory.buildOrderValueSchema();
        GenericRecord record = TestSchemaFactory.buildOrderValueRecord(schema);
        SafeFieldExtractor extractor = new SafeFieldExtractor(record);

        assertThat(extractor.getArray("tags")).contains("urgent", "retail");
    }

    @Test
    void get_nullField_shouldReturnNullWithoutException() {
        Schema schema = TestSchemaFactory.buildOrderValueSchema();
        GenericRecord record = TestSchemaFactory.buildOrderValueRecord(schema);
        SafeFieldExtractor extractor = new SafeFieldExtractor(record);

        assertThatNoException().isThrownBy(() -> extractor.get("description"));
        assertThat(extractor.get("description")).isNull();
    }

    @Test
    void getOptional_nullField_shouldReturnEmpty() {
        Schema schema = TestSchemaFactory.buildOrderValueSchema();
        GenericRecord record = TestSchemaFactory.buildOrderValueRecord(schema);
        SafeFieldExtractor extractor = new SafeFieldExtractor(record);

        assertThat(extractor.getOptional("description", String.class)).isEmpty();
    }

    @Test
    void getOptional_presentField_shouldReturnValue() {
        Schema schema = TestSchemaFactory.buildOrderValueSchema();
        GenericRecord record = TestSchemaFactory.buildOrderValueRecord(schema);
        SafeFieldExtractor extractor = new SafeFieldExtractor(record);

        Optional<Boolean> result = extractor.getOptional("active", Boolean.class);
        assertThat(result).isPresent().contains(true);
    }
}
