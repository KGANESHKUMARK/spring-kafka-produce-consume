package com.example.kafkaavro.schema;

import com.example.kafkaavro.exception.SchemaFetchException;
import io.confluent.kafka.schemaregistry.client.SchemaMetadata;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import org.apache.avro.Schema;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class SchemaRegistryService {

    private final SchemaRegistryClient client;
    private final ConcurrentHashMap<String, Schema> schemaCache = new ConcurrentHashMap<>();

    public SchemaRegistryService(SchemaRegistryClient client) {
        this.client = client;
    }

    public Schema getKeySchema(String topic) {
        return getSchema(topic + "-key");
    }

    public Schema getValueSchema(String topic) {
        return getSchema(topic + "-value");
    }

    private Schema getSchema(String subject) {
        return schemaCache.computeIfAbsent(subject, s -> {
            try {
                SchemaMetadata metadata = client.getLatestSchemaMetadata(s);
                return new Schema.Parser().parse(metadata.getSchema());
            } catch (Exception e) {
                throw new SchemaFetchException(s, e);
            }
        });
    }
}
