package com.example.kafkaavro.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.avro.Schema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@RestController
@RequestMapping("/api/schema")
@Tag(name = "Schema Comparison", description = "APIs for comparing and testing schemas")
public class SchemaComparisonController {

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/compare")
    @Operation(
        summary = "Compare two schema versions",
        description = "Compares two schema versions and shows what changed (fields added, removed, modified)"
    )
    @ApiResponse(responseCode = "200", description = "Comparison completed successfully")
    @ApiResponse(responseCode = "404", description = "Schema or version not found")
    public ResponseEntity<?> compareSchemas(@RequestBody Map<String, Object> request) {
        try {
            String subject = (String) request.get("subject");
            int version1 = (int) request.get("version1");
            int version2 = (int) request.get("version2");
            
            // Get both schemas
            String url1 = schemaRegistryUrl + "/subjects/" + subject + "/versions/" + version1;
            String url2 = schemaRegistryUrl + "/subjects/" + subject + "/versions/" + version2;
            
            Map<String, Object> schema1Response = restTemplate.getForObject(url1, Map.class);
            Map<String, Object> schema2Response = restTemplate.getForObject(url2, Map.class);
            
            if (schema1Response == null || schema2Response == null) {
                return ResponseEntity.status(404).body(Map.of(
                    "error", "Schema version not found",
                    "subject", subject
                ));
            }
            
            String schema1String = (String) schema1Response.get("schema");
            String schema2String = (String) schema2Response.get("schema");
            
            Schema schema1 = new Schema.Parser().parse(schema1String);
            Schema schema2 = new Schema.Parser().parse(schema2String);
            
            // Compare schemas
            ComparisonResult result = compareSchemaFields(schema1, schema2);
            
            Map<String, Object> response = new HashMap<>();
            response.put("subject", subject);
            response.put("version1", version1);
            response.put("version2", version2);
            response.put("compatible", result.fieldsRemoved.isEmpty());
            response.put("changes", Map.of(
                "fieldsAdded", result.fieldsAdded,
                "fieldsRemoved", result.fieldsRemoved,
                "fieldsModified", result.fieldsModified
            ));
            response.put("summary", String.format(
                "Added: %d, Removed: %d, Modified: %d",
                result.fieldsAdded.size(),
                result.fieldsRemoved.size(),
                result.fieldsModified.size()
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Comparison failed",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/test-compatibility")
    @Operation(
        summary = "Test schema compatibility",
        description = "Tests if a new schema is compatible with the latest registered schema before registering it"
    )
    @ApiResponse(responseCode = "200", description = "Compatibility test completed")
    @ApiResponse(responseCode = "404", description = "Subject not found")
    public ResponseEntity<?> testCompatibility(@RequestBody Map<String, Object> request) {
        try {
            String subject = (String) request.get("subject");
            String newSchemaString = objectMapper.writeValueAsString(request.get("schema"));
            
            // Get latest schema
            String latestUrl = schemaRegistryUrl + "/subjects/" + subject + "/versions/latest";
            Map<String, Object> latestResponse;
            
            try {
                latestResponse = restTemplate.getForObject(latestUrl, Map.class);
            } catch (Exception e) {
                return ResponseEntity.status(404).body(Map.of(
                    "error", "Subject not found",
                    "subject", subject,
                    "message", "No existing schema to compare against"
                ));
            }
            
            String existingSchemaString = (String) latestResponse.get("schema");
            
            Schema existingSchema = new Schema.Parser().parse(existingSchemaString);
            Schema newSchema = new Schema.Parser().parse(newSchemaString);
            
            // Compare for compatibility
            ComparisonResult result = compareSchemaFields(existingSchema, newSchema);
            
            boolean compatible = result.fieldsRemoved.isEmpty();
            List<String> messages = new ArrayList<>();
            
            if (compatible) {
                messages.add("Schema is backward compatible with version " + latestResponse.get("version"));
                if (!result.fieldsAdded.isEmpty()) {
                    messages.add("New fields added: " + result.fieldsAdded);
                }
                if (!result.fieldsModified.isEmpty()) {
                    messages.add("Fields modified: " + result.fieldsModified.size());
                }
            } else {
                messages.add("Schema is NOT backward compatible");
                messages.add("Fields removed: " + result.fieldsRemoved);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("compatible", compatible);
            response.put("subject", subject);
            response.put("currentVersion", latestResponse.get("version"));
            response.put("compatibilityLevel", "BACKWARD");
            response.put("messages", messages);
            response.put("changes", Map.of(
                "fieldsAdded", result.fieldsAdded,
                "fieldsRemoved", result.fieldsRemoved,
                "fieldsModified", result.fieldsModified
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Compatibility test failed",
                "message", e.getMessage()
            ));
        }
    }

    private ComparisonResult compareSchemaFields(Schema schema1, Schema schema2) {
        ComparisonResult result = new ComparisonResult();
        
        if (schema1.getType() != Schema.Type.RECORD || schema2.getType() != Schema.Type.RECORD) {
            return result;
        }
        
        Map<String, Schema.Field> fields1 = new HashMap<>();
        Map<String, Schema.Field> fields2 = new HashMap<>();
        
        for (Schema.Field field : schema1.getFields()) {
            fields1.put(field.name(), field);
        }
        
        for (Schema.Field field : schema2.getFields()) {
            fields2.put(field.name(), field);
        }
        
        // Find added fields
        for (String fieldName : fields2.keySet()) {
            if (!fields1.containsKey(fieldName)) {
                result.fieldsAdded.add(fieldName);
            }
        }
        
        // Find removed fields
        for (String fieldName : fields1.keySet()) {
            if (!fields2.containsKey(fieldName)) {
                result.fieldsRemoved.add(fieldName);
            }
        }
        
        // Find modified fields
        for (String fieldName : fields1.keySet()) {
            if (fields2.containsKey(fieldName)) {
                Schema.Field field1 = fields1.get(fieldName);
                Schema.Field field2 = fields2.get(fieldName);
                
                if (!field1.schema().equals(field2.schema())) {
                    Map<String, String> modification = new HashMap<>();
                    modification.put("field", fieldName);
                    modification.put("oldType", field1.schema().getType().getName());
                    modification.put("newType", field2.schema().getType().getName());
                    result.fieldsModified.add(modification);
                }
            }
        }
        
        return result;
    }

    private static class ComparisonResult {
        List<String> fieldsAdded = new ArrayList<>();
        List<String> fieldsRemoved = new ArrayList<>();
        List<Map<String, String>> fieldsModified = new ArrayList<>();
    }
}
